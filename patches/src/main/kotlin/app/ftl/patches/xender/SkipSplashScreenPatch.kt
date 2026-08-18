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

            // registerForActivityResults() first — populates the w0/x0/y0 launchers
            // that delayCreateData()'s storage check and both permission methods
            // depend on. handleCheckPermissionGrantCode(I) (SplashActivity's real
            // dispatcher, seen in full) routes: 0→delayCreateData, 1→requestSplashPermissions
            // (standard runtime perms — resolves to just POST_NOTIFICATIONS on newer
            // Android), 2→requestForManageAllFilesPermissions (the separate All-Files-
            // Access settings-intent flow storage actually needs on scoped-storage
            // Android). Normally the guide/splash fragment calls this dispatcher
            // multiple times in sequence; calling all 3 relevant branches directly
            // here replaces that. (Skipping case 3, requestMiuiNetCard — MIUI-specific,
            // unrelated to permissions.) All real declared methods on SplashActivity
            // itself. v0 is free here: nothing has written to it yet.
            fingerprint.method.addInstructions(
                superCallIndex + 1,
                """
                    invoke-direct {p0}, Lcn/xender/ui/activity/SplashActivity;->registerForActivityResults()V
                    invoke-direct {p0}, Lcn/xender/ui/activity/SplashActivity;->delayCreateData()V
                    invoke-direct {p0}, Lcn/xender/ui/activity/SplashActivity;->requestSplashPermissions()V
                    invoke-direct {p0}, Lcn/xender/ui/activity/SplashActivity;->requestForManageAllFilesPermissions()V
                    const/4 v0, 0x0
                    invoke-virtual {p0, v0}, Lcn/xender/ui/activity/SplashActivity;->toMainActivity(Landroid/os/Bundle;)V
                    invoke-virtual {p0}, Lcn/xender/ui/activity/SplashActivity;->finish()V
                    return-void
                """.trimIndent(),
            )
        }
    }
}
