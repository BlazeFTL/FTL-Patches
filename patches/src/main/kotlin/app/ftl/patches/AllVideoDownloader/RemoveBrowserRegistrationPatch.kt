package app.ftl.patches.alldownloader

import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

val removeBrowserRegistrationPatch = resourcePatch(
    name = "Remove from default browser list",
    description = "Removes the unscoped http/https <data> entries from MainActivity's first " +
        "intent-filter carrying them so the app stops appearing as a candidate in the system's " +
        "default browser / \"open with\" chooser.",
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

            val targetFilter = filterList.firstOrNull { filter ->
                val dataEls = filter.getElementsByTagName("data")
                (0 until dataEls.length).any { i ->
                    val data = dataEls.item(i) as Element
                    val scheme = data.getAttribute("android:scheme")
                    val host = data.getAttribute("android:host")
                    (scheme == "http" || scheme == "https") && host.isEmpty()
                }
            } ?: return@use

            val dataEls = targetFilter.getElementsByTagName("data")
            val dataList = (0 until dataEls.length).map { dataEls.item(it) as Element }
            dataList.forEach { data ->
                val scheme = data.getAttribute("android:scheme")
                val host = data.getAttribute("android:host")
                if ((scheme == "http" || scheme == "https") && host.isEmpty()) {
                    targetFilter.removeChild(data)
                }
            }
        }
    }
}
