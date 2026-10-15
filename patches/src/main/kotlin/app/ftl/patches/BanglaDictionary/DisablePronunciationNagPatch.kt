package app.ftl.patches.bangladictionary

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.removeInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.findFreeRegister
import app.morphe.patcher.util.p0Register
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.morphe.patcher.util.registersUsed
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod

private const val UPDATE_VISIBILITY_METHOD = "updatePronunciationVisibility"

private fun Fingerprint.InstructionMatch.fieldSmali(): String {
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
        val flagA = initMatches[0].fieldSmali()
        val flagB = initMatches[1].fieldSmali()

        TtsEngineCheckFingerprint.let {
            it.method.removeInstruction(it.instructionMatches.last().index)
        }

        TtsSpeakFingerprint.let { fingerprint ->
            val dialogCalls = fingerprint.instructionMatches.take(2)

            fingerprint.method.apply {
                val thisRegister = p0Register

                dialogCalls.asReversed().forEach { call ->
                    val index = call.index
                    val scratch = findFreeRegister(index, call.instruction.registersUsed + thisRegister)
                    val skipTarget = getInstruction(index + 1)

                    addInstructionsWithLabels(
                        index,
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
        val rowId = setupMatches[2].fieldSmali()
        val rootView = setupMatches[1].fieldSmali()
        val buttonField = PronunciationButtonBindFingerprint.instructionMatches[1].fieldSmali()

        val prefMatches = TtsInitTimePrefChangedFingerprint.instructionMatches
        val activityField = prefMatches[1].fieldSmali()
        val ttsField = prefMatches[2].fieldSmali()
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
