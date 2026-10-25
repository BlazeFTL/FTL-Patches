package app.ftl.patches.mobilism

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
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

internal object ModSettingsMenuItemVisibleFingerprint : Fingerprint(
    definingClass = "Landroidx/appcompat/view/menu/MenuItemImpl;",
    name = "isVisible",
    returnType = "Z",
    parameters = listOf()
)

@Suppress("unused")
val modSettingsPatch = bytecodePatch(
    name = "Mod settings",
    description = "Adds a Mod settings entry to the 3-dot menu to show or hide sidebar items."
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
            method.addInstructions(
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

        ModSettingsMenuItemVisibleFingerprint.method.let { method ->
            val reg = method.findFreeRegister(0)
            method.addInstructions(
                0,
                """
                    invoke-static { p0 }, $EXT/ModDrawer;->forceHidden(Ljava/lang/Object;)Z
                    move-result v$reg
                    if-eqz v$reg, :mod_drawer_continue
                    const/16 v$reg, 0x0
                    return v$reg
                    :mod_drawer_continue
                    nop
                """
            )
        }
    }
}
