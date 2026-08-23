package app.ftl.patches.xender

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchFirst
import app.morphe.patcher.methodCall
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.patch.BytecodePatchContext
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
 * Matches MainActivity.onCreate(Bundle) by its real signature. Anchored the same way
 * as SplashOnCreateFingerprint, for the same reason.
 */
private object MainOnCreateFingerprint : Fingerprint(
    definingClass = "Lcn/xender/ui/activity/MainActivity;",
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

/*
 * Skipping SplashActivity entirely also skips the local-data init and the runtime
 * permission requests it normally performs before handing off to MainActivity - which
 * is why storage permission is never asked and the Apps/Photo/Video tabs stay empty.
 * Those four calls live in obfuscated classes (single/double-letter, renamed every
 * build), so instead of pinning them we find each by its real, stable method name +
 * signature and let the resolved defining class flow into the injected smali at
 * patch time - never a hardcoded obfuscated identifier.
 *
 * Assumption: each name+signature pair below is unique app-wide. That held for this
 * build (18.8.0.prime); if a future version throws an ambiguous/ no-match error here,
 * re-check with the compare tool and narrow the fingerprint further.
 */
private object CheckIsUpdatedComeInFingerprint : Fingerprint(
    name = "checkIsUpdatedComeIn",
    returnType = "Z",
    parameters = emptyList(),
)

private object ExeInitFingerprint : Fingerprint(
    name = "exeInit",
    returnType = "V",
    parameters = listOf("Z"),
)

private object SplashNeedGrantPermissionFingerprint : Fingerprint(
    name = "splashNeedGrantPermission",
    returnType = "[Ljava/lang/String;",
    parameters = listOf("Landroid/app/Activity;"),
)

private object IsAndroidRAndTargetRFingerprint : Fingerprint(
    name = "isAndroidRAndTargetR",
    returnType = "Z",
    parameters = emptyList(),
)

/** Builds `DefiningClass;->name(paramTypes)returnType` from a matched fingerprint. */
context(_: BytecodePatchContext)
private fun Fingerprint.staticCallSmali(): String {
    val resolved = originalMethod
    val params = resolved.parameterTypes.joinToString("") { it.toString() }
    return "${resolved.definingClass}->${resolved.name}($params)${resolved.returnType}"
}

val skipSplashPatch = bytecodePatch(
    name = "Skip splash screen",
    description = "Jumps straight to the main activity from the splash screen, skipping the splash animation entirely. Also restores the storage-permission request and local media scan that the splash screen normally performs, so the Apps/Photo/Video tabs still load.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_XENDER)

    execute {
        // registerForActivityResults() and toMainActivity(Bundle) are both real,
        // already-declared methods on SplashActivity (called later in the same
        // method during the normal flow), so no obfuscated symbols are referenced
        // here. v0 is guaranteed free: it's inserted immediately after the super
        // call, before any other register is touched.
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

        // Compensate in MainActivity.onCreate, right after the super call - the same
        // spot the stock permission/init-data flow would have run by if the splash
        // screen hadn't been skipped. MainActivity.onCreate(Bundle) has 5 registers
        // and 2 params (this, Bundle), leaving v0-v2 free at this exact point.
        val checkUpdateSmali = CheckIsUpdatedComeInFingerprint.staticCallSmali()
        val exeInitSmali = ExeInitFingerprint.staticCallSmali()
        val needPermissionSmali = SplashNeedGrantPermissionFingerprint.staticCallSmali()
        val isAndroidRSmali = IsAndroidRAndTargetRFingerprint.staticCallSmali()

        MainOnCreateFingerprint.let { fingerprint ->
            val superCallIndex = fingerprint.instructionMatches.first().index

            fingerprint.method.addInstructionsWithLabels(
                superCallIndex + 1,
                """
                    invoke-static {}, $checkUpdateSmali
                    move-result v0
                    invoke-static {v0}, $exeInitSmali

                    invoke-static {p0}, $needPermissionSmali
                    move-result-object v0
                    array-length v1, v0
                    if-lez v1, :cond_su_permcheck
                    const/4 v1, 0x0
                    invoke-virtual {p0, v0, v1}, Landroid/app/Activity;->requestPermissions([Ljava/lang/String;I)V
                    :cond_su_permcheck

                    invoke-static {}, $isAndroidRSmali
                    move-result v0
                    if-eqz v0, :cond_su_filemgr
                    invoke-static {}, Landroid/os/Environment;->isExternalStorageManager()Z
                    move-result v0
                    if-nez v0, :cond_su_filemgr
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
                    :cond_su_filemgr
                    nop
                """.trimIndent(),
            )
        }
    }
}
