package app.ftl.patches.xfolder

import app.ftl.util.returnEarly
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

/**
 * Overrides the hardcoded default of a [Fingerprint] matched via the
 * string+literal+methodCall+move-result shape (see the getter fingerprints
 * in Fingerprints.kt). The literal is always the 2nd filter (index 1); its
 * destination register is read off the match so the replacement instruction
 * reuses whatever register the original build happened to assign.
 */
private fun BytecodePatchContext.overrideBooleanDefault(fingerprint: Fingerprint, newValue: Boolean) {
    val literalMatch = fingerprint.instructionMatches[1]
    val register = literalMatch.getInstruction<OneRegisterInstruction>().registerA

    fingerprint.method.replaceInstruction(
        literalMatch.index,
        "const/4 v$register, ${if (newValue) "0x1" else "0x0"}",
    )
}

@Suppress("unused")
val disableRateDialogPatch = bytecodePatch(
    name = "Disable Rate Us Dialog",
    description = "Prevents the in-app rate-us dialog from ever being shown.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_XFOLDER)

    execute {
        // Inserts return-void before the dialog helper is built, matching the
        // stock->modified diff exactly (Lp7/g is never constructed/shown).
        ShowRateDialogFingerprint.method.returnEarly()
    }
}

@Suppress("unused")
val changeCleanerAndMediaTabDefaultsPatch = bytecodePatch(
    name = "Change Cleaner & Media Tab Defaults",
    description = "Changes first-run defaults: hides the Image, Video, Audio and Cleaner tabs, " +
        "and shows hidden files by default. Only affects users who have never changed the " +
        "setting themselves - the preference override still takes priority.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_XFOLDER)

    execute {
        overrideBooleanDefault(UseImageDefaultFingerprint, false)
        overrideBooleanDefault(UseVideoDefaultFingerprint, false)
        overrideBooleanDefault(UseAudioDefaultFingerprint, false)
        overrideBooleanDefault(CleanerTabDefaultFingerprint, false)
        overrideBooleanDefault(ShowHiddenDefaultFingerprint, true)
    }
}
