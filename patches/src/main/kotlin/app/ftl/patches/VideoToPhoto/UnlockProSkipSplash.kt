package app.ftl.patches.videotophoto

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

internal val VIDEO_TO_PHOTO_COMPATIBILITY = Compatibility(
    name = "Video To Photo",
    packageName = "kallossoft.videotophoto",
    targets = listOf(
        AppTarget(version = "5.0.2")
    )
)

private fun MutableMethod.returnTrue() = addInstructions(0, "const/4 v0, 0x1\nreturn v0")

private fun MutableMethod.setDelay(index: Int, register: Int, millis: Int) =
    replaceInstruction(index, "const-wide/16 v$register, 0x${millis.toString(16)}")

@Suppress("unused")
val unlockProSkipOnboardingPatch = bytecodePatch(
    name = "Unlock Pro and skip onboarding",
    description = "Unlocks Pro features and skips the onboarding screens."
) {
    compatibleWith(VIDEO_TO_PHOTO_COMPATIBILITY)

    execute {
        AccessLevelIsActiveFingerprint.method.returnTrue()
        AccessLevelIsLifetimeFingerprint.method.returnTrue()
        AdaptyProfileUpdateFingerprint.method.returnTrue()
        IsProFingerprint.method.returnTrue()
        SkipOnboardingPaywallFingerprint.method.returnTrue()

        OnboardingCompleteFlagFingerprint.let {
            val match = it.instructionMatches.last()
            val register = match.getInstruction<OneRegisterInstruction>().registerA
            it.method.addInstructions(match.index + 1, "const/16 v$register, 0x1")
        }

        ProLockedUiFingerprint.classDef.let { classDef ->
            classDef.methods.forEach { method ->
                val instructions = method.implementation?.instructions ?: return@forEach

                instructions.withIndex()
                    .filter { (_, instruction) ->
                        instruction.opcode == Opcode.IGET_BOOLEAN &&
                            (instruction as ReferenceInstruction).reference.let { reference ->
                                reference is FieldReference &&
                                    reference.definingClass == classDef.type &&
                                    reference.type == "Z"
                            }
                    }
                    .map { it.index }
                    .asReversed()
                    .forEach { index ->
                        val register = method.getInstruction<OneRegisterInstruction>(index).registerA
                        method.addInstructions(index + 1, "const/16 v$register, 0x1")
                    }
            }
        }
    }
}

@Suppress("unused")
val skipSplashDelayPatch = bytecodePatch(
    name = "Skip splash delay",
    description = "Shortens the splash screen waits."
) {
    compatibleWith(VIDEO_TO_PHOTO_COMPATIBILITY)

    execute {
        SplashTimeoutFingerprint.let {
            val match = it.instructionMatches.first()
            it.method.setDelay(match.index, match.getInstruction<OneRegisterInstruction>().registerA, 100)
        }

        SplashJobDelayFingerprint.let {
            val match = it.instructionMatches[1]
            it.method.setDelay(match.index, match.getInstruction<OneRegisterInstruction>().registerA, 1)
        }

        SplashInitTimeoutFingerprint.let {
            val match = it.instructionMatches[1]
            it.method.setDelay(match.index, match.getInstruction<OneRegisterInstruction>().registerA, 100)
        }
    }
}
