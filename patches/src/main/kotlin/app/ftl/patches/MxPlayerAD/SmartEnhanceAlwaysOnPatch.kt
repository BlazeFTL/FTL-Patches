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
private const val TICKS_FIELD = "patch_smartEnhanceAlwaysOnTicks"
private const val START_METHOD = "patch_smartEnhanceAlwaysOnStart"

// Stock M9(): the "next video" reset - force-disables Smart Enhance. Stock shape
// (single-arg E0(I)V; Slider only adds a new overload, never alters this call).
// Verify uniqueness against a live dex (rule 6).
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

// Real, stable Activity lifecycle override. A fresh video open builds a new
// ActivityScreen, which resets the flag in here and never goes through M9() -
// that path only runs for "next video" on an already-live activity.
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
        val forceMethod = SmartEnhanceForceMethodFingerprint.method
        val matches = SmartEnhanceForceMethodFingerprint.instructionMatches
        val activityScreenType = forceMethod.definingClass

        val activityScreen = mutableClassDefBy(activityScreenType)
            ?: throw PatchException("Could not resolve ActivityScreen class")

        activityScreen.fields.add(
            ImmutableField(activityScreenType, TICKS_FIELD, "I", AccessFlags.PRIVATE.value, null, null, null)
                .toMutable(),
        )

        // --- Burst re-apply after a fresh open --------------------------------------
        // The player is built asynchronously after onCreate and the app re-derives its
        // "off" state during setup, so a single call can lose the race. Re-apply every
        // 300ms, 9 times (~2.7s), then stop - it must NOT run forever or it would keep
        // overwriting whatever the user drags the Control Slider to.
        // ActivityScreen becomes its own Runnable (no new class possible here).
        val runnableType = "Ljava/lang/Runnable;"
        if (runnableType !in activityScreen.interfaces) activityScreen.interfaces.add(runnableType)

        val postBody = """
            new-instance v1, Landroid/os/Handler;
            invoke-static {}, Landroid/os/Looper;->getMainLooper()Landroid/os/Looper;
            move-result-object v2
            invoke-direct {v1, v2}, Landroid/os/Handler;-><init>(Landroid/os/Looper;)V
            const-wide/16 v2, 0x12c
            invoke-virtual {v1, p0, v2, v3}, Landroid/os/Handler;->postDelayed(Ljava/lang/Runnable;J)Z
        """.trimIndent()

        val runMethod = ImmutableMethod(
            activityScreen.type,
            "run",
            emptyList(),
            "V",
            AccessFlags.PUBLIC.value or AccessFlags.FINAL.value,
            null,
            null,
            MutableMethodImplementation(5),
        ).toMutable()
        runMethod.addInstructions(
            0,
            """
                iget v0, p0, $activityScreenType->$TICKS_FIELD:I
                const/16 v1, 0x9
                if-ge v0, v1, :done
                add-int/lit8 v0, v0, 0x1
                iput v0, p0, $activityScreenType->$TICKS_FIELD:I
                const-string v0, "$SMART_ENHANCE_ALWAYS_ON_KEY"
                invoke-static {v0}, $MOD_SETTINGS_CLASS->get(Ljava/lang/String;)Z
                move-result v0
                if-eqz v0, :reschedule
                const-string v0, "$SMART_ENHANCE_DEFAULT_PCT_KEY"
                invoke-static {v0}, $MOD_SETTINGS_CLASS->getInt(Ljava/lang/String;)I
                move-result v0
                invoke-virtual {p0, v0}, $activityScreenType->$ENHANCE_APPLY_PERCENT_METHOD(I)V
                :reschedule
                $postBody
                :done
                return-void
            """.trimIndent(),
        )
        activityScreen.methods.add(runMethod)

        // First post only - deliberately touches nothing else, since it runs at the very
        // start of onCreate before the activity is set up.
        val startMethod = ImmutableMethod(
            activityScreen.type,
            START_METHOD,
            emptyList(),
            "V",
            AccessFlags.PUBLIC.value or AccessFlags.FINAL.value,
            null,
            null,
            MutableMethodImplementation(5),
        ).toMutable()
        startMethod.addInstructions(
            0,
            """
                $postBody
                return-void
            """.trimIndent(),
        )
        activityScreen.methods.add(startMethod)

        // onCreate has .registers 18, so p0 is v16 - past the 4-bit limit of plain
        // invoke-virtual {p0}. The assembler then silently drops the method instead of
        // reporting an error ("Collection is empty"), so this must use the /range form
        // (same reason stock's own Ha() calls Sa() via invoke-virtual/range).
        ActivityScreenOnCreateFingerprint.method.addInstructions(
            0,
            "invoke-virtual/range {p0 .. p0}, $activityScreenType->$START_METHOD()V",
        )

        // --- M9() ("next video" reset): apply the default when Always On is on,
        // otherwise behave like stock (force off = 0%).
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
    }
}
