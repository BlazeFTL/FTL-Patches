package app.ftl.patches.videodownloader

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.OpcodesFilter
import app.morphe.patcher.extensions.InstructionExtensions.removeInstructions
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode

private object HlsConvertGateFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "V",
    parameters = listOf(
        "Landroid/content/Context;",
        "Ljava/lang/String;",
        "Ljava/util/ArrayList;"
    ),
    filters = OpcodesFilter.opcodesToFilters(
        Opcode.IGET_BOOLEAN,
        Opcode.IF_EQZ,
        Opcode.IGET,
        Opcode.IF_NE,
        Opcode.IPUT_BOOLEAN
    )
)

@Suppress("unused")
val fixHlsSeekPatch = bytecodePatch(
    name = "Fix HLS seek playback",
    description = "Remuxes classic MPEG-TS HLS downloads to MP4 so seeking in players has no dark frames.",
    default = true
) {
    compatibleWith(COMPATIBILITY_VIDEO_DOWNLOADER)

    execute {
        HlsConvertGateFingerprint.let {
            it.method.removeInstructions(it.instructionMatches[0].index, 2)
        }
    }
}
