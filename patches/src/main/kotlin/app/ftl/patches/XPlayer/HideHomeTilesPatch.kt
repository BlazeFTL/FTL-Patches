package app.ftl.patches.xplayer

import app.morphe.patcher.patch.resourcePatch

private const val PROMOTER = "res/layout/layout_in_app_promoter.xml"

@Suppress("unused")
val hideHomeTilesPatch = resourcePatch(
    name = "Hide Home Tiles",
    description = "Hides the tile row at the top of the home page (All Videos, Downloader, Privacy, Cleaner...). Toggle in Mod Settings.",
) {
    compatibleWith(XPLAYER_TARGET)

    dependsOn(modSettingsPatch)

    execute {
        document(PROMOTER).use { document ->
            document.addSelfGate(PROMOTER, ModKeys.HIDE_HOME_TILES, "ll_btn_content")
        }
    }
}
