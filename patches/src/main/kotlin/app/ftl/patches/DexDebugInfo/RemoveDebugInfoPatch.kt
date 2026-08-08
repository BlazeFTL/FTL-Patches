package app.morphe.patches.all.misc.debuginfo

import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.MethodImplementation
import com.android.tools.smali.dexlib2.iface.MethodParameter
import com.android.tools.smali.dexlib2.iface.debug.DebugItem

val removeDebugInfoPatch = bytecodePatch(
    name = "Remove debug info",
    description = "Removes line numbers, local variable names, parameter names and source file references from every class.",
    default = true
) {
    execute {
        classes.toList().forEach { classDef ->
            val needsSourceFileStrip = classDef.sourceFile != null
            val needsMethodStrip = classDef.methods.any { method ->
                val impl = method.implementation
                (impl != null && impl.debugItems.iterator().hasNext()) ||
                    method.parameters.any { it.name != null }
            }
            if (!needsSourceFileStrip && !needsMethodStrip) return@forEach

            val mutableClass = mutableClassDefBy(classDef)
            if (needsSourceFileStrip) mutableClass.sourceFile = null

            val cleaned = mutableClass.methods.map { method ->
                val impl = method.implementation ?: return@map method

                val hasDebugItems = impl.debugItems.iterator().hasNext()
                val hasNamedParams = method.parameters.any { it.name != null }
                if (!hasDebugItems && !hasNamedParams) return@map method

                val strippedImpl = object : MethodImplementation by impl {
                    override fun getDebugItems(): Iterable<DebugItem> = emptyList()
                }

                object : Method by method {
                    override fun getImplementation(): MethodImplementation = strippedImpl
                    override fun getParameters(): List<MethodParameter> =
                        method.parameters.map { param ->
                            object : MethodParameter by param {
                                override fun getName(): String? = null
                            }
                        }
                }
            }

            mutableClass.methods.clear()
            mutableClass.methods.addAll(cleaned)
        }
    }
}
