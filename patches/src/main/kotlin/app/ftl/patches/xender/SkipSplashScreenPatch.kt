package app.ftl.patches.xender

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableClass
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter

private const val MAIN_ACTIVITY_CLASS = "Lcn/xender/ui/activity/MainActivity;"
private const val INIT_LOCAL_DATA_METHOD = "initLocalData"
private const val REQUEST_STARTUP_PERMISSIONS_METHOD = "requestStartupPermissions"

private object MainActivityStartupFingerprint : Fingerprint(
    definingClass = MAIN_ACTIVITY_CLASS,
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

private fun addStartupHelpers(mainActivity: MutableClass) {
    if (mainActivity.methods.none { it.name == INIT_LOCAL_DATA_METHOD }) {
        val initLocalData = ImmutableMethod(
            MAIN_ACTIVITY_CLASS,
            INIT_LOCAL_DATA_METHOD,
            emptyList(),
            "V",
            AccessFlags.PRIVATE.value,
            null,
            null,
            MutableMethodImplementation(2),
        ).toMutable()
        initLocalData.addInstructions(
            0,
            """
                invoke-static {}, Lcn/xender/b0;->checkIsUpdatedComeIn()Z
                move-result v0
                invoke-static {v0}, Lcn/xender/f;->exeInit(Z)V
                return-void
            """.trimIndent(),
        )
        mainActivity.methods.add(initLocalData)
    }

    if (mainActivity.methods.none { it.name == REQUEST_STARTUP_PERMISSIONS_METHOD }) {
        val requestStartupPermissions = ImmutableMethod(
            MAIN_ACTIVITY_CLASS,
            REQUEST_STARTUP_PERMISSIONS_METHOD,
            emptyList(),
            "V",
            AccessFlags.PRIVATE.value,
            null,
            null,
            MutableMethodImplementation(5),
        ).toMutable()
        requestStartupPermissions.addInstructions(
            0,
            """
                invoke-static {p0}, Lcn/xender/core/permission/b;->splashNeedGrantPermission(Landroid/app/Activity;)[Ljava/lang/String;
                move-result-object v0
                array-length v1, v0
                if-lez v1, :cond_0
                const/4 v1, 0x0
                invoke-virtual {p0, v0, v1}, Landroid/app/Activity;->requestPermissions([Ljava/lang/String;I)V

                :cond_0
                invoke-static {}, Lcn/xender/core/c;->isAndroidRAndTargetR()Z
                move-result v0
                if-eqz v0, :cond_1
                invoke-static {}, Landroid/os/Environment;->isExternalStorageManager()Z
                move-result v0
                if-nez v0, :cond_1
                new-instance v0, Landroid/content/Intent;
                const-string v1, "android.settings.MANAGE_APP_ALL_FILES_ACCESS_PERMISSION"
                invoke-direct {v0, v1}, Landroid/content/Intent;-><init>(Ljava/lang/String;)V
                new-instance v1, Ljava/lang/StringBuilder;
                const-string v2, "package:"
                invoke-direct {v1, v2}, Ljava/lang/StringBuilder;-><init>()V
                invoke-virtual {p0}, Landroid/content/Context;->getPackageName()Ljava/lang/String;
                move-result-object v2
                invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
                invoke-virtual {v1}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;
                move-result-object v1
                invoke-static {v1}, Landroid/net/Uri;->parse(Ljava/lang/String;)Landroid/net/Uri;
                move-result-object v1
                invoke-virtual {v0, v1}, Landroid/content/Intent;->setData(Landroid/net/Uri;)Landroid/content/Intent;
                invoke-virtual {p0, v0}, Landroid/content/Context;->startActivity(Landroid/content/Intent;)V

                :cond_1
                return-void
            """.trimIndent(),
        )
        mainActivity.methods.add(requestStartupPermissions)
    }
}

val skipSplashScreenPatch = bytecodePatch(
    name = "Skip splash screen",
    description = "Restores MainActivity local-data and startup-permission helpers, then reduces SplashActivity to the wanted direct MainActivity launch flow.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_XENDER)

    execute {
        val mainActivity = mutableClassDefBy(MAIN_ACTIVITY_CLASS)
        addStartupHelpers(mainActivity)

        MainActivityStartupFingerprint.let { fingerprint ->
            val superCallIndex = fingerprint.instructionMatches[0].index
            fingerprint.method.addInstructions(
                superCallIndex + 1,
                """
                    invoke-direct {p0}, Lcn/xender/ui/activity/MainActivity;->initLocalData()V
                    invoke-direct {p0}, Lcn/xender/ui/activity/MainActivity;->requestStartupPermissions()V
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
