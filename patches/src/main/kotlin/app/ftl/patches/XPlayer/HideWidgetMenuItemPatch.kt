package app.ftl.patches.xplayer

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

private val MENU_FILES = listOf(
    "res/menu/menu_all_video_list.xml",
    "res/menu/menu_folder_list.xml"
)

@Suppress("unused")
val hideWidgetMenuItemPatch = resourcePatch(
    name = "Hide Widgets Menu Item",
    description = "Removes Widgets from the home 3-dot menu.",
) {
    compatibleWith(
        Compatibility(
            name = "XPlayer - Video Player",
            packageName = "video.player.videoplayer",
            targets = listOf(AppTarget(version = "2.9.2"))
        )
    )

    execute {
        MENU_FILES.forEach { path ->
            document(path).use { document ->
                val items = document.getElementsByTagName("item")
                val widget = (0 until items.length)
                    .map { items.item(it) as Element }
                    .firstOrNull { it.getAttribute("android:id") == "@id/widget" }
                    ?: throw PatchException("Widgets menu item not found in $path")

                widget.setAttribute("android:visible", "false")
            }
        }
    }
}
