package app.ftl.patches.mxplayerad

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.removeInstructions
import app.morphe.patcher.literal
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableField.Companion.toMutable
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.ImmutableField
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod

private const val SMART_ENHANCE_ALWAYS_ON_KEY = "smart_enhance_always_on"
private const val SMART_ENHANCE_DEFAULT_PCT_KEY = "smart_enhance_default_pct"
private const val STARTED_FIELD = "patch_smartEnhanceAlwaysOnStarted"

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
        val forceMethod = SmartEnhanceForceMethodFingerprint.method
        val matches = SmartEnhanceForceMethodFingerprint.instructionMatches
        val activityScreenType = forceMethod.definingClass

        val activityScreen = mutableClassDefBy(activityScreenType)
            ?: throw PatchException("Could not resolve ActivityScreen class")

        // --- New field: guards the poller against starting more than once ---------
        activityScreen.fields.add(
            ImmutableField(activityScreenType, STARTED_FIELD, "Z", AccessFlags.PRIVATE.value, null, null, null)
                .toMutable(),
        )

        // --- Fold the poller into ActivityScreen itself (self-as-Runnable) ---------
        // No new class (same constraint as removeRecycleBinPatch): ActivityScreen
        // implements Runnable itself and gets one new run()V method carrying the
        // repeat logic (confirmed no existing run()V on stock ActivityScreen),
        // scheduled via the real, unobfuscated View.postDelayed - no new fields
        // needed beyond the started-guard above, and delegates to
        // patch_applySmartEnhancePercent(I)V (added by Slider) for the actual work.
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

        // --- M9(): flip force-disable to force-enable-at-default-%, and start the --
        // poller loop the first time M9 ever fires for this Activity instance (guard
        // field above prevents restarting it - and once running, the loop's own
        // 300ms recheck covers every later video in the same session on its own, so
        // it never needs to be kicked off again). Delegates entirely to Slider's
        // patch_applySmartEnhancePercent(I)V for the actual flag/icon/E0 work.
        forceMethod.removeInstructions(matches[0].index, matches[6].index - matches[0].index + 1)
        forceMethod.addInstructions(
            matches[0].index,
            """
                iget-boolean v0, p0, $activityScreenType->$STARTED_FIELD:Z
                if-nez v0, :already_started
                const/4 v0, 0x1
                iput-boolean v0, p0, $activityScreenType->$STARTED_FIELD:Z
                invoke-virtual {p0}, $activityScreenType->run()V
                :already_started
                const-string v0, "$SMART_ENHANCE_DEFAULT_PCT_KEY"
                invoke-static {v0}, $MOD_SETTINGS_CLASS->getInt(Ljava/lang/String;)I
                move-result v0
                invoke-virtual {p0, v0}, $activityScreenType->$ENHANCE_APPLY_PERCENT_METHOD(I)V
                return-void
            """.trimIndent(),
        )
    }
}
