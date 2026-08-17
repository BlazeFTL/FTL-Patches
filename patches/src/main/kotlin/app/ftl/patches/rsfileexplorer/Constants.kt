package app.ftl.patches.rsfileexplorer

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

internal val COMPATIBILITY_RS_FILE_EXPLORER = Compatibility(
    packageName = "com.rs.explorer.filemanager",
    name = "RS File Manager",
    targets = listOf(
        AppTarget(version = "2.3.0.4", versionCode = 239),
    ),
)
