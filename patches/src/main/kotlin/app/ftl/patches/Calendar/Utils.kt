package app.ftl.patches.calendar

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

internal fun MutableMethod.indexOfCall(
    name: String,
    from: Int = 0,
    owner: String? = null,
): Int {
    val list = implementation!!.instructions
    for (i in from until list.size) {
        val ref = (list[i] as? ReferenceInstruction)?.reference as? MethodReference ?: continue
        if (ref.name == name && (owner == null || ref.definingClass == owner)) return i
    }
    throw PatchException("Call to '$name' not found in ${this.definingClass}->${this.name}")
}

internal fun MutableMethod.indexOfFieldGet(name: String, from: Int = 0): Int {
    val list = implementation!!.instructions
    for (i in from until list.size) {
        val ins = list[i]
        if (ins.opcode != Opcode.IGET_OBJECT && ins.opcode != Opcode.SGET_OBJECT) continue
        val ref = (ins as? ReferenceInstruction)?.reference as? FieldReference ?: continue
        if (ref.name == name) return i
    }
    throw PatchException("Field '$name' not found in ${this.definingClass}->${this.name}")
}

internal fun MutableMethod.addAfterSuperCall(smali: String) {
    val idx = implementation!!.instructions.indexOfFirst { it.opcode == Opcode.INVOKE_SUPER }
    if (idx < 0) throw PatchException("invoke-super not found in ${this.definingClass}->${this.name}")
    addInstructions(idx + 1, smali)
}
