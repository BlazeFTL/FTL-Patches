package your.patches.category

import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.booleanOption
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.debug.EndLocal
import com.android.tools.smali.dexlib2.iface.debug.LineNumber
import com.android.tools.smali.dexlib2.iface.debug.PrologueEnd
import com.android.tools.smali.dexlib2.iface.debug.RestartLocal
import com.android.tools.smali.dexlib2.iface.debug.SetSourceFile
import com.android.tools.smali.dexlib2.iface.debug.StartLocal

val removeDebugInfoPatch = bytecodePatch(
    name = "Remove debug info",
    description = "Removes debug info (.source, .line, .local, .prologue) from every class in all classes*.dex files.",
    default = true
) {
    val removeAll by booleanOption(key = "removeAll", default = true, title = "Remove all debug info")
    val removeSource by booleanOption(key = "removeSource", default = true, title = "Remove .source")
    val removeLine by booleanOption(key = "removeLine", default = true, title = "Remove .line")
    val removePrologue by booleanOption(key = "removePrologue", default = true, title = "Remove .prologue")
    val removeLocal by booleanOption(key = "removeLocal", default = true, title = "Remove .xxx local")

    execute {
        classDefForEach { classDef ->
            val mutableClass = mutableClassDefBy(classDef)

            mutableClass.methods.forEach { method ->
                val impl = method.implementation as? MutableMethodImplementation ?: return@forEach

                val locations = impl.instructions.map { it.location } +
                    impl.newLabelForIndex(impl.instructions.size).location

                locations.forEach { location ->
                    location.debugItems.removeIf { item ->
                        removeAll == true ||
                            (removeSource == true && item is SetSourceFile) ||
                            (removeLine == true && item is LineNumber) ||
                            (removePrologue == true && item is PrologueEnd) ||
                            (removeLocal == true && (item is StartLocal || item is EndLocal || item is RestartLocal))
                    }
                }
            }
        }
    }
}
