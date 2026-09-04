package app.ftl.patches.mxplayerad

import app.morphe.patcher.patch.bytecodePatch

val cleanMeTabPatch = bytecodePatch(
    name = "Clean Me Tab",
    description = "Removes promo rows and unused tiles from the Me tab: WhatsApp Status " +
        "Saver, Legal/Help, the local-tiles pager, Local Network, Music Player, Cloud " +
        "Drive, MX Share, and Private Folder.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_MX_PLAYER_AD)

    dependsOn(cleanMeTabLayoutsPatch, cleanMeTabTilesPatch)
}
