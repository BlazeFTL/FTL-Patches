package app.ftl.patches.bangladictionary

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.removeInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod

private const val UPDATE_VISIBILITY_METHOD = "updatePronunciationVisibility"

private fun fieldSmali(instruction: Instruction): String {
    val reference = (instruction as ReferenceInstruction).reference as FieldReference
    return "${reference.definingClass}->${reference.name}:${reference.type}"
}

val disablePronunciationNagPatch = bytecodePatch(
    name = "Disable pronunciation nag",
    description = "Removes the \"device doesn't support pronunciation\" toast and install-TTS dialog, " +
        "and hides the pronunciation button when no usable TTS engine/language is available.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_BANGLA_DICTIONARY)

    execute {
        val initMatches = TtsInitFingerprint.instructionMatches
        val flagA = fieldSmali(initMatches[0].instruction)
        val flagB = fieldSmali(initMatches[1].instruction)

        TtsEngineCheckFingerprint.let {
            it.method.removeInstruction(it.instructionMatches.last().index)
        }

        TtsSpeakFingerprint.let { fingerprint ->
            val invokeIndices = fingerprint.instructionMatches.take(2).map { it.index }

            fingerprint.method.apply {
                invokeIndices.asReversed().forEach { invokeIndex ->
                    var newInstanceIndex = invokeIndex
                    while (newInstanceIndex >= 0 && getInstruction(newInstanceIndex).opcode != Opcode.NEW_INSTANCE) {
                        newInstanceIndex--
                    }
                    if (newInstanceIndex < 0) throw PatchException("Install dialog listener not found")

                    val scratch = getInstruction<OneRegisterInstruction>(newInstanceIndex).registerA
                    val skipTarget = getInstruction(invokeIndex + 1)

                    addInstructionsWithLabels(
                        newInstanceIndex,
                        """
                            iget-boolean v$scratch, p0, $flagA
                            if-nez v$scratch, :show_dialog
                            iget-boolean v$scratch, p0, $flagB
                            if-eqz v$scratch, :skip_dialog
                            :show_dialog
                            nop
                        """.trimIndent(),
                        ExternalLabel("skip_dialog", skipTarget),
                    )
                }
            }
        }

        val setupMatches = PronunciationSetupFingerprint.instructionMatches
        val rootView = fieldSmali(setupMatches[1].instruction)
        val rowId = fieldSmali(setupMatches[2].instruction)
        val buttonField = fieldSmali(PronunciationButtonBindFingerprint.instructionMatches[1].instruction)

        val prefMatches = TtsInitTimePrefChangedFingerprint.instructionMatches
        val activityField = fieldSmali(prefMatches[1].instruction)
        val ttsField = fieldSmali(prefMatches[2].instruction)
        val afterVisibilityIndex = prefMatches[3].index + 1

        val uiClass = PronunciationSetupFingerprint.originalClassDef.type
        val updateRef = "$uiClass->$UPDATE_VISIBILITY_METHOD()V"

        val updateMethod = ImmutableMethod(
            uiClass,
            UPDATE_VISIBILITY_METHOD,
            null,
            "V",
            AccessFlags.PUBLIC.value or AccessFlags.FINAL.value,
            null,
            null,
            MutableMethodImplementation(5),
        ).toMutable()

        updateMethod.addInstructionsWithLabels(
            0,
            """
                sget v0, $rowId
                iget-object v1, p0, $rootView
                invoke-virtual { v1, v0 }, Landroid/view/View;->findViewById(I)Landroid/view/View;
                move-result-object v0
                iget-object v1, p0, $activityField
                iget-object v1, v1, $ttsField
                const/16 v2, 0x8
                if-eqz v1, :apply
                iget-boolean v3, v1, $flagA
                if-nez v3, :show
                iget-boolean v1, v1, $flagB
                if-eqz v1, :apply
                :show
                const/4 v2, 0x0
                :apply
                if-eqz v0, :skip_row
                invoke-virtual { v0, v2 }, Landroid/view/View;->setVisibility(I)V
                :skip_row
                iget-object v0, p0, $buttonField
                if-eqz v0, :done
                invoke-virtual { v0, v2 }, Landroid/view/View;->setVisibility(I)V
                :done
                return-void
            """.trimIndent(),
        )

        TtsInitTimePrefChangedFingerprint.method.addInstruction(
            afterVisibilityIndex,
            "invoke-virtual { p0 }, $updateRef",
        )

        PronunciationSetupFingerprint.method.apply {
            val lastIndex = implementation!!.instructions.count() - 1
            if (getInstruction(lastIndex).opcode != Opcode.RETURN_VOID) {
                throw PatchException("Unexpected end of pronunciation setup method")
            }
            addInstruction(lastIndex, "invoke-virtual { p0 }, $updateRef")
        }

        PronunciationSetupFingerprint.classDef.methods.add(updateMethod)
    }
}
