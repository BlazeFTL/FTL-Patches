package your.patches.category

import com.android.tools.smali.dexlib2.iface.debug.EndLocal
import com.android.tools.smali.dexlib2.iface.debug.LineNumber
import com.android.tools.smali.dexlib2.iface.debug.PrologueEnd
import com.android.tools.smali.dexlib2.iface.debug.RestartLocal
import com.android.tools.smali.dexlib2.iface.debug.SetSourceFile
import com.android.tools.smali.dexlib2.iface.debug.StartLocal

val removeDebugInfoPatch = bytecodePatch(
    name = "Remove debug info",
    description = "Removes debug info (.source, .line, .local, .param, .prologue) from every class in all classes*.dex files.",
    default = true
) {
    val removeAll by booleanOption(name = "Remove all debug info", default = true)
    val removeSource by booleanOption(name = "Remove .source", default = true)
    val removeLine by booleanOption(name = "Remove .line", default = true)
    val removeParam by booleanOption(name = "Remove .param", default = true)
    val removePrologue by booleanOption(name = "Remove .prologue", default = true)
    val removeLocal by booleanOption(name = "Remove .xxx local", default = true)

    execute {
        classes.forEach { classDef ->
            val mutableClass = mutableClassDefBy(classDef)

            mutableClass.methods.forEach { method ->
                if (removeAll || removeParam) {
                    method.parameters.forEach { it.name = null }
                }

                val impl = method.implementation ?: return@forEach

                impl.debugItems.removeAll { item ->
                    removeAll ||
                        (removeSource && item is SetSourceFile) ||
                        (removeLine && item is LineNumber) ||
                        (removePrologue && item is PrologueEnd) ||
                        (removeLocal && (item is StartLocal || item is EndLocal || item is RestartLocal))
                }
            }
        }
    }
}
