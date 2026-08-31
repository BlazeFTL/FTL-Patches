package app.ftl.patches.rsfileexplorer

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.removeInstructions
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

/**
 * Matches the method that builds the "add a remote connection" list (sharebrowser,
 * http, ftp, smb, webdav, flashair, bluetooth, ...). Name is obfuscated and reshuffles
 * every build, so it's found by the app's own real (unobfuscated) protocol-scheme
 * string literals instead.
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
 * Matches the method that builds the Category section (Photos, Music, Video, Books,
 * Archives). Found the same way, via its own real content-scheme string literals.
 */
private object CategoryListFingerprint : Fingerprint(
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
 * Matches the method that builds the Storage section's entry list (root storage, SD
 * card, OTG, encrypted vault, downloader, ...). Found via the "root" scheme literal
 * every entry is compared against, plus the loop's own increment immediately followed
 * by its goto - both fixed points in this method, not obfuscated names.
 */
private object StorageEntryListFingerprint : Fingerprint(
    returnType = "V",
    parameters = emptyList(),
    filters = listOf(
        string("root"),
        opcode(Opcode.ADD_INT_LIT8),
        opcode(Opcode.GOTO, InstructionLocation.MatchAfterImmediately()),
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
    description = "Hides the remote-connection and Category sections from the navigation sidebar, and hides Encrypt and Downloader from the Storage section.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_RS_FILE_EXPLORER)

    execute {
        // Both are private, single-purpose section builders; gutting them to
        // return-void means their section is never built and never added to the
        // sidebar's section list. Matched and edited independently - neither depends
        // on the other, or on locating their (obfuscated) caller.
        RemoteConnectionListFingerprint.method.gut()
        CategoryListFingerprint.method.gut()

        // --- Storage section: hide the Encrypt and Downloader entries only ---

        val storageMethod = StorageEntryListFingerprint.method
        val storageInstructions = storageMethod.implementation!!.instructions

        val rootStringMatch = StorageEntryListFingerprint.instructionMatches[0]
        val incrementIndex = StorageEntryListFingerprint.instructionMatches[1].index
        // Captured as an instruction object, not an index, so it stays valid after the
        // insertion below shifts every later index.
        val incrementInstruction = storageInstructions[incrementIndex]

        // The register holding the "root" string is free again right after this point
        // in the original code (about to be reassigned to "root" itself), so it's
        // reused here as scratch space for the two new string checks.
        val scratchRegister = rootStringMatch.getInstruction<OneRegisterInstruction>().registerA
        // The register holding the entry's own scheme identifier, being compared
        // against "root" on the very next instruction - the same value the new checks
        // need to test.
        val identifierRegister =
            (storageInstructions[rootStringMatch.index + 1] as FiveRegisterInstruction).registerD

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
