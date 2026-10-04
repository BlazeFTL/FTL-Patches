package app.ftl.patches.firefox

import app.morphe.patcher.InstructionFilter
import app.morphe.patcher.InstructionLocation
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction

private fun filter(
    location: InstructionLocation,
    predicate: (method: Method, instruction: Instruction) -> Boolean,
) = object : InstructionFilter {
    override val location = location
    override fun matches(enclosingMethod: Method, instruction: Instruction) =
        predicate(enclosingMethod, instruction)
}

internal fun constTo(
    register: Int,
    value: Int,
    location: InstructionLocation = InstructionLocation.MatchAfterAnywhere(),
) = filter(location) { _, instruction ->
    instruction is NarrowLiteralInstruction &&
        instruction is OneRegisterInstruction &&
        instruction.registerA == register &&
        instruction.narrowLiteral == value
}

internal fun moveObject(
    dest: Int,
    src: Int,
    location: InstructionLocation = InstructionLocation.MatchAfterAnywhere(),
) = filter(location) { _, instruction ->
    instruction.opcode == Opcode.MOVE_OBJECT &&
        (instruction as TwoRegisterInstruction).registerA == dest &&
        instruction.registerB == src
}

internal fun moveStaticParam(
    dest: Int,
    parameterIndex: Int,
    location: InstructionLocation = InstructionLocation.MatchAfterAnywhere(),
) = filter(location) { method, instruction ->
    instruction.opcode == Opcode.MOVE_OBJECT_FROM16 &&
        (instruction as TwoRegisterInstruction).registerA == dest &&
        instruction.registerB == parameterRegister(method, parameterIndex)
}

internal fun callInto(
    classPart: String,
    name: String,
    location: InstructionLocation = InstructionLocation.MatchAfterAnywhere(),
) = filter(location) { _, instruction ->
    val reference = (instruction as? ReferenceInstruction)?.reference as? MethodReference
    reference != null && reference.name == name && reference.definingClass.contains(classPart)
}

private fun parameterRegister(method: Method, parameterIndex: Int): Int {
    val static = AccessFlags.STATIC.isSet(method.accessFlags)
    var total = if (static) 0 else 1
    var offset = total
    method.parameterTypes.forEachIndexed { i, type ->
        val width = if (type == "J" || type == "D") 2 else 1
        if (i < parameterIndex) offset += width
        total += width
    }
    return method.implementation!!.registerCount - total + offset
}
