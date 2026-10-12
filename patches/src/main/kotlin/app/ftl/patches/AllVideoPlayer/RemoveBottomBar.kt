package app.ftl.patches.allvideoplayer

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation
import app.morphe.patcher.extensions.InstructionExtensions.removeInstruction
import app.morphe.patcher.literal
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode

private object HideBottomBarFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("L", "I"),
    filters = listOf(
        literal(0x28eb8b41, opcodes = listOf(Opcode.CONST)),
        methodCall(
            parameters = listOf("L", "J", "J", "F", "L", "L", "L", "I", "I"),
            returnType = "V",
            opcode = Opcode.INVOKE_STATIC_RANGE,
            location = InstructionLocation.MatchAfterWithin(10)
        )
    )
)

val hideBottomBarPatch = bytecodePatch(
    name = "Hide bottom bar",
    description = "Hides the bottom navigation bar.",
    default = true
) {
    compatibleWith(
        Compatibility(
            packageName = "com.allformatplayer.streamvideoplayer",
            name = "Video Player",
            targets = listOf(AppTarget(version = "1.4"))
        )
    )

    execute {
        HideBottomBarFingerprint.let {
            it.method.removeInstruction(it.instructionMatches[1].index)
        }
    }
}
