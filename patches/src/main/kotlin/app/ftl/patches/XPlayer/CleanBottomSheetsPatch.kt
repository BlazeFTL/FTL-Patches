package app.ftl.patches.xplayer

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.AccessFlags

private val BOTTOM_SHEETS = listOf(
    "res/layout/bottom_sheet_video.xml",
    "res/layout/bottom_sheet_folder.xml"
)

private const val MENU_HOOK = """
    invoke-virtual {p0}, Landroidx/fragment/app/Fragment;->getContext()Landroid/content/Context;
    move-result-object v0
    invoke-static {v0, p1}, Lapp/ftl/extension/xplayer/ModMenus;->clean(Landroid/content/Context;Landroid/view/Menu;)V
"""

private fun MutableMethod.localCount() =
    implementation!!.registerCount - parameterTypes.size -
        if (AccessFlags.STATIC.isSet(accessFlags)) 0 else 1

private val cleanBottomSheetLayoutsPatch = resourcePatch {
    compatibleWith(XPLAYER_TARGET)

    dependsOn(modSettingsPatch)

    execute {
        BOTTOM_SHEETS.forEach { path ->
            document(path).use { document ->
                document.addGate(path, ModKeys.CLEAN_SHEETS, GateMode.TEXT, listOf("lock", "add_to_playlist"))
            }
        }
    }
}

@Suppress("unused")
val cleanBottomSheetsPatch = bytecodePatch(
    name = "Clean 3 Dot Menu",
    description = "Removes Lock and Add to playlist from the video and folder 3-dot bottom sheets and from the selection toolbar menus. Toggle in Mod Settings.",
) {
    compatibleWith(XPLAYER_TARGET)

    dependsOn(modSettingsPatch, cleanBottomSheetLayoutsPatch)

    execute {
        SelectModeMenuFingerprint.matchAll().forEach { match ->
            val method = match.method
            if (method.localCount() < 1) {
                throw PatchException("Not enough registers in ${method.definingClass}->${method.name}")
            }
            if (method.implementation!!.registerCount - 1 > 15) {
                throw PatchException("Menu register out of range in ${method.definingClass}->${method.name}")
            }

            val superCall = match.instructionMatches.last().index
            method.addInstructions(superCall + 1, MENU_HOOK)
        }
    }
}
