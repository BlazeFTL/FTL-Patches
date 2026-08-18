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

/**
 * Matches MainActivity.onCreate(Bundle). Anchored on the call to initNavigation(),
 * MainActivity's own real (unobfuscated) private method — shared anchor with
 * CleanMainUiPatch's insertion at the same point.
 */
private object MainActivityStartupPermissionsFingerprint : Fingerprint(
    definingClass = "Lcn/xender/ui/activity/MainActivity;",
    name = "onCreate",
    returnType = "V",
    parameters = listOf("Landroid/os/Bundle;"),
    filters = listOf(
        methodCall(smali = "Lcn/xender/ui/activity/MainActivity;->initNavigation()V"),
    ),
)

val skipSplashScreenPatch = bytecodePatch(
    name = "Skip splash screen",
    description = "Jumps straight from SplashActivity.onCreate() to the main activity, skipping the splash/guide UI. Requests storage permission from MainActivity.onCreate() instead, since that's normally only ever requested by the guide/splash fragment being skipped.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_XENDER)

    execute {
        // SplashActivity: matches the confirmed-working reference build exactly —
        // registerForActivityResults(), toMainActivity(null), finish(). No permission
        // calls here; the reference doesn't request permission from SplashActivity
        // at all when skipping — it does it from MainActivity instead (below).
        SplashOnCreateFingerprint.let { fingerprint ->
            val superCallIndex = fingerprint.instructionMatches[0].index
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

        // MainActivity: inlines the reference's own requestStartupPermissions()
        // body verbatim (confirmed present in the working reference build, called
        // from its MainActivity.onCreate — this is the actual permission fix, not
        // anything in SplashActivity). Two parts: (1) splashNeedGrantPermission()
        // + Activity.requestPermissions() for the standard runtime permission
        // array: real app method (already called this same way elsewhere in the
        // app) + real Android SDK call. (2) isAndroidRAndTargetR() +
        // Environment.isExternalStorageManager() gated All-Files-Access settings
        // intent: all real Android SDK/app calls, no obfuscated identifiers.
        // v0-v2 confirmed free at this point: the code right after initNavigation()
        // reuses p1 as scratch, not v0-v2.
        MainActivityStartupPermissionsFingerprint.let { fingerprint ->
            val index = fingerprint.instructionMatches[0].index
            fingerprint.method.addInstructions(
                index + 1,
                """
                    invoke-static {p0}, Lcn/xender/core/permission/b;->splashNeedGrantPermission(Landroid/app/Activity;)[Ljava/lang/String;
                    move-result-object v0
                    array-length v1, v0
                    if-lez v1, :ftl_startup_perm_done
                    const/4 v1, 0x0
                    invoke-virtual {p0, v0, v1}, Landroid/app/Activity;->requestPermissions([Ljava/lang/String;I)V
                    :ftl_startup_perm_done
                    invoke-static {}, Lcn/xender/core/c;->isAndroidRAndTargetR()Z
                    move-result v0
                    if-eqz v0, :ftl_startup_allfiles_done
                    invoke-static {}, Landroid/os/Environment;->isExternalStorageManager()Z
                    move-result v0
                    if-nez v0, :ftl_startup_allfiles_done
                    new-instance v0, Landroid/content/Intent;
                    const-string v1, "android.settings.MANAGE_APP_ALL_FILES_ACCESS_PERMISSION"
                    invoke-direct {v0, v1}, Landroid/content/Intent;-><init>(Ljava/lang/String;)V
                    new-instance v1, Ljava/lang/StringBuilder;
                    const-string v2, "package:"
                    invoke-direct {v1, v2}, Ljava/lang/StringBuilder;-><init>(Ljava/lang/String;)V
                    invoke-virtual {p0}, Landroid/content/Context;->getPackageName()Ljava/lang/String;
                    move-result-object v2
                    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
                    invoke-virtual {v1}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;
                    move-result-object v1
                    invoke-static {v1}, Landroid/net/Uri;->parse(Ljava/lang/String;)Landroid/net/Uri;
                    move-result-object v1
                    invoke-virtual {v0, v1}, Landroid/content/Intent;->setData(Landroid/net/Uri;)Landroid/content/Intent;
                    invoke-virtual {p0, v0}, Landroid/content/Context;->startActivity(Landroid/content/Intent;)V
                    :ftl_startup_allfiles_done
                """.trimIndent(),
            )
        }
    }
}
