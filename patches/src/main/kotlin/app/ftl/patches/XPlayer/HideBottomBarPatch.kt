package app.ftl.patches.xplayer

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

private const val BOTTOM_TAB_ID = "@id/bottom_tab"

@Suppress("unused")
val hideBottomBarPatch = resourcePatch(
    name = "Hide Bottom Bar",
    description = "Hides the bottom tab bar on the home screen.",
) {
    compatibleWith(
        Compatibility(
            name = "XPlayer - Video Player",
            packageName = "video.player.videoplayer",
            targets = listOf(AppTarget(version = "2.9.2"))
        )
    )

    execute {
        document("res/layout/activity_app.xml").use { document ->
            val layouts = document.getElementsByTagName("LinearLayout")

            val bottomTab = (0 until layouts.length)
                .map { layouts.item(it) as Element }
                .firstOrNull { it.getAttribute("android:id") == BOTTOM_TAB_ID }
                ?: throw PatchException("Bottom tab bar not found in activity_app.xml")

            bottomTab.setAttribute("android:layout_width", "0.0dip")
            bottomTab.setAttribute("android:layout_height", "0.0dip")
        }
    }
}
