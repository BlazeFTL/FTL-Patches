package app.ftl.patches.mxplayerad

import app.morphe.patcher.patch.bytecodePatch

val cleanMeTabPatch = bytecodePatch(
    name = "Clean Me Tab",
    description = "Removes promo rows and unused tiles from the Me tab. Optionally adds a " +
        "Network Stream tile.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_MX_PLAYER_AD)

    dependsOn(cleanMeTabLayoutsPatch, cleanMeTabTilesPatch, addNetworkStreamTilePatch)
}
