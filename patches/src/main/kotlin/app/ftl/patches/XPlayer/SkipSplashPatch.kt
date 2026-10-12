package app.ftl.patches.xplayer

import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element
import org.w3c.dom.NodeList

private const val SPLASH_ACTIVITY = "com.inshot.xplayer.activities.SplashActivity"
private const val FILE_EXPLORER_ACTIVITY = "com.inshot.xplayer.activities.FileExplorerActivity"
private const val LAUNCHER_CATEGORY = "android.intent.category.LAUNCHER"

private fun NodeList.elements(): List<Element> = (0 until length).map { item(it) as Element }

@Suppress("unused")
val skipSplashScreenPatch = resourcePatch(
    name = "Skip Splash Screen",
    description = "Launches directly into the file explorer instead of the splash screen.",
    default = true,
) {
    compatibleWith(
        Compatibility(
            name = "XPlayer - Video Player",
            packageName = "video.player.videoplayer",
        )
    )

    execute {
        document("AndroidManifest.xml").use { document ->
            val activities = document.getElementsByTagName("activity").elements()

            fun findActivity(name: String): Element =
                activities.firstOrNull { it.getAttribute("android:name") == name }
                    ?: throw PatchException("Activity not found: $name")

            val splash = findActivity(SPLASH_ACTIVITY)
            val explorer = findActivity(FILE_EXPLORER_ACTIVITY)

            val launcherFilters = splash.getElementsByTagName("intent-filter").elements().filter { filter ->
                filter.getElementsByTagName("category").elements().any {
                    it.getAttribute("android:name") == LAUNCHER_CATEGORY
                }
            }

            if (launcherFilters.isEmpty()) {
                throw PatchException("Launcher intent-filter not found in $SPLASH_ACTIVITY")
            }

            launcherFilters.forEach { explorer.appendChild(splash.removeChild(it)) }
            explorer.setAttribute("android:exported", "true")
        }
    }
}
