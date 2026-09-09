package app.ftl.patches.mxplayerad

import app.morphe.patcher.patch.bytecodePatch

val cleanMeTabPatch = bytecodePatch(
    name = "Clean Me Tab",
    description = "WARNING: MX Player has an integrity check, and some mods add their own on " +
        "top. Use a Play Store build, Use URV Manager, patch with signing off, then use " +
        "MT Manager Enhanced Signature Kill or the app may refuse to start." +
        "Removes promo rows and unused tiles from the Me tab. Optional Add Network Stream tile.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_MX_PLAYER_AD)

    dependsOn(cleanMeTabLayoutsPatch, cleanMeTabTilesPatch, addNetworkStreamTilePatch)

    addNetworkStreamOption()
}
