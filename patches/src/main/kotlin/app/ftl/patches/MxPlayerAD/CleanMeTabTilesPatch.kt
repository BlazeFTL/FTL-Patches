package app.ftl.patches.mxplayerad

import app.morphe.patcher.extensions.InstructionExtensions.removeInstructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstructions
import app.morphe.patcher.patch.bytecodePatch

// name = null keeps this out of the top-level patch list - cleanMeTabPatch pulls it
// in via dependsOn, so the user only sees one "Clean Me Tab" toggle.
internal val cleanMeTabTilesPatch = bytecodePatch(
    name = null,
    description = "Removes the Music Player and Cloud Drive tiles, and forces the " +
        "MX Share and Private Folder tiles off.",
) {
    compatibleWith(COMPATIBILITY_MX_PLAYER_AD)

    execute {
        val matches = LocalMeTilesFingerprint.stringMatches
        val mxShareIndex = matches[0].index
        val privateFolderIndex = matches[1].index
        val musicPlayerIndex = matches[4].index
        val cloudDriveIndex = matches[5].index
        val method = LocalMeTilesFingerprint.method

        // Each tile is: new-instance, icon const, title const, name const-string,
        // invoke-direct <init>, invoke-virtual add - 6 instructions starting 3 before
        // the row's name string. Removed from the bottom up so earlier indices stay valid.
        // Local Network is left untouched - it's a real, current tile, not clutter.
        method.removeInstructions(cloudDriveIndex - 3, 6)
        method.removeInstructions(musicPlayerIndex - 3, 6)

        // MX Share / Private Folder: each guarded by `invoke-static {}, LX;->y()Z` then
        // `move-result v1` 6 and 5 instructions before its name string. Force the
        // feature-flag result to false instead of calling the (obfuscated, renames
        // every build) flag class. Replaced 2-for-2 (padded with a nop) rather than
        // shrunk, so indices below the tile blocks above never shift - this method's
        // other tile edits (this patch and addNetworkStreamTilePatch) both read their
        // indices from the same cached fingerprint match and must stay valid no matter
        // which patch happens to run first.
        method.replaceInstructions(privateFolderIndex - 6, "const/4 v1, 0x0\nnop")
        method.replaceInstructions(mxShareIndex - 6, "const/4 v1, 0x0\nnop")
    }
}
