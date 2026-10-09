package app.ftl.patches.xplayer

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

private const val DIALOG = "res/layout/dialog_player_menu.xml"
private const val FRAGMENT = "res/layout/fragment_player_menu.xml"

private val HIDDEN_ITEMS = listOf(
    "menu_audio", "menu_subtitle", "btn_audio_mode", "menu_cast", "menu_bookmark", "menu_favorite",
    "play_mode_text", "cl_play_mode_menu",
    "brightness_tv", "iv_brightness", "sb_brightness", "tv_brightness_value",
    "volume_tv", "iv_volume", "sb_volume", "tv_volume_value"
)

private val HIDDEN_LINES = listOf("line_1", "line_3")

private val HIDDEN_BARRIERS = listOf(
    "brightness_left_barrier", "brightness_right_barrier",
    "volume_left_barrier", "volume_right_barrier"
)

private val REPEAT_ICONS = listOf("repeat_order", "repeat_repeat", "repeat_shuffle", "repeat_loop", "repeat_not_play_next")

private val REPEAT_CONSTRAINTS = listOf(
    "app:layout_constraintStart_toStartOf",
    "app:layout_constraintStart_toEndOf",
    "app:layout_constraintTop_toBottomOf"
)

private val COLLAPSED_MENU_ITEMS = listOf("menu_cast", "menu_bookmark", "menu_favorite")

@Suppress("unused")
val cleanPlayerMenuPatch = resourcePatch(
    name = "Player Side Cleaned",
    description = "Cleans the player side menu: audio, subtitle, cast, bookmark, favorite, play mode, brightness and volume.",
) {
    compatibleWith(
        Compatibility(
            name = "XPlayer - Video Player",
            packageName = "video.player.videoplayer",
            targets = listOf(AppTarget(version = "2.9.2"))
        )
    )

    execute {
        document(DIALOG).use { document ->
            val ids = document.indexById()

            HIDDEN_ITEMS.forEach {
                ids.byId(DIALOG, it).setAttributes(
                    "android:alpha" to "0.0",
                    "android:clickable" to "false",
                    "android:focusable" to "false",
                    "android:visibility" to "gone"
                )
            }

            HIDDEN_LINES.forEach {
                ids.byId(DIALOG, it).setAttributes(
                    "android:alpha" to "0.0",
                    "android:visibility" to "gone"
                )
            }

            HIDDEN_BARRIERS.forEach {
                ids.byId(DIALOG, it).setAttribute("android:visibility", "gone")
            }

            ids.byId(DIALOG, "repeat_mode_text")
                .setAttribute("app:layout_constraintTop_toBottomOf", "@id/line_0")
            ids.byId(DIALOG, "decoder_tv")
                .setAttribute("app:layout_constraintTop_toBottomOf", "@id/line_2")

            REPEAT_ICONS.forEach { icon ->
                val element = ids.byId(DIALOG, icon)
                REPEAT_CONSTRAINTS.forEach { element.removeAttribute(it) }
            }
        }

        document(FRAGMENT).use { document ->
            val ids = document.indexById()

            COLLAPSED_MENU_ITEMS.forEach {
                ids.byId(FRAGMENT, it).setAttributes(
                    "android:layout_height" to "0.0dip",
                    "android:minWidth" to "0.0dip"
                )
            }

            val favorite = ids.byId(FRAGMENT, "menu_favorite")
            val badge = (0 until favorite.childNodes.length)
                .map { favorite.childNodes.item(it) }
                .filterIsInstance<Element>()
                .firstOrNull { it.tagName.endsWith("ConstraintLayout") }
                ?: throw PatchException("Favorite badge layout not found in $FRAGMENT")

            badge.setAttributes(
                "android:layout_width" to "0.0dip",
                "android:layout_height" to "0.0dip"
            )

            ids.byId(FRAGMENT, "view_favorite_red").setAttribute("android:layout_width", "0.0dip")
        }
    }
}
