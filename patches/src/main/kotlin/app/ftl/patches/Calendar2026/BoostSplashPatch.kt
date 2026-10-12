package app.ftl.patches.calendar2026

import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

@Suppress("unused")
val boostSplashPatch = bytecodePatch(
    name = "Boost splash",
    description = "Removes the splash screen delay."
) {
    compatibleWith(COMPATIBILITY_CALENDAR)

    execute {
        SplashOnResumeFingerprint.let {
            val match = it.instructionMatches.first()
            val register = match.getInstruction<OneRegisterInstruction>().registerA
            it.method.replaceInstruction(match.index, "const-wide/16 v$register, 0x0")
        }

        SplashStartTimerFingerprint.let {
            val match = it.instructionMatches.first()
            val register = match.getInstruction<OneRegisterInstruction>().registerA
            it.method.replaceInstruction(match.index, "const-wide/16 v$register, 0x0")
        }
    }
}
