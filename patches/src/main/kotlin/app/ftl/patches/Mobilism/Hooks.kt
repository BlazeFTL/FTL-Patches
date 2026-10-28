package app.ftl.patches.mobilism

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

internal const val EXT_CLASS_PREFIX = "Lapp/ftl/extension/mobilism"

internal fun MutableMethod.findInstruction(predicate: (Instruction) -> Boolean): Int =
    implementation!!.instructions.indexOfFirst(predicate)

internal fun MutableMethod.indexOfFieldPut(fieldName: String): Int {
    val index = findInstruction {
        it.opcode == Opcode.IPUT_OBJECT &&
            ((it as? ReferenceInstruction)?.reference as? FieldReference)?.name == fieldName
    }
    if (index < 0) throw PatchException("iput-object $fieldName not found in $name")
    return index
}

internal fun MutableMethod.indexOfVirtualCall(methodName: String): Int {
    val index = findInstruction {
        (it.opcode == Opcode.INVOKE_VIRTUAL || it.opcode == Opcode.INVOKE_VIRTUAL_RANGE) &&
            ((it as? ReferenceInstruction)?.reference as? MethodReference)?.name == methodName
    }
    if (index < 0) throw PatchException("invoke-virtual $methodName not found in $name")
    return index
}

internal fun MutableMethod.indexesOfReturnVoid(): List<Int> {
    val list = implementation!!.instructions.withIndex()
        .filter { it.value.opcode == Opcode.RETURN_VOID }
        .map { it.index }
    if (list.isEmpty()) throw PatchException("return-void not found in $name")
    return list
}

internal fun Instruction.firstRegister(): Int = when (this) {
    is FiveRegisterInstruction -> registerC
    is RegisterRangeInstruction -> startRegister
    is TwoRegisterInstruction -> registerA
    else -> throw PatchException("Unsupported instruction ${opcode.name}")
}

internal fun MutableMethod.insertAfterSuperCall(smali: String) {
    val index = findInstruction {
        it.opcode == Opcode.INVOKE_SUPER || it.opcode == Opcode.INVOKE_SUPER_RANGE
    }
    if (index < 0) throw PatchException("super call not found in $name")
    addInstructions(index + 1, smali)
}
