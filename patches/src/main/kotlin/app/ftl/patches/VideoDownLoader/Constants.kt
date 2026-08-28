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

// Unlock Pro only - IsPurchaseValidFingerprint was built against 2.7.2 (172) smali;
// other versions aren't verified so this patch is pinned instead of using the
// any-version compat above.
internal val COMPATIBILITY_VIDEO_DOWNLOADER_2_7_2 = Compatibility(
    packageName = "video.downloader.videodownloader",
    name = "Video Downloader",
    targets = listOf(AppTarget(version = "2.7.2", versionCode = 172))
)
