package app.ftl.patches.xplayer

import app.morphe.patcher.patch.resourcePatch

private const val ACTIVITY_APP = "res/layout/activity_app.xml"

@Suppress("unused")
val hideBottomBarPatch = resourcePatch(
    name = "Hide Bottom Bar",
    description = "Hides the bottom tab bar on the home screen. Toggle in Mod Settings.",
) {
    compatibleWith(XPLAYER_COMPATIBILITY)

    dependsOn(modSettingsPatch)

    execute {
        document(ACTIVITY_APP).use { document ->
            document.addGate(ACTIVITY_APP, ModKeys.HIDE_BOTTOM_BAR, GateMode.ZERO, listOf("bottom_tab"))
        }
    }
}
