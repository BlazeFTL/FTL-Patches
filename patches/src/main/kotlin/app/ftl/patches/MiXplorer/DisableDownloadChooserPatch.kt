package app.ftl.patches.mixplorer

import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

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
    description = "Removes the ACTION_VIEW http/https intent filters from MiXplorer's Explore/Download/Copy to/Extract to shell activities so the app stops showing up multiple times in Firefox's (and other browsers') download-complete chooser. Share-to (SEND/SEND_MULTIPLE) entries and local/ftp/smb file handling are untouched.",
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
                    val schemes = (0 until dataEls.length)
                        .map { dataEls.item(it) as Element }
                        .mapNotNull { it.getAttribute("android:scheme").takeIf(String::isNotEmpty) }
                        .toSet()

                    if (schemes.any { it == "http" || it == "https" }) {
                        activity.removeChild(filter)
                    }
                }
            }
        }
    }
}
