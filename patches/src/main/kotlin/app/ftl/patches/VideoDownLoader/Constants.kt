package app.ftl.patches.videodownloader

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

// Manifest android:name form (dotted, no L/;) - used for XML matching, not bytecode.
internal const val MAIN_TABS_ACTIVITY =
    "video.downloader.videodownloader.activity.MainTabsActivity"

internal const val BROWSER_DOWNLOADER_ACTIVITY =
    "video.downloader.videodownloader.five.activity.BrowserDownloaderActivity"

internal val COMPATIBILITY_VIDEO_DOWNLOADER = Compatibility(
    packageName = "video.downloader.videodownloader",
    name = "Video Downloader",
    targets = listOf(AppTarget(version = null))
)

// Unlock Pro's fingerprint/patch only holds on this exact build - restrict separately
// from the shared (version-agnostic) compatibility above.
internal val COMPATIBILITY_VIDEO_DOWNLOADER_UNLOCK_PRO = Compatibility(
    packageName = "video.downloader.videodownloader",
    name = "Video Downloader",
    targets = listOf(AppTarget(version = "2.7.2", versionCode = 172)),
)
