package app.ftl.patches.xfolder

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

internal const val SPLASH_ACTIVITY_CLASS = "filemanger.manager.iostudio.manager.SplashActivity"
internal const val MAIN_ACTIVITY_CLASS = "filemanger.manager.iostudio.manager.MainActivity"

internal val COMPATIBILITY_XFOLDER = Compatibility(
    packageName = "files.fileexplorer.filemanager",
    name = "File Manager - XFolder",
    targets = listOf(
        // Version/versionCode as given directly (1.5.5.7 / 1557000) - not the
        // versionCode inside the compare zip's info.json (24011893), which is
        // an unrelated bundle/split version code, per instruction to ignore it.
        AppTarget(version = "1.5.5.7", versionCode = 1557000),
    ),
)
