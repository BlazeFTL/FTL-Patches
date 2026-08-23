package app.ftl.patches.alldownloader

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

// SplashActivity, LanguageActivity and MainActivity are the app's own real, dotted
// package names (not obfuscated - only the app's internal fields/methods and its
// helper classes are). Safe to pin per the fingerprinting rule that allows real
// declared class names as anchors.
internal const val SPLASH_ACTIVITY_CLASS =
    "Lvideoplayer/videodownloader/downloader/twelve/activity/SplashActivity;"

internal const val MAIN_ACTIVITY_CLASS =
    "Lvideoplayer/videodownloader/downloader/activity/MainActivity;"

internal const val LANGUAGE_ACTIVITY_CLASS =
    "Lvideoplayer/videodownloader/downloader/activity/LanguageActivity;"

// Manifest android:name form (dotted, no L/; ) - used for XML matching, not bytecode.
internal const val WEB_DOWNLOAD_ACTIVITY =
    "videoplayer.videodownloader.downloader.old.activity.WebDownloadActivity"

internal val COMPATIBILITY_ALL_VIDEO_DOWNLOADER = Compatibility(
    packageName = "videoplayer.videodownloader.downloader",
    name = "All Video Downloader & Ace Player",
    targets = listOf(
        // versionCode taken from the supplied MT2 diff (info.json), not the shorter
        // "97" shown in-app - apps often show a truncated build label separate from
        // the real manifest versionCode.
        AppTarget(version = "1.9.7", versionCode = 24011893),
    ),
)
