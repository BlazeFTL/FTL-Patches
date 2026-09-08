package app.ftl.patches.mxplayerad

import app.morphe.patcher.extensions.InstructionExtensions.removeInstructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

// name = null keeps this out of the top-level patch list - cleanMeTabPatch pulls it
// in via dependsOn, so the user only sees one "Clean Me Tab" toggle.
internal val cleanMeTabTilesPatch = bytecodePatch(
    name = null,
    description = "Removes the Music Player and Cloud Drive tiles, and forces the " +
        "MX Share and Private Folder tiles off.",
) {
    compatibleWith(COMPATIBILITY_MX_PLAYER_AD)

    // Resource edit (XML, not bytecode) - can't run inside this patch's own execute
    // block, so it rides along as a dependency instead. Enabling this patch now also
    // strips the same MX Share / Private Folder entry points from the per-file "more"
    // sheet and the multi-select toolbar.
    dependsOn(cleanMeTabMenusPatch)

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

// name = null - only reached via cleanMeTabTilesPatch's dependsOn above, never listed
// on its own.
internal val cleanMeTabMenusPatch = resourcePatch(
    name = null,
    description = "Removes Add to Playlist, File Transfer, and Private Folder from the " +
        "per-file \"more\" sheet, and drops Private Folder and File Transfer from the " +
        "multi-select toolbar overflow.",
) {
    compatibleWith(COMPATIBILITY_MX_PLAYER_AD)

    execute {
        // fragment_more_bottom_sheet_dialog.xml: static layout, each row a fixed-id
        // LinearLayout. Code elsewhere still findViewById()s these rows, so hide -
        // visibility=gone + 0dp - rather than remove from the tree.
        document("res/layout/fragment_more_bottom_sheet_dialog.xml").use { doc ->
            for (id in listOf("ll_add_to_playlist", "transfer_share", "option_private_folder")) {
                doc.byId(id).apply {
                    setAttribute("android:visibility", "gone")
                    setAttribute("android:layout_width", "0dp")
                    setAttribute("android:layout_height", "0dp")
                }
            }
        }

        // list_action_mode.xml: menu <item>s aren't pre-bound views, so they're
        // removed outright - matches the reference (items absent from Mod, not
        // android:visible="false").
        document("res/menu/list_action_mode.xml").use { doc ->
            for (id in listOf("option_private_folder", "mx_share")) {
                doc.byId(id).let { it.parentNode.removeChild(it) }
            }
        }
    }
}

// Document is parsed without namespace-awareness, so "android:id" is the literal
// attribute name, not a namespace-split one.
private fun org.w3c.dom.Document.byId(id: String): Element {
    val nodes = getElementsByTagName("*")
    for (i in 0 until nodes.length) {
        val element = nodes.item(i) as Element
        if (element.getAttribute("android:id") == "@id/$id") return element
    }
    throw NoSuchElementException("no element with id @id/$id")
}
