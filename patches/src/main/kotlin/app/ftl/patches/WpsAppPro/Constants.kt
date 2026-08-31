package app.ftl.patches.wpsapppro

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

internal val COMPATIBILITY_WPS_APP_PRO = Compatibility(
    packageName = "com.themausoft.wpsapppro",
    name = "WPSApp Pro",
    targets = listOf(
        // versionCode given directly (118) - the MT2 diff's info.json carried
        // versionCode 24011893, a stale/reused placeholder value the compare
        // tool already left behind on unrelated apps elsewhere in this repo.
        AppTarget(version = "1.7.05", versionCode = 118),
    ),
)
