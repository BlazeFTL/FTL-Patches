package app.ftl.patches.alldownloader

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.InstructionLocation.MatchAfterWithin
import app.morphe.patcher.checkCast
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.intOption
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

private object SplashAdGateFingerprint : Fingerprint(
    definingClass = SPLASH_ACTIVITY_CLASS,
    name = "onCreate",
    returnType = "V",
    parameters = listOf("Landroid/os/Bundle;"),
    filters = listOf(
        opcode(Opcode.INVOKE_STATIC),
        opcode(Opcode.MOVE_RESULT, MatchAfterImmediately()),
        opcode(Opcode.IF_EQZ, MatchAfterImmediately()),
        opcode(Opcode.NEW_INSTANCE, MatchAfterImmediately()),
        methodCall(smali = "Landroid/os/CountDownTimer;->start()Landroid/os/CountDownTimer;"),
    ),
)

private object HideNavigationFlagsFingerprint : Fingerprint(
    definingClass = SPLASH_ACTIVITY_CLASS,
    name = "onCreate",
    returnType = "V",
    parameters = listOf("Landroid/os/Bundle;"),
    filters = listOf(
        methodCall(smali = "Landroid/view/View;->getSystemUiVisibility()I"),
        opcode(Opcode.OR_INT_LIT16, MatchAfterWithin(1)),
        methodCall(smali = "Landroid/view/View;->setSystemUiVisibility(I)V", location = MatchAfterImmediately()),
    ),
)

private object SplashProceedFingerprint : Fingerprint(
    definingClass = SPLASH_ACTIVITY_CLASS,
    returnType = "V",
    parameters = listOf(),
    filters = listOf(
        methodCall(smali = "Landroid/app/Activity;->finish()V"),
    ),
)

private object SplashCountdownTimerCtorFingerprint : Fingerprint(
    name = "<init>",
    returnType = "V",
    parameters = listOf(SPLASH_ACTIVITY_CLASS),
    filters = listOf(
        methodCall(smali = "Landroid/os/CountDownTimer;-><init>(JJ)V"),
    ),
)

private object SplashCountdownTimerOnFinishFingerprint : Fingerprint(
    classFingerprint = SplashCountdownTimerCtorFingerprint,
    name = "onFinish",
    returnType = "V",
    parameters = listOf(),
    filters = listOf(
        checkCast(SPLASH_ACTIVITY_CLASS),
    ),
)

private object SplashCountdownTimerOnTickFingerprint : Fingerprint(
    classFingerprint = SplashCountdownTimerCtorFingerprint,
    name = "onTick",
    returnType = "V",
    parameters = listOf("J"),
    filters = listOf(
        checkCast(SPLASH_ACTIVITY_CLASS),
    ),
)

val boostSplashScreenPatch = bytecodePatch(
    name = "Boost Splash Screen",
    description = "Fixes Remove Ads And Remove Ads Lite Gettings Stuck In Splash Screen " +
        "Useless if you also select skip splash and language activity patch. " +
        "Also stops the splash from hiding the on-screen navigation buttons.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_ALL_VIDEO_DOWNLOADER)

    val boostDelayMs by intOption(
        key = "boostDelayMs",
        default = 100,
        title = "Splash duration (ms)",
        description = "How long the splash screen stays visible before continuing.",
        validator = { it != null && it in 1..10000 },
    )

    execute {
        val delay = boostDelayMs ?: 100

        SplashAdGateFingerprint.let { fingerprint ->
            val matches = fingerprint.instructionMatches
            val invokeIndex = matches[0].index
            val moveResultIndex = matches[1].index
            val gateReg = matches[1].getInstruction<OneRegisterInstruction>().registerA

            fingerprint.method.replaceInstruction(invokeIndex, "const/16 v$gateReg, 0x1")
            fingerprint.method.replaceInstruction(moveResultIndex, "nop")
        }

        HideNavigationFlagsFingerprint.let { fingerprint ->
            val setVisibilityMatch = fingerprint.instructionMatches.last()
            val flagsReg = setVisibilityMatch.getInstruction<FiveRegisterInstruction>().registerD

            fingerprint.method.addInstructions(setVisibilityMatch.index, "const/4 v$flagsReg, 0x0")
        }

        SplashCountdownTimerCtorFingerprint.let { fingerprint ->
            val superCallMatch = fingerprint.instructionMatches.first()
            val durationReg = superCallMatch.getInstruction<FiveRegisterInstruction>().registerD

            fingerprint.method.addInstructions(superCallMatch.index, "const-wide/16 v$durationReg, $delay")
        }

        val proceedMethod = SplashProceedFingerprint.method
        val proceedCallSmali = "${proceedMethod.definingClass}->${proceedMethod.name}()V"

        SplashCountdownTimerOnFinishFingerprint.let { fingerprint ->
            val castMatch = fingerprint.instructionMatches.first()
            val splashReg = castMatch.getInstruction<OneRegisterInstruction>().registerA

            fingerprint.method.addInstructions(
                castMatch.index + 1,
                """
                    invoke-virtual {v$splashReg}, $proceedCallSmali
                    return-void
                """.trimIndent(),
            )
        }

        SplashCountdownTimerOnTickFingerprint.let { fingerprint ->
            val castMatch = fingerprint.instructionMatches.first()

            fingerprint.method.addInstructions(castMatch.index + 1, "return-void")
        }
    }
}
