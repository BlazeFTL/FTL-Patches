package app.ftl.patches.mxplayerad

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.removeInstructions
import app.morphe.patcher.patch.bytecodePatch

private const val SMART_ENHANCE_ALWAYS_ON_KEY = "smart_enhance_always_on"
private const val SMART_ENHANCE_DEFAULT_PCT_KEY = "smart_enhance_default_pct"

// Real, stable Activity lifecycle override. A fresh video open builds a new
// ActivityScreen, which resets the flag directly in here and never goes through
// M9() at all - that reset path only runs for "next video" on an already-live
// activity (confirmed: this is a separate reset point from M9, found by testing).
internal object ActivityScreenOnCreateFingerprint : Fingerprint(
    definingClass = "Lcom/mxtech/videoplayer/ActivityScreen;",
    name = "onCreate",
    returnType = "V",
    parameters = listOf("Landroid/os/Bundle;"),
)

internal val smartEnhanceAlwaysOnPatch = bytecodePatch(
    name = "Smart Enhance Always On",
    description = "Applies Smart Enhance automatically on every video, at a default level " +
        "set in Mod Settings (still adjustable per-video via the Control Slider).",
    default = false,
) {
    compatibleWith(COMPATIBILITY_MX_PLAYER_AD)
    dependsOn(
        smartEnhanceControlSliderPatch,
        modSettingsPatch,
        modSettingFlagPatch(SMART_ENHANCE_ALWAYS_ON_KEY),
        modSettingFlagPatch(SMART_ENHANCE_DEFAULT_PCT_KEY),
    )

    execute {
        // SmartEnhanceForceMethodFingerprint now lives in SmartEnhanceControlSliderPatch.kt
        // (same package, no import needed) - Slider needs M9's identity too, for its
        // own lock/unlock fix, independent of whether this patch is installed.
        val forceMethod = SmartEnhanceForceMethodFingerprint.method
        val matches = SmartEnhanceForceMethodFingerprint.instructionMatches
        val activityScreenType = forceMethod.definingClass

        // --- M9() ("next video" reset): apply the default when Always On is on,
        // otherwise behave like stock (force off = 0%). Delegates entirely to
        // Slider's patch_applySmartEnhancePercent(I)V.
        forceMethod.removeInstructions(matches[0].index, matches[6].index - matches[0].index + 1)
        forceMethod.addInstructions(
            matches[0].index,
            """
                const-string v0, "$SMART_ENHANCE_ALWAYS_ON_KEY"
                invoke-static {v0}, $MOD_SETTINGS_CLASS->get(Ljava/lang/String;)Z
                move-result v0
                if-eqz v0, :off
                const-string v0, "$SMART_ENHANCE_DEFAULT_PCT_KEY"
                invoke-static {v0}, $MOD_SETTINGS_CLASS->getInt(Ljava/lang/String;)I
                move-result v0
                goto :apply
                :off
                const/4 v0, 0x0
                :apply
                invoke-virtual {p0, v0}, $activityScreenType->$ENHANCE_APPLY_PERCENT_METHOD(I)V
                return-void
            """.trimIndent(),
        )

        // --- onCreate(): prime state for a fresh video open ------------------------
        // patch_applySmartEnhancePercent(I)V already returns early if the player field
        // is null (added for exactly this reason), so calling it here is safe even
        // though the player doesn't exist yet at this point in onCreate - it still
        // correctly sets the on/off flag, the stored level, and refreshes the icon.
        // Slider's surfaceCreated-triggered forcer (added for the lock/unlock case)
        // then picks up that already-set state once the player/surface actually exist,
        // retrying across the same async-setup window a fresh open goes through -
        // so no separate retry loop is needed here.
        // onCreate has many registers here (confirmed 18 in the compare), which can put
        // p0 out of range for a plain invoke - move it to a low register first, the
        // same idiom stock's own Ha() uses for the same reason.
        ActivityScreenOnCreateFingerprint.method.addInstructions(
            0,
            """
                move-object/from16 v0, p0
                const-string v1, "$SMART_ENHANCE_ALWAYS_ON_KEY"
                invoke-static {v1}, $MOD_SETTINGS_CLASS->get(Ljava/lang/String;)Z
                move-result v1
                if-eqz v1, :skip
                const-string v1, "$SMART_ENHANCE_DEFAULT_PCT_KEY"
                invoke-static {v1}, $MOD_SETTINGS_CLASS->getInt(Ljava/lang/String;)I
                move-result v1
                invoke-virtual {v0, v1}, $activityScreenType->$ENHANCE_APPLY_PERCENT_METHOD(I)V
                :skip
            """.trimIndent(),
        )
    }
}
