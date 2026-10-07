package app.ftl.patches.mxplayer

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.InstructionLocation.MatchAfterWithin
import app.morphe.patcher.literal
import app.morphe.patcher.opcode
import app.morphe.patcher.string
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.removeInstruction
import app.morphe.patcher.patch.booleanOption
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ThreeRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

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

    val hideFileTransfer by booleanOption(
        key = "hideFileTransfer",
        default = true,
        title = "Hide File Transfer",
        description = "Removes the File Transfer tile.",
    )
    val hidePrivateFolder by booleanOption(
        key = "hidePrivateFolder",
        default = true,
        title = "Hide Private Folder",
        description = "Removes the Private Folder tile.",
    )
    val hideVideoPlaylists by booleanOption(
        key = "hideVideoPlaylists",
        default = true,
        title = "Hide Video Playlists",
        description = "Removes the Video Playlists tile.",
    )

    execute {
        val hide = listOf(
            hideFileTransfer != false,
            hidePrivateFolder != false,
            hideVideoPlaylists != false,
        )
        if (hide.none { it }) return@execute

        ToolTilesArrayFingerprint.let {
            val startIndex = it.instructionMatches[it.instructionMatches.size - 2].index
            val newArrayIndex = it.instructionMatches.last().index
            val instructions = it.method.implementation!!.instructions

            val arrayType = ((instructions[newArrayIndex]
                as ReferenceInstruction).reference as TypeReference).type

            val values = (startIndex + 2..startIndex + 12)
                .map { i -> instructions[i] }
                .filter { i -> i.opcode == Opcode.APUT_OBJECT }
                .map { i -> (i as ThreeRegisterInstruction).registerA }

            check(values.size == 7) { "Expected 7 array stores, found ${values.size}" }

            val kept = values.filterIndexed { i, _ -> i >= hide.size || !hide[i] }

            for (index in startIndex + 12 downTo startIndex) {
                it.method.removeInstruction(index)
            }

            it.method.addInstructions(
                startIndex,
                buildString {
                    appendLine("const/4 v5, 0x${kept.size.toString(16)}")
                    appendLine("new-array v5, v5, $arrayType")
                    kept.forEachIndexed { i, reg ->
                        appendLine("const/4 v4, 0x${i.toString(16)}")
                        appendLine("aput-object v$reg, v5, v4")
                    }
                },
            )
        }
    }
}
