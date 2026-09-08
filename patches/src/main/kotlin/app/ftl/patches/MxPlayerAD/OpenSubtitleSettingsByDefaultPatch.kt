package app.ftl.patches.mxplayerad

import app.morphe.patcher.patch.booleanOption
import app.morphe.patcher.patch.resourcePatch

// name = null - cleanSidebarShortcutsPatch pulls this in via dependsOn as a configurable option.
internal val openSubtitleSettingsByDefaultPatch = resourcePatch(
    name = null,
    description = "Expands the subtitle settings block by default instead of collapsed.",
) {
    compatibleWith(COMPATIBILITY_MX_PLAYER_AD)

    val openSubtitleSettings by booleanOption(
        key = "openSubtitleSettings",
        default = true,
        title = "Open subtitle settings by default",
        description = "Expands Sync/Speed/Panel/Customization in the subtitle menu instead of collapsed.",
    )

    execute {
        if (openSubtitleSettings != true) return@execute

        document("res/layout/menu_subtitle.xml").use { document ->
            document.documentElement.findById("subtitle_settings_detail")
                ?.setAttribute("android:visibility", "visible")
        }
    }
}
