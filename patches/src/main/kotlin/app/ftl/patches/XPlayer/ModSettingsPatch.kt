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
internal const val MOD_SETTINGS_TITLE = "Mod Settings"
private const val SHOW_CALL =
    "invoke-static {v%d}, Lapp/ftl/extension/xplayer/ModSettings;->show(Landroid/content/Context;)V"

private val MENU_FILES = listOf(
    "res/menu/menu_all_video_list.xml",
    "res/menu/menu_folder_list.xml"
)

internal val XPLAYER_TARGET = Compatibility(
    name = "XPlayer - Video Player",
    packageName = "video.player.videoplayer",
    targets = listOf(AppTarget(version = "2.9.2"))
)

private val modSettingsMenuPatch = resourcePatch {
    compatibleWith(XPLAYER_TARGET)

    execute {
        MENU_FILES.forEach { path ->
            document(path).use { document ->
                val items = document.getElementsByTagName("item")
                val widget = (0 until items.length)
                    .map { items.item(it) as Element }
                    .firstOrNull { it.getAttribute("android:id") == WIDGET_ITEM_ID }
                    ?: throw PatchException("Widgets menu item not found in $path")

                widget.setAttribute("android:title", MOD_SETTINGS_TITLE)
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
