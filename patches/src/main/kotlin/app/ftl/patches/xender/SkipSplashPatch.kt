package app.ftl.patches.xender

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchFirst
import app.morphe.patcher.methodCall
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch

/**
 * Matches SplashActivity.onCreate(Bundle) by its real signature. Anchored on the
 * invoke-super call to BaseActivity.onCreate - the method's first instruction in
 * every build seen so far, and a real unobfuscated app class, not a synthetic one.
 */
private object SplashOnCreateFingerprint : Fingerprint(
    definingClass = "Lcn/xender/ui/activity/SplashActivity;",
    name = "onCreate",
    returnType = "V",
    parameters = listOf("Landroid/os/Bundle;"),
    filters = listOf(
        methodCall(
            smali = "Lcn/xender/ui/activity/BaseActivity;->onCreate(Landroid/os/Bundle;)V",
            location = MatchFirst(),
        ),
    ),
)

/**
 * Jumps straight from the splash screen to the main activity. registerForActivityResults()
 * and toMainActivity(Bundle) are both real, already-declared methods on SplashActivity
 * (called later in the same method during the normal flow), so no obfuscated symbols
 * are referenced here. v0 is guaranteed free: it's inserted immediately after the
 * super call, before any other register is touched.
 */
val skipSplashPatch = bytecodePatch(
    name = "Skip splash screen",
    description = "Jumps straight to the main activity from the splash screen, skipping the splash animation entirely.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_XENDER)

    execute {
        SplashOnCreateFingerprint.let { fingerprint ->
            val superCallIndex = fingerprint.instructionMatches.first().index

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
