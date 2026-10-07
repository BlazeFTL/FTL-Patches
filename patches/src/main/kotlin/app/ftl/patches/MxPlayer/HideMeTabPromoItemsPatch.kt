package app.ftl.patches.mxplayer

import app.morphe.patcher.patch.booleanOption
import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

private val FRAGMENT_ME_LAYOUTS = listOf(
    "res/layout/fragment_me.xml",
    "res/layout-v22/fragment_me.xml",
)

val hideMeTabPromoItemsPatch = resourcePatch(
    name = "Hide Settings Page UseLess Buttons",
    description = "Collapses the WhatsApp, Legal, and Help entries on the Me tab.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_MX_PLAYER_PRO)

    val hideWhatsApp by booleanOption(
        key = "hideWhatsApp",
        default = true,
        title = "Hide WhatsApp",
        description = "Collapses the WhatsApp entry.",
    )
    val hideLegal by booleanOption(
        key = "hideLegal",
        default = true,
        title = "Hide Legal",
        description = "Collapses the Legal entry.",
    )
    val hideHelp by booleanOption(
        key = "hideHelp",
        default = true,
        title = "Hide Help",
        description = "Collapses the Help entry.",
    )

    execute {
        val itemIds = buildList {
            if (hideLegal != false) add("legal")
            if (hideHelp != false) add("help")
        }

        FRAGMENT_ME_LAYOUTS.forEach { path ->
            document(path).use { document ->
                val root = document.documentElement

                if (hideWhatsApp != false) root.findById("whatsapp_layout")?.collapse()

                itemIds.forEach { id ->
                    val item = root.findById(id) ?: return@forEach
                    item.collapse()
                    (item.parentNode as? Element)?.collapse()
                }
            }
        }
    }
}
