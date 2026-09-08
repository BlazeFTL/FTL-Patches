package app.ftl.patches.mxplayerad

import app.morphe.patcher.extensions.InstructionExtensions.replaceInstructions
import app.morphe.patcher.patch.booleanOption
import app.morphe.patcher.patch.bytecodePatch

// name = null - cleanMeTabPatch pulls this in via dependsOn as a configurable option.
internal val addNetworkStreamTilePatch = bytecodePatch(
    name = null,
    description = "Adds a Network Stream tile to the Me tab.",
) {
    compatibleWith(COMPATIBILITY_MX_PLAYER_AD)

    dependsOn(resolveNetworkStreamResourcesPatch)

    val addNetworkStream by booleanOption(
        key = "addNetworkStream",
        default = true,
        title = "Add Network Stream tile",
        description = "WARNING: MX Player has an integrity check, and some mods add their own on " +
            "top. Use a Play Store build, patch with signing off, then strip signature " +
            "verification (MT Manager Enhanced or a modded build) - or the app may refuse to start.",
    )

    execute {
        if (addNetworkStream != true) return@execute

        val videoPlaylistsIndex = LocalMeTilesFingerprint.stringMatches[2].index
        val method = LocalMeTilesFingerprint.method

        // Video Playlists tile: icon const, title const, name const-string - 2, 1, and
        // 0 instructions before its own name string. Same-size 1-for-1 swaps, so this
        // never shifts anything else this method's other tile edits rely on. Icon/title
        // ids come from resolveNetworkStreamResourcesPatch, not a hardcoded literal.
        method.replaceInstructions(videoPlaylistsIndex - 2, "const v2, ${"0x%08x".format(networkStreamIconId)}")
        method.replaceInstructions(videoPlaylistsIndex - 1, "const v3, ${"0x%08x".format(networkStreamTitleId)}")
        method.replaceInstructions(videoPlaylistsIndex, "const-string v4, \"Network Stream\"")
    }
}
