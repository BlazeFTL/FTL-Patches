package app.ftl.patches.xplayer

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

private val HIDDEN_ITEM_IDS = setOf("@id/lock", "@id/add_to_playlist")

private val BOTTOM_SHEETS = mapOf(
    "res/layout/bottom_sheet_video.xml" to false,
    "res/layout/bottom_sheet_folder.xml" to true
)

@Suppress("unused")
val cleanBottomSheetsPatch = resourcePatch(
    name = "Clean 3 Dot Menu",
    description = "Removes Lock and Add to playlist from the video and folder 3-dot bottom sheets.",
) {
    compatibleWith(
        Compatibility(
            name = "XPlayer - Video Player",
            packageName = "video.player.videoplayer",
            targets = listOf(AppTarget(version = "2.9.2"))
        )
    )

    execute {
        BOTTOM_SHEETS.forEach { (path, markInvisible) ->
            document(path).use { document ->
                val all = document.getElementsByTagName("*")
                val targets = (0 until all.length)
                    .map { all.item(it) as Element }
                    .filter { it.getAttribute("android:id") in HIDDEN_ITEM_IDS }

                if (targets.size != HIDDEN_ITEM_IDS.size) {
                    throw PatchException("Expected ${HIDDEN_ITEM_IDS.size} items in $path, found ${targets.size}")
                }

                targets.forEach {
                    it.setAttribute("android:height", "0.0dip")
                    it.setAttribute("android:width", "0.0dip")
                    if (markInvisible) it.setAttribute("android:visible", "false")
                }
            }
        }
    }
}
