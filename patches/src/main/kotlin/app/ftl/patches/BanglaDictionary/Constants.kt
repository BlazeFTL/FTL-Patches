package app.ftl.patches.bangladictionary

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

internal val COMPATIBILITY_BANGLA_DICTIONARY = Compatibility(
    packageName = "com.hdictionary.bn",
    name = "Bangla Dictionary",
    targets = listOf(
        AppTarget(version = "11.5.9", versionCode = 1159),
    ),
)
