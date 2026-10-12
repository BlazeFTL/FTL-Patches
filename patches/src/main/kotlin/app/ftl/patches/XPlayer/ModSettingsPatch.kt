package app.ftl.patches.xplayer

import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import org.w3c.dom.Element

private const val WIDGET_ITEM_ID = "@id/widget"
private const val SETTINGS_ITEM_ID = "@id/setting"
private const val ICON_PATH = "res/drawable/ic_mod_settings.xml"
internal const val MOD_SETTINGS_TITLE = "Mod Settings"
private const val SHOW_CALL =
    "invoke-static {v%d}, Lapp/ftl/extension/xplayer/ModSettings;->show(Landroid/content/Context;)V"

private const val ICON_XML = """<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24.0dip"
    android:height="24.0dip"
    android:viewportWidth="24.0"
    android:viewportHeight="24.0">
    <path
        android:fillColor="#ff000000"
        android:pathData="M3,17v2h6v-2L3,17zM3,5v2h10L13,5L3,5zM13,21v-2h8v-2h-8v-2h-2v6h2zM7,9v2L3,11v2h4v2h2L9,9L7,9zM21,13v-2L11,11v2h10zM15,9h2L17,7h4L21,5h-4L17,3h-2v6z" />
</vector>
"""

private val MENU_FILES = listOf(
    "res/menu/menu_all_video_list.xml",
    "res/menu/menu_folder_list.xml"
)

internal val XPLAYER_TARGET = Compatibility(
    name = "XPlayer - Video Player",
    packageName = "video.player.videoplayer",
    targets = listOf(AppTarget(version = "2.9.2"))
)

private fun org.w3c.dom.Document.findItem(id: String): Element? {
    val items = getElementsByTagName("item")
    return (0 until items.length)
        .map { items.item(it) as Element }
        .firstOrNull { it.getAttribute("android:id") == id }
}

private val modSettingsMenuPatch = resourcePatch {
    compatibleWith(XPLAYER_TARGET)

    execute {
        val icon = get(ICON_PATH)
        icon.parentFile?.mkdirs()
        icon.writeText(ICON_XML)

        MENU_FILES.forEach { path ->
            document(path).use { document ->
                val widget = document.findItem(WIDGET_ITEM_ID)
                    ?: throw PatchException("Widgets menu item not found in $path")
                val settings = document.findItem(SETTINGS_ITEM_ID)
                    ?: throw PatchException("Settings menu item not found in $path")

                widget.setAttribute("android:title", MOD_SETTINGS_TITLE)
                widget.setAttribute("android:icon", "@drawable/ic_mod_settings")
                widget.setAttribute("app:iconTint", "?homeMenuIconTint")
                widget.removeAttribute("android:visible")

                val parent = settings.parentNode
                if (widget.parentNode !== parent) {
                    throw PatchException("Widgets and Settings items are in different menus in $path")
                }
                parent.removeChild(widget)
                parent.insertBefore(widget, settings.nextSibling)
            }
        }
    }
}

@Suppress("unused")
val modSettingsPatch = bytecodePatch(
    name = "Mod Settings",
    description = "Adds a Mod Settings entry below Settings in the home 3-dot menu."
) {
    compatibleWith(XPLAYER_TARGET)

    dependsOn(modSettingsMenuPatch)

    extendWith("extensions/xplayer.mpe")

    execute {
        WidgetMenuClickFingerprint.matchAll().forEach { match ->
            val matches = match.instructionMatches
            val contextRegister = matches[1].getInstruction<FiveRegisterInstruction>().registerD
            if (contextRegister > 15) throw PatchException("Context register out of range")

            match.method.replaceInstruction(matches[2].index, SHOW_CALL.format(contextRegister))
        }
    }
}
