package app.morphe.patches.all.misc.debugging

import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethodParameter
import java.lang.reflect.Field

private val parameterNameField: Field by lazy {
    MutableMethodParameter::class.java.getDeclaredField("name").apply {
        isAccessible = true
    }
}

val removeAllDexDebugInfoPatch = bytecodePatch(
    name = "Remove all DEX debug info",
    description = "Removes debug information from every DEX method.",
    default = true,
) {
    execute {
        classes.toList().forEach { classDef ->
            val mutableClass = mutableClassDefBy(classDef)

            mutableClass.methods.toList().forEach { method ->
                method.implementation?.let { implementation ->
                    val iterator = implementation.debugItems.iterator()
                    while (iterator.hasNext()) {
                        iterator.next()
                        (iterator as MutableIterator<*>).remove()
                    }
                }

                method.parameters.forEach { parameter ->
                    parameterNameField.set(parameter, null)
                }
            }
        }
    }
}
