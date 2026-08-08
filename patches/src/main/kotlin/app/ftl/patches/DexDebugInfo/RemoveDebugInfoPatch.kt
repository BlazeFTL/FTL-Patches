package patches.misc.debug

import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.iface.debug.DebugItem
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation

val removeDebugInfoPatch = bytecodePatch(
    name = "Remove debug info",
    description = "Strips line numbers, local variable debug info, and source file names from bytecode, and removes BuildConfig classes.",
    default = true,
) {
    execute {
        classes.removeAll { it.type.endsWith("/BuildConfig;") }

        classes.toList().forEach { classDef ->
            val mutableClass = mutableClassDefBy(classDef)
            mutableClass.sourceFile = null

            mutableClass.methods.forEach { method ->
                val impl = method.implementation ?: return@forEach
                method.implementation = ImmutableMethodImplementation(
                    impl.registerCount,
                    impl.instructions,
                    impl.tryBlocks,
                    emptyList<DebugItem>(),
                )
            }
        }
    
}
}
