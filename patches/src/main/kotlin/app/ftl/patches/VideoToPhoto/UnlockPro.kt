package app.ftl.patches.videotophoto

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

val COMPATIBILITY_VIDEO_TO_PHOTO = Compatibility(
    packageName = "kallossoft.videotophoto",
    name = "Video to Photo",
    targets = listOf(AppTarget(version = "5.0.1"))
)

@Suppress("unused")
val unlockProPatch = bytecodePatch(
    name = "Unlock Pro",
    description = "Unlocks Pro features.",
    default = true
) {
    compatibleWith(COMPATIBILITY_VIDEO_TO_PHOTO)

    execute {
        IsProStateFingerprint.let {
            val index = it.instructionMatches.last().index
            val register = it.method.getInstruction<OneRegisterInstruction>(index).registerA
            it.method.replaceInstruction(index, "const/4 v$register, 0x1")
        }

        listOf(
            AccessLevelIsActiveFingerprint,
            AccessLevelIsLifetimeFingerprint
        ).forEach {
            val index = it.instructionMatches.first().index
            val register = it.method.getInstruction<OneRegisterInstruction>(index).registerA
            it.method.addInstruction(index + 1, "const/4 v$register, 0x1")
        }
    }
}
