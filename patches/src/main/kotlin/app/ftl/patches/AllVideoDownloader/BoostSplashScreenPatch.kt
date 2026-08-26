package app.ftl.patches.alldownloader

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.InstructionLocation.MatchAfterWithin
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.literal
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.removeInstructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.intOption
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

/**
 * The ad-gate check (`Lsc/w;->J(Landroid/content/Context;)Z`) and the CountDownTimer
 * subclass it constructs (`SplashActivity$c`) are both obfuscated - neither is pinned
 * here. Instead the anchor is the real, app-specific total-duration literal (0xbb8 =
 * 3000ms), which occurs exactly once in this class. The preceding invoke-static /
 * move-result / if-eqz / new-instance opcodes are matched immediately before it purely
 * structurally (no signatures pinned), so the whole chain only lines up at the one
 * real gate-plus-countdown site even though each opcode alone is common.
 */
private object SplashCountdownFingerprint : Fingerprint(
    definingClass = SPLASH_ACTIVITY_CLASS,
    name = "onCreate",
    returnType = "V",
    parameters = listOf("Landroid/os/Bundle;"),
    filters = listOf(
        opcode(Opcode.INVOKE_STATIC),
        opcode(Opcode.MOVE_RESULT, MatchAfterImmediately()),
        opcode(Opcode.IF_EQZ, MatchAfterImmediately()),
        opcode(Opcode.NEW_INSTANCE, MatchAfterImmediately()),
        literal(0xbb8, opcodes = listOf(Opcode.CONST_WIDE_16), location = MatchAfterImmediately()),
    ),
)

/**
 * Nav-bar hiding runs entirely through real, unobfuscated Android SDK calls -
 * View.getSystemUiVisibility()/setSystemUiVisibility(I) - so nothing obfuscated is
 * pinned. Both calls plus the or-int/lit16 flag combine between them are each unique
 * in this class, so the chain needs no class/field/method name from the app itself.
 */
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

/**
 * SplashActivity's own "proceed past the splash" method - builds the Intent to
 * MainActivity/LanguageActivity, starts it, and finishes this Activity. Its own
 * name is a single obfuscated letter, so it's found instead by the one call in
 * the whole class to the real, unobfuscated `Activity.finish()`.
 */
private object SplashProceedFingerprint : Fingerprint(
    definingClass = SPLASH_ACTIVITY_CLASS,
    returnType = "V",
    parameters = listOf(),
    filters = listOf(
        methodCall(smali = "Landroid/app/Activity;->finish()V"),
    ),
)

/**
 * Locates the countdown timer's own obfuscated inner class - not by its name, but
 * by its constructor: a real CountDownTimer subclass whose own constructor takes
 * exactly (SplashActivity, long, long) and delegates into CountDownTimer's real
 * (J, J) constructor. That signature exists on only this one class in the app.
 */
private object SplashCountdownTimerCtorFingerprint : Fingerprint(
    name = "<init>",
    returnType = "V",
    parameters = listOf(SPLASH_ACTIVITY_CLASS, "J", "J"),
    filters = listOf(
        methodCall(smali = "Landroid/os/CountDownTimer;-><init>(JJ)V"),
    ),
)

/**
 * onFinish()/onTick(J) are real CountDownTimer overrides, so their names can't be
 * obfuscated regardless of build - only the enclosing class (resolved above) was
 * ever obfuscated. onFinish's own body still runs the same ad-gate as onCreate,
 * then either shows an interstitial or falls back to another ad call - if a
 * separate ad-removal patch has stripped those ad SDK classes, that callback
 * never fires and the app hangs on the (now very brief) splash forever.
 */
private object SplashCountdownTimerOnFinishFingerprint : Fingerprint(
    classFingerprint = SplashCountdownTimerCtorFingerprint,
    name = "onFinish",
    returnType = "V",
    parameters = listOf(),
    filters = listOf(
        // The synthetic back-reference to the outer SplashActivity - obfuscated
        // field name, real field type, read back off the match instead of pinned.
        fieldAccess(
            definingClass = "this",
            type = SPLASH_ACTIVITY_CLASS,
            opcode = Opcode.IGET_OBJECT,
        ),
    ),
)

private object SplashCountdownTimerOnTickFingerprint : Fingerprint(
    classFingerprint = SplashCountdownTimerCtorFingerprint,
    name = "onTick",
    returnType = "V",
    parameters = listOf("J"),
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
        SplashCountdownFingerprint.let { fingerprint ->
            val matches = fingerprint.instructionMatches
            val invokeIndex = matches[0].index
            val moveResultIndex = matches[1].index
            val durationMatch = matches[4]
            val durationReg = durationMatch.getInstruction<OneRegisterInstruction>().registerA
            val delay = boostDelayMs ?: 100

            // Force the ad-gated branch to always take the "build and start the
            // countdown" path (p1 is the Bundle param, free to reuse - the real
            // invoke-static/move-result pair it replaces did the same thing).
            fingerprint.method.replaceInstruction(invokeIndex, "const/4 p1, 0x1")
            fingerprint.method.replaceInstruction(moveResultIndex, "nop")
            // Shrink the countdown itself from 3s down to the configured delay.
            fingerprint.method.replaceInstruction(durationMatch.index, "const-wide/16 v$durationReg, $delay")
        }

        HideNavigationFlagsFingerprint.let { fingerprint ->
            val setVisibilityMatch = fingerprint.instructionMatches.last()
            val flagsReg = setVisibilityMatch.getInstruction<FiveRegisterInstruction>().registerD

            // Zero the flags right before they're applied, regardless of whatever the
            // (obfuscated) condition guarding this block decided - nav/status bars stay visible.
            fingerprint.method.addInstructions(setVisibilityMatch.index, "const/4 v$flagsReg, 0x0")
        }

        // Route onFinish() straight to the real post-splash navigation instead of
        // through the ad-gate/ad-show calls (see fingerprint doc above for why).
        val proceedMethod = SplashProceedFingerprint.method
        val proceedCallSmali = "${proceedMethod.definingClass}->${proceedMethod.name}()V"

        SplashCountdownTimerOnFinishFingerprint.let { fingerprint ->
            val backRefField = fingerprint.instructionMatches.first()
                .getInstruction<ReferenceInstruction>().reference as FieldReference
            val backRefFieldSmali = "${backRefField.definingClass}->${backRefField.name}:${backRefField.type}"
            val method = fingerprint.method
            val total = method.implementation!!.instructions.size

            method.removeInstructions(0, total)
            method.addInstructions(
                0,
                """
                    iget-object v0, p0, $backRefFieldSmali
                    invoke-virtual {v0}, $proceedCallSmali
                    return-void
                """.trimIndent(),
            )
        }

        // onTick only fires if a future user sets the delay above the 1s countdown
        // interval - emptied so it can never re-trigger the ad-gate/cancel path either.
        SplashCountdownTimerOnTickFingerprint.let { fingerprint ->
            val method = fingerprint.method
            val total = method.implementation!!.instructions.size

            method.removeInstructions(0, total)
            method.addInstructions(0, "return-void")
        }
    }
}
