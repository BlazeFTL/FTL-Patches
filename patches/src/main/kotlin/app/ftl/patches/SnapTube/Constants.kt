package app.ftl.patches.snaptube

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

// Manifest android:name form (dotted, no L/;) - used for XML matching, not bytecode.
internal const val LINK_HANDLE_ACTIVITY_ALIAS =
    "com.snaptube.linkhook.LinkHandleActivity"

// Unique action string on the unscoped http/https intent-filter of the alias above.
internal const val OPEN_WEBVIEW_ACTION =
    "snaptube.intent.action.OPEN_WEBVIEW"

internal val COMPATIBILITY_SNAPTUBE = Compatibility(
    name = "SnapTube",
    packageName = "com.snaptube.premium",
    targets = listOf(
        AppTarget(version = "7.64.0.76450210", versionCode = 76450210),
    ),

)
