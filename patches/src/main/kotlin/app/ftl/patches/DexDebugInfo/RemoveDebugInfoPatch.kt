package app.ftl.patches.DexDebugInfo

import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableClass
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation

private fun clearDebugItems(implementation: MutableMethodImplementation) {
    val instructionListField =
        MutableMethodImplementation::class.java.getDeclaredField("instructionList").apply {
            isAccessible = true
        }

    @Suppress("UNCHECKED_CAST")
    val locations = instructionListField.get(implementation) as List<Any>

    for (location in locations) {
        val getDebugItems = location.javaClass.getMethod("getDebugItems")
        @Suppress("UNCHECKED_CAST")
        val debugItems = getDebugItems.invoke(location) as MutableList<Any>
        debugItems.clear()
    }
}

private fun clearParameterNames(method: MutableMethod) {
    val parameters = method.parameters
    for (parameter in parameters) {
        val nameField = parameter.javaClass.getDeclaredField("name").apply {
            isAccessible = true
        }
        nameField.set(parameter, null)
    }
}

private fun sameMethod(a: MutableMethod, b: com.android.tools.smali.dexlib2.iface.Method): Boolean =
    a.name == b.name &&
        a.returnType == b.returnType &&
        a.parameterTypes.map(CharSequence::toString) ==
            b.parameterTypes.map(CharSequence::toString)

val removeDebugInfoPatch = bytecodePatch(
    name = "Remove all DEX debug info",
    description = "Removes source files, method debug items, and parameter names from every DEX class.",
    default = true,
) {
    execute {
        classDefForEach { originalClass ->
            val methodsNeedingChanges = originalClass.methods.filter { method ->
                val hasParameterNames = method.parameters.any { it.name != null }
                val hasDebugItems =
                    method.implementation?.debugItems?.iterator()?.hasNext() == true

                hasParameterNames || hasDebugItems
            }

            val needsSourceRemoval = originalClass.sourceFile != null

            if (!needsSourceRemoval && methodsNeedingChanges.isEmpty()) {
                return@classDefForEach
            }

            val mutableClass = mutableClassDefBy(originalClass)

            if (needsSourceRemoval) {
                mutableClass.setSourceFile(null)
            }

            if (methodsNeedingChanges.isEmpty()) {
                return@classDefForEach
            }

            mutableClass.methods.forEach { mutableMethod ->
                val originalMethod = methodsNeedingChanges.firstOrNull {
                    sameMethod(mutableMethod, it)
                } ?: return@forEach

                if (originalMethod.parameters.any { it.name != null }) {
                    clearParameterNames(mutableMethod)
                }

                if (originalMethod.implementation?.debugItems?.iterator()?.hasNext() == true) {
                    mutableMethod.implementation?.let(::clearDebugItems)
                }
            }
        }
    }
}
