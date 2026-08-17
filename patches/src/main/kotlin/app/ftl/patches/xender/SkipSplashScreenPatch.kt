package app.ftl.patches.xender

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch

/**
 * Matches SplashActivity.onCreate(Bundle). Anchored on the super call to
 * BaseActivity.onCreate() — the app's own real (unobfuscated) base class and a
 * real Android lifecycle method, so this holds across obfuscated builds.
 */
private object SplashOnCreateFingerprint : Fingerprint(
    definingClass = "Lcn/xender/ui/activity/SplashActivity;",
    name = "onCreate",
    returnType = "V",
    parameters = listOf("Landroid/os/Bundle;"),
    filters = listOf(
        methodCall(smali = "Lcn/xender/ui/activity/BaseActivity;->onCreate(Landroid/os/Bundle;)V"),
    ),
)

val skipSplashScreenPatch = bytecodePatch(
    name = "Skip splash screen",
    description = "Jumps straight from SplashActivity.onCreate() to the main activity, skipping the splash/guide UI and its permission flow entirely.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_XENDER)

    execute {
        SplashOnCreateFingerprint.let { fingerprint ->
            val superCallIndex = fingerprint.instructionMatches[0].index

            // registerForActivityResults(), toMainActivity(null), finish(), return-void —
            // all real declared methods on SplashActivity itself (registerForActivityResults
            // and toMainActivity are already relied on by the existing splash patch;
            // finish() is SplashActivity's own override). v0 is free here: nothing has
            // written to it yet in the method, and the inserted return-void means nothing
            // downstream reads it either.
            fingerprint.method.addInstructions(
                superCallIndex + 1,
                """
                    invoke-direct {p0}, Lcn/xender/ui/activity/SplashActivity;->registerForActivityResults()V
                    const/4 v0, 0x0
                    invoke-virtual {p0, v0}, Lcn/xender/ui/activity/SplashActivity;->toMainActivity(Landroid/os/Bundle;)V
                    invoke-virtual {p0}, Lcn/xender/ui/activity/SplashActivity;->finish()V
                    return-void
                """.trimIndent(),
            )
        }
    }
}
