package app.ftl.patches.rsfileexplorer

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.removeInstructions
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

/**
 * Anchor for the whole sidebar-builder class. Matches the method that builds
 * the "add a remote connection" list (sharebrowser, http, ftp, smb, webdav,
 * flashair, bluetooth, ...) via its own two real (unobfuscated) protocol-scheme
 * string literals - a pair unique to this one feature. Every other fingerprint
 * below pins `classFingerprint` to this one, so none of them can ever match a
 * method in some unrelated class elsewhere in the app - the bug that broke the
 * previous version of this patch (an unscoped fingerprint silently matched an
 * unrelated method elsewhere that also happened to contain the word "root").
 */
private object RemoteConnectionListFingerprint : Fingerprint(
    returnType = "V",
    parameters = emptyList(),
    filters = listOf(
        string("sharebrowser://"),
        string("http://"),
    ),
)

/**
 * Matches the method that builds the Category section (Photos, Music, Video,
 * Books, Archives), via its own real content-scheme string literals.
 */
private object CategoryListFingerprint : Fingerprint(
    classFingerprint = RemoteConnectionListFingerprint,
    returnType = "V",
    parameters = emptyList(),
    filters = listOf(
        string("gallery://local/buckets/"),
        string("music://"),
        string("video://"),
        string("book://"),
        string("archive://"),
    ),
)

/**
 * Matches the method that (re)builds the Bookmarks section list. Its own
 * class, the private List field it clears/repopulates, and the 0-arg static
 * factory it reads from are all obfuscated and reshuffle every build, so none
 * of those are pinned. Instead this is matched by a chain of only real,
 * unobfuscated JDK calls that occur back-to-back nowhere else in the class: an
 * IGET_OBJECT read of a List field, immediately cleared via the real
 * `java.util.List#clear()`, immediately followed by a re-read of the same
 * shape of field, an obfuscated 0-arg factory returning the real
 * `java.util.ArrayList`, and immediately repopulated via the real
 * `java.util.List#addAll(Collection)`. A near-identical clear+refill idiom
 * exists elsewhere in this class (a conditional "refresh" method), but there
 * it's separated by other instructions rather than fully back-to-back, so
 * requiring every step immediately after the last is what keeps this
 * fingerprint pointed at only this one method.
 */
private object BookmarksListFingerprint : Fingerprint(
    classFingerprint = RemoteConnectionListFingerprint,
    returnType = "V",
    parameters = emptyList(),
    filters = listOf(
        opcode(Opcode.IGET_OBJECT),
        methodCall(
            definingClass = "Ljava/util/List;",
            name = "clear",
            parameters = emptyList(),
            returnType = "V",
            opcode = Opcode.INVOKE_INTERFACE,
            location = MatchAfterImmediately(),
        ),
        opcode(Opcode.IGET_OBJECT, MatchAfterImmediately()),
        methodCall(
            parameters = emptyList(),
            returnType = "Ljava/util/ArrayList;",
            opcode = Opcode.INVOKE_STATIC,
            location = MatchAfterImmediately(),
        ),
        opcode(Opcode.MOVE_RESULT_OBJECT, MatchAfterImmediately()),
        methodCall(
            definingClass = "Ljava/util/List;",
            name = "addAll",
            parameters = listOf("Ljava/util/Collection;"),
            returnType = "Z",
            opcode = Opcode.INVOKE_INTERFACE,
            location = MatchAfterImmediately(),
        ),
    ),
)

/**
 * Matches the method that builds the Storage section's entry list (root
 * storage, SD card, OTG, encrypted vault, downloader, ...). The class scope
 * alone would already stop this from matching some unrelated "root" string
 * elsewhere in the app (the previous version's bug), but this also pins the
 * exact real, unobfuscated shape immediately following the literal: every
 * entry gets compared with the real `java.lang.String#equals(Object)`, whose
 * result is immediately branched on. Within this class "root" only appears
 * twice - once here, and once in a static array initializer that has no
 * equals-call after it at all - so this shape alone already disambiguates the
 * two even before the class scope is considered. The loop's own increment,
 * immediately followed by its own goto, is then found the same way as before,
 * now guaranteed to be this loop's and not some coincidental one elsewhere.
 */
private object StorageEntryListFingerprint : Fingerprint(
    classFingerprint = RemoteConnectionListFingerprint,
    returnType = "V",
    parameters = emptyList(),
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
        opcode(Opcode.ADD_INT_LIT8),
        opcode(Opcode.GOTO, MatchAfterImmediately()),
    ),
)

/** Replaces a matched no-arg void method's entire body with a single `return-void`. */
private fun MutableMethod.gut() {
    val instructionCount = implementation!!.instructions.size
    removeInstructions(0, instructionCount)
    addInstructions(0, "return-void")
}

val cleanSideBarPatch = bytecodePatch(
    name = "Clean sidebar",
    description = "Hides the Bookmarks, remote-connection and Category sections from the navigation sidebar, and hides Encrypt and Downloader from the Storage section.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_RS_FILE_EXPLORER)

    execute {
        // All 3 are private, single-purpose section builders; gutting them to
        // return-void means their section is never built and never added to the
        // sidebar's section list. Matched and edited independently - neither depends
        // on the others, or on locating their (obfuscated) caller.
        BookmarksListFingerprint.method.gut()
        RemoteConnectionListFingerprint.method.gut()
        CategoryListFingerprint.method.gut()

        // --- Storage section: hide the Encrypt and Downloader entries only ---

        val storageMethod = StorageEntryListFingerprint.method
        val storageInstructions = storageMethod.implementation!!.instructions
        val matches = StorageEntryListFingerprint.instructionMatches

        val rootStringMatch = matches[0]
        val equalsCallMatch = matches[1]
        val incrementIndex = matches[4].index
        // Captured as an instruction object, not an index, so it stays valid after the
        // insertion below shifts every later index.
        val incrementInstruction = storageInstructions[incrementIndex]

        // The register holding the "root" string is free again right after this point
        // in the original code (about to be reassigned to "root" itself), so it's
        // reused here as scratch space for the two new string checks.
        val scratchRegister = rootStringMatch.getInstruction<OneRegisterInstruction>().registerA
        // The register holding the entry's own scheme identifier - the argument being
        // compared against "root" - the same value the new checks need to test.
        val identifierRegister = equalsCallMatch.getInstruction<FiveRegisterInstruction>().registerD

        storageMethod.addInstructionsWithLabels(
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
            """.trimIndent(),
            ExternalLabel("loop_increment", incrementInstruction),
        )
    }
}
