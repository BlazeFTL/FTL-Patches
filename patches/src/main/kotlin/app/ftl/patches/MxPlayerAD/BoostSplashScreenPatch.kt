package app.ftl.patches.mxplayerad

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.InstructionLocation.MatchAfterWithin
import app.morphe.patcher.MethodCallFilter
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.removeInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.literal
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

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
 * though patching b() to always return true already makes this site compute the same
 * q0 value (false, welcome ad flow off) on its own.
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

private object EntryRouteFingerprint : Fingerprint(
    definingClass = ACTIVITY_WELCOME_MX_CLASS,
    returnType = "V",
    parameters = emptyList(),
    filters = listOf(
        methodCall(
            definingClass = "this",
            parameters = emptyList(),
            returnType = "V",
            opcodes = listOf(Opcode.INVOKE_VIRTUAL),
        ),
        methodCall(
            definingClass = "this",
            parameters = emptyList(),
            returnType = "Z",
            opcodes = listOf(Opcode.INVOKE_VIRTUAL),
        ),
        methodCall(
            definingClass = "this",
            parameters = listOf("Z"),
            returnType = "V",
            opcodes = listOf(Opcode.INVOKE_VIRTUAL),
        ),
        methodCall(
            smali = "Lcom/google/android/ump/UserMessagingPlatform;->getConsentInformation(Landroid/content/Context;)Lcom/google/android/ump/ConsentInformation;",
        ),
    ),
)

private object TabsRepoPrefetchFingerprint : Fingerprint(
    definingClass = ACTIVITY_WELCOME_MX_CLASS,
    name = "onCreate",
    returnType = "V",
    parameters = listOf("Landroid/os/Bundle;"),
    filters = listOf(
        methodCall(
            definingClass = "Lcom/mxtech/videoplayer/ad/online/tab/repository/TabsRepositoryBridge;",
            parameters = emptyList(),
            returnType = "V",
            opcodes = listOf(Opcode.INVOKE_VIRTUAL),
        ),
    ),
)

private object ForceLogoutRequestFingerprint : Fingerprint(
    definingClass = ACTIVITY_WELCOME_MX_CLASS,
    name = "onCreate",
    returnType = "V",
    parameters = listOf("Landroid/os/Bundle;"),
    filters = listOf(
        string("FORCE_LOGOUT_LEGACY_ACCOUNT"),
        methodCall(
            parameters = listOf("Ljava/lang/Runnable;"),
            returnType = "Ljava/util/concurrent/Future;",
            opcodes = listOf(Opcode.INVOKE_VIRTUAL),
            location = MatchAfterWithin(12),
        ),
    ),
)

private object ChannelUnreadFetchFingerprint : Fingerprint(
    definingClass = ACTIVITY_WELCOME_MX_CLASS,
    name = "onCreate",
    returnType = "V",
    parameters = listOf("Landroid/os/Bundle;"),
    filters = listOf(
        methodCall(
            definingClass = "Lcom/mxtech/videoplayer/ad/online/mxchannel/utils/MXChannelUnreadHelper;",
            parameters = emptyList(),
            returnType = "V",
            opcodes = listOf(Opcode.INVOKE_STATIC),
        ),
    ),
)

private object ServerPreconnectFingerprint : Fingerprint(
    definingClass = ACTIVITY_WELCOME_MX_CLASS,
    name = "onCreate",
    returnType = "V",
    parameters = listOf("Landroid/os/Bundle;"),
    filters = listOf(
        string("https://www.amazon.in"),
        methodCall(
            smali = "Ljava/util/concurrent/ExecutorService;->submit(Ljava/lang/Runnable;)Ljava/util/concurrent/Future;",
            location = MatchAfterWithin(12),
        ),
    ),
)

private object FatafatBundleInitFingerprint : Fingerprint(
    definingClass = ACTIVITY_WELCOME_MX_CLASS,
    name = "onCreate",
    returnType = "V",
    parameters = listOf("Landroid/os/Bundle;"),
    filters = listOf(
        methodCall(
            definingClass = "Lcom/mxtech/videoplayer/ad/online/fatafat/tarzen/core/",
            returnType = "V",
            opcodes = listOf(Opcode.INVOKE_STATIC),
        ),
        methodCall(
            definingClass = "Lcom/mxtech/videoplayer/ad/online/fatafat/tarzen/bundle/",
            returnType = "V",
            opcodes = listOf(Opcode.INVOKE_STATIC),
        ),
    ),
)

private object ApiRequestExecuteFingerprint : Fingerprint(
    definingClass = "Lcom/mxtech/videoplayer/ad/online/apiclient/",
    returnType = "Ljava/lang/Object;",
    parameters = emptyList(),
    filters = listOf(
        string("POST"),
        string("PUT"),
    ),
)

private const val STARTUP_NETWORK_BLOCK_MS = 20000L

private val SPLASH_LOGO_DRAWABLES = listOf(
    "res/drawable/pvui_logo_prime_video_splash.xml" to """
        <?xml version="1.0" encoding="utf-8"?>
        <vector xmlns:android="http://schemas.android.com/apk/res/android"
            android:width="1dp"
            android:height="1dp"
            android:viewportWidth="1"
            android:viewportHeight="1" />
    """.trimIndent(),
    "res/drawable/background_splash_screen.xml" to """
        <?xml version="1.0" encoding="utf-8"?>
        <shape xmlns:android="http://schemas.android.com/apk/res/android"
            android:shape="rectangle">
            <solid android:color="?android:attr/colorBackground" />
        </shape>
    """.trimIndent(),
)

private val removeSplashLogoResourcePatch = resourcePatch {
    execute {
        SPLASH_LOGO_DRAWABLES.forEach { (path, content) ->
            runCatching { get(path) }
                .getOrNull()
                ?.takeIf { it.exists() }
                ?.writeText(content)
        }
    }
}

val boostSplashScreenPatch = bytecodePatch(
    name = "Boost Splash Screen Time",
    description = "Dont Apply These Patches Over Some Other Persons Mod, Use Untouched Apk. " +
        "Disables MX Player's welcome/splash ad so playback starts immediately. " +
        "Also removes startup network calls and the splash logo.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_MX_PLAYER_AD)

    dependsOn(removeSplashLogoResourcePatch)

    execute {
        // 1. Kill the check at its source: short-circuits Lxbm;->b()Z to true before
        // any of the original remote-config/time-window logic runs. Stock b() ends in
        // `xor-int/lit8 v0, v2, 0x1` where v2 = 1 only while the welcome ad window is
        // active, so true means "welcome ad off". Original body is left in place below
        // the return, just unreachable.
        WelcomeAdEnabledFingerprint.method.addInstructions(
            0,
            """
                const/4 v0, 0x1
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

        method.replaceInstruction(invokeIndex, "const/4 v$resultRegister, 0x1")
        method.removeInstruction(invokeIndex + 1)

        EntryRouteFingerprint.let { fingerprint ->
            val calls = fingerprint.instructionMatches
            fun calledName(index: Int) =
                (calls[index].getInstruction<ReferenceInstruction>().reference as MethodReference).name

            val owner = fingerprint.method.definingClass

            fingerprint.method.addInstructions(
                0,
                """
                    invoke-virtual {p0}, $owner->${calledName(0)}()V
                    invoke-virtual {p0}, $owner->${calledName(1)}()Z
                    const/4 v0, 0x0
                    invoke-virtual {p0, v0}, $owner->${calledName(2)}(Z)V
                    return-void
                """.trimIndent(),
            )
        }

        listOf(
            TabsRepoPrefetchFingerprint,
            ForceLogoutRequestFingerprint,
            ChannelUnreadFetchFingerprint,
            ServerPreconnectFingerprint,
            FatafatBundleInitFingerprint,
        ).forEach { fingerprint ->
            val target = fingerprint.method
            fingerprint.instructionMatches
                .filter { it.filter is MethodCallFilter }
                .forEach { target.replaceInstruction(it.index, "nop") }
        }

        ApiRequestExecuteFingerprint.method.addInstructionsWithLabels(
            0,
            """
                invoke-static {}, Landroid/os/SystemClock;->elapsedRealtime()J
                move-result-wide v0
                invoke-static {}, Landroid/os/Process;->getStartElapsedRealtime()J
                move-result-wide v2
                sub-long/2addr v0, v2
                const-wide/16 v2, 0x${STARTUP_NETWORK_BLOCK_MS.toString(16)}
                cmp-long v0, v0, v2
                if-gez v0, :mx_allow
                new-instance v0, Ljava/io/IOException;
                invoke-direct {v0}, Ljava/io/IOException;-><init>()V
                throw v0
                :mx_allow
                nop
            """.trimIndent(),
        )
    }
}
