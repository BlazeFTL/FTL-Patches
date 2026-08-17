package app.ftl.patches.rsfileexplorer

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

private val COMPATIBILITY_RS_FILE_EXPLORER = Compatibility(
    packageName = "com.rs.explorer.filemanager",
    name = "RS File Manager",
    targets = listOf(
        AppTarget(version = "2.3.0.4", versionCode = 239),
    ),
)

private const val SPLASH_ACTIVITY = "com.edili.filemanager.module.activity.FirstActivity"
private const val MAIN_ACTIVITY = "com.edili.filemanager.MainActivity"

val skipSplashScreenPatch = resourcePatch(
    name = "Skip splash screen",
    description = "Moves the launcher intent filter from the splash activity to the main activity, so the app boots straight to the file list instead of showing the splash screen.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_RS_FILE_EXPLORER)

    execute {
        document("AndroidManifest.xml").use { document ->
            val activities = document.getElementsByTagName("activity")
            var splash: Element? = null
            var main: Element? = null

            for (i in 0 until activities.length) {
                val activity = activities.item(i) as? Element ?: continue
                when (activity.getAttribute("android:name")) {
                    SPLASH_ACTIVITY -> splash = activity
                    MAIN_ACTIVITY -> main = activity
                }
            }

            val splashActivity = splash ?: return@use
            val mainActivity = main ?: return@use

            val intentFilters = splashActivity.getElementsByTagName("intent-filter")
            var launcherFilter: Element? = null

            for (i in 0 until intentFilters.length) {
                val filter = intentFilters.item(i) as? Element ?: continue
                val actions = filter.getElementsByTagName("action")
                val hasMainAction = (0 until actions.length).any { idx ->
                    (actions.item(idx) as? Element)?.getAttribute("android:name") == "android.intent.action.MAIN"
                }
                if (hasMainAction) {
                    launcherFilter = filter
                    break
                }
            }

            // Only the MAIN/LAUNCHER intent-filter moves; the splash activity keeps
            // its other intent-filter (com.rs.action.permission.require) untouched,
            // matching the reference diff.
            val filterToMove = launcherFilter ?: return@use
            splashActivity.removeChild(filterToMove)
            mainActivity.insertBefore(filterToMove, mainActivity.firstChild)
        }
    }
}
