package app.ftl.patches.videodownloader

import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

val disableDownloadHandlerPatch = resourcePatch(
    name = "Disable downloader from download menu",
    description = "Removes the unscoped http/https <data> entries from BrowserDownloaderActivity's " +
        "mimeType-scoped intent-filter (identified by having mimeType children but no pathPattern " +
        "children, not by position) so the app stops offering itself in the system \"Download file " +
        "with\" chooser for ordinary web downloads. The ftp/sftp schemes and mimeType entries on " +
        "the same filter, and the separate extension-pathPattern filter used for direct file-link " +
        "downloads, are left in place.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_VIDEO_DOWNLOADER)

    execute {
        document("AndroidManifest.xml").use { document ->
            val activities = document.getElementsByTagName("activity")
            var activity: Element? = null
            for (i in 0 until activities.length) {
                val el = activities.item(i) as? Element ?: continue
                if (el.getAttribute("android:name") == BROWSER_DOWNLOADER_ACTIVITY) {
                    activity = el
                    break
                }
            }
            val browserDownloaderActivity = activity ?: return@use

            val filters = browserDownloaderActivity.getElementsByTagName("intent-filter")
            val filterList = (0 until filters.length).map { filters.item(it) as Element }

            val targetFilter = filterList.firstOrNull { filter ->
                val dataEls = filter.getElementsByTagName("data")
                val dataList = (0 until dataEls.length).map { dataEls.item(it) as Element }
                val hasMimeType = dataList.any { it.getAttribute("android:mimeType").isNotEmpty() }
                val hasPathPattern = dataList.any { it.getAttribute("android:pathPattern").isNotEmpty() }
                val hasBareHttp = dataList.any {
                    val scheme = it.getAttribute("android:scheme")
                    scheme == "http" || scheme == "https"
                }
                hasMimeType && !hasPathPattern && hasBareHttp
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
