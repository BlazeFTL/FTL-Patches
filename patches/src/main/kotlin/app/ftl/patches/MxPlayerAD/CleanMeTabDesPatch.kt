package app.ftl.patches.mxplayerad

import app.morphe.patcher.patch.bytecodePatch

val cleanMeTabPatch = bytecodePatch(
    name = "Clean Me Tab",
    description = "Adds Mod Settings switches, on by default, that hide promo rows, unused tiles, and " +
        "Private Folder / File Transfer / Add to Playlist entries.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_MX_PLAYER_AD)

    dependsOn(cleanMeTabLayoutsPatch, cleanMeTabTilesPatch)
}
