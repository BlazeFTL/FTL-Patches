package app.ftl.patches.removeadslite.util

import app.morphe.patcher.extensions.InstructionExtensions.instructionsOrNull
import app.morphe.patcher.util.proxy.mutableTypes.MutableClass
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.Reference
import com.android.tools.smali.dexlib2.util.MethodUtil

fun ClassDef.filterMethods(
    predicate: (ClassDef, Method) -> Boolean,
): List<Method> = buildList {
    val classDef = this@filterMethods
    methods.forEach { method ->
        method.instructionsOrNull ?: return@forEach
        if (predicate(classDef, method)) add(method)
    }
}

fun List<ClassDef>.filterMethods(
    predicate: (ClassDef, Method) -> Boolean,
): List<Method> = flatMap { it.filterMethods(predicate) }

fun MutableClass.findMutableMethodOf(method: Method) = methods.first {
    MethodUtil.methodSignaturesMatch(it, method)
}

inline fun <reified T : Reference> Instruction.getReference(): T? =
    (this as? ReferenceInstruction)?.reference as? T
