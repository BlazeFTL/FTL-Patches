package app.ftl.patches.mxplayerad

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.InstructionLocation.MatchAfterWithin
import app.morphe.patcher.OpcodesFilter
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.booleanOption
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.BuilderOffsetInstruction
import com.android.tools.smali.dexlib2.builder.Label
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction20t

// invoke-virtual vs. invoke-virtual/range is a register-pressure-driven
// compiler choice, not a semantic one - see the ActivityScreen fingerprint
// fix earlier in this package for the full explanation. Matching both forms
// avoids the same class of spurious failure here.
private class AnyInvokeVirtualFilter(location: InstructionLocation = InstructionLocation.MatchAfterAnywhere()) :
    OpcodesFilter(listOf(Opcode.INVOKE_VIRTUAL, Opcode.INVOKE_VIRTUAL_RANGE), location)

// All five items below live in the same class's menu-building method. That
// class/method pair is fully obfuscated and will rename every build, so
// nothing about it is pinned - each fingerprint anchors purely on the real,
// never-obfuscated resource entry names (icon/string R-fields) sitting right
// next to each item's own construction code.
//
// Every edit is a single in-place instruction swap (replaceInstruction), not
// a remove+add - swapping a branch/label-bearing slot's content in place
// keeps whatever label targets it, unlike remove+add which re-homes the
// label elsewhere (the exact bug that broke the ql patch earlier). New gotos
// reuse an existing branch's already-resolved target where possible, so nothing
// here needs to fabricate label wiring by hand.

context(patchContext: BytecodePatchContext)
private fun Fingerprint.target(matchIndex: Int): Label {
    // instructionMatches[].instruction is captured at match time, before the
    // method has been converted to its mutable Builder* representation - it's
    // still the original immutable DexBackedInstruction there. Re-read the
    // same index through implementation!!.instructions, which forces (and
    // returns) the mutable Builder* form that actually has a resolvable target.
    val index = instructionMatches[matchIndex].index
    return (method.implementation!!.instructions[index] as BuilderOffsetInstruction).target
}

// Bookmark: `iget-boolean v4, Lbrc;->d:Z` / `if-eqz v4, :cond_e`, right before
// this item's own construction.
internal object BookmarkFingerprint : Fingerprint(
    filters = listOf(
        opcode(Opcode.IGET_BOOLEAN),
        opcode(Opcode.IF_EQZ, location = MatchAfterImmediately()),
        opcode(Opcode.NEW_INSTANCE, location = MatchAfterImmediately()),
        fieldAccess(name = "ic_menu_bookmark", opcode = Opcode.SGET, location = MatchAfterImmediately()),
    ),
)

// Favourite: `iget-boolean v4, Lbrc;->g:Z` / `if-eqz v4, :cond_12`. In stock,
// :cond_12's target also happens to sit right after Add to Playlist's own
// block (the two are unconditionally back to back), which is why hiding
// Favourite via its own unmodified target would take Add to Playlist with
// it. To keep them independent, Favourite's own edit instead points at
// wherever Add to Playlist's block starts (see below), narrowing its skip to
// just itself.
internal object FavouriteFingerprint : Fingerprint(
    filters = listOf(
        opcode(Opcode.IGET_BOOLEAN),
        opcode(Opcode.IF_EQZ, location = MatchAfterImmediately()),
        opcode(Opcode.NEW_INSTANCE, location = MatchAfterImmediately()),
        fieldAccess(name = "ic_more_add_to_favourites", opcode = Opcode.SGET, location = MatchAfterImmediately()),
    ),
)

// Add to Playlist: unconditional in stock - no existing gate to widen, so
// hiding it means inserting a brand new jump at its own construction's start.
// That start is plain fallthrough (nothing branches directly into it), so
// swapping its first instruction carries none of the label risk above.
internal object AddToPlaylistFingerprint : Fingerprint(
    filters = listOf(
        opcode(Opcode.NEW_INSTANCE),
        fieldAccess(name = "ic_video_playlist_navigation", opcode = Opcode.SGET, location = MatchAfterImmediately()),
    ),
)

// Tutorial: `sget-boolean v4, Ljb5;->g:Z` / `if-nez v4, :cond_15`. The same
// Ljb5;->g:Z flag also gates two unrelated items (Chapter, Cut) elsewhere in
// this method, so the icon-name anchor is what keeps this fingerprint on
// only the Tutorial occurrence.
internal object TutorialFingerprint : Fingerprint(
    filters = listOf(
        opcode(Opcode.SGET_BOOLEAN),
        opcode(Opcode.IF_NEZ, location = MatchAfterImmediately()),
        opcode(Opcode.NEW_INSTANCE, location = MatchAfterImmediately()),
        fieldAccess(name = "icon_tutorial", opcode = Opcode.SGET, location = MatchAfterImmediately()),
    ),
)

// Playing Queue: unconditional, driven by a reorder-position value rather
// than a boolean flag, so like Add to Playlist there's no existing gate to
// widen. Its block-start instruction IS a real branch target though (reached
// both by fallthrough and by an explicit goto from the previous item), so it
// needs the in-place swap treatment. The new goto reuses the target of this
// item's own second-placement "goto" a few instructions later, which already
// points exactly at the next item's (Aspect Ratio's) start.
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

val cleanSidebarShortcutsPatch = bytecodePatch(
    name = "Clean sidebar shortcuts",
    description = "Independently hide Bookmark, Favourite, Add to Playlist, Tutorial, and/or " +
        "Playing Queue from the player's shortcut sidebar.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_MX_PLAYER_AD)

    val hideBookmark by booleanOption(key = "hideBookmark", default = true, title = "Hide Bookmark")
    val hideFavourite by booleanOption(key = "hideFavourite", default = true, title = "Hide Favourite")
    val hideAddToPlaylist by booleanOption(key = "hideAddToPlaylist", default = true, title = "Hide Add to Playlist")
    val hideTutorial by booleanOption(key = "hideTutorial", default = true, title = "Hide Tutorial")
    val hidePlayingQueue by booleanOption(key = "hidePlayingQueue", default = true, title = "Hide Playing Queue")

    execute {
        if (hideBookmark == true) {
            val m = BookmarkFingerprint
            m.method.replaceInstruction(
                m.instructionMatches[1].index,
                BuilderInstruction20t(Opcode.GOTO_16, m.target(1)),
            )
        }

        // Read Add to Playlist's start index before either edit below - a
        // replaceInstruction() never changes instruction count, so this index
        // stays valid regardless of which of the two edits happens, or their order.
        val addToPlaylistStart = AddToPlaylistFingerprint.instructionMatches[0].index

        if (hideFavourite == true) {
            val m = FavouriteFingerprint
            val narrowedTarget = m.method.implementation!!.newLabelForIndex(addToPlaylistStart)
            m.method.replaceInstruction(
                m.instructionMatches[1].index,
                BuilderInstruction20t(Opcode.GOTO_16, narrowedTarget),
            )
        }

        if (hideAddToPlaylist == true) {
            val m = FavouriteFingerprint
            val cond12Target = m.target(1)
            AddToPlaylistFingerprint.method.replaceInstruction(
                addToPlaylistStart,
                BuilderInstruction20t(Opcode.GOTO_16, cond12Target),
            )
        }

        if (hideTutorial == true) {
            val m = TutorialFingerprint
            m.method.replaceInstruction(
                m.instructionMatches[1].index,
                BuilderInstruction20t(Opcode.GOTO_16, m.target(1)),
            )
        }

        if (hidePlayingQueue == true) {
            val m = PlayingQueueFingerprint
            m.method.replaceInstruction(
                m.instructionMatches[0].index,
                BuilderInstruction20t(Opcode.GOTO_16, m.target(5)),
            )
        }
    }
}
