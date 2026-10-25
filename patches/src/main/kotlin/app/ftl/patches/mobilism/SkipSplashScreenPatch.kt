package app.ftl.patches.mobilism

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

private const val SPLASH_ACTIVITY = "demigos.com.mobilism.UI.SplashActivity"
private const val MAIN_ACTIVITY = "demigos.com.mobilism.UI.MainActivity_"
private const val LAUNCHER_CATEGORY = "android.intent.category.LAUNCHER"

@Suppress("unused")
val skipSplashScreenPatch = resourcePatch(
    name = "Skip splash screen",
    description = "Launches the home screen directly instead of the splash screen."
) {
    compatibleWith(COMPATIBILITY_MOBILISM)

    execute {
        document("AndroidManifest.xml").use { document ->
            val activities = document.getElementsByTagName("activity")
            var splash: Element? = null
            var main: Element? = null
            for (i in 0 until activities.length) {
                val activity = activities.item(i) as Element
                when (activity.getAttribute("android:name")) {
                    SPLASH_ACTIVITY -> splash = activity
                    MAIN_ACTIVITY -> main = activity
                }
            }
            val splashActivity = splash ?: throw PatchException("SplashActivity not found in manifest")
            val mainActivity = main ?: throw PatchException("MainActivity_ not found in manifest")

            var launcher: Element? = null
            val filters = splashActivity.getElementsByTagName("intent-filter")
            for (i in 0 until filters.length) {
                val filter = filters.item(i) as Element
                val categories = filter.getElementsByTagName("category")
                for (j in 0 until categories.length) {
                    if ((categories.item(j) as Element).getAttribute("android:name") == LAUNCHER_CATEGORY) {
                        launcher = filter
                    }
                }
            }
            val launcherFilter = launcher ?: throw PatchException("Launcher intent-filter not found on SplashActivity")

            splashActivity.removeChild(launcherFilter)
            mainActivity.appendChild(launcherFilter)
        }
    }
}
