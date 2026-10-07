package app.ftl.patches.mxplayerad

import app.morphe.patcher.patch.resourcePatch

internal val cleanMeTabLayoutsPatch = resourcePatch(
    name = null,
    description = "Adds Mod Settings switches for the WhatsApp Status Saver row, the Legal/Help group, and the " +
        "local-tiles pager on the Me tab.",
) {
    compatibleWith(COMPATIBILITY_MX_PLAYER_AD)

    dependsOn(
        modSettingsPatch,
        modSettingFlagPatch(KEY_ME_HIDE_STATUS_SAVER),
        modSettingFlagPatch(KEY_ME_HIDE_LEGAL_HELP),
        modSettingFlagPatch(KEY_ME_HIDE_TILES_PAGER),
    )

    execute {
        document("res/layout/layout_local_me_page_fragment.xml").use { document ->
            val root = document.documentElement
            root.findById("whatsapp_status_saver")
                ?.addModViewHider(KEY_ME_HIDE_STATUS_SAVER, "whatsapp_status_saver", "collapse")
            root.findById("group_b")
                ?.addModViewHider(KEY_ME_HIDE_LEGAL_HELP, "group_b", "gone")
        }

        document("res/layout/item_local_tiles_v5.xml").use { document ->
            val root = document.documentElement
            root.findById("tiles_scroll_host")
                ?.addModViewHider(KEY_ME_HIDE_TILES_PAGER, "tiles_scroll_host", "gone")
            root.findById("tiles_indicator")
                ?.addModViewHider(KEY_ME_HIDE_TILES_PAGER, "tiles_indicator", "collapse")
        }
    }
}
