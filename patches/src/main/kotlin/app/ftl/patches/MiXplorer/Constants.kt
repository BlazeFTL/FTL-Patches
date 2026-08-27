package app.ftl.patches.mixplorer

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

internal val COMPATIBILITY_MIXPLORER = Compatibility(
    packageName = "com.mixplorer",
    name = "MiXplorer",
    // version = null -> any version supported.
    targets = listOf(AppTarget(version = null)),
)
