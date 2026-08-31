package app.ftl.patches.xfolder

import app.ftl.util.returnEarly
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

/**
 * Renames the launcher activity's android:name from SplashActivity to
 * MainActivity, so the app launches straight into MainActivity and the splash
 * screen never shows. No `name`, so it isn't independently toggleable - it
 * only runs as a dependency of unlockProAndSkipSplashPatch.
 */
internal val renameSplashToMainPatch = resourcePatch(
    description = "Renames the SplashActivity manifest entry to MainActivity to skip the splash screen.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_XFOLDER)

    execute {
        document("AndroidManifest.xml").use { document ->
            val activities = document.getElementsByTagName("activity")
            for (i in 0 until activities.length) {
                val activity = activities.item(i) as? Element ?: continue
                if (activity.getAttribute("android:name") == SPLASH_ACTIVITY_CLASS) {
                    activity.setAttribute("android:name", MAIN_ACTIVITY_CLASS)
                    break
                }
            }
        }
    }
}

@Suppress("unused")
val unlockProAndSkipSplashPatch = bytecodePatch(
    name = "Unlock Pro & Skip Splash Screen",
    description = "Forces the ad-removed/Pro check to always return true, and renames the launcher " +
        "activity from SplashActivity to MainActivity so the splash screen is skipped.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_XFOLDER)
    dependsOn(renameSplashToMainPatch)

    execute {
        // b()Z gates ad-removed/Pro state - forcing it true unlocks Pro everywhere.
        IsAdRemovedFingerprint.method.returnEarly(true)
    }
}
