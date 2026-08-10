package app.ftl.patches.snaptube

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

internal val COMPATIBILITY_SNAPTUBE = Compatibility(
    name = "SnapTube",
    packageName = "com.snaptube.premium",
    targets = listOf(
        AppTarget(version = "7.64.0.76450210", versionCode = 76450210),
    ),

)
