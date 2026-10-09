package app.ftl.patches.xplayer

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.resourcePatch

private const val TOPBAR = "res/layout/simple_player_topbar.xml"
private const val CONTROLBAR = "res/layout/simple_player_controlbar.xml"

@Suppress("unused")
val hidePlayerCastSeekPatch = resourcePatch(
    name = "Hide Cast FF FB In Player",
    description = "Hides the cast, custom and 10 second forward/backward buttons in the player.",
) {
    compatibleWith(
        Compatibility(
            name = "XPlayer - Video Player",
            packageName = "video.player.videoplayer",
            targets = listOf(AppTarget(version = "2.9.2"))
        )
    )

    execute {
        document(TOPBAR).use { document ->
            val ids = document.indexById()
            listOf("iv_cast", "iv_custom1").forEach {
                ids.byId(TOPBAR, it).setAttributes(
                    "android:layout_width" to "0.0dip",
                    "android:layout_height" to "0.0dip",
                    "android:padding" to "0.0dip"
                )
            }
        }

        document(CONTROLBAR).use { document ->
            val ids = document.indexById()
            ids.byId(CONTROLBAR, "video_fb10").setAttributes(
                "android:layout_width" to "0.0dip",
                "android:layout_height" to "0.0dip",
                "android:layout_marginRight" to "0.0dip"
            )
            ids.byId(CONTROLBAR, "video_ff10").setAttributes(
                "android:layout_width" to "0.0dip",
                "android:layout_height" to "0.0dip",
                "android:layout_marginLeft" to "0.0dip"
            )
        }
    }
}
