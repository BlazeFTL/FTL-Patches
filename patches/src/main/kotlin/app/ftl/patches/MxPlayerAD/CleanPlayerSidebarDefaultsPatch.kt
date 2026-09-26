package app.ftl.patches.mxplayerad

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.InstructionLocation.MatchAfterWithin
import app.morphe.patcher.OpcodesFilter
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.BuilderOffsetInstruction
import com.android.tools.smali.dexlib2.builder.Label
import com.android.tools.smali.dexlib2.iface.instruction.Instruction

private class AnyInvokeVirtualFilter(location: InstructionLocation = InstructionLocation.MatchAfterAnywhere()) :
    OpcodesFilter(listOf(Opcode.INVOKE_VIRTUAL, Opcode.INVOKE_VIRTUAL_RANGE), location)

context(patchContext: BytecodePatchContext)
private fun Fingerprint.target(matchIndex: Int): Label {
    val index = instructionMatches[matchIndex].index
    return (method.implementation!!.instructions[index] as BuilderOffsetInstruction).target
}

// All 5 fingerprints below match inside the same method (MenuHelper.d(), class Lswb;), which
// builds the player sidebar's item list. Confirmed against the real smali (not assumed): that
// method reuses register v4 as its "item currently being built" register throughout, and always
// fully drains it (adds it to a list) before the next item's block begins - so v4 is dead, and
// safe scratch, immediately before every block's own first instruction, including all 5 below.

internal object BookmarkFingerprint : Fingerprint(
    filters = listOf(
        opcode(Opcode.IGET_BOOLEAN),
        opcode(Opcode.IF_EQZ, location = MatchAfterImmediately()),
        opcode(Opcode.NEW_INSTANCE, location = MatchAfterImmediately()),
        fieldAccess(name = "ic_menu_bookmark", opcode = Opcode.SGET, location = MatchAfterImmediately()),
    ),
)

internal object FavouriteFingerprint : Fingerprint(
    filters = listOf(
        opcode(Opcode.IGET_BOOLEAN),
        opcode(Opcode.IF_EQZ, location = MatchAfterImmediately()),
        opcode(Opcode.NEW_INSTANCE, location = MatchAfterImmediately()),
        fieldAccess(name = "ic_more_add_to_favourites", opcode = Opcode.SGET, location = MatchAfterImmediately()),
    ),
)

internal object AddToPlaylistFingerprint : Fingerprint(
    filters = listOf(
        opcode(Opcode.NEW_INSTANCE),
        fieldAccess(name = "ic_video_playlist_navigation", opcode = Opcode.SGET, location = MatchAfterImmediately()),
    ),
)

internal object TutorialFingerprint : Fingerprint(
    filters = listOf(
        opcode(Opcode.SGET_BOOLEAN),
        opcode(Opcode.IF_NEZ, location = MatchAfterImmediately()),
        opcode(Opcode.NEW_INSTANCE, location = MatchAfterImmediately()),
        fieldAccess(name = "icon_tutorial", opcode = Opcode.SGET, location = MatchAfterImmediately()),
    ),
)

internal object PlayingQueueFingerprint : Fingerprint(
    filters = listOf(
        opcode(Opcode.NEW_INSTANCE),
        fieldAccess(name = "ic_video_playlist_white", opcode = Opcode.SGET, location = MatchAfterImmediately()),
        opcode(Opcode.DIV_INT_2ADDR, location = MatchAfterWithin(20)),
        opcode(Opcode.IF_NEZ, location = MatchAfterImmediately()),
        AnyInvokeVirtualFilter(location = MatchAfterImmediately()),
        opcode(Opcode.GOTO, location = MatchAfterImmediately()),
    ),
)

// name = null - only reached via cleanSidebarShortcutsPatch's dependsOn below.
internal val cleanSidebarShortcutItemsPatch = bytecodePatch(
    name = null,
    description = "Adds Mod Settings switches for the Bookmark, Favourite, Add to Playlist, Tutorial, and " +
        "Playing Queue shortcuts in the player sidebar.",
) {
    compatibleWith(COMPATIBILITY_MX_PLAYER_AD)

    dependsOn(
        modSettingsPatch,
        modSettingFlagPatch(KEY_SIDEBAR_HIDE_BOOKMARK),
        modSettingFlagPatch(KEY_SIDEBAR_HIDE_FAVOURITE),
        modSettingFlagPatch(KEY_SIDEBAR_HIDE_ADD_TO_PLAYLIST),
        modSettingFlagPatch(KEY_SIDEBAR_HIDE_TUTORIAL),
        modSettingFlagPatch(KEY_SIDEBAR_HIDE_PLAYING_QUEUE),
    )

    execute {
        // Inserted right before each block's own first instruction: check the flag, and if it
        // says "hide", jump straight to hideTarget (the same place the block's own stock
        // condition would have jumped to skip it). If the flag says "keep", fall through into
        // the ORIGINAL, completely untouched instructions, which still run their own stock
        // check exactly as before.
        fun conditionalHide(fingerprint: Fingerprint, insertIndex: Int, key: String, hideTarget: Instruction) =
            fingerprint.method.addInstructionsWithLabels(
                insertIndex,
                """
                    const-string v4, "$key"
                    invoke-static {v4}, $MOD_SETTINGS_CLASS->get(Ljava/lang/String;)Z
                    move-result v4
                    if-nez v4, :hide
                """.trimIndent(),
                ExternalLabel("hide", hideTarget),
            )

        // Processed from the last block in the method to the first, so that inserting at one
        // position never shifts the not-yet-used index of a block still to come.

        conditionalHide(
            TutorialFingerprint,
            TutorialFingerprint.instructionMatches[0].index,
            KEY_SIDEBAR_HIDE_TUTORIAL,
            TutorialFingerprint.target(1).location.instruction!!,
        )

        // Favourite and Add to Playlist share one stock guard (Lswb;->g:Z) and sit back to
        // back with no branch between them - stock code that reaches Favourite always falls
        // through into Add to Playlist too. Add to Playlist's own check is inserted first, so
        // Favourite's "hide" jump can be re-pointed at THAT check's entry (not at the original,
        // now-shifted, instruction it used to point to) - otherwise hiding both at once would
        // skip Add to Playlist's own check and always show it.
        val atpInsertIndex = AddToPlaylistFingerprint.instructionMatches[0].index
        conditionalHide(
            AddToPlaylistFingerprint,
            atpInsertIndex,
            KEY_SIDEBAR_HIDE_ADD_TO_PLAYLIST,
            FavouriteFingerprint.target(1).location.instruction!!,
        )
        val atpCheckEntry = AddToPlaylistFingerprint.method.getInstruction(atpInsertIndex)
        conditionalHide(
            FavouriteFingerprint,
            FavouriteFingerprint.instructionMatches[0].index,
            KEY_SIDEBAR_HIDE_FAVOURITE,
            atpCheckEntry,
        )

        conditionalHide(
            BookmarkFingerprint,
            BookmarkFingerprint.instructionMatches[0].index,
            KEY_SIDEBAR_HIDE_BOOKMARK,
            BookmarkFingerprint.target(1).location.instruction!!,
        )

        conditionalHide(
            PlayingQueueFingerprint,
            PlayingQueueFingerprint.instructionMatches[0].index,
            KEY_SIDEBAR_HIDE_PLAYING_QUEUE,
            PlayingQueueFingerprint.target(5).location.instruction!!,
        )
    }
}

val cleanSidebarShortcutsPatch = bytecodePatch(
    name = "Sidebar & Player Defaults",
    description = "Cleans the player sidebar and More menu; sets default shortcuts and subtitle view. " +
        "Configurable in Mod Settings, except the default shortcuts bitmask, still a Morphe option " +
        "pending its Mod Settings move.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_MX_PLAYER_AD)
    dependsOn(
        hideVideoDisplayPatch,
        cleanSidebarMorePatch,
        cleanSidebarShortcutItemsPatch,
        defaultShortcutsPatch,
        openSubtitleSettingsByDefaultPatch,
    )

    enableDefaultShortcutsOption()
    defaultShortcutsMaskOption()
}
