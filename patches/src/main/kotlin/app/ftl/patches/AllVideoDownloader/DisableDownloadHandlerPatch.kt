package app.ftl.patches.alldownloader

import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

val disableDownloadHandlerPatch = resourcePatch(
    name = "Disable downloader from download menu",
    description = "Strips WebDownloadActivity's scheme/mimeType/host/pathPattern intent-filter data " +
        "(action and category tags kept) so it no longer offers itself as a handler in the system " +
        "download/\"complete action using\" chooser.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_ALL_VIDEO_DOWNLOADER)

    execute {
        document("AndroidManifest.xml").use { document ->
            val activities = document.getElementsByTagName("activity")
            var activity: Element? = null
            for (i in 0 until activities.length) {
                val el = activities.item(i) as? Element ?: continue
                if (el.getAttribute("android:name") == WEB_DOWNLOAD_ACTIVITY) {
                    activity = el
                    break
                }
            }
            val webDownloadActivity = activity ?: return@use

            val filters = webDownloadActivity.getElementsByTagName("intent-filter")
            val filterList = (0 until filters.length).map { filters.item(it) as Element }

            filterList.forEach { filter ->
                val dataEls = filter.getElementsByTagName("data")
                val dataList = (0 until dataEls.length).map { dataEls.item(it) as Element }
                dataList.forEach { filter.removeChild(it) }
            }
        }
    }
}
