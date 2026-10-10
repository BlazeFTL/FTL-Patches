package app.ftl.patches.xplayer

import app.morphe.patcher.patch.resourcePatch

private const val DIALOG = "res/layout/dialog_player_menu.xml"
private const val FRAGMENT = "res/layout/fragment_player_menu.xml"

private val DIALOG_HIDDEN = listOf(
    "menu_audio", "menu_subtitle", "btn_audio_mode", "menu_cast", "menu_bookmark", "menu_favorite",
    "play_mode_text", "cl_play_mode_menu",
    "brightness_tv", "iv_brightness", "sb_brightness", "tv_brightness_value",
    "volume_tv", "iv_volume", "sb_volume", "tv_volume_value",
    "line_1", "line_3",
    "brightness_left_barrier", "brightness_right_barrier",
    "volume_left_barrier", "volume_right_barrier"
)

private val COLLAPSED_MENU_ITEMS = listOf("menu_cast", "menu_bookmark", "menu_favorite")

@Suppress("unused")
val cleanPlayerMenuPatch = resourcePatch(
    name = "Player Side Cleaned",
    description = "Cleans the player side menu: audio, subtitle, cast, bookmark, favorite, play mode, brightness and volume. Toggle in Mod Settings.",
) {
    compatibleWith(XPLAYER_COMPATIBILITY)

    dependsOn(modSettingsPatch)

    execute {
        document(DIALOG).use { document ->
            document.addGate(DIALOG, ModKeys.CLEAN_PLAYER_MENU, GateMode.GONE, DIALOG_HIDDEN)
        }

        document(FRAGMENT).use { document ->
            document.addGate(FRAGMENT, ModKeys.CLEAN_PLAYER_MENU, GateMode.COLLAPSE, COLLAPSED_MENU_ITEMS)
            document.addGate(FRAGMENT, ModKeys.CLEAN_PLAYER_MENU, GateMode.BADGE, listOf("menu_favorite"))
            document.addGate(FRAGMENT, ModKeys.CLEAN_PLAYER_MENU, GateMode.WIDTH, listOf("view_favorite_red"))
        }
    }
}
