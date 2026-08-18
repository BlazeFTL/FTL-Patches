package app.ftl.patches.xender

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.string
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val SPLASH_ACTIVITY_CLASS = "Lcn/xender/ui/activity/SplashActivity;"
private const val MAIN_ACTIVITY_CLASS = "Lcn/xender/ui/activity/MainActivity;"
private const val MAIN_BINDING_CLASS = "Lcn/xender/databinding/c;"

/**
 * Finds SplashActivity.onCreate(Bundle) by its framework lifecycle signature and
 * the super call, not by any compiler-generated or obfuscated method name.
 */
private object SplashOnCreateFingerprint : Fingerprint(
    definingClass = SPLASH_ACTIVITY_CLASS,
    name = "onCreate",
    returnType = "V",
    parameters = listOf("Landroid/os/Bundle;"),
    filters = listOf(
        methodCall(
            parameters = listOf("Landroid/os/Bundle;"),
            returnType = "V",
            opcode = Opcode.INVOKE_SUPER,
        ),
    ),
)

/**
 * Finds the splash-to-main navigation method by its stable user-facing route log
 * and framework Intent(Context, Class) constructor. The method name is not used.
 */
internal object SplashToMainFingerprint : Fingerprint(
    definingClass = SPLASH_ACTIVITY_CLASS,
    returnType = "V",
    parameters = listOf("Landroid/os/Bundle;"),
    filters = listOf(
        methodCall(
            smali = "Landroid/content/Intent;-><init>(Landroid/content/Context;Ljava/lang/Class;)V",
            opcode = Opcode.INVOKE_DIRECT,
        ),
        string("go to next activity:"),
    ),
)

/**
 * Finds Xender's permission helper call from the existing SplashActivity permission
 * registration path. The helper's class and method names are intentionally omitted;
 * the actual MethodReference is read from the matched instruction.
 */
private object SplashPermissionHelperFingerprint : Fingerprint(
    definingClass = SPLASH_ACTIVITY_CLASS,
    returnType = "V",
    parameters = listOf("Ljava/util/Map;"),
    filters = listOf(
        methodCall(
            parameters = listOf("Landroid/app/Activity;"),
            returnType = "[Ljava/lang/String;",
            opcode = Opcode.INVOKE_STATIC,
        ),
    ),
)

/**
 * Finds the app helper that decides whether Android R all-files access applies.
 * The nearby package-prefix and settings-action strings make this path unique while
 * avoiding the helper's obfuscated owner and method name.
 */
private object AndroidRTargetCheckFingerprint : Fingerprint(
    definingClass = SPLASH_ACTIVITY_CLASS,
    returnType = "V",
    parameters = emptyList(),
    filters = listOf(
        string("package:"),
        methodCall(
            parameters = emptyList(),
            returnType = "Z",
            opcode = Opcode.INVOKE_STATIC,
        ),
        string("android.settings.MANAGE_APP_ALL_FILES_ACCESS_PERMISSION"),
    ),
)

/**
 * Finds MainActivity.onCreate(Bundle) at the stable data-binding assignment for
 * activity_main. The field name is deliberately matched only by its binding type.
 */
private object MainOnCreateFingerprint : Fingerprint(
    definingClass = MAIN_ACTIVITY_CLASS,
    name = "onCreate",
    returnType = "V",
    parameters = listOf("Landroid/os/Bundle;"),
    filters = listOf(
        methodCall(
            parameters = listOf("Landroid/os/Bundle;"),
            returnType = "V",
            opcode = Opcode.INVOKE_SUPER,
        ),
        methodCall(
            returnType = "Landroidx/databinding/o;",
            parameters = listOf("Landroid/app/Activity;", "I"),
            opcode = Opcode.INVOKE_STATIC,
        ),
        fieldAccess(
            type = MAIN_BINDING_CLASS,
            opcode = Opcode.IPUT_OBJECT,
        ),
    ),
)

private object MainOnResumeFingerprint : Fingerprint(
    definingClass = MAIN_ACTIVITY_CLASS,
    name = "onResume",
    returnType = "V",
    parameters = emptyList(),
)

private object MainOnWindowFocusChangedFingerprint : Fingerprint(
    definingClass = MAIN_ACTIVITY_CLASS,
    name = "onWindowFocusChanged",
    returnType = "V",
    parameters = listOf("Z"),
)

internal fun MethodReference.toSmali(): String =
    "${definingClass}->${name}(${parameterTypes.joinToString("")})${returnType}"

internal fun ReferenceInstruction.methodReference(): MethodReference = reference as MethodReference

private fun cleanUiInstructions(): String =
    """
        sget v0, Lcn/xender/R${'$'}id;->x_main_navigation_view:I
        invoke-virtual {p0, v0}, Landroid/app/Activity;->findViewById(I)Landroid/view/View;
        move-result-object v0
        if-eqz v0, :xender_clean_0
        const/16 v1, 0x8
        invoke-virtual {v0, v1}, Landroid/view/View;->setVisibility(I)V
        :xender_clean_0
        sget v0, Lcn/xender/R${'$'}id;->action_guide:I
        invoke-virtual {p0, v0}, Landroid/app/Activity;->findViewById(I)Landroid/view/View;
        move-result-object v0
        if-eqz v0, :xender_clean_1
        const/16 v1, 0x8
        invoke-virtual {v0, v1}, Landroid/view/View;->setVisibility(I)V
        :xender_clean_1
        sget v0, Lcn/xender/R${'$'}id;->x_drawer_rate_item:I
        invoke-virtual {p0, v0}, Landroid/app/Activity;->findViewById(I)Landroid/view/View;
        move-result-object v0
        if-eqz v0, :xender_clean_2
        const/16 v1, 0x8
        invoke-virtual {v0, v1}, Landroid/view/View;->setVisibility(I)V
        :xender_clean_2
        sget v0, Lcn/xender/R${'$'}id;->x_drawer_help_item:I
        invoke-virtual {p0, v0}, Landroid/app/Activity;->findViewById(I)Landroid/view/View;
        move-result-object v0
        if-eqz v0, :xender_clean_3
        const/16 v1, 0x8
        invoke-virtual {v0, v1}, Landroid/view/View;->setVisibility(I)V
        :xender_clean_3
        sget v0, Lcn/xender/R${'$'}id;->x_drawer_about_item:I
        invoke-virtual {p0, v0}, Landroid/app/Activity;->findViewById(I)Landroid/view/View;
        move-result-object v0
        if-eqz v0, :xender_clean_4
        const/16 v1, 0x8
        invoke-virtual {v0, v1}, Landroid/view/View;->setVisibility(I)V
        :xender_clean_4
        sget v0, Lcn/xender/R${'$'}id;->connect_button:I
        invoke-virtual {p0, v0}, Landroid/app/Activity;->findViewById(I)Landroid/view/View;
        move-result-object v0
        if-eqz v0, :xender_clean_5
        invoke-virtual {v0}, Landroid/view/View;->bringToFront()V
        :xender_clean_5
        sget v0, Lcn/xender/R${'$'}id;->create_btn:I
        invoke-virtual {p0, v0}, Landroid/app/Activity;->findViewById(I)Landroid/view/View;
        move-result-object v0
        if-eqz v0, :xender_clean_6
        invoke-virtual {v0}, Landroid/view/View;->bringToFront()V
        :xender_clean_6
        sget v0, Lcn/xender/R${'$'}id;->join_btn:I
        invoke-virtual {p0, v0}, Landroid/app/Activity;->findViewById(I)Landroid/view/View;
        move-result-object v0
        if-eqz v0, :xender_clean_7
        invoke-virtual {v0}, Landroid/view/View;->bringToFront()V
        :xender_clean_7
    """.trimIndent()

private fun localDataInitializationInstructions(): String =
    """
        invoke-static {}, Lcn/xender/b0;->checkIsUpdatedComeIn()Z
        move-result v0
        invoke-static {v0}, Lcn/xender/f;->exeInit(Z)V
    """.trimIndent()

private fun startupPermissionInstructions(
    permissionHelper: String,
    androidRTargetCheck: String,
): String =
    """
        invoke-static {p0}, $permissionHelper
        move-result-object v0
        array-length v1, v0
        if-lez v1, :xender_permissions_all_files
        const/4 v1, 0x0
        invoke-virtual {p0, v0, v1}, Landroid/app/Activity;->requestPermissions([Ljava/lang/String;I)V
        :xender_permissions_all_files
        invoke-static {}, $androidRTargetCheck
        move-result v0
        if-eqz v0, :xender_permissions_done
        invoke-static {}, Landroid/os/Environment;->isExternalStorageManager()Z
        move-result v0
        if-nez v0, :xender_permissions_done
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
        :xender_permissions_done
    """.trimIndent()

/**
 * Skips SplashActivity's visual flow and applies the supplied A-to-B clean UI
 * transformation. All app-specific helper calls are resolved from stable call
 * signatures in the stock APK, so a ProGuard rename does not invalidate them.
 */
val skipSplashAndCleanUiPatch = bytecodePatch(
    name = "Skip Xender splash and clean UI",
    description = "Enters Xender's main activity without displaying the splash flow, requests required storage access from the main screen, and hides or reorders selected UI elements.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_XENDER)

    execute {
        val toMainMethod = SplashToMainFingerprint.method
        val toMain = "${toMainMethod.definingClass}->${toMainMethod.name}(${toMainMethod.parameterTypes.joinToString("")})${toMainMethod.returnType}"
        val permissionHelper = SplashPermissionHelperFingerprint.instructionMatches.first()
            .getInstruction<ReferenceInstruction>().methodReference().toSmali()
        val androidRTargetCheck = AndroidRTargetCheckFingerprint.instructionMatches[1]
            .getInstruction<ReferenceInstruction>().methodReference().toSmali()

        SplashOnCreateFingerprint.method.addInstructions(
            SplashOnCreateFingerprint.instructionMatches.first().index + 1,
            """
                const/4 v0, 0x0
                invoke-virtual {p0, v0}, $toMain
                invoke-virtual {p0}, Landroid/app/Activity;->finish()V
                return-void
            """.trimIndent(),
        )

        val mainSuperIndex = MainOnCreateFingerprint.instructionMatches.first().index
        val bindingAssignmentIndex = MainOnCreateFingerprint.instructionMatches.last().index + 1
        // Insert the post-binding UI block first; the startup block is earlier in
        // the method and will shift this block forward automatically.
        MainOnCreateFingerprint.method.addInstructions(bindingAssignmentIndex, cleanUiInstructions())
        MainOnCreateFingerprint.method.addInstructions(
            mainSuperIndex + 1,
            localDataInitializationInstructions() + "\n" +
                startupPermissionInstructions(permissionHelper, androidRTargetCheck),
        )


        MainOnResumeFingerprint.method.addInstructions(1, cleanUiInstructions())
        MainOnWindowFocusChangedFingerprint.method.addInstructions(1, cleanUiInstructions())
    }
}
