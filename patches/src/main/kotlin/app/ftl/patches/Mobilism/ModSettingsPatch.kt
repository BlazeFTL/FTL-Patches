package app.ftl.patches.mobilism

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.findFreeRegister

private const val EXT = "Lapp/ftl/extension/mobilism"

internal object ModSettingsHomeMenuCreateFingerprint : Fingerprint(
    definingClass = "Ldemigos/com/mobilism/UI/Main/HomeFragment;",
    name = "onCreateOptionsMenu",
    returnType = "V",
    parameters = listOf("Landroid/view/Menu;", "Landroid/view/MenuInflater;"),
    filters = listOf(
        methodCall(smali = "Landroid/view/MenuInflater;->inflate(ILandroid/view/Menu;)V")
    )
)

internal object ModSettingsTopMenuCreateFingerprint : Fingerprint(
    definingClass = "Ldemigos/com/mobilism/UI/Main/TopFragment;",
    name = "onCreateOptionsMenu",
    returnType = "V",
    parameters = listOf("Landroid/view/Menu;", "Landroid/view/MenuInflater;"),
    filters = listOf(
        methodCall(smali = "Landroid/view/MenuInflater;->inflate(ILandroid/view/Menu;)V")
    )
)

internal object ModSettingsHomeMenuSelectedFingerprint : Fingerprint(
    definingClass = "Ldemigos/com/mobilism/UI/Main/HomeFragment;",
    name = "onOptionsItemSelected",
    returnType = "Z",
    parameters = listOf("Landroid/view/MenuItem;")
)

internal object ModSettingsTopMenuSelectedFingerprint : Fingerprint(
    definingClass = "Ldemigos/com/mobilism/UI/Main/TopFragment;",
    name = "onOptionsItemSelected",
    returnType = "Z",
    parameters = listOf("Landroid/view/MenuItem;")
)

internal object ModSettingsMenuItemSetVisibleFingerprint : Fingerprint(
    definingClass = "Landroidx/appcompat/view/menu/MenuItemImpl;",
    name = "setVisible",
    returnType = "Landroid/view/MenuItem;",
    parameters = listOf("Z")
)

internal object ModSettingsMenuItemSetVisibleIntFingerprint : Fingerprint(
    definingClass = "Landroidx/appcompat/view/menu/MenuItemImpl;",
    name = "setVisibleInt",
    returnType = "Z",
    parameters = listOf("Z")
)

@Suppress("unused")
val modSettingsPatch = bytecodePatch(
    name = "Mod settings",
    description = "Adds a Mod settings entry to the 3-dot menu to show or hide sidebar items and toggle mod features."
) {
    compatibleWith(COMPATIBILITY_MOBILISM)

    extendWith("extensions/mobilism.mpe")

    execute {
        listOf(ModSettingsHomeMenuCreateFingerprint, ModSettingsTopMenuCreateFingerprint).forEach { fingerprint ->
            val inflateIndex = fingerprint.instructionMatches.first().index
            fingerprint.method.addInstructions(
                inflateIndex + 1,
                "invoke-static { p1 }, $EXT/ModSettings;->addMenuItem(Landroid/view/Menu;)V"
            )
        }

        listOf(ModSettingsHomeMenuSelectedFingerprint, ModSettingsTopMenuSelectedFingerprint).forEach { fingerprint ->
            val method = fingerprint.method
            val reg = method.findFreeRegister(0)
            method.addInstructionsWithLabels(
                0,
                """
                    invoke-static { p0, p1 }, $EXT/ModSettings;->onOptionsItemSelected(Ljava/lang/Object;Landroid/view/MenuItem;)Z
                    move-result v$reg
                    if-eqz v$reg, :mod_settings_continue
                    return v$reg
                    :mod_settings_continue
                    nop
                """
            )
        }

        val filterVisible = """
            invoke-static { p0, p1 }, $EXT/ModDrawer;->filterVisible(Ljava/lang/Object;Z)Z
            move-result p1
        """
        ModSettingsMenuItemSetVisibleFingerprint.method.addInstructions(0, filterVisible)
        ModSettingsMenuItemSetVisibleIntFingerprint.methodOrNull?.addInstructions(0, filterVisible)
    }
}
