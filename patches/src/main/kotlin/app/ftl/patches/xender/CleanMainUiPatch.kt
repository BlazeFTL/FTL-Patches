import app.ftl.util.getFreeRegisterProvider
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.AccessFlags

private object MainActivityOnCreateFingerprint : Fingerprint(
    definingClass = "Lcn/xender/ui/activity/MainActivity;",
    name = "onCreate",
    returnType = "V",
    parameters = listOf("Landroid/os/Bundle;"),
    filters = listOf(
        methodCall(smali = "Lcn/xender/ui/activity/MainActivity;->initNavigation()V"),
    ),
)

private object MainActivityOnResumeFingerprint : Fingerprint(
    definingClass = "Lcn/xender/ui/activity/MainActivity;",
    name = "onResume",
    returnType = "V",
    parameters = emptyList(),
    filters = listOf(
        methodCall(smali = "Landroidx/fragment/app/FragmentActivity;->onResume()V"),
    ),
)

private object MainActivityOpenDrawerFingerprint : Fingerprint(
    definingClass = "Lcn/xender/ui/activity/MainActivity;",
    name = "drawerEnterClick",
    returnType = "V",
    parameters = emptyList(),
    filters = listOf(
        methodCall(smali = "Landroidx/drawerlayout/widget/DrawerLayout;->openDrawer(Landroid/view/View;)V"),
    ),
)

private object MainActivityWindowFocusChangedFingerprint : Fingerprint(
    definingClass = "Lcn/xender/ui/activity/MainActivity;",
    name = "onWindowFocusChanged",
    returnType = "V",
    parameters = listOf("Z"),
    filters = listOf(
        methodCall(smali = "Landroid/app/Activity;->onWindowFocusChanged(Z)V"),
    ),
)

/**
 * Inserts the same small bytecode-only UI routine that the working comparison
 * applies. Resource IDs are read from Xender's own R$id class, and every view
 * lookup is null-safe because some drawer views are inflated lazily.
 */
private fun MutableMethod.addXenderUiCustomizations(index: Int) {
    val registerCount = implementation!!.registerCount
    val parameterWidth = parameterTypes.sumOf { type -> if (type == "J" || type == "D") 2 else 1 }
    val parameterStart = registerCount - parameterWidth - if (AccessFlags.STATIC.isSet(accessFlags)) 0 else 1
    val parameterRegisters = parameterStart until registerCount
    val registers = getFreeRegisterProvider(index, 2, *parameterRegisters.toList().toIntArray())
    val idRegister = registers.getFreeRegister()
    val viewRegister = registers.getFreeRegister()

    addInstructions(
        index,
        """
            sget v$idRegister, Lcn/xender/R${'$'}id;->x_main_navigation_view:I
            invoke-virtual {p0, v$idRegister}, Landroid/app/Activity;->findViewById(I)Landroid/view/View;
            move-result-object v$viewRegister
            if-eqz v$viewRegister, :hide_navigation_done
            const/16 v$idRegister, 0x8
            invoke-virtual {v$viewRegister, v$idRegister}, Landroid/view/View;->setVisibility(I)V
            :hide_navigation_done

            sget v$idRegister, Lcn/xender/R${'$'}id;->action_guide:I
            invoke-virtual {p0, v$idRegister}, Landroid/app/Activity;->findViewById(I)Landroid/view/View;
            move-result-object v$viewRegister
            if-eqz v$viewRegister, :hide_guide_done
            const/16 v$idRegister, 0x8
            invoke-virtual {v$viewRegister, v$idRegister}, Landroid/view/View;->setVisibility(I)V
            :hide_guide_done

            sget v$idRegister, Lcn/xender/R${'$'}id;->x_drawer_rate_item:I
            invoke-virtual {p0, v$idRegister}, Landroid/app/Activity;->findViewById(I)Landroid/view/View;
            move-result-object v$viewRegister
            if-eqz v$viewRegister, :hide_rate_done
            const/16 v$idRegister, 0x8
            invoke-virtual {v$viewRegister, v$idRegister}, Landroid/view/View;->setVisibility(I)V
            :hide_rate_done

            sget v$idRegister, Lcn/xender/R${'$'}id;->x_drawer_help_item:I
            invoke-virtual {p0, v$idRegister}, Landroid/app/Activity;->findViewById(I)Landroid/view/View;
            move-result-object v$viewRegister
            if-eqz v$viewRegister, :hide_help_done
            const/16 v$idRegister, 0x8
            invoke-virtual {v$viewRegister, v$idRegister}, Landroid/view/View;->setVisibility(I)V
            :hide_help_done

            sget v$idRegister, Lcn/xender/R${'$'}id;->x_drawer_about_item:I
            invoke-virtual {p0, v$idRegister}, Landroid/app/Activity;->findViewById(I)Landroid/view/View;
            move-result-object v$viewRegister
            if-eqz v$viewRegister, :hide_about_done
            const/16 v$idRegister, 0x8
            invoke-virtual {v$viewRegister, v$idRegister}, Landroid/view/View;->setVisibility(I)V
            :hide_about_done

            sget v$idRegister, Lcn/xender/R${'$'}id;->connect_button:I
            invoke-virtual {p0, v$idRegister}, Landroid/app/Activity;->findViewById(I)Landroid/view/View;
            move-result-object v$viewRegister
            if-eqz v$viewRegister, :front_connect_done
            invoke-virtual {v$viewRegister}, Landroid/view/View;->bringToFront()V
            :front_connect_done

            sget v$idRegister, Lcn/xender/R${'$'}id;->create_btn:I
            invoke-virtual {p0, v$idRegister}, Landroid/app/Activity;->findViewById(I)Landroid/view/View;
            move-result-object v$viewRegister
            if-eqz v$viewRegister, :front_create_done
            invoke-virtual {v$viewRegister}, Landroid/view/View;->bringToFront()V
            :front_create_done

            sget v$idRegister, Lcn/xender/R${'$'}id;->join_btn:I
            invoke-virtual {p0, v$idRegister}, Landroid/app/Activity;->findViewById(I)Landroid/view/View;
            move-result-object v$viewRegister
            if-eqz v$viewRegister, :front_join_done
            invoke-virtual {v$viewRegister}, Landroid/view/View;->bringToFront()V
            :front_join_done
        """.trimIndent(),
    )
}

val cleanMainUiPatch = bytecodePatch(
    name = "Clean main UI",
    description = "Uses direct bytecode edits to hide the bottom navigation, guide, and selected drawer items, then brings the primary action buttons to the front after the relevant lifecycle events.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_XENDER)

    execute {
        MainActivityOnCreateFingerprint.let { fingerprint ->
            val index = fingerprint.instructionMatches[0].index
            fingerprint.method.addXenderUiCustomizations(index + 1)
        }

        MainActivityOnResumeFingerprint.let { fingerprint ->
            val index = fingerprint.instructionMatches[0].index
            fingerprint.method.addXenderUiCustomizations(index + 1)
        }

        MainActivityOpenDrawerFingerprint.let { fingerprint ->
            val index = fingerprint.instructionMatches[0].index
            fingerprint.method.addXenderUiCustomizations(index + 1)
        }

        MainActivityWindowFocusChangedFingerprint.let { fingerprint ->
            val index = fingerprint.instructionMatches[0].index
            fingerprint.method.addXenderUiCustomizations(index + 1)
        }
    }
}
