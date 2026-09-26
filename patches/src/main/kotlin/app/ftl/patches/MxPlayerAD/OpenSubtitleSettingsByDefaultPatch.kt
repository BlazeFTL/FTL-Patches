package app.ftl.patches.mxplayerad

import app.morphe.patcher.patch.resourcePatch

// name = null - only reached via cleanSidebarShortcutsPatch's dependsOn below.
internal val openSubtitleSettingsByDefaultPatch = resourcePatch(
    name = null,
    description = "Adds a Mod Settings switch that expands the subtitle settings block by default.",
) {
    compatibleWith(COMPATIBILITY_MX_PLAYER_AD)

    dependsOn(modSettingsPatch, modSettingFlagPatch(KEY_SUBTITLE_OPEN_SETTINGS))

    execute {
        document("res/layout/menu_subtitle.xml").use { document ->
            document.documentElement.findById("subtitle_settings_detail")
                ?.addModViewHider(KEY_SUBTITLE_OPEN_SETTINGS, "subtitle_settings_detail", "show")
        }
    }
}
