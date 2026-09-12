package app.ftl.patches.mxplayerad

import app.morphe.patcher.patch.booleanOption
import app.morphe.patcher.patch.resourcePatch

// Unregistered here - cleanSidebarShortcutsPatch registers it, so it's configured from there.
internal val openSubtitleSettingsOption = booleanOption(
    key = "openSubtitleSettings",
    default = true,
    title = "Open subtitle settings by default",
    description = "Expands Sync/Speed/Panel/Customization in the subtitle menu instead of collapsed.",
)

// name = null - only reached via cleanSidebarShortcutsPatch's dependsOn below.
internal val openSubtitleSettingsByDefaultPatch = resourcePatch(
    name = null,
    description = "Expands the subtitle settings block by default instead of collapsed.",
) {
    compatibleWith(COMPATIBILITY_MX_PLAYER_AD)

    execute {
        if (openSubtitleSettingsOption.value != true) return@execute

        document("res/layout/menu_subtitle.xml").use { document ->
            document.documentElement.findById("subtitle_settings_detail")
                ?.setAttribute("android:visibility", "visible")
        }
    }
}
