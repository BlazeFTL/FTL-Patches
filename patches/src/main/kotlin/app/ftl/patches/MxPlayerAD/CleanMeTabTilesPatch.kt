package app.ftl.patches.mxplayerad

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import org.w3c.dom.Element

// name = null - only reached via cleanMeTabTilesPatch's dependsOn below.
internal val cleanMeTabMenusPatch = resourcePatch(
    name = null,
    description = "Adds Mod Settings switches for Add to Playlist, File Transfer, and Private Folder in the " +
        "per-file \"more\" sheet.",
) {
    compatibleWith(COMPATIBILITY_MX_PLAYER_AD)

    dependsOn(
        modSettingsPatch,
        modSettingFlagPatch(KEY_HIDE_ADD_TO_PLAYLIST),
        modSettingFlagPatch(KEY_HIDE_FILE_TRANSFER),
        modSettingFlagPatch(KEY_HIDE_PRIVATE_FOLDER),
    )

    execute {
        document("res/layout/fragment_more_bottom_sheet_dialog.xml").use { doc ->
            doc.byId("ll_add_to_playlist")
                .addModViewHider(KEY_HIDE_ADD_TO_PLAYLIST, "ll_add_to_playlist", "both")
            doc.byId("transfer_share")
                .addModViewHider(KEY_HIDE_FILE_TRANSFER, "transfer_share", "both")
            doc.byId("option_private_folder")
                .addModViewHider(KEY_HIDE_PRIVATE_FOLDER, "option_private_folder", "both")
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

// MediaListFragment.G3(Ljg;Landroid/view/Menu;)Z inflates list_action_mode.xml, then
// conditionally strips items - same idea as cleanMeTabMenusPatch, but "Add to Playlist"
// isn't declared in that XML, and the split action-mode toolbar (G0 = its Add to
// Playlist button) is a *second* view inflated from a different layout, built entirely
// in this method. Both need a bytecode edit instead of a resource one.
internal val MediaListActionModeFingerprint = Fingerprint(
    definingClass = "Lcom/mxtech/videoplayer/list/MediaListFragment;",
    returnType = "Z",
    // First param (Ljg;) is obfuscated, second is the real Menu passed to inflate.
    parameters = listOf("L", "Landroid/view/Menu;"),
    filters = listOf(
        // Edit 1 lands right after this - the only menu inflate call in the method.
        methodCall(smali = "Landroid/view/MenuInflater;->inflate(ILandroid/view/Menu;)V"),
        // Existing "Add to Playlist" id lookup (still used below to bind the G0
        // button). Reused for edit 1 instead of hardcoding the obfuscated R$id class
        // it lives in - that class renames every build, the field name doesn't.
        fieldAccess(name = "add_2_playlist", type = "I", opcode = Opcode.SGET),
        // The View field assigned right after is G0 - name looks minifier-generated
        // (A0..G0 run sequentially in this method), so it's matched by shape (type +
        // opcode, right after the id lookup above), never by name, and its reference
        // is reused the same way for edit 2.
        fieldAccess(
            type = "Landroid/view/View;",
            opcode = Opcode.IPUT_OBJECT,
            location = InstructionLocation.MatchAfterWithin(4),
        ),
        // Unique, unobfuscated end-of-method anchor - sits immediately after the block
        // edit 2 wraps.
        methodCall(smali = "Landroid/view/ViewGroup;->addView(Landroid/view/View;)V"),
    ),
)

// name = null - only reached via cleanMeTabTilesPatch's dependsOn below.
internal val cleanMeTabActionModeMenuPatch = bytecodePatch(
    name = null,
    description = "Adds Mod Settings switches for Add to Playlist, File Transfer, and Private Folder in the " +
        "multi-select overflow menu and the split action-mode toolbar.",
) {
    compatibleWith(COMPATIBILITY_MX_PLAYER_AD)

    dependsOn(
        modSettingsPatch,
        resolveMeTabMenuIdsPatch,
        modSettingFlagPatch(KEY_HIDE_ADD_TO_PLAYLIST),
        modSettingFlagPatch(KEY_HIDE_FILE_TRANSFER),
        modSettingFlagPatch(KEY_HIDE_PRIVATE_FOLDER),
    )

    execute {
        val matches = MediaListActionModeFingerprint.instructionMatches
        val method = MediaListActionModeFingerprint.method

        val inflateIndex = matches[0].index
        val addToPlaylistIdRef = (matches[1].instruction as ReferenceInstruction).reference as FieldReference
        val g0Instruction = matches[2].instruction
        val g0FieldRef = (g0Instruction as ReferenceInstruction).reference as FieldReference
        val thisRegister = (g0Instruction as TwoRegisterInstruction).registerB
        val addViewIndex = matches[3].index
        // Parameter registers are always the method's last N slots; this method has
        // 3 (this, p1, p2), all single-width, so p1 = registerCount - 2. p1 is dead
        // right after inflate() - the next original instruction overwrites it.
        val p1Register = method.implementation!!.registerCount - 2

        val addToPlaylistId =
            "${addToPlaylistIdRef.definingClass}->${addToPlaylistIdRef.name}:${addToPlaylistIdRef.type}"
        val g0Field = "${g0FieldRef.definingClass}->${g0FieldRef.name}:${g0FieldRef.type}"

        val stockToolbarBlock = method.getInstruction(addViewIndex - 6)
        val cond14d = method.getInstruction(addViewIndex)
        val afterInflate = method.getInstruction(inflateIndex + 1)

        // Edit 2 first (higher index) so edit 1's insertion below doesn't shift it.
        // The six stock instructions stay in place and run when the switch is off.
        method.addInstructionsWithLabels(
            addViewIndex - 6,
            """
                const-string v1, "$KEY_HIDE_ADD_TO_PLAYLIST"
                invoke-static {v1}, $MOD_SETTINGS_CLASS->get(Ljava/lang/String;)Z
                move-result v1
                if-eqz v1, :stock_block
                iget-object v0, v$thisRegister, $g0Field
                if-eqz v0, :cond_14d
                const/16 v1, 0x8
                invoke-virtual {v0, v1}, Landroid/view/View;->setVisibility(I)V
                goto :cond_14d
            """.trimIndent(),
            ExternalLabel("stock_block", stockToolbarBlock),
            ExternalLabel("cond_14d", cond14d),
        )

        method.addInstructionsWithLabels(
            inflateIndex + 1,
            """
                const-string v$p1Register, "$KEY_HIDE_ADD_TO_PLAYLIST"
                invoke-static {v$p1Register}, $MOD_SETTINGS_CLASS->get(Ljava/lang/String;)Z
                move-result v$p1Register
                if-eqz v$p1Register, :skip_add_to_playlist
                sget v$p1Register, $addToPlaylistId
                invoke-interface {p2, v$p1Register}, Landroid/view/Menu;->removeItem(I)V
                :skip_add_to_playlist
                const-string v$p1Register, "$KEY_HIDE_PRIVATE_FOLDER"
                invoke-static {v$p1Register}, $MOD_SETTINGS_CLASS->get(Ljava/lang/String;)Z
                move-result v$p1Register
                if-eqz v$p1Register, :skip_private_folder
                const v$p1Register, ${"0x%08x".format(privateFolderMenuId)}
                invoke-interface {p2, v$p1Register}, Landroid/view/Menu;->removeItem(I)V
                :skip_private_folder
                const-string v$p1Register, "$KEY_HIDE_FILE_TRANSFER"
                invoke-static {v$p1Register}, $MOD_SETTINGS_CLASS->get(Ljava/lang/String;)Z
                move-result v$p1Register
                if-eqz v$p1Register, :next
                const v$p1Register, ${"0x%08x".format(mxShareMenuId)}
                invoke-interface {p2, v$p1Register}, Landroid/view/Menu;->removeItem(I)V
            """.trimIndent(),
            ExternalLabel("next", afterInflate),
        )
    }
}

// name = null keeps this out of the top-level patch list - cleanMeTabPatch pulls it
// in via dependsOn, so the user only sees one "Clean Me Tab" toggle.
internal val cleanMeTabTilesPatch = bytecodePatch(
    name = null,
    description = "Adds Mod Settings switches for the Music Player, Cloud Drive, File Transfer and Private " +
        "Folder tiles, and for swapping Video Playlists for Network Stream.",
) {
    compatibleWith(COMPATIBILITY_MX_PLAYER_AD)

    dependsOn(
        cleanMeTabMenusPatch,
        cleanMeTabActionModeMenuPatch,
        resolveNetworkStreamResourcesPatch,
        modSettingsPatch,
        modSettingFlagPatch(KEY_ME_HIDE_MUSIC_PLAYER),
        modSettingFlagPatch(KEY_ME_HIDE_CLOUD_DRIVE),
        modSettingFlagPatch(KEY_ME_SHOW_NETWORK_STREAM),
        modSettingFlagPatch(KEY_HIDE_FILE_TRANSFER),
        modSettingFlagPatch(KEY_HIDE_PRIVATE_FOLDER),
    )

    execute {
        val matches = LocalMeTilesFingerprint.stringMatches
        val mxShareIndex = matches[0].index
        val privateFolderIndex = matches[1].index
        val videoPlaylistsIndex = matches[2].index
        val musicPlayerIndex = matches[4].index
        val cloudDriveIndex = matches[5].index
        val method = LocalMeTilesFingerprint.method

        val cloudNext = method.getInstruction(cloudDriveIndex + 3)
        val musicNext = method.getInstruction(musicPlayerIndex + 3)
        val videoStock = method.getInstruction(videoPlaylistsIndex - 2)
        val videoNext = method.getInstruction(videoPlaylistsIndex + 1)
        val privateNext = method.getInstruction(privateFolderIndex - 4)
        val shareNext = method.getInstruction(mxShareIndex - 4)

        fun skipTile(index: Int, key: String, next: Instruction) =
            method.addInstructionsWithLabels(
                index - 2,
                """
                    const-string v2, "$key"
                    invoke-static {v2}, $MOD_SETTINGS_CLASS->get(Ljava/lang/String;)Z
                    move-result v2
                    if-nez v2, :next
                """.trimIndent(),
                ExternalLabel("next", next),
            )

        fun forceFlagOffAfterCompute(index: Int, key: String, consumer: Instruction) =
            method.addInstructionsWithLabels(
                index - 4,
                """
                    const-string v2, "$key"
                    invoke-static {v2}, $MOD_SETTINGS_CLASS->get(Ljava/lang/String;)Z
                    move-result v2
                    if-eqz v2, :keep
                    const/4 v1, 0x0
                """.trimIndent(),
                ExternalLabel("keep", consumer),
            )

        // Highest index first so the lower indices computed above stay valid.
        val edits = listOf(
            cloudDriveIndex to { skipTile(cloudDriveIndex, KEY_ME_HIDE_CLOUD_DRIVE, cloudNext) },
            musicPlayerIndex to { skipTile(musicPlayerIndex, KEY_ME_HIDE_MUSIC_PLAYER, musicNext) },
            videoPlaylistsIndex to {
                method.addInstructionsWithLabels(
                    videoPlaylistsIndex - 2,
                    """
                        const-string v2, "$KEY_ME_SHOW_NETWORK_STREAM"
                        invoke-static {v2}, $MOD_SETTINGS_CLASS->get(Ljava/lang/String;)Z
                        move-result v2
                        if-eqz v2, :stock
                        const v2, ${"0x%08x".format(networkStreamIconId)}
                        const v3, ${"0x%08x".format(networkStreamTitleId)}
                        const-string v4, "Network Stream"
                        goto :next
                    """.trimIndent(),
                    ExternalLabel("stock", videoStock),
                    ExternalLabel("next", videoNext),
                )
            },
            privateFolderIndex to { forceFlagOffAfterCompute(privateFolderIndex, KEY_HIDE_PRIVATE_FOLDER, privateNext) },
            mxShareIndex to { forceFlagOffAfterCompute(mxShareIndex, KEY_HIDE_FILE_TRANSFER, shareNext) },
        )

        edits.sortedByDescending { it.first }.forEach { it.second() }

        method.addInstructions(
            0,
            """
                const-string v0, "${method.name}"
                invoke-static {p0, v0}, $MOD_SETTINGS_CLASS->onTilesOwner(Ljava/lang/Object;Ljava/lang/String;)V
            """.trimIndent(),
        )
    }
}
