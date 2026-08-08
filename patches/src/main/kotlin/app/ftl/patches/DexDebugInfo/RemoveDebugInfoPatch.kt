package app.ftl.patches.DexDebugInfo

import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation

@Suppress("unused")
val removeDebugInfoPatch = bytecodePatch(
    name = "Remove all DEX debug info",
    description = "Removes debug information from every class and method in every processed DEX file.",
    default = true,
) {
    execute {
        classDefForEach { classDef ->
            val mutableClass = mutableClassDefBy(classDef)

            mutableClass.setSourceFile(null)

            mutableClass.methods.forEach { method ->
                method.parameters.forEach { parameter ->
                    runCatching {
                        val field = parameter.javaClass.getDeclaredField("name")
                        field.isAccessible = true
                        field.set(parameter, null)
                    }
                }

                val implementation = method.implementation ?: return@forEach

                if (implementation is MutableMethodImplementation) {
                    runCatching {
                        val instructionListField =
                            MutableMethodImplementation::class.java.getDeclaredField("instructionList")
                        instructionListField.isAccessible = true

                        @Suppress("UNCHECKED_CAST")
                        val locations =
                            instructionListField.get(implementation) as MutableList<Any>

                        locations.forEach { location ->
                            val debugItemsMethod =
                                location.javaClass.getMethod("getDebugItems")

                            @Suppress("UNCHECKED_CAST")
                            val debugItems =
                                debugItemsMethod.invoke(location) as MutableList<Any>

                            debugItems.clear()
                        }
                    }
                }
            }
        }
    }
}
