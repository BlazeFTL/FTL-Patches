package app.ftl.patches.videodownloader

import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

val removeBrowserRegistrationPatch = resourcePatch(
    name = "Remove from default browser list",
    description = "Removes http/https <data> entries from MainTabsActivity's " +
        "so the app stops appearing as a candidate in the system's " +
        "default browser / \"open with\" chooser.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_VIDEO_DOWNLOADER)

    execute {
        document("AndroidManifest.xml").use { document ->
            val activities = document.getElementsByTagName("activity")
            var activity: Element? = null
            for (i in 0 until activities.length) {
                val el = activities.item(i) as? Element ?: continue
                if (el.getAttribute("android:name") == MAIN_TABS_ACTIVITY) {
                    activity = el
                    break
                }
            }
            val mainTabsActivity = activity ?: return@use

            val filters = mainTabsActivity.getElementsByTagName("intent-filter")
            val filterList = (0 until filters.length).map { filters.item(it) as Element }

            val targetFilter = filterList.firstOrNull { filter ->
                val dataEls = filter.getElementsByTagName("data")
                val dataList = (0 until dataEls.length).map { dataEls.item(it) as Element }
                val hasMimeType = dataList.any { it.getAttribute("android:mimeType").isNotEmpty() }
                val hasBareHttp = dataList.any {
                    val scheme = it.getAttribute("android:scheme")
                    scheme == "http" || scheme == "https"
                }
                !hasMimeType && hasBareHttp
            } ?: return@use

            val dataEls = targetFilter.getElementsByTagName("data")
            val dataList = (0 until dataEls.length).map { dataEls.item(it) as Element }
            dataList.forEach { data ->
                val scheme = data.getAttribute("android:scheme")
                if (scheme == "http" || scheme == "https") {
                    targetFilter.removeChild(data)
                }
            }
        }
    }
}
