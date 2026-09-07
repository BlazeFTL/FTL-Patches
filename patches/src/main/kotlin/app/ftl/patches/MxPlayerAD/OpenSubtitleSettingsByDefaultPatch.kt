package app.ftl.patches.mxplayerad

import app.morphe.patcher.patch.resourcePatch

val openSubtitleSettingsByDefaultPatch = resourcePatch(
    name = "Open subtitle settings by default",
    description = "Expands the Sync/Speed/Panel/Customization settings block in the subtitle " +
        "menu by default instead of it being collapsed.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_MX_PLAYER_AD)

    execute {
        document("res/layout/menu_subtitle.xml").use { document ->
            document.documentElement.findById("subtitle_settings_detail")
                ?.setAttribute("android:visibility", "visible")
        }
    }
}
