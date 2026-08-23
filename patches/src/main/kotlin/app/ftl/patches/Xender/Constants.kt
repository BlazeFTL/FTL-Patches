package app.ftl.patches.xender

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

internal val COMPATIBILITY_XENDER = Compatibility(
    packageName = "cn.xender",
    name = "Xender",
    targets = listOf(
        AppTarget(version = "18.8.0.prime"),
    ),

)
