package app.ftl.patches.mixplorer

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

private val COMPATIBILITY_MIXPLORER = Compatibility(
    packageName = "com.mixplorer",
    name = "MiXplorer",
    // version = null -> any version supported.
    targets = listOf(AppTarget(version = null)),
)

// Activities that register themselves against http/https VIEW intents purely to
// appear in the browser's "Download complete / Open with" chooser as separate
// entries (Explore / Download / Copy to / Extract to), all pointing at the same app.
private val TARGET_ACTIVITIES = setOf(
    "com.mixplorer.activities.ExploreActivity",
    "com.mixplorer.activities.DownloadActivity",
    "com.mixplorer.activities.CopyActivity",
    "com.mixplorer.activities.ExtractActivity",
)

val disableDownloadChooserPatch = resourcePatch(
    name = "Disable download chooser duplicates",
    description = "Removes only the http/https <data> entries from MiXplorer's Explore/Download/Copy to/Extract to shell activities' VIEW intent filters, so the app stops showing up multiple times in Firefox's (and other browsers') pre-download link chooser. Every other scheme (file/content/smb/ftp/sftp), Share-to (SEND/SEND_MULTIPLE), and mimeType/pathPattern matching stay intact.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_MIXPLORER)

    execute {
        document("AndroidManifest.xml").use { document ->
            val activities = document.getElementsByTagName("activity")
            val activityList = (0 until activities.length)
                .mapNotNull { activities.item(it) as? Element }
                .filter { it.getAttribute("android:name") in TARGET_ACTIVITIES }

            activityList.forEach { activity ->
                val filters = activity.getElementsByTagName("intent-filter")
                val filterList = (0 until filters.length).map { filters.item(it) as Element }

                filterList.forEach { filter ->
                    val actions = filter.getElementsByTagName("action")
                    val hasView = (0 until actions.length)
                        .map { actions.item(it) as Element }
                        .any { it.getAttribute("android:name") == "android.intent.action.VIEW" }
                    if (!hasView) return@forEach // not a VIEW filter, leave SEND/SEND_MULTIPLE alone

                    val dataEls = filter.getElementsByTagName("data")
                    val dataList = (0 until dataEls.length).map { dataEls.item(it) as Element }

                    val schemeEls = dataList.filter { it.getAttribute("android:scheme").isNotEmpty() }
                    val httpSchemeEls = schemeEls.filter {
                        val s = it.getAttribute("android:scheme")
                        s == "http" || s == "https"
                    }
                    if (httpSchemeEls.isEmpty()) return@forEach // no http/https here, leave filter untouched

                    // Only strip the http/https <data> nodes, keep file/content/smb/ftp/sftp
                    // and every mimeType/pathPattern <data> node so local handling still works.
                    httpSchemeEls.forEach { filter.removeChild(it) }

                    // Edge case: filter had ONLY http/https as scheme(s) -> now scheme-less and
                    // would over-broadly match by default. Drop the whole (now-useless) filter.
                    if (schemeEls.size == httpSchemeEls.size) {
                        activity.removeChild(filter)
                    }
                }
            }
        }
    }
}
