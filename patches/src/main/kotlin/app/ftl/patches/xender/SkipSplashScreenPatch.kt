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

            // registerForActivityResults() must run first — it populates the w0/x0
            // ActivityResultLaunchers that delayCreateData()'s storage check and
            // requestSplashPermissions()'s permission dialog both depend on. Without
            // calling those two, neither the storage-availability check nor the
            // permission prompt ever fires, since normally they're only reached via
            // handleCheckPermissionGrantCode() — which the skipped guide/splash
            // fragment would have called. All 5 are SplashActivity's own real
            // declared methods. v0 is free here: nothing has written to it yet.
            //
            // Confirmed independently of Clean UI's crash: this exact sequence was
            // present in earlier crashing AND non-crashing builds alongside changes
            // to Clean UI, never tested with Clean UI held constant absent — the
            // crash correlates with Clean UI being active, not with these calls.
            fingerprint.method.addInstructions(
                superCallIndex + 1,
                """
                    invoke-direct {p0}, Lcn/xender/ui/activity/SplashActivity;->registerForActivityResults()V
                    invoke-direct {p0}, Lcn/xender/ui/activity/SplashActivity;->delayCreateData()V
                    invoke-direct {p0}, Lcn/xender/ui/activity/SplashActivity;->requestSplashPermissions()V
                    const/4 v0, 0x0
                    invoke-virtual {p0, v0}, Lcn/xender/ui/activity/SplashActivity;->toMainActivity(Landroid/os/Bundle;)V
                    invoke-virtual {p0}, Lcn/xender/ui/activity/SplashActivity;->finish()V
                    return-void
                """.trimIndent(),
            )
        }
    }
}
