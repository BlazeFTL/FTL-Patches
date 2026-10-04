package app.ftl.patches.videodownloader

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

val hideScanAdMenuPatch = bytecodePatch(
    name = "Hide Scan (AD) menu item",
    description = "Removes the Scan (AD) row from the browser overflow menu."
) {
    compatibleWith(VIDEO_DOWNLOADER_PACKAGE)

    execute {
        ScanAdMenuBuilderFingerprint.let { fingerprint ->
            val method = fingerprint.method
            val stringIndex = fingerprint.instructionMatches.first().index

            val gateIndex = (stringIndex - 1 downTo 1).firstOrNull { index ->
                method.getInstruction(index).opcode == Opcode.IF_NEZ &&
                    method.getInstruction(index - 1).opcode == Opcode.IGET
            } ?: throw PatchException("Scan (AD) gate not found")

            val register = method.getInstruction<OneRegisterInstruction>(gateIndex).registerA

            method.addInstructions(gateIndex, "const/16 v$register, 0x1")
        }
    }
}
