package app.ftl.patches.mixplorer

import app.morphe.patcher.patch.Compatibility

internal val COMPATIBILITY_MIXPLORER = Compatibility(
    packageName = "com.mixplorer",
    name = "MiXplorer",
    // No app targets specified -> compatible with any version.
    targets = listOf(),
)
