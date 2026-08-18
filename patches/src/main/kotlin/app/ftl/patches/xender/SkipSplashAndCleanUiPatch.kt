package app.ftl.patches.xender

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.removeInstructions
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
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

    extendWith("extensions/xender.mpe")

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

        MainOnCreateFingerprint.let { fingerprint ->
            val instructions = fingerprint.method.implementation!!.instructions
            val bindingIndex = instructions.indexOfFirst { instruction ->
                instruction.opcode == Opcode.IPUT_OBJECT &&
                    (instruction as? ReferenceInstruction)?.reference.let { reference ->
                        (reference as? FieldReference)?.definingClass == "Lcn/xender/ui/activity/MainActivity;" &&
                            (reference as? FieldReference)?.name == "J0"
                    }
            }

            if (bindingIndex >= 0) {
                fingerprint.method.addInstructions(
                    bindingIndex + 1,
                    "invoke-static {p0}, Lapp/ftl/extension/xender/XenderCleanUi;->schedule(Landroid/app/Activity;)V",
                )
            }
        }

        MainOnResumeFingerprint.method.addInstructions(
            0,
            """
                invoke-static {p0}, Lapp/ftl/extension/xender/XenderCleanUi;->schedule(Landroid/app/Activity;)V
            """.trimIndent(),
        )

        DrawerEnterClickFingerprint.method.addInstructions(
            0,
            """
                invoke-static {p0}, Lapp/ftl/extension/xender/XenderCleanUi;->schedule(Landroid/app/Activity;)V
            """.trimIndent(),
        )

        HiddenViewFingerprint.method.let { fingerprint ->
            val implementation = fingerprint.method.implementation!!
            implementation.removeInstructions(0, implementation.instructions.size)
            fingerprint.method.addInstructions(
                0,
                """
                    return-void
                """.trimIndent(),
            )
        }
    }
}
