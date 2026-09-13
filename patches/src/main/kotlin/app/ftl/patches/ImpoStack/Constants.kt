package app.ftl.patches.impostack

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

internal val COMPATIBILITY_IMPOSTACK = Compatibility(
    packageName = "com.bytedz.printflow",
    name = "ImpoStack",
    targets = listOf(
        // versionName / versionCode as given directly (1.4.0 / 10).
        AppTarget(version = "1.4.0", versionCode = 10),
    ),
)
