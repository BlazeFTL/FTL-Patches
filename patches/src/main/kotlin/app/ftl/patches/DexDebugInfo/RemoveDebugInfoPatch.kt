package app.ftl.patches.DexDebugInfo

import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation

private fun clearDebugItems(implementation: MutableMethodImplementation) {
    val instructionListField =
        MutableMethodImplementation::class.java.getDeclaredField("instructionList")
    instructionListField.isAccessible = true

    val locations = instructionListField.get(implementation) as? Iterable<*>
        ?: return

    for (location in locations) {
        if (location == null) continue

        val getDebugItems = location.javaClass.getMethod("getDebugItems")
        @Suppress("UNCHECKED_CAST")
        val debugItems =
            getDebugItems.invoke(location) as? MutableCollection<Any>
                ?: continue

        debugItems.clear()
    }
}

private fun clearParameterNames(method: Any) {
    val parameters = runCatching {
        method.javaClass.getMethod("getParameters").invoke(method) as? Iterable<*>
    }.getOrNull() ?: return

    for (parameter in parameters) {
        if (parameter == null) continue

        runCatching {
            val field = parameter.javaClass.getDeclaredField("name")
            field.isAccessible = true
            field.set(parameter, null)
        }
    }
}

val removeDebugInfoPatch = bytecodePatch(
    name = "Remove all DEX debug info",
    description = "Removes debug information from every class and method in every processed DEX file.",
    default = true,
) {
    execute {
        classDefForEach { classDef ->
            val mutableClass = mutableClassDefBy(classDef)

            runCatching {
                mutableClass.setSourceFile(null)
            }

            mutableClass.methods.forEach { method ->
                clearParameterNames(method)

                val implementation = method.implementation
                if (implementation is MutableMethodImplementation) {
                    clearDebugItems(implementation)
                }
            }
        }
    }
}
