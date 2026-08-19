package app.ftl.patches.rsfileexplorer

import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

private const val DOWNLOAD_ACTIVITY = "com.edili.filemanager.module.download.RsDownloadActivity"

val disableDownloadHandlerPatch = resourcePatch(
    name = "Disable downloader from download menu",
    description = "Strips RsDownloadActivity's file-extension and scheme/mimeType intent filters so it no longer offers itself as a handler in the system download/\"complete action using\" chooser.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_RS_FILE_EXPLORER)

    execute {
        document("AndroidManifest.xml").use { document ->
            val activities = document.getElementsByTagName("activity")
            var activity: Element? = null
            for (i in 0 until activities.length) {
                val el = activities.item(i) as? Element ?: continue
                if (el.getAttribute("android:name") == DOWNLOAD_ACTIVITY) {
                    activity = el
                    break
                }
            }
            val downloadActivity = activity ?: return@use

            val filters = downloadActivity.getElementsByTagName("intent-filter")
            val filterList = (0 until filters.length).map { filters.item(it) as Element }

            filterList.forEach { filter ->
                val dataEls = filter.getElementsByTagName("data")
                val dataList = (0 until dataEls.length).map { dataEls.item(it) as Element }
                if (dataList.isEmpty()) return@forEach // custom-action filter, not a download catcher

                // host/pathPattern present -> extension-catcher filter, drop it whole.
                // scheme/mimeType only -> strip its data so the shell matches nothing.
                if (dataList.any { it.hasAttribute("android:host") || it.hasAttribute("android:pathPattern") }) {
                    downloadActivity.removeChild(filter)
                } else {
                    dataList.forEach { filter.removeChild(it) }
                }
            }
        }
    }
}
