package app.ftl.patches.snaptube

import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

val removeBrowserRegistrationPatch = resourcePatch(
    name = "Remove from default browser list",
    description = "Strips the LinkHandleActivity alias's unscoped " +
        "so the app stops appearing as a candidate in the system's " +
        "default browser / \"open with\" chooser. " +
    default = false,
) {
    compatibleWith(COMPATIBILITY_SNAPTUBE)

    execute {
        document("AndroidManifest.xml").use { document ->
            val aliases = document.getElementsByTagName("activity-alias")
            var alias: Element? = null
            for (i in 0 until aliases.length) {
                val el = aliases.item(i) as? Element ?: continue
                if (el.getAttribute("android:name") == LINK_HANDLE_ACTIVITY_ALIAS) {
                    alias = el
                    break
                }
            }
            val linkHandleAlias = alias ?: return@use

            val filters = linkHandleAlias.getElementsByTagName("intent-filter")
            val filterList = (0 until filters.length).map { filters.item(it) as Element }

            val openWebviewFilter = filterList.firstOrNull { filter ->
                val actions = filter.getElementsByTagName("action")
                (0 until actions.length).any { i ->
                    (actions.item(i) as Element).getAttribute("android:name") == OPEN_WEBVIEW_ACTION
                }
            } ?: return@use

            val dataEls = openWebviewFilter.getElementsByTagName("data")
            val dataList = (0 until dataEls.length).map { dataEls.item(it) as Element }
            dataList.forEach { openWebviewFilter.removeChild(it) }
        }
    }
}
