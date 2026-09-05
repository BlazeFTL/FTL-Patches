package app.ftl.patches.mxplayerad

import app.morphe.patcher.extensions.InstructionExtensions.replaceInstructions
import app.morphe.patcher.patch.bytecodePatch

val addNetworkStreamTilePatch = bytecodePatch(
    name = "Add Network Stream To Me Tab",
    description = "WARNING MX PLAYER HAS INTEGRITY CHECK. Mod By Youarefinished Also Has Their Own Integrity Check. So Download From Play Store Use URV Manager With Signing Turned Off, Patch And Then Kill Signature Verification With MT Manager Enhanced (VIP Only).",
    default = false,
) {
    compatibleWith(COMPATIBILITY_MX_PLAYER_AD)

    execute {
        val videoPlaylistsIndex = LocalMeTilesFingerprint.stringMatches[2].index
        val method = LocalMeTilesFingerprint.method

        // Video Playlists tile: icon const, title const, name const-string - 2, 1, and
        // 0 instructions before its own name string. Same-size 1-for-1 swaps, so this
        // never shifts anything else this method's other tile edits rely on.
        method.replaceInstructions(videoPlaylistsIndex - 2, "const v2, 0x7f08161f")
        method.replaceInstructions(videoPlaylistsIndex - 1, "const v3, 0x7f1219f3")
        method.replaceInstructions(videoPlaylistsIndex, "const-string v4, \"Network Stream\"")
    }
}
