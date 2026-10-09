package app.ftl.patches.xplayer

import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference
import org.w3c.dom.Element

private const val WIDGET_ITEM_ID = "@id/widget"
private const val SEARCH_WINDOW = 4
private const val SHOW_CALL =
    "invoke-static {v%d}, Lapp/ftl/extension/xplayer/ModSettings;->show(Landroid/content/Context;)V"

private val MENU_FILES = listOf(
    "res/menu/menu_all_video_list.xml",
    "res/menu/menu_folder_list.xml"
)

private val XPLAYER_COMPATIBILITY = Compatibility(
    name = "XPlayer - Video Player",
    packageName = "video.player.videoplayer",
    targets = listOf(AppTarget(version = "2.9.2"))
)

private fun Instruction.isAddWidgetClass() =
    opcode == Opcode.CONST_CLASS &&
        ((this as ReferenceInstruction).reference as? TypeReference)?.type == ADD_WIDGET_ACTIVITY

private fun Instruction.isIntentInit(): Boolean {
    if (opcode != Opcode.INVOKE_DIRECT) return false
    val ref = (this as ReferenceInstruction).reference as? MethodReference ?: return false
    return ref.definingClass == "Landroid/content/Intent;" &&
        ref.name == "<init>" &&
        ref.parameterTypes.map { it.toString() } == listOf("Landroid/content/Context;", "Ljava/lang/Class;")
}

private fun Instruction.isStartActivity(): Boolean {
    if (opcode != Opcode.INVOKE_VIRTUAL) return false
    val ref = (this as ReferenceInstruction).reference as? MethodReference ?: return false
    return ref.name == "startActivity" &&
        ref.parameterTypes.map { it.toString() } == listOf("Landroid/content/Intent;")
}

private val modSettingsMenuPatch = resourcePatch {
    compatibleWith(XPLAYER_COMPATIBILITY)

    execute {
        MENU_FILES.forEach { path ->
            document(path).use { document ->
                val items = document.getElementsByTagName("item")
                val widget = (0 until items.length)
                    .map { items.item(it) as Element }
                    .firstOrNull { it.getAttribute("android:id") == WIDGET_ITEM_ID }
                    ?: throw PatchException("Widgets menu item not found in $path")

                widget.setAttribute("android:title", "Mod Settings")
                widget.setAttribute("android:icon", "@drawable/ic_settings")
                widget.setAttribute("app:iconTint", "?homeMenuIconTint")
                widget.removeAttribute("android:visible")
            }
        }
    }
}

@Suppress("unused")
val modSettingsPatch = bytecodePatch(
    name = "Mod Settings",
    description = "Adds a Mod Settings entry in place of Widgets in the home 3-dot menu."
) {
    compatibleWith(XPLAYER_COMPATIBILITY)

    dependsOn(modSettingsMenuPatch)

    extendWith("extensions/xplayer.mpe")

    execute {
        WidgetMenuClickFingerprint.matchAll().forEach { match ->
            val method = match.method
            val instructions = method.implementation!!.instructions

            val classIndex = instructions.indexOfFirst { it.isAddWidgetClass() }
            if (classIndex < 0) throw PatchException("AddWidgetActivity reference not found")

            val initIndex = (classIndex + 1 until minOf(classIndex + 1 + SEARCH_WINDOW, instructions.size))
                .firstOrNull { instructions[it].isIntentInit() }
                ?: throw PatchException("Intent constructor not found after AddWidgetActivity")

            val startIndex = (initIndex + 1 until minOf(initIndex + 1 + SEARCH_WINDOW, instructions.size))
                .firstOrNull { instructions[it].isStartActivity() }
                ?: throw PatchException("startActivity not found after AddWidgetActivity")

            val contextRegister = (instructions[initIndex] as FiveRegisterInstruction).registerD
            if (contextRegister > 15) throw PatchException("Context register out of range")

            method.replaceInstruction(startIndex, SHOW_CALL.format(contextRegister))
        }
    }
}
