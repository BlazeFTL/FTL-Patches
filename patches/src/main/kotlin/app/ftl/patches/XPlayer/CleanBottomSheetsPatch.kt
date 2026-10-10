package app.ftl.patches.xplayer

import app.morphe.patcher.patch.resourcePatch

private val BOTTOM_SHEETS = listOf(
    "res/layout/bottom_sheet_video.xml",
    "res/layout/bottom_sheet_folder.xml"
)

@Suppress("unused")
val cleanBottomSheetsPatch = resourcePatch(
    name = "Clean 3 Dot Menu",
    description = "Removes Lock and Add to playlist from the video and folder 3-dot bottom sheets. Toggle in Mod Settings.",
) {
    compatibleWith(XPLAYER_COMPATIBILITY)

    dependsOn(modSettingsPatch)

    execute {
        BOTTOM_SHEETS.forEach { path ->
            document(path).use { document ->
                document.addGate(path, ModKeys.CLEAN_SHEETS, GateMode.TEXT, listOf("lock", "add_to_playlist"))
            }
        }
    }
}
