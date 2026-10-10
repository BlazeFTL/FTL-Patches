package app.ftl.patches.xplayer

import app.morphe.patcher.patch.resourcePatch

private const val RECENT = "res/layout/item_recent_video_list.xml"

@Suppress("unused")
val hideRecentVideosPatch = resourcePatch(
    name = "Hide Recent Videos",
    description = "Hides the recent videos row on the home page. Toggle in Mod Settings.",
) {
    compatibleWith(XPLAYER_TARGET)

    dependsOn(modSettingsPatch)

    execute {
        document(RECENT).use { document ->
            document.addSelfGate(RECENT, ModKeys.HIDE_RECENT)
        }
    }
}
