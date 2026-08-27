package app.ftl.patches.alldownloader

import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

val removeBrowserRegistrationPatch = resourcePatch(
    name = "Remove from default browser list",
    description = "Removes the unscoped http/https <data> entries from MainActivity's " +
        "intent-filters (matched by scheme value, not position) so the app stops appearing as " +
        "a candidate in the system's default browser / \"open with\" chooser. Other schemes and " +
        "mimeTypes on the same filters (about, javascript, inline, file, text/html, etc., used for " +
        "the app's internal WebView) are left in place.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_ALL_VIDEO_DOWNLOADER)

    execute {
        document("AndroidManifest.xml").use { document ->
            val activities = document.getElementsByTagName("activity")
            var activity: Element? = null
            for (i in 0 until activities.length) {
                val el = activities.item(i) as? Element ?: continue
                if (el.getAttribute("android:name") == MAIN_ACTIVITY_MANIFEST_NAME) {
                    activity = el
                    break
                }
            }
            val mainActivity = activity ?: return@use

            val filters = mainActivity.getElementsByTagName("intent-filter")
            val filterList = (0 until filters.length).map { filters.item(it) as Element }

            filterList.forEach { filter ->
                val dataEls = filter.getElementsByTagName("data")
                val dataList = (0 until dataEls.length).map { dataEls.item(it) as Element }
                dataList.forEach { data ->
                    val scheme = data.getAttribute("android:scheme")
                    val host = data.getAttribute("android:host")
                    if ((scheme == "http" || scheme == "https") && host.isEmpty()) {
                        filter.removeChild(data)
                    }
                }
            }
        }
    }
}
