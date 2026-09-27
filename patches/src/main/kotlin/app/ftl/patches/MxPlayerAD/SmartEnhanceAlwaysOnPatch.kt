package app.ftl.patches.mxplayerad

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.removeInstructions
import app.morphe.patcher.literal
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod

private const val SMART_ENHANCE_ALWAYS_ON_KEY = "smart_enhance_always_on"
private const val SMART_ENHANCE_DEFAULT_PCT_KEY = "smart_enhance_default_pct"

// Stock M9(): force-disables Smart Enhance (q=false, E0(-1)) on some internal reset
// path - exact trigger unconfirmed, only its shape matters here. This is the STOCK
// shape (single-arg E0(I)V, literal v1=-1) - Slider adds a *new* E0(IF)V overload
// alongside the untouched original rather than replacing it, so M9 keeps this exact
// shape regardless of whether Slider has already run. Anchored purely on opcode/
// literal shape, no obfuscated names read: CONST_4(0) -> SPUT_BOOLEAN(q) ->
// IGET_OBJECT(player field) -> CONST_4(-1) -> INVOKE_VIRTUAL(E0(I)V) ->
// INVOKE_VIRTUAL(icon refresh) -> RETURN_VOID.
// Verify uniqueness against a live dex before shipping (rule 6) - this exact 7-opcode
// run is plausible-but-unconfirmed to be singular across the whole class.
internal object SmartEnhanceForceMethodFingerprint : Fingerprint(
    returnType = "V",
    parameters = emptyList(),
    filters = listOf(
        opcode(Opcode.CONST_4),
        opcode(Opcode.SPUT_BOOLEAN, location = MatchAfterImmediately()),
        opcode(Opcode.IGET_OBJECT, location = MatchAfterImmediately()),
        literal(-1, location = MatchAfterImmediately()),
        opcode(Opcode.INVOKE_VIRTUAL, location = MatchAfterImmediately()), // E0(I)V
        opcode(Opcode.INVOKE_VIRTUAL, location = MatchAfterImmediately()), // icon refresh
        opcode(Opcode.RETURN_VOID, location = MatchAfterImmediately()),
    ),
)

// Real, stable Activity lifecycle override - never renamed, in any build, for the
// same reason SDK interface overrides (onProgressChanged etc.) aren't either.
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
        // --- M9(): flip force-disable to force-enable-at-default-% -----------------
        // Delegates entirely to Slider's patch_applySmartEnhancePercent(I)V, which
        // already handles the flag flip, icon refresh, live-label update and the
        // actual E0(IF)V call - M9 no longer needs to touch any of that itself.
        val forceMethod = SmartEnhanceForceMethodFingerprint.method
        val matches = SmartEnhanceForceMethodFingerprint.instructionMatches
        val activityScreenType = forceMethod.definingClass

        forceMethod.removeInstructions(matches[0].index, matches[6].index - matches[0].index + 1)
        forceMethod.addInstructions(
            matches[0].index,
            """
                const-string v0, "$SMART_ENHANCE_DEFAULT_PCT_KEY"
                invoke-static {v0}, $MOD_SETTINGS_CLASS->getInt(Ljava/lang/String;)I
                move-result v0
                invoke-virtual {p0, v0}, $activityScreenType->$ENHANCE_APPLY_PERCENT_METHOD(I)V
                return-void
            """.trimIndent(),
        )

        // NOT done here: onCreate() also sets `Llle;->q = false` on its very first
        // line in the real build (see the Always-On compare). Harmless to leave as
        // stock - the poller below corrects it within 300ms of the first tick, so
        // it's at most a one-frame stale icon state. Not worth its own fingerprint
        // for a cosmetic, self-correcting detail.

        // --- Fold the poller into ActivityScreen itself (self-as-Runnable) ---------
        // No new class (same constraint as removeRecycleBinPatch): ActivityScreen
        // implements Runnable itself and gets one new run()V method carrying the
        // repeat logic (confirmed no existing run()V on stock ActivityScreen),
        // scheduled via the real, unobfuscated View.postDelayed - no new fields
        // needed, and again delegates to patch_applySmartEnhancePercent(I)V for the
        // actual work.
        val activityScreen = mutableClassDefBy(activityScreenType)
            ?: throw PatchException("Could not resolve ActivityScreen class")
        val runnableType = "Ljava/lang/Runnable;"
        if (runnableType !in activityScreen.interfaces) activityScreen.interfaces.add(runnableType)

        val tickMethod = ImmutableMethod(
            activityScreen.type,
            "run",
            emptyList(),
            "V",
            AccessFlags.PUBLIC.value or AccessFlags.FINAL.value,
            null,
            null,
            MutableMethodImplementation(5),
        ).toMutable()

        tickMethod.addInstructions(
            0,
            """
                const-string v0, "$SMART_ENHANCE_ALWAYS_ON_KEY"
                invoke-static {v0}, $MOD_SETTINGS_CLASS->get(Ljava/lang/String;)Z
                move-result v0
                if-eqz v0, :reschedule

                const-string v0, "$SMART_ENHANCE_DEFAULT_PCT_KEY"
                invoke-static {v0}, $MOD_SETTINGS_CLASS->getInt(Ljava/lang/String;)I
                move-result v0
                invoke-virtual {p0, v0}, $activityScreenType->$ENHANCE_APPLY_PERCENT_METHOD(I)V

                :reschedule
                invoke-virtual {p0}, Landroid/app/Activity;->getWindow()Landroid/view/Window;
                move-result-object v1
                invoke-virtual {v1}, Landroid/view/Window;->getDecorView()Landroid/view/View;
                move-result-object v1
                const-wide/16 v2, 0x12c
                invoke-virtual {v1, p0, v2, v3}, Landroid/view/View;->postDelayed(Ljava/lang/Runnable;J)Z
                return-void
            """.trimIndent(),
        )
        activityScreen.methods.add(tickMethod)

        // --- Kick off the first tick at the very start of onCreate() ---------------
        // Window/DecorView are already attached before onCreate's own body runs, so
        // scheduling here (rather than at some specific player-construction site) is
        // both simpler and more robust than matching a generic
        // new-instance/invoke-direct/iput-object shape.
        val onCreateMethod = ActivityScreenOnCreateFingerprint.method
        onCreateMethod.addInstructions(
            0,
            "invoke-virtual {p0}, $activityScreenType->run()V",
        )
    }
}
