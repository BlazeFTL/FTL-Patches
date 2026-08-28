package app.ftl.patches.mxplayer

import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

private const val WELCOME_ACTIVITY_SUFFIX = ".ActivityWelcomeMX"
private const val MAIN_ACTIVITY_SUFFIX = ".ActivityMediaList"

val skipWelcomeScreenPatch = resourcePatch(
    name = "Skip Splash Screen",
    description = "Skips Splash Screen so the app boots straight past the splash and update screen.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_MX_PLAYER_PRO)

    execute {
        document("AndroidManifest.xml").use { document ->
            val activities = document.getElementsByTagName("activity")
            var welcome: Element? = null
            var main: Element? = null

            for (i in 0 until activities.length) {
                val activity = activities.item(i) as? Element ?: continue
                when {
                    activity.getAttribute("android:name").endsWith(WELCOME_ACTIVITY_SUFFIX) -> welcome = activity
                    activity.getAttribute("android:name").endsWith(MAIN_ACTIVITY_SUFFIX) -> main = activity
                }
            }

            val welcomeActivity = welcome ?: return@use
            val mainActivity = main ?: return@use

            val children = welcomeActivity.childNodes
            val movedChildren = buildList {
                for (i in 0 until children.length) add(children.item(i))
            }

            movedChildren.forEach { child ->
                welcomeActivity.removeChild(child)
                mainActivity.appendChild(child)
            }

            welcomeActivity.removeAttribute("android:theme")
            welcomeActivity.removeAttribute("android:exported")
        }
    }
}
