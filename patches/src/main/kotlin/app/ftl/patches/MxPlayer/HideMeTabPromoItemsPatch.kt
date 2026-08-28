package app.ftl.patches.mxplayer

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

    execute {
        FRAGMENT_ME_LAYOUTS.forEach { path ->
            document(path).use { document ->
                val root = document.documentElement

                root.findById("whatsapp_layout")?.collapse()

                listOf("legal", "help").forEach { id ->
                    val item = root.findById(id) ?: return@forEach
                    item.collapse()
                    (item.parentNode as? Element)?.collapse()
                }
            }
        }
    }
}
