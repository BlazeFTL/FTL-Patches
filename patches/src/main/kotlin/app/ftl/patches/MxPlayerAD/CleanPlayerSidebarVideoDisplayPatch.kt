package app.ftl.patches.mxplayerad

private const val MENU_MORE_LAYOUT = "res/layout/menu_more.xml"

// name = null - only reached via cleanSidebarShortcutsPatch's dependsOn below.
internal val hideVideoDisplayPatch = app.morphe.patcher.patch.resourcePatch(
    name = null,
    description = "Adds a Mod Settings switch for the Video Display row in the player sidebar.",
) {
    compatibleWith(COMPATIBILITY_MX_PLAYER_AD)

    dependsOn(modSettingsPatch, modSettingFlagPatch(KEY_SIDEBAR_HIDE_VIDEO_DISPLAY))

    execute {
        document(MENU_MORE_LAYOUT).use { document ->
            val root = document.documentElement
            root.findById("tv_video_display")
                ?.addModViewHider(KEY_SIDEBAR_HIDE_VIDEO_DISPLAY, "tv_video_display", "both")
            root.findById("sw_video_display")
                ?.addModViewHider(KEY_SIDEBAR_HIDE_VIDEO_DISPLAY, "sw_video_display", "both")
        }
    }
}
