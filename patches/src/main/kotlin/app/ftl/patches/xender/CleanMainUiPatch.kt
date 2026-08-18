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
private const val CLEAN_UI_HELPER_NAME = "ftlApplyCleanUi"

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

private object MainActivityOpenDrawerFingerprint : Fingerprint(
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

/**
 * Adds a private static helper with its own three-register budget. The lifecycle methods only
 * call this helper with p0, so they do not need a free scratch register at the insertion point.
 */
private fun addXenderUiHelper(mainActivity: MutableClass) {
    if (mainActivity.methods.any { it.name == CLEAN_UI_HELPER_NAME }) return

    val helper = ImmutableMethod(
        MAIN_ACTIVITY_CLASS,
        CLEAN_UI_HELPER_NAME,
        listOf(ImmutableMethodParameter(MAIN_ACTIVITY_CLASS, null, "activity")),
        "V",
        AccessFlags.PRIVATE.value or AccessFlags.STATIC.value,
        null,
        null,
        MutableMethodImplementation(3),
    ).toMutable()

    helper.addInstructions(
        0,
        """
            sget v0, Lcn/xender/R${'$'}id;->x_main_navigation_view:I
            invoke-virtual {p0, v0}, Landroid/app/Activity;->findViewById(I)Landroid/view/View;
            move-result-object v1
            if-eqz v1, :hide_navigation_done
            const/16 v0, 0x8
            invoke-virtual {v1, v0}, Landroid/view/View;->setVisibility(I)V
            :hide_navigation_done

            sget v0, Lcn/xender/R${'$'}id;->action_guide:I
            invoke-virtual {p0, v0}, Landroid/app/Activity;->findViewById(I)Landroid/view/View;
            move-result-object v1
            if-eqz v1, :hide_guide_done
            const/16 v0, 0x8
            invoke-virtual {v1, v0}, Landroid/view/View;->setVisibility(I)V
            :hide_guide_done

            sget v0, Lcn/xender/R${'$'}id;->x_drawer_rate_item:I
            invoke-virtual {p0, v0}, Landroid/app/Activity;->findViewById(I)Landroid/view/View;
            move-result-object v1
            if-eqz v1, :hide_rate_done
            const/16 v0, 0x8
            invoke-virtual {v1, v0}, Landroid/view/View;->setVisibility(I)V
            :hide_rate_done

            sget v0, Lcn/xender/R${'$'}id;->x_drawer_help_item:I
            invoke-virtual {p0, v0}, Landroid/app/Activity;->findViewById(I)Landroid/view/View;
            move-result-object v1
            if-eqz v1, :hide_help_done
            const/16 v0, 0x8
            invoke-virtual {v1, v0}, Landroid/view/View;->setVisibility(I)V
            :hide_help_done

            sget v0, Lcn/xender/R${'$'}id;->x_drawer_about_item:I
            invoke-virtual {p0, v0}, Landroid/app/Activity;->findViewById(I)Landroid/view/View;
            move-result-object v1
            if-eqz v1, :hide_about_done
            const/16 v0, 0x8
            invoke-virtual {v1, v0}, Landroid/view/View;->setVisibility(I)V
            :hide_about_done

            sget v0, Lcn/xender/R${'$'}id;->connect_button:I
            invoke-virtual {p0, v0}, Landroid/app/Activity;->findViewById(I)Landroid/view/View;
            move-result-object v1
            if-eqz v1, :front_connect_done
            invoke-virtual {v1}, Landroid/view/View;->bringToFront()V
            :front_connect_done

            sget v0, Lcn/xender/R${'$'}id;->create_btn:I
            invoke-virtual {p0, v0}, Landroid/app/Activity;->findViewById(I)Landroid/view/View;
            move-result-object v1
            if-eqz v1, :front_create_done
            invoke-virtual {v1}, Landroid/view/View;->bringToFront()V
            :front_create_done

            sget v0, Lcn/xender/R${'$'}id;->join_btn:I
            invoke-virtual {p0, v0}, Landroid/app/Activity;->findViewById(I)Landroid/view/View;
            move-result-object v1
            if-eqz v1, :front_join_done
            invoke-virtual {v1}, Landroid/view/View;->bringToFront()V
            :front_join_done
            return-void
        """.trimIndent(),
    )

    mainActivity.methods.add(helper)
}

private fun MutableMethod.addXenderUiHelperCall(index: Int) {
    addInstructions(
        index,
        """
            invoke-static {p0}, Lcn/xender/ui/activity/MainActivity;->ftlApplyCleanUi(Lcn/xender/ui/activity/MainActivity;)V
        """.trimIndent(),
    )
}

val cleanMainUiPatch = bytecodePatch(
    name = "Clean main UI",
    description = "Uses a register-isolated helper method to hide selected Xender UI items and bring the primary action buttons to the front after the relevant lifecycle events.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_XENDER)

    execute {
        addXenderUiHelper(mutableClassDefBy(MAIN_ACTIVITY_CLASS))

        MainActivityOnCreateFingerprint.let { fingerprint ->
            val index = fingerprint.instructionMatches[0].index
            fingerprint.method.addXenderUiHelperCall(index + 1)
        }

        MainActivityOnResumeFingerprint.let { fingerprint ->
            val index = fingerprint.instructionMatches[0].index
            fingerprint.method.addXenderUiHelperCall(index + 1)
        }

        MainActivityOpenDrawerFingerprint.let { fingerprint ->
            val index = fingerprint.instructionMatches[0].index
            fingerprint.method.addXenderUiHelperCall(index + 1)
        }

        MainActivityWindowFocusChangedFingerprint.let { fingerprint ->
            val index = fingerprint.instructionMatches[0].index
            fingerprint.method.addXenderUiHelperCall(index + 1)
        }
    }
}
