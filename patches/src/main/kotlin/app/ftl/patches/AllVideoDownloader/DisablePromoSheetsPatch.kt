package app.ftl.patches.videodownloader

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.util.ReferenceUtil

val disablePromoSheetsPatch = bytecodePatch(
    name = "Disable how-to and default browser sheets",
    description = "Prevents the How to Download sheet and the Set as Default Browser sheet from showing."
) {
    compatibleWith(VIDEO_DOWNLOADER_PACKAGE)

    execute {
        HowToDownloadPromptGateFingerprint.let { fingerprint ->
            fingerprint.method.addInstructions(
                fingerprint.instructionMatches[1].index + 1,
                """
                    const/4 p0, 0x0
                    return p0
                """
            )
        }

        DefaultBrowserSheetOnStartFingerprint.let { fingerprint ->
            val matches = fingerprint.instructionMatches
            val dialogField = ReferenceUtil.getFieldDescriptor(
                matches[1].getInstruction<ReferenceInstruction>().reference as FieldReference
            )

            fingerprint.method.addInstructionsWithLabels(
                matches[0].index + 1,
                """
                    iget-object v0, p0, $dialogField
                    if-eqz v0, :no_dialog
                    invoke-virtual {v0}, Landroid/app/Dialog;->dismiss()V
                    :no_dialog
                    return-void
                """
            )
        }
    }
}
