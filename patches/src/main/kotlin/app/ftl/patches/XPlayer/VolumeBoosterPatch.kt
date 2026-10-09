package app.ftl.patches.xplayer

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction

private const val BOOST_REGISTER = 8

private fun <T> List<T>.single(name: String): T =
    if (size == 1) first() else throw PatchException("$name: expected 1 match, found $size")

private fun MutableMethod.replaceWithTriple(index: Int) {
    val instruction = implementation!!.instructions[index]
    val registers = instruction as TwoRegisterInstruction

    when (instruction.opcode) {
        Opcode.SHL_INT_2ADDR -> replaceInstruction(
            index, "mul-int/lit8 v${registers.registerA}, v${registers.registerA}, 0x3"
        )
        Opcode.SHL_INT_LIT8, Opcode.MUL_INT_LIT8 -> {
            val expected = if (instruction.opcode == Opcode.SHL_INT_LIT8) 1 else 2
            if ((instruction as NarrowLiteralInstruction).narrowLiteral != expected) {
                throw PatchException("Unexpected literal in $definingClass->$name")
            }
            replaceInstruction(
                index, "mul-int/lit8 v${registers.registerA}, v${registers.registerB}, 0x3"
            )
        }
        else -> throw PatchException("Unexpected opcode ${instruction.opcode} in $definingClass->$name")
    }
}

@Suppress("unused")
val volumeBoosterPatch = bytecodePatch(
    name = "Volume Booster",
    description = "Raises the volume boost limit in the player and background playback."
) {
    compatibleWith(
        Compatibility(
            name = "XPlayer - Video Player",
            packageName = "video.player.videoplayer",
            targets = listOf(AppTarget(version = "2.9.2"))
        )
    )

    execute {
        VolumeBarInitFingerprint.matchAll().single("VolumeBarInit").let {
            it.method.replaceWithTriple(it.instructionMatches[1].index)
        }

        VolumeSwipeFingerprint.matchAll().single("VolumeSwipe").let {
            it.method.replaceWithTriple(it.instructionMatches[6].index)
            it.method.replaceWithTriple(it.instructionMatches[3].index)
        }

        VolumeBarMaxFingerprint.matchAll().single("VolumeBarMax").let {
            it.method.replaceWithTriple(it.instructionMatches[1].index)
        }

        VolumeKeyFingerprint.matchAll().single("VolumeKey").let {
            it.method.replaceWithTriple(it.instructionMatches[3].index)
            it.method.replaceWithTriple(it.instructionMatches[1].index)
        }

        MaxVolumeFingerprint.matchAll().single("MaxVolume").let {
            it.method.replaceWithTriple(it.instructionMatches[2].index)
        }

        VolumeSetFingerprint.matchAll().single("VolumeSet").let { match ->
            val method = match.method
            val matches = match.instructionMatches

            val maxRegister = (matches[0].instruction as OneRegisterInstruction).registerA
            val tempRegister = (matches[5].instruction as OneRegisterInstruction).registerA
            val valueRegister = (matches[7].instruction as OneRegisterInstruction).registerA

            val afterPut = matches[7].index + 1
            method.addInstructionsWithLabels(
                afterPut,
                """
                    sub-int v$tempRegister, v$valueRegister, v$maxRegister
                    if-lez v$tempRegister, :skip
                    mul-int/lit8 v$tempRegister, v$tempRegister, 0x2
                    add-int/2addr v$valueRegister, v$tempRegister
                """,
                ExternalLabel("skip", method.implementation!!.instructions[afterPut])
            )
            method.addInstruction(matches[6].index, "shr-int/lit8 v$maxRegister, v$maxRegister, 0x1")
            method.addInstruction(matches[0].index + 1, "mul-int/lit8 v$maxRegister, v$maxRegister, 0x2")
        }

        ServiceVolumeBoostFingerprint.matchAll().single("ServiceVolumeBoost").let { match ->
            val method = match.method
            val matches = match.instructionMatches

            val locals = method.implementation!!.registerCount - method.parameterTypes.size - 1
            if (locals <= BOOST_REGISTER) {
                throw PatchException("Not enough registers in ${method.definingClass}->${method.name}")
            }

            val levelRegister = (matches[2].instruction as OneRegisterInstruction).registerA
            val maxRegister = (matches[5].instruction as OneRegisterInstruction).registerA
            val floatIndex = matches[9].index
            val volumeRegister = (matches[9].instruction as TwoRegisterInstruction).registerA

            method.replaceInstruction(floatIndex, "int-to-float v$volumeRegister, v$BOOST_REGISTER")
            method.addInstructionsWithLabels(
                floatIndex,
                """
                    sub-int v$volumeRegister, v$BOOST_REGISTER, v$maxRegister
                    if-lez v$volumeRegister, :skip
                    mul-int/lit8 v$volumeRegister, v$volumeRegister, 0x2
                    add-int/2addr v$BOOST_REGISTER, v$volumeRegister
                """,
                ExternalLabel("skip", method.implementation!!.instructions[floatIndex])
            )
            method.addInstruction(matches[2].index + 1, "move v$BOOST_REGISTER, v$levelRegister")
        }
    }
}
