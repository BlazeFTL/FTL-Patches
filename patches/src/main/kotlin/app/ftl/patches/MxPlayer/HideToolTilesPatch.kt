package app.ftl.patches.mxplayer

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.InstructionLocation.MatchAfterWithin
import app.morphe.patcher.literal
import app.morphe.patcher.opcode
import app.morphe.patcher.string
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.removeInstruction
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

// definingClass/name deliberately omitted: LKW/I1 are ProGuard-obfuscated and
// already broke once (Lo/mg -> Lo/sg style rename). Anchored instead on the
// unobfuscated tile-label strings, in the exact order they're built, ending
// on the literal 7 (array size) -> new-array pair this patch rewrites.
internal object ToolTilesArrayFingerprint : Fingerprint(
    filters = listOf(
        string("MX Share"),
        string("Private Folder"),
        string("Video Playlists"),
        string("Network Stream"),
        string("Local Network"),
        string("Media Manager"),
        string("Cloud Drive"),
        literal(7, location = MatchAfterWithin(8)),
        opcode(Opcode.NEW_ARRAY, location = MatchAfterImmediately()),
    ),
)

val hideToolTilesPatch = bytecodePatch(
    name = "Hide File Transfer, Video Playlist, Private Folder tiles",
    description = "Removes the File Transfer, Video Playlist, and Private Folder tiles from settings Page.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_MX_PLAYER_PRO)

    execute {
        ToolTilesArrayFingerprint.let {
            // literal(7) and NEW_ARRAY are always the last 2 matches, regardless
            // of how many string filters precede them.
            val startIndex = it.instructionMatches[it.instructionMatches.size - 2].index
            val newArrayIndex = it.instructionMatches.last().index

            // Array element type (originally "[LNW;") is itself obfuscated and
            // renames between builds, so read it off the matched instruction
            // instead of hardcoding it.
            val arrayType = ((it.method.implementation!!.instructions[newArrayIndex]
                as ReferenceInstruction).reference as TypeReference).type

            for (index in startIndex + 12 downTo startIndex) {
                it.method.removeInstruction(index)
            }

            it.method.addInstructions(
                startIndex,
                """
                    const/4 v5, 0x4
                    new-array v5, v5, $arrayType
                    const/4 v4, 0x0
                    aput-object v11, v5, v4
                    const/4 v4, 0x1
                    aput-object v12, v5, v4
                    const/4 v4, 0x2
                    aput-object v13, v5, v4
                    const/4 v4, 0x3
                    aput-object v1, v5, v4
                """.trimIndent(),
            )
        }
    }
}
