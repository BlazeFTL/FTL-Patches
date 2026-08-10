package app.ftl.util

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation

fun MutableMethod.ensureRegisters(needed: Int) {
    val impl = implementation ?: return
    if (impl.registerCount >= needed) return
    val field = MutableMethodImplementation::class.java.declaredFields
        .firstOrNull { it.type == Int::class.javaPrimitiveType }
        ?.apply { isAccessible = true }
        ?: throw PatchException(
            "MutableMethodImplementation has no int field (registerCount). dexlib2 internal layout changed?",
        )
    field.setInt(impl, needed)

}
