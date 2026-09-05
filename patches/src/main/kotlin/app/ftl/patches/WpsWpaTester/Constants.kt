package app.ftl.patches.wpswpatester

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

// App's own real, declared Application subclass (required by the manifest to
// resolve/instantiate it, so it's kept unobfuscated by the class shrinker).
// Only its members (fields/methods) are minified - safe to pin per the
// fingerprinting rule that allows the app's own declared class names.
internal const val APP_CLASS = "Las/wps/wpatester/ui/base/App;"

internal val COMPATIBILITY_WPS_WPA_TESTER = Compatibility(
    packageName = "com.tester.wpswpatester",
    name = "WIFI WPS WPA TESTER",
    targets = listOf(
        // versionCode taken from the supplied MT2 diff (info.json), not the
        // shorter "1883" build label shown in-app - apps often show a
        // truncated label separate from the real manifest versionCode.
        AppTarget(version = "rc-5.164", versionCode = 1883),
    ),
)
