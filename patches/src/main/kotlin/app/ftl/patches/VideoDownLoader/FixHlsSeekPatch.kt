package app.ftl.patches.videodownloader

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation
import app.morphe.patcher.OpcodesFilter
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.removeInstructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

private const val APP_OPEN_MANAGER = "Landroid/supprot/design/widgit/open_ad/AppOpenManager;"

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

private object BackgroundConvertGateFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = emptyList(),
    filters = listOf(
        fieldAccess(
            opcode = Opcode.SGET_BOOLEAN,
            definingClass = APP_OPEN_MANAGER,
            type = "Z"
        ),
        opcode(Opcode.IF_EQZ, InstructionLocation.MatchAfterImmediately()),
        methodCall(
            smali = "Ljava/lang/System;->currentTimeMillis()J",
            location = InstructionLocation.MatchAfterWithin(5)
        ),
        methodCall(
            smali = "Ljava/lang/Thread;->start()V",
            location = InstructionLocation.MatchAfterWithin(30)
        )
    )
)

@Suppress("unused")
val fixHlsSeekPatch = bytecodePatch(
    name = "Fix HLS seek playback",
    description = "Remuxes classic MPEG-TS HLS downloads to MP4 so seeking in players has no dark frames, " +
        "and runs the conversion when the download finishes even if the app is minimized.",
    default = true
) {
    compatibleWith(COMPATIBILITY_VIDEO_DOWNLOADER)

    execute {
        HlsConvertGateFingerprint.let {
            it.method.removeInstructions(it.instructionMatches[0].index, 2)
        }

        BackgroundConvertGateFingerprint.method.apply {
            val gateIndices = implementation!!.instructions
                .withIndex()
                .filter { (_, instruction) ->
                    instruction.opcode == Opcode.SGET_BOOLEAN &&
                        ((instruction as ReferenceInstruction).reference as FieldReference)
                            .definingClass == APP_OPEN_MANAGER
                }
                .map { it.index }

            gateIndices.forEach { index ->
                val register = getInstruction<OneRegisterInstruction>(index).registerA
                replaceInstruction(index, "const/16 v$register, 0x1")
            }
        }
    }
}
