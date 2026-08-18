import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch

/**
 * The launcher is redirected at runtime without changing the manifest: the
 * MainActivity startup work is restored first, then SplashActivity is reduced
 * to registering its result launchers and opening MainActivity.
 */
private object MainActivityStartupFingerprint : Fingerprint(
    definingClass = "Lcn/xender/ui/activity/MainActivity;",
    name = "onCreate",
    returnType = "V",
    parameters = listOf("Landroid/os/Bundle;"),
    filters = listOf(
        methodCall(smali = "Lcn/xender/ui/activity/BaseActivity;->onCreate(Landroid/os/Bundle;)V"),
    ),
)

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
    description = "Jumps directly from SplashActivity.onCreate() to MainActivity without executing the splash UI or its permission/data flow.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_XENDER)

    execute {
        MainActivityStartupFingerprint.let { fingerprint ->
            val superCallIndex = fingerprint.instructionMatches[0].index
            fingerprint.method.addInstructions(
                superCallIndex + 1,
                """
                    invoke-static {}, Lcn/xender/b0;->checkIsUpdatedComeIn()Z
                    move-result v0
                    invoke-static {v0}, Lcn/xender/f;->exeInit(Z)V

                    invoke-static {p0}, Lcn/xender/core/permission/b;->splashNeedGrantPermission(Landroid/app/Activity;)[Ljava/lang/String;
                    move-result-object v0
                    array-length v1, v0
                    if-lez v1, :startup_permissions_done
                    const/4 v1, 0x0
                    invoke-virtual {p0, v0, v1}, Landroid/app/Activity;->requestPermissions([Ljava/lang/String;I)V

                    :startup_permissions_done
                    invoke-static {}, Lcn/xender/core/c;->isAndroidRAndTargetR()Z
                    move-result v0
                    if-eqz v0, :all_files_permission_done
                    invoke-static {}, Landroid/os/Environment;->isExternalStorageManager()Z
                    move-result v0
                    if-nez v0, :all_files_permission_done
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

                    :all_files_permission_done
                """.trimIndent(),
            )
        }

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
    }
}
