package app.ftl.patches.allvideoplayer

import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

private const val PACKAGE_NAME = "com.allformatplayer.streamvideoplayer"
private const val SPLASH_ACTIVITY = "$PACKAGE_NAME.splash.SplashScreen"
private const val MAIN_ACTIVITY = "$PACKAGE_NAME.MainActivity"
private const val LAUNCHER_CATEGORY = "android.intent.category.LAUNCHER"

val skipSplashScreenPatch = resourcePatch(
    name = "Skip splash screen",
    description = "Launches the app directly into the main screen.",
    default = true
) {
    compatibleWith(
        Compatibility(
            packageName = PACKAGE_NAME,
            name = "Video Player All Format"
        )
    )

    execute {
        document("AndroidManifest.xml").use { document ->
            val activities = document.getElementsByTagName("activity")

            fun findActivity(name: String): Element {
                for (i in 0 until activities.length) {
                    val activity = activities.item(i) as Element
                    if (activity.getAttribute("android:name") == name) return activity
                }
                throw PatchException("Activity not found: $name")
            }

            val splash = findActivity(SPLASH_ACTIVITY)
            val main = findActivity(MAIN_ACTIVITY)

            val filters = splash.getElementsByTagName("intent-filter")
            val launcherFilters = (0 until filters.length)
                .map { filters.item(it) as Element }
                .filter { filter ->
                    val categories = filter.getElementsByTagName("category")
                    (0 until categories.length).any {
                        (categories.item(it) as Element).getAttribute("android:name") == LAUNCHER_CATEGORY
                    }
                }

            if (launcherFilters.isEmpty()) throw PatchException("Launcher intent-filter not found in $SPLASH_ACTIVITY")

            launcherFilters.forEach { filter ->
                splash.removeChild(filter)
                main.appendChild(filter)
            }
        }
    }
}
