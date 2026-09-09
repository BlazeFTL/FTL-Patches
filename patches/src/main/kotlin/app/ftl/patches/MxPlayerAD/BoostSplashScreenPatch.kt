package app.ftl.patches.mxplayerad

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.removeInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.literal
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

/**
 * Matches Lxbm;->b()Z ("shouldWelcomeAdEnabled") purely by its real, unobfuscated string
 * literals - the remote-config key plus the two cached ad-window timestamps it reads.
 * Neither the class (Lxbm) nor the method name (b) is pinned - both reshuffle every
 * build, per the Lo/mg->Lo/sg lesson. Anchoring on the method itself instead of only at
 * a call site patches every caller in one place - including Lxbm;->a()Z, which also
 * calls b() internally.
 */
private object WelcomeAdEnabledFingerprint : Fingerprint(
    returnType = "Z",
    parameters = emptyList(),
    strings = listOf(
        "shouldWelcomeAdEnabled",
        "welcomeAdStartTime",
        "welcomeAdEndTime",
    ),
)

/**
 * Matches the same b()Z check inlined into ActivityWelcomeMX's own constructor (stock:
 * `invoke-static {}, Lxbm;->b()Z` / move-result / const/4 v1,0x1 / xor-int/2addr, the
 * xor result then written to the (also obfuscated) q0 field). ACTIVITY_WELCOME_MX_CLASS
 * and "<init>" are both real and stable; the obfuscated call itself is matched by shape
 * only (static, 0 params, returns Z), never by name. Kept as a second, explicit edit
 * alongside [WelcomeAdEnabledFingerprint] to mirror the reference build exactly, even
 * though patching b() to always return false already makes this site compute the same
 * q0 value on its own.
 */
private object WelcomeAdEnabledCheckFingerprint : Fingerprint(
    definingClass = ACTIVITY_WELCOME_MX_CLASS,
    name = "<init>",
    returnType = "V",
    parameters = emptyList(),
    filters = listOf(
        methodCall(
            parameters = emptyList(),
            returnType = "Z",
            opcodes = listOf(Opcode.INVOKE_STATIC),
        ),
        opcode(Opcode.MOVE_RESULT, location = MatchAfterImmediately()),
        literal(1, location = MatchAfterImmediately()),
        opcode(Opcode.XOR_INT_2ADDR, location = MatchAfterImmediately()),
    ),
)

val boostSplashScreenPatch = bytecodePatch(
    name = "Boost Splash Screen Time",
    description = "Disables MX Player's welcome/splash ad so playback starts immediately.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_MX_PLAYER_AD)

    execute {
        // 1. Kill the check at its source: short-circuits Lxbm;->b()Z to false before
        // any of the original remote-config/time-window logic runs. Original body is
        // left in place below the return, just unreachable.
        WelcomeAdEnabledFingerprint.method.addInstructions(
            0,
            """
                const/4 v0, 0x0
                return v0
            """.trimIndent(),
        )

        // 2. Also hardcode the inlined call in ActivityWelcomeMX's constructor, matching
        // the reference build's own diff there. replaceInstruction() on the call itself
        // (not remove+insert) keeps any label bound to it landing on the new code
        // instead of getting stranded.
        val method = WelcomeAdEnabledCheckFingerprint.method
        val matches = WelcomeAdEnabledCheckFingerprint.instructionMatches

        val invokeIndex = matches[0].index
        val resultRegister = matches[1].getInstruction<OneRegisterInstruction>().registerA

        method.replaceInstruction(invokeIndex, "const/4 v$resultRegister, 0x0")
        method.removeInstruction(invokeIndex + 1)
    }
}
