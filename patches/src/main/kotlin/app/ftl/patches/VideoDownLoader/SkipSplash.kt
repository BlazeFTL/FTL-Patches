package app.ftl.patches.videodownloader

import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

/*
 * Finds the launcher activity by its MAIN/LAUNCHER intent-filter rather than pinning
 * MainActivity's own android:name - BROWSER_DOWNLOADER_ACTIVITY and MAIN_TABS_ACTIVITY
 * live under two different subpackages in this app (.five.activity vs .activity), so
 * MainActivity's real package is unverified. Renaming whatever the launcher resolves
 * to avoids hardcoding a guess.
 */
val skipSplashPatch = resourcePatch(
    name = "Skip splash screen",
    description = "Skips splash screen " +
        "so the app opens directly to the main screen.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_VIDEO_DOWNLOADER)

    execute {
        document("AndroidManifest.xml").use { document ->
            val activities = document.getElementsByTagName("activity")
            val activityList = (0 until activities.length).mapNotNull { activities.item(it) as? Element }

            val launcher = activityList.firstOrNull { activity ->
                val filters = activity.getElementsByTagName("intent-filter")
                (0 until filters.length).any { i ->
                    val filter = filters.item(i) as Element
                    val actions = filter.getElementsByTagName("action")
                    val categories = filter.getElementsByTagName("category")
                    val hasMain = (0 until actions.length).any {
                        (actions.item(it) as Element).getAttribute("android:name") ==
                            "android.intent.action.MAIN"
                    }
                    val hasLauncher = (0 until categories.length).any {
                        (categories.item(it) as Element).getAttribute("android:name") ==
                            "android.intent.category.LAUNCHER"
                    }
                    hasMain && hasLauncher
                }
            } ?: return@use

            if (launcher.getAttribute("android:name") == MAIN_TABS_ACTIVITY) return@use

            launcher.setAttribute("android:name", MAIN_TABS_ACTIVITY)
        }
    }
}
