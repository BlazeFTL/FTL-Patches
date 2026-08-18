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
private const val HIDE_UI_METHOD = "hideUiElement"
private const val BRING_UI_METHOD = "bringUiElementToFront"
private const val APPLY_UI_METHOD = "applyUiCustomizations"

private object MainActivityOnCreateFingerprint : Fingerprint(
    definingClass = MAIN_ACTIVITY_CLASS,
    name = "onCreate",
    returnType = "V",
    parameters = listOf("Landroid/os/Bundle;"),
    filters = listOf(
        methodCall(smali = "Lcn/xender/ui/activity/MainActivity;->initNavigation()V"),
    ),
)

private object MainActivityOnResumeFingerprint : Fingerprint(
    definingClass = MAIN_ACTIVITY_CLASS,
    name = "onResume",
    returnType = "V",
    parameters = emptyList(),
    filters = listOf(
        methodCall(smali = "Landroidx/fragment/app/FragmentActivity;->onResume()V"),
    ),
)

private object MainActivityDrawerFingerprint : Fingerprint(
    definingClass = MAIN_ACTIVITY_CLASS,
    name = "drawerEnterClick",
    returnType = "V",
    parameters = emptyList(),
    filters = listOf(
        methodCall(smali = "Landroidx/drawerlayout/widget/DrawerLayout;->openDrawer(Landroid/view/View;)V"),
    ),
)

private object MainActivityWindowFocusChangedFingerprint : Fingerprint(
    definingClass = MAIN_ACTIVITY_CLASS,
    name = "onWindowFocusChanged",
    returnType = "V",
    parameters = listOf("Z"),
    filters = listOf(
        methodCall(smali = "Landroid/app/Activity;->onWindowFocusChanged(Z)V"),
    ),
)

private fun immutableMethod(
    name: String,
    parameters: List<ImmutableMethodParameter>,
    returnType: String,
    accessFlags: Int,
    registers: Int,
    body: String,
): MutableMethod {
    val method = ImmutableMethod(
        MAIN_ACTIVITY_CLASS,
        name,
        parameters,
        returnType,
        accessFlags,
        null,
        null,
        MutableMethodImplementation(registers),
    ).toMutable()
    method.addInstructions(0, body.trimIndent())
    return method
}

private fun addXenderUiHelpers(mainActivity: MutableClass) {
    if (mainActivity.methods.none { it.name == HIDE_UI_METHOD }) {
        mainActivity.methods.add(
            immutableMethod(
                HIDE_UI_METHOD,
                listOf(ImmutableMethodParameter("I", null, "id")),
                "V",
                AccessFlags.PRIVATE.value,
                3,
                """
                    invoke-virtual {p0, p1}, Landroid/app/Activity;->findViewById(I)Landroid/view/View;
                    move-result-object v0;
                    if-eqz v0, :cond_0;
                    const/16 p0, 0x8;
                    invoke-virtual {v0, p0}, Landroid/view/View;->setVisibility(I)V;
                    :cond_0
                    return-void;
                """,
            ),
        )
    }

    if (mainActivity.methods.none { it.name == BRING_UI_METHOD }) {
        mainActivity.methods.add(
            immutableMethod(
                BRING_UI_METHOD,
                listOf(ImmutableMethodParameter("I", null, "id")),
                "V",
                AccessFlags.PRIVATE.value,
                3,
                """
                    invoke-virtual {p0, p1}, Landroid/app/Activity;->findViewById(I)Landroid/view/View;
                    move-result-object v0;
                    if-eqz v0, :cond_0;
                    invoke-virtual {v0}, Landroid/view/View;->bringToFront()V;
                    :cond_0
                    return-void;
                """,
            ),
        )
    }

    if (mainActivity.methods.none { it.name == APPLY_UI_METHOD }) {
        mainActivity.methods.add(
            immutableMethod(
                APPLY_UI_METHOD,
                emptyList(),
                "V",
                AccessFlags.PUBLIC.value,
                2,
                """
                    sget v0, Lcn/xender/R${'$'}id;->x_main_navigation_view:I;
                    invoke-direct {p0, v0}, Lcn/xender/ui/activity/MainActivity;->hideUiElement(I)V;
                    sget v0, Lcn/xender/R${'$'}id;->action_guide:I;
                    invoke-direct {p0, v0}, Lcn/xender/ui/activity/MainActivity;->hideUiElement(I)V;
                    sget v0, Lcn/xender/R${'$'}id;->x_drawer_rate_item:I;
                    invoke-direct {p0, v0}, Lcn/xender/ui/activity/MainActivity;->hideUiElement(I)V;
                    sget v0, Lcn/xender/R${'$'}id;->x_drawer_help_item:I;
                    invoke-direct {p0, v0}, Lcn/xender/ui/activity/MainActivity;->hideUiElement(I)V;
                    sget v0, Lcn/xender/R${'$'}id;->x_drawer_about_item:I;
                    invoke-direct {p0, v0}, Lcn/xender/ui/activity/MainActivity;->hideUiElement(I)V;
                    sget v0, Lcn/xender/R${'$'}id;->connect_button:I;
                    invoke-direct {p0, v0}, Lcn/xender/ui/activity/MainActivity;->bringUiElementToFront(I)V;
                    sget v0, Lcn/xender/R${'$'}id;->create_btn:I;
                    invoke-direct {p0, v0}, Lcn/xender/ui/activity/MainActivity;->bringUiElementToFront(I)V;
                    sget v0, Lcn/xender/R${'$'}id;->join_btn:I;
                    invoke-direct {p0, v0}, Lcn/xender/ui/activity/MainActivity;->bringUiElementToFront(I)V;
                    return-void;
                """,
            ),
        )
    }
}

private fun MutableMethod.addApplyUiCall(index: Int) {
    addInstructions(
        index,
        "invoke-virtual {p0}, Lcn/xender/ui/activity/MainActivity;->applyUiCustomizations()V",
    )
}

val cleanMainUiPatch = bytecodePatch(
    name = "Clean main UI",
    description = "Adds the wanted register-isolated MainActivity UI helpers and reapplies them at safe lifecycle points.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_XENDER)

    execute {
        val mainActivity = mutableClassDefBy(MAIN_ACTIVITY_CLASS)
        addXenderUiHelpers(mainActivity)

        MainActivityOnCreateFingerprint.let { fingerprint ->
            fingerprint.method.addApplyUiCall(fingerprint.instructionMatches[0].index + 1)
        }

        MainActivityOnResumeFingerprint.let { fingerprint ->
            fingerprint.method.addApplyUiCall(fingerprint.instructionMatches[0].index + 1)
        }

        MainActivityDrawerFingerprint.let { fingerprint ->
            // drawerEnterClick overwrites p0 with DrawerView before openDrawer().
            // Insert at method entry while p0 is still MainActivity.
            fingerprint.method.addApplyUiCall(0)
        }

        MainActivityWindowFocusChangedFingerprint.let { fingerprint ->
            fingerprint.method.addApplyUiCall(fingerprint.instructionMatches[0].index + 1)
        }
    }
}
