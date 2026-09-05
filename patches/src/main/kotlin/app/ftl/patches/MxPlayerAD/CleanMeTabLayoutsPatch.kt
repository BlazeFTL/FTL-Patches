package app.ftl.patches.mxplayerad

import app.morphe.patcher.patch.resourcePatch

// name = null keeps this out of the top-level patch list - cleanMeTabPatch pulls it
// in via dependsOn, so the user only sees one "Clean Me Tab" toggle.
internal val cleanMeTabLayoutsPatch = resourcePatch(
    name = null,
    description = "Collapses the WhatsApp Status Saver row, the Legal/Help group, and the " +
        "local-tiles pager on the Me tab.",
) {
    compatibleWith(COMPATIBILITY_MX_PLAYER_AD)

    execute {
        document("res/layout/layout_local_me_page_fragment.xml").use { document ->
            val root = document.documentElement
            root.findById("whatsapp_status_saver")?.collapse()
            root.findById("group_b")?.hide()
        }

        document("res/layout/item_local_tiles_v5.xml").use { document ->
            val root = document.documentElement
            root.findById("tiles_scroll_host")?.hide()
            root.findById("tiles_indicator")?.collapse()
        }
    }
}
