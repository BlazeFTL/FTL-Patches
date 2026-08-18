package app.ftl.patches.xender

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.removeInstructions
import app.morphe.patcher.patch.bytecodePatch

private val MainOnCreateFingerprint = Fingerprint(
    definingClass = "Lcn/xender/ui/activity/MainActivity;",
    name = "onCreate",
    returnType = "V",
    parameters = listOf("Landroid/os/Bundle;"),
    filters = listOf(
        methodCall(
            smali = "Lcn/xender/ui/activity/BaseActivity;->onCreate(Landroid/os/Bundle;)V",
        ),
    ),
)

private val SplashOnCreateFingerprint = Fingerprint(
    definingClass = "Lcn/xender/ui/activity/SplashActivity;",
    name = "onCreate",
    returnType = "V",
    parameters = listOf("Landroid/os/Bundle;"),
    filters = listOf(
        methodCall(
            smali = "Lcn/xender/ui/activity/BaseActivity;->onCreate(Landroid/os/Bundle;)V",
        ),
    ),
)

private val MainOnResumeFingerprint = Fingerprint(
    definingClass = "Lcn/xender/ui/activity/MainActivity;",
    name = "onResume",
    returnType = "V",
    parameters = emptyList(),
)

private val DrawerEnterClickFingerprint = Fingerprint(
    definingClass = "Lcn/xender/ui/activity/MainActivity;",
    name = "drawerEnterClick",
    returnType = "V",
    parameters = emptyList(),
)

private val HiddenViewFingerprint = Fingerprint(
    definingClass = "Lcn/xender/views/ConnectButtonView;",
    name = "hiddenView",
    returnType = "V",
    parameters = emptyList(),
)

val skipSplashAndCleanUiPatch = bytecodePatch(
    name = "Skip splash and clean UI",
    description = "Skips Xender's splash flow and keeps selected connection controls visible while hiding unwanted UI elements.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_XENDER)

    execute {
        SplashOnCreateFingerprint.let { fingerprint ->
            val index = fingerprint.instructionMatches.first().index
            fingerprint.method.addInstructions(
                index + 1,
                """
                    invoke-direct {p0}, Lcn/xender/ui/activity/SplashActivity;->registerForActivityResults()V
                    const/4 v0, 0x0
                    invoke-virtual {p0, v0}, Lcn/xender/ui/activity/SplashActivity;->toMainActivity(Landroid/os/Bundle;)V
                    invoke-virtual {p0}, Lcn/xender/ui/activity/SplashActivity;->finish()V
                    return-void
                """.trimIndent(),
            )
        }

        MainOnCreateFingerprint.let { fingerprint ->
            val index = fingerprint.instructionMatches.first().index
            fingerprint.method.addInstructions(
                index + 1,
                """
                    invoke-static {}, Lcn/xender/b0;->checkIsUpdatedComeIn()Z
                    move-result v0
                    invoke-static {v0}, Lcn/xender/f;->exeInit(Z)V
                    invoke-static {p0}, Lcn/xender/core/permission/b;->splashNeedGrantPermission(Landroid/app/Activity;)[Ljava/lang/String;
                    move-result-object v0
                    array-length v1, v0
                    if-lez v1, :cond_ftl_permissions_done
                    const/4 v1, 0x0
                    invoke-virtual {p0, v0, v1}, Landroid/app/Activity;->requestPermissions([Ljava/lang/String;I)V
                    :cond_ftl_permissions_done
                    invoke-static {}, Lcn/xender/core/c;->isAndroidRAndTargetR()Z
                    move-result v0
                    if-eqz v0, :cond_ftl_storage_done
                    invoke-static {}, Landroid/os/Environment;->isExternalStorageManager()Z
                    move-result v0
                    if-nez v0, :cond_ftl_storage_done
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
                    invoke-virtual {p0, v0}, Landroid/app/Activity;->startActivity(Landroid/content/Intent;)V
                    :cond_ftl_storage_done
                """.trimIndent(),
            )
        }

        val cleanUi = """
            invoke-virtual {p0}, Landroid/app/Activity;->getWindow()Landroid/view/Window;
            move-result-object v0
            invoke-virtual {v0}, Landroid/view/Window;->getDecorView()Landroid/view/View;
            move-result-object v0
            sget v1, Lcn/xender/R$id;->x_main_navigation_view:I
            invoke-virtual {v0, v1}, Landroid/view/View;->findViewById(I)Landroid/view/View;
            move-result-object v1
            if-eqz v1, :cond_ftl_nav
            const/16 v2, 0x8
            invoke-virtual {v1, v2}, Landroid/view/View;->setVisibility(I)V
            :cond_ftl_nav
            sget v1, Lcn/xender/R$id;->action_guide:I
            invoke-virtual {v0, v1}, Landroid/view/View;->findViewById(I)Landroid/view/View;
            move-result-object v1
            if-eqz v1, :cond_ftl_guide
            const/16 v2, 0x8
            invoke-virtual {v1, v2}, Landroid/view/View;->setVisibility(I)V
            :cond_ftl_guide
            sget v1, Lcn/xender/R$id;->x_drawer_rate_item:I
            invoke-virtual {v0, v1}, Landroid/view/View;->findViewById(I)Landroid/view/View;
            move-result-object v1
            if-eqz v1, :cond_ftl_rate
            const/16 v2, 0x8
            invoke-virtual {v1, v2}, Landroid/view/View;->setVisibility(I)V
            :cond_ftl_rate
            sget v1, Lcn/xender/R$id;->x_drawer_help_item:I
            invoke-virtual {v0, v1}, Landroid/view/View;->findViewById(I)Landroid/view/View;
            move-result-object v1
            if-eqz v1, :cond_ftl_help
            const/16 v2, 0x8
            invoke-virtual {v1, v2}, Landroid/view/View;->setVisibility(I)V
            :cond_ftl_help
            sget v1, Lcn/xender/R$id;->x_drawer_about_item:I
            invoke-virtual {v0, v1}, Landroid/view/View;->findViewById(I)Landroid/view/View;
            move-result-object v1
            if-eqz v1, :cond_ftl_about
            const/16 v2, 0x8
            invoke-virtual {v1, v2}, Landroid/view/View;->setVisibility(I)V
            :cond_ftl_about
            sget v1, Lcn/xender/R$id;->connect_button:I
            invoke-virtual {v0, v1}, Landroid/view/View;->findViewById(I)Landroid/view/View;
            move-result-object v1
            if-eqz v1, :cond_ftl_connect
            invoke-virtual {v1}, Landroid/view/View;->bringToFront()V
            :cond_ftl_connect
            sget v1, Lcn/xender/R$id;->create_btn:I
            invoke-virtual {v0, v1}, Landroid/view/View;->findViewById(I)Landroid/view/View;
            move-result-object v1
            if-eqz v1, :cond_ftl_create
            invoke-virtual {v1}, Landroid/view/View;->bringToFront()V
            :cond_ftl_create
            sget v1, Lcn/xender/R$id;->join_btn:I
            invoke-virtual {v0, v1}, Landroid/view/View;->findViewById(I)Landroid/view/View;
            move-result-object v1
            if-eqz v1, :cond_ftl_join
            invoke-virtual {v1}, Landroid/view/View;->bringToFront()V
            :cond_ftl_join
        """.trimIndent()

        MainOnCreateFingerprint.method.addInstructions(
            MainOnCreateFingerprint.method.implementation!!.instructions.size - 1,
            cleanUi,
        )

        MainOnResumeFingerprint.method.addInstructions(
            0,
            cleanUi,
        )

        DrawerEnterClickFingerprint.method.addInstructions(
            0,
            cleanUi,
        )

        HiddenViewFingerprint.method.let { method ->
            val instructionCount = method.implementation!!.instructions.size
            method.removeInstructions(0, instructionCount)
            method.addInstructions(0, "return-void")
        }
    }
}
