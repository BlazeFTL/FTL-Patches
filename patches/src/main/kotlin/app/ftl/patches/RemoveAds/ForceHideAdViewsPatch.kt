package app.ftl.patches.removeads

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructionsOrNull
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.WideLiteralInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.value.IntEncodedValue

private fun Instruction.argRegisters(): List<Int>? = when (this) {
    is FiveRegisterInstruction -> when (registerCount) {
        0 -> emptyList()
        1 -> listOf(registerC)
        2 -> listOf(registerC, registerD)
        3 -> listOf(registerC, registerD, registerE)
        4 -> listOf(registerC, registerD, registerE, registerF)
        5 -> listOf(registerC, registerD, registerE, registerF, registerG)
        else -> null
    }
    is RegisterRangeInstruction -> (startRegister until startRegister + registerCount).toList()
    else -> null
}

private val NARROW_CONST_OPCODES = setOf(Opcode.CONST, Opcode.CONST_4, Opcode.CONST_16, Opcode.CONST_HIGH16)
private val MOVE_OBJECT_OPCODES = setOf(Opcode.MOVE_OBJECT, Opcode.MOVE_OBJECT_16, Opcode.MOVE_OBJECT_FROM16)
private val INVOKE_OPCODES = setOf(
    Opcode.INVOKE_VIRTUAL, Opcode.INVOKE_INTERFACE,
    Opcode.INVOKE_VIRTUAL_RANGE, Opcode.INVOKE_INTERFACE_RANGE,
)

private data class SetVisibilityHit(val index: Int, val valReg: Int)

private fun scanMethod(
    instructions: List<Instruction>,
    adResourceIds: Set<Int>,
    adRelatedFields: MutableMap<String, MutableSet<String>>,
    recordFieldWrites: Boolean,
): List<SetVisibilityHit> {
    val constValue = mutableMapOf<Int, Long>()
    val isAdView = mutableMapOf<Int, Boolean>()
    val hits = mutableListOf<SetVisibilityHit>()

    instructions.forEachIndexed { index, instruction ->
        when {
            instruction is WideLiteralInstruction && instruction.opcode in NARROW_CONST_OPCODES -> {
                val reg = (instruction as OneRegisterInstruction).registerA
                constValue[reg] = instruction.wideLiteral
                isAdView[reg] = false
            }

            instruction.opcode in MOVE_OBJECT_OPCODES -> {
                val two = instruction as TwoRegisterInstruction
                isAdView[two.registerA] = isAdView[two.registerB] == true
            }

            instruction.opcode == Opcode.MOVE_RESULT_OBJECT -> {
                val reg = (instruction as OneRegisterInstruction).registerA
                val prev = instructions.getOrNull(index - 1)
                val ref = (prev as? ReferenceInstruction)?.reference as? MethodReference
                val prevArgs = prev?.argRegisters()
                isAdView[reg] = ref != null && prev != null && prevArgs != null &&
                    ref.name == "findViewById" && ref.parameterTypes.size == 1 &&
                    prevArgs.isNotEmpty() &&
                    constValue[prevArgs.last()]?.toInt() in adResourceIds
            }

            instruction.opcode == Opcode.IGET_OBJECT -> {
                val two = instruction as TwoRegisterInstruction
                val fieldRef = (instruction as ReferenceInstruction).reference as FieldReference
                isAdView[two.registerA] = adRelatedFields[fieldRef.definingClass]?.contains(fieldRef.name) == true
            }

            instruction.opcode == Opcode.IPUT_OBJECT && recordFieldWrites -> {
                val two = instruction as TwoRegisterInstruction
                if (isAdView[two.registerA] == true) {
                    val fieldRef = (instruction as ReferenceInstruction).reference as FieldReference
                    adRelatedFields.getOrPut(fieldRef.definingClass) { mutableSetOf() }.add(fieldRef.name)
                }
            }

            instruction.opcode in INVOKE_OPCODES -> {
                val ref = (instruction as ReferenceInstruction).reference as? MethodReference ?: return@forEachIndexed
                if (ref.name == "setVisibility" && ref.definingClass == "Landroid/view/View;" && ref.parameterTypes.size == 1) {
                    val regs = instruction.argRegisters() ?: return@forEachIndexed
                    if (regs.size >= 2 && isAdView[regs[0]] == true) {
                        hits.add(SetVisibilityHit(index, regs[1]))
                    }
                }
            }
        }
    }
    return hits
}

val forceHideAdViewsPatch = bytecodePatch(
    name = null,
    description = "Forces View.setVisibility(GONE) on any view resolved from an ad-related resource id, " +
        "overriding whatever visibility the app's own code tries to set at runtime.",
) {
    execute {
        val adResourceIds = mutableSetOf<Int>()
        classDefForEach { classDef ->
            if (!classDef.type.endsWith("/R\$id;")) return@classDefForEach
            classDef.staticFields.forEach { field ->
                val value = (field.initialValue as? IntEncodedValue)?.value ?: return@forEach
                if (idLooksAdRelated(field.name)) adResourceIds.add(value)
            }
        }
        if (adResourceIds.isEmpty()) return@execute

        val adRelatedFields = mutableMapOf<String, MutableSet<String>>()
        classDefForEach { classDef ->
            classDef.methods.forEach { method ->
                val instructions = method.instructionsOrNull ?: return@forEach
                scanMethod(instructions.toList(), adResourceIds, adRelatedFields, recordFieldWrites = true)
            }
        }

        classDefForEach { classDef ->
            val hasCandidateCall = classDef.methods.any { method ->
                (method.instructionsOrNull ?: emptyList()).any { instr ->
                    instr.opcode in INVOKE_OPCODES &&
                        ((instr as? ReferenceInstruction)?.reference as? MethodReference)?.let {
                            it.name == "setVisibility" && it.definingClass == "Landroid/view/View;"
                        } == true
                }
            }
            if (!hasCandidateCall) return@classDefForEach

            val mutableClass = mutableClassDefBy(classDef)
            mutableClass.methods.forEach { method ->
                val instructions = method.instructionsOrNull?.toList() ?: return@forEach
                val hits = scanMethod(instructions, adResourceIds, adRelatedFields, recordFieldWrites = false)
                hits.asReversed().forEach { hit ->
                    method.addInstruction(hit.index, "const/16 v${hit.valReg}, 0x8")
                }
            }
        }
    }
}
