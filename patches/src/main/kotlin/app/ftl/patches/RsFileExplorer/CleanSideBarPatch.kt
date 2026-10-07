package app.ftl.patches.rsfileexplorer

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.InstructionLocation.MatchAfterWithin
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.removeInstructions
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

/**
 * As of 2.3.1.1 the sidebar was rewritten: Bookmarks, remote-connection and
 * Category are no longer separate private no-arg methods on an
 * ExpandableListAdapter (pre-2.3.1.1 shape) - all of it, plus Storage, is
 * now inlined directly into the constructor of the new section-builder,
 * alongside several unrelated toggle rows. Gutting a whole method is no
 * longer an option (it would take the entire sidebar down with it), so
 * every section below is instead hidden by skipping only the one call that
 * registers its finished section object into the sidebar's master list -
 * the section is still built into a local, now-unreferenced object, it
 * just never gets added.
 *
 * Anchor for this constructor: its own two real (unobfuscated)
 * protocol-scheme string literals for the remote-connection section
 * (sharebrowser, http, ...), plus the real MainActivity/View parameter
 * types and the real CONSTRUCTOR access flag - required because a sibling
 * class's `<clinit>` holds the same two literals in its backing string
 * array and would otherwise satisfy the string filters too.
 */
private object SideBarBuilderFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.CONSTRUCTOR),
    returnType = "V",
    parameters = listOf("Lcom/edili/filemanager/MainActivity;", "Landroid/view/View;"),
    filters = listOf(
        string("sharebrowser://"),
        string("http://"),
    ),
)

/** Every section's finished object is registered the same way: one real,
 *  unobfuscated `CopyOnWriteArrayList#add(Object)` call. Removing it plus
 *  the 10 instructions right after it (this section's own position
 *  bookkeeping into a ConcurrentHashMap, keyed off the list's own real
 *  `size()` right before the add - an idiom identical and unique per
 *  section) fully un-registers the section without touching anything
 *  upstream that builds it. */
private const val REGISTRATION_BLOCK_SIZE = 11

private fun MutableMethod.dropSection(registerCallIndex: Int) {
    removeInstructions(registerCallIndex, REGISTRATION_BLOCK_SIZE)
}

private object RemoteConnectionSectionFingerprint : Fingerprint(
    classFingerprint = SideBarBuilderFingerprint,
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.CONSTRUCTOR),
    returnType = "V",
    parameters = listOf("Lcom/edili/filemanager/MainActivity;", "Landroid/view/View;"),
    filters = listOf(
        string("sharebrowser://"),
        string("http://"),
        methodCall(
            definingClass = "Ljava/util/concurrent/CopyOnWriteArrayList;",
            name = "add",
            parameters = listOf("Ljava/lang/Object;"),
            returnType = "Z",
            opcode = Opcode.INVOKE_VIRTUAL,
        ),
    ),
)

/** Matches the Category section's five real content-scheme string
 *  literals (Photos, Music, Video, Books, Archives), then the same
 *  registration call as above. */
private object CategorySectionFingerprint : Fingerprint(
    classFingerprint = SideBarBuilderFingerprint,
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.CONSTRUCTOR),
    returnType = "V",
    parameters = listOf("Lcom/edili/filemanager/MainActivity;", "Landroid/view/View;"),
    filters = listOf(
        string("gallery://local/buckets/"),
        string("music://"),
        string("video://"),
        string("book://"),
        string("archive://"),
        methodCall(
            definingClass = "Ljava/util/concurrent/CopyOnWriteArrayList;",
            name = "add",
            parameters = listOf("Ljava/lang/Object;"),
            returnType = "Z",
            opcode = Opcode.INVOKE_VIRTUAL,
        ),
    ),
)

/** Matches the Bookmarks section's (re)population of its own field: the
 *  real `java.util.LinkedList#clear()`, immediately followed by a 0-arg
 *  obfuscated static factory returning the real `java.util.ArrayList`,
 *  immediately followed by the real `java.util.LinkedList#addAll(Collection)`
 *  - a shape that appears exactly once in this method. Its own class, the
 *  field it clears/repopulates and the factory it reads from are all
 *  obfuscated and reshuffle every build, so none of those are pinned. */
private object BookmarksSectionFingerprint : Fingerprint(
    classFingerprint = SideBarBuilderFingerprint,
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.CONSTRUCTOR),
    returnType = "V",
    parameters = listOf("Lcom/edili/filemanager/MainActivity;", "Landroid/view/View;"),
    filters = listOf(
        opcode(Opcode.IGET_OBJECT),
        methodCall(
            definingClass = "Ljava/util/LinkedList;",
            name = "clear",
            parameters = emptyList(),
            returnType = "V",
            opcode = Opcode.INVOKE_VIRTUAL,
            location = MatchAfterImmediately(),
        ),
        methodCall(
            parameters = emptyList(),
            returnType = "Ljava/util/ArrayList;",
            opcode = Opcode.INVOKE_STATIC,
            location = MatchAfterImmediately(),
        ),
        opcode(Opcode.MOVE_RESULT_OBJECT, MatchAfterImmediately()),
        methodCall(
            definingClass = "Ljava/util/LinkedList;",
            name = "addAll",
            parameters = listOf("Ljava/util/Collection;"),
            returnType = "Z",
            opcode = Opcode.INVOKE_VIRTUAL,
            location = MatchAfterImmediately(),
        ),
        methodCall(
            definingClass = "Ljava/util/concurrent/CopyOnWriteArrayList;",
            name = "add",
            parameters = listOf("Ljava/lang/Object;"),
            returnType = "Z",
            opcode = Opcode.INVOKE_VIRTUAL,
        ),
    ),
)

/**
 * Matches the Storage section's entry-building loop (root storage, SD
 * card, OTG, encrypted vault, downloader, ...): every entry's identifier is
 * compared with the real `java.lang.String#equals(Object)` against the
 * real "root" literal, whose result is immediately branched on - as of
 * 2.3.1.1 that branch builds either the root entry or a regular one
 * inline, and the two paths only reconverge well after, at the loop's own
 * increment, so that can no longer be pinned as immediately following
 * IF_EQZ (the bug that broke this on 2.3.1.1: both branches together run
 * under 25 instructions before reconverging, so MatchAfterWithin(30) finds
 * it on either path without risk of hitting some other loop's increment
 * first). "root" appears a second time in a sibling class's static array
 * initializer with no equals-call after it, but the class scope above
 * already excludes that class entirely.
 */
private object StorageEntryListFingerprint : Fingerprint(
    classFingerprint = SideBarBuilderFingerprint,
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.CONSTRUCTOR),
    returnType = "V",
    parameters = listOf("Lcom/edili/filemanager/MainActivity;", "Landroid/view/View;"),
    filters = listOf(
        string("root"),
        methodCall(
            definingClass = "Ljava/lang/String;",
            name = "equals",
            parameters = listOf("Ljava/lang/Object;"),
            returnType = "Z",
            opcode = Opcode.INVOKE_VIRTUAL,
            location = MatchAfterImmediately(),
        ),
        opcode(Opcode.MOVE_RESULT, MatchAfterImmediately()),
        opcode(Opcode.IF_EQZ, MatchAfterImmediately()),
        opcode(Opcode.ADD_INT_LIT8, MatchAfterWithin(30)),
        opcode(Opcode.GOTO, MatchAfterImmediately()),
    ),
)

val cleanSideBarPatch = bytecodePatch(
    name = "Clean sidebar",
    description = "Hides the Bookmarks, remote-connection and Category sections from the navigation sidebar, and hides Encrypt and Downloader from the Storage section.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_RS_FILE_EXPLORER)

    execute {
        // All four fingerprints below match against the same shared constructor, and
        // each one's index is only valid against the bytecode as it stood when
        // matched - not after some other edit has since shifted it. Every target is
        // therefore captured FIRST, before any edit runs, then applied strictly
        // highest-index-first: an edit only ever shifts indices *after* its own
        // position, so processing this way guarantees every other still-pending
        // target - positioned earlier in the method - is never invalidated by an
        // edit that runs before it.
        val method = SideBarBuilderFingerprint.method

        val remoteIndex = RemoteConnectionSectionFingerprint.instructionMatches.last().index
        val bookmarksIndex = BookmarksSectionFingerprint.instructionMatches.last().index
        val categoryIndex = CategorySectionFingerprint.instructionMatches.last().index

        val storageMatches = StorageEntryListFingerprint.instructionMatches
        val rootStringMatch = storageMatches[0]
        val equalsCallMatch = storageMatches[1]
        // Captured as an instruction object, not an index, so it stays valid no matter
        // what runs before it.
        val incrementInstruction = method.implementation!!.instructions[storageMatches[4].index]

        // The register holding the "root" string is free again right after this point
        // in the original code (about to be reassigned to "root" itself), so it's
        // reused here as scratch space for the two new string checks.
        val scratchRegister = rootStringMatch.getInstruction<OneRegisterInstruction>().registerA
        // The register holding the entry's own scheme identifier - the argument being
        // compared against "root" - the same value the new checks need to test.
        val identifierRegister = equalsCallMatch.getInstruction<FiveRegisterInstruction>().registerD

        // --- Storage section: hide the Encrypt and Downloader entries only ---
        // Highest-positioned edit, applied first, while nothing has moved yet.

        // The trailing "nop" after :keep_entry is required, not decorative: addInstructionsWithLabels
        // appends ":loop_increment\nnop" to the end of this text before compiling it as one block. A
        // label with nothing after it merges onto that same appended nop, and anything landing there
        // gets rewired to the external label - so without its own nop, :keep_entry would silently
        // become a second name for :loop_increment instead of falling through to the original code.
        method.addInstructionsWithLabels(
            rootStringMatch.index,
            """
                const-string v$scratchRegister, "encrypt://"
                invoke-virtual {v$scratchRegister, v$identifierRegister}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
                move-result v$scratchRegister
                if-nez v$scratchRegister, :skip_entry
                const-string v$scratchRegister, "downloader"
                invoke-virtual {v$scratchRegister, v$identifierRegister}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
                move-result v$scratchRegister
                if-eqz v$scratchRegister, :keep_entry
                :skip_entry
                goto :loop_increment
                :keep_entry
                nop
            """.trimIndent(),
            ExternalLabel("loop_increment", incrementInstruction),
        )

        // --- Category, Bookmarks, Remote Connection: drop each section's
        // registration, strictly in descending index order (all three positioned
        // before Storage above, so none of them are affected by that insertion) ---
        method.dropSection(categoryIndex)
        method.dropSection(bookmarksIndex)
        method.dropSection(remoteIndex)
    }
}
