package app.ftl.patches.xplayer

import app.morphe.patcher.patch.resourcePatch

private const val PROMOTER = "res/layout/layout_in_app_promoter.xml"
private const val TRASH_DIALOG = "res/layout/dialog_trash_delete_confirm.xml"
private const val TILES_CLASS = "app.ftl.extension.xplayer.ModHideTilesLayout"

@Suppress("unused")
val hideHomeTilesPatch = resourcePatch(
    name = "Hide Home Tiles",
    description = "Hides the tile row at the top of the home page (All Videos, Downloader, Privacy, Cleaner...) and the Move to Trash option in the delete dialog. Toggle in Mod Settings.",
) {
    compatibleWith(XPLAYER_TARGET)

    dependsOn(modSettingsPatch)

    execute {
        document(PROMOTER).use { document ->
            document.replaceTagById(PROMOTER, "ll_btn_content", TILES_CLASS)
        }

        document(TRASH_DIALOG).use { document ->
            document.addGate(TRASH_DIALOG, ModKeys.HIDE_HOME_TILES, GateMode.UNCHECK, listOf("cb_move_to_trash"))
        }
    }
}
