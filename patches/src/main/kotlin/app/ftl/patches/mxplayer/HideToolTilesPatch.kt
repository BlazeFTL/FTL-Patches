package app.ftl.patches.mxplayer

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.literal
import app.morphe.patcher.opcode
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.removeInstruction
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode

internal object ToolTilesArrayFingerprint : Fingerprint(
    definingClass = "LKW;",
    name = "I1",
    filters = listOf(
        literal(7),
        opcode(Opcode.NEW_ARRAY, location = MatchAfterImmediately()),
    ),
)

val hideToolTilesPatch = bytecodePatch(
    name = "Hide File Transfer, Video Playlist, Private Folder tiles",
    description = "Removes the File Transfer, Video Playlist, and Private Folder tiles from the tools tile list.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_MX_PLAYER_PRO, COMPATIBILITY_MX_PLAYER_FREE)

    execute {
        ToolTilesArrayFingerprint.let {
            val startIndex = it.instructionMatches[0].index

            for (index in startIndex + 12 downTo startIndex) {
                it.method.removeInstruction(index)
            }

            it.method.addInstructions(
                startIndex,
                """
                    const/4 v5, 0x4
                    new-array v5, v5, [LNW;
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
