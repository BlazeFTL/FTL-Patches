package app.ftl.patches.wifianalyzer

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

// MainActivity is the app's own real, declared Activity class name (not
// obfuscated - only its internal fields/methods and the app's helper classes
// are). Safe to pin per the fingerprinting rule that allows real declared
// class names as anchors.
internal const val MAIN_ACTIVITY_CLASS = "Labdelrahman/wifianalyzerpro/MainActivity;"

internal val COMPATIBILITY_WIFI_ANALYZER_PRO = Compatibility(
    packageName = "abdelrahman.wifianalyzerpro",
    name = "WiFi Analyzer",
    targets = listOf(
        // versionCode taken from the supplied MT2 diff (info.json), not the
        // shorter "149" shown in-app/Play Store - apps often show a truncated
        // build label separate from the real manifest versionCode.
        AppTarget(version = "9.4", versionCode = 149),
    ),
)
