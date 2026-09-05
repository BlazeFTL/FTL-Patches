package app.ftl.patches.mxplayerad

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

internal val COMPATIBILITY_MX_PLAYER_AD = Compatibility(
    packageName = "com.mxtech.videoplayer.ad",
    name = "MX Player",
    targets = listOf(
        AppTarget(version = "3.1.4", versionCode = 2001003524),
    ),
)

internal const val ACTIVITY_WELCOME_MX_CLASS = "Lcom/mxtech/videoplayer/ad/ActivityWelcomeMX;"
