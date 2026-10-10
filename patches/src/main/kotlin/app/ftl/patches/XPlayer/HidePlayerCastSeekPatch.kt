package app.ftl.patches.xplayer

import app.morphe.patcher.patch.resourcePatch

private const val TOPBAR = "res/layout/simple_player_topbar.xml"
private const val CONTROLBAR = "res/layout/simple_player_controlbar.xml"

@Suppress("unused")
val hidePlayerCastSeekPatch = resourcePatch(
    name = "Hide Cast FF FB In Player",
    description = "Hides the cast, custom and 10 second forward/backward buttons in the player. Toggle in Mod Settings.",
) {
    compatibleWith(XPLAYER_COMPATIBILITY)

    dependsOn(modSettingsPatch)

    execute {
        document(TOPBAR).use { document ->
            document.addGate(TOPBAR, ModKeys.HIDE_PLAYER_BUTTONS, GateMode.ZERO, listOf("iv_cast", "iv_custom1"))
        }

        document(CONTROLBAR).use { document ->
            document.addGate(CONTROLBAR, ModKeys.HIDE_PLAYER_BUTTONS, GateMode.ZERO, listOf("video_fb10", "video_ff10"))
        }
    }
}
