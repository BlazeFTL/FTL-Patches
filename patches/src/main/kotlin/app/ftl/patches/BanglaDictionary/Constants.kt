package app.ftl.patches.bangladictionary

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

internal val COMPATIBILITY_BANGLA_DICTIONARY = Compatibility(
    packageName = "com.hdictionary.bn",
    name = "Bangla Dictionary",
    targets = listOf(
        // versionCode taken from the supplied MT2 diff (info.json), not the
        // shorter "1151" shown in-app/Play Store - apps often show a truncated
        // build label separate from the real manifest versionCode.
        AppTarget(version = "11.5.1", versionCode = 1151),
    ),
)
