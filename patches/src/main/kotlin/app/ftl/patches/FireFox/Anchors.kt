package app.ftl.patches.firefox

import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

internal fun Instruction.callRegisters(): List<Int> = when (this) {
    is RegisterRangeInstruction -> (startRegister until startRegister + registerCount).toList()
    is FiveRegisterInstruction -> listOf(registerC, registerD, registerE, registerF, registerG).take(registerCount)
    else -> emptyList()
}

internal fun Instruction.refersToType(type: String): Boolean =
    ((this as? ReferenceInstruction)?.reference as? TypeReference)?.type == type

internal fun Instruction.calls(name: String): Boolean =
    ((this as? ReferenceInstruction)?.reference as? MethodReference)?.name == name

internal fun MutableMethod.parameterBase(): Int {
    var total = if (AccessFlags.STATIC.isSet(accessFlags)) 0 else 1
    for (type in parameterTypes) {
        val descriptor = type.toString()
        total += if (descriptor == "J" || descriptor == "D") 2 else 1
    }
    return implementation!!.registerCount - total
}

internal fun MutableMethod.firstIndexFrom(from: Int, predicate: (Int, Instruction) -> Boolean): Int {
    val count = implementation!!.instructions.size
    for (i in from until count) {
        if (predicate(i, getInstruction<Instruction>(i))) return i
    }
    return -1
}

internal fun MutableMethod.lastIndexBefore(from: Int, window: Int, predicate: (Instruction) -> Boolean): Int {
    val lowest = maxOf(0, from - window)
    for (i in from - 1 downTo lowest) {
        if (predicate(getInstruction<Instruction>(i))) return i
    }
    return -1
}

internal fun MutableMethod.callParameters(index: Int): List<Pair<String, Int>> {
    val call = getInstruction<Instruction>(index)
    val reference = (call as? ReferenceInstruction)?.reference as? MethodReference ?: return emptyList()
    val registers = call.callRegisters()
    var cursor = if (call.opcode.name.contains("static")) 0 else 1
    val result = ArrayList<Pair<String, Int>>()
    for (parameter in reference.parameterTypes) {
        val descriptor = parameter.toString()
        val register = registers.getOrNull(cursor) ?: break
        result += descriptor to register
        cursor += if (descriptor == "J" || descriptor == "D") 2 else 1
    }
    return result
}

internal fun MutableMethod.callArgument(index: Int, type: String): Int? =
    callParameters(index).firstOrNull { it.first == type }?.second

internal fun Liveness.holder(source: Int, at: Int, what: String): Int {
    val holders = copiesOf(source, at)
    return holders.firstOrNull { it < 16 }
        ?: holders.firstOrNull()
        ?: throw PatchException("$what is not held by any register at $at")
}

internal fun MutableMethod.lowHolder(live: Liveness, source: Int, at: Int, what: String): Int {
    val register = live.holder(source, at, what)
    if (register > 15) throw PatchException("$what only in v$register at $at, out of 4 bit range")
    return register
}

internal fun MutableMethod.staticCall(at: Int, target: String, registers: List<Int>): String {
    if (registers.all { it < 16 }) {
        return "invoke-static {${registers.joinToString(", ") { "v$it" }}}, $target"
    }
    val scratch = Liveness(this).takeLow(registers.size, at, exclude = registers.toSet())
    val lines = ArrayList<String>()
    registers.forEachIndexed { i, register -> lines += "move-object/from16 v${scratch[i]}, v$register" }
    lines += "invoke-static {${scratch.joinToString(", ") { "v$it" }}}, $target"
    return lines.joinToString("\n")
}
