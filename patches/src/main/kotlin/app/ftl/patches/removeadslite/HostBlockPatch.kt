package app.ftl.patches.removeadslite

import app.ftl.patches.removeadslite.hosts.HostsBlocker
import app.morphe.patcher.extensions.InstructionExtensions.instructionsOrNull
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.encodedValue.MutableStringEncodedValue
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.iface.value.StringEncodedValue
import java.util.logging.Logger

const val DEFAULT_REDIRECT_IP = "0.0.0.0"

// Packaged into the patches jar from patches/src/main/resources/removeadslite/hosts.txt
// at build time - no file picker, no path option, nothing to configure.
private const val HOSTS_RESOURCE_PATH = "removeadslite/hosts.txt"

// Loaded once and kept for the process lifetime (not per-run like adobo's
// version) since the list is fixed/bundled rather than user-swappable -
// avoids re-parsing a multi-MB list on every APK in a batch patching run.
private val hostsBlocker: HostsBlocker by lazy {
    val stream = object {}.javaClass.classLoader.getResourceAsStream(HOSTS_RESOURCE_PATH)
        ?: error("Bundled hosts list not found on classpath at $HOSTS_RESOURCE_PATH")
    HostsBlocker.fromString(stream.bufferedReader().use { it.readText() })
}

// name = null: pulled in via RemoveAdsLitePatch's dependsOn, not its own toggle.
// name=null patches don't surface their own options in expert mode (no gear
// icon on the parent entry) - so this takes redirectIpProvider as a lambda
// instead. RemoveAdsLitePatch declares the actual option and passes it in,
// same split as adobo's baseHostsBlockerPatch/hostsBlockerPatch.
internal fun hostBlockPatch(redirectIpProvider: () -> String) = bytecodePatch(
    name = null,
    description = "Blocks ads, trackers, and analytics using a hosts list bundled with the " +
        "patch. Only the matched host substring is redirected - the rest of the string " +
        "(scheme, path, query) is left intact - so control flow is untouched.",
) {
    val logger = Logger.getLogger(this::class.java.name)

    execute {
        val redirectIp = redirectIpProvider()
        val blockedHosts = mutableSetOf<String>()

        classDefForEach { classDef ->
            var needsPatch = false

            for (field in classDef.fields) {
                val value = (field.initialValue as? StringEncodedValue)?.value ?: continue
                if (hostsBlocker.isBlocked(value)) {
                    needsPatch = true
                    break
                }
            }

            if (!needsPatch) {
                outer@ for (method in classDef.methods) {
                    val instructions = method.instructionsOrNull ?: continue
                    for (instruction in instructions) {
                        if (instruction.opcode != Opcode.CONST_STRING) continue
                        val value = ((instruction as ReferenceInstruction).reference as StringReference).string
                        if (hostsBlocker.isBlocked(value)) {
                            needsPatch = true
                            break@outer
                        }
                    }
                }
            }

            if (!needsPatch) return@classDefForEach

            val mutableClass = mutableClassDefBy(classDef)

            mutableClass.fields.forEach { field ->
                val encoded = field.initialValue as? MutableStringEncodedValue ?: return@forEach
                val value = encoded.value
                if (!hostsBlocker.isBlocked(value)) return@forEach

                val blockedHost = HostsBlocker.extractHost(value) ?: return@forEach
                blockedHosts.add(blockedHost)
                encoded.setValue(value.replace(blockedHost, redirectIp, ignoreCase = true))
            }

            mutableClass.methods.forEach { method ->
                val instructions = method.instructionsOrNull?.toList() ?: return@forEach
                instructions.forEachIndexed { index, instruction ->
                    if (instruction.opcode != Opcode.CONST_STRING) return@forEachIndexed
                    val value = ((instruction as ReferenceInstruction).reference as StringReference).string
                    if (!hostsBlocker.isBlocked(value)) return@forEachIndexed

                    val blockedHost = HostsBlocker.extractHost(value) ?: return@forEachIndexed
                    blockedHosts.add(blockedHost)
                    val updated = value.replace(blockedHost, redirectIp, ignoreCase = true)

                    val register = (instruction as OneRegisterInstruction).registerA
                    method.replaceInstruction(index, "const-string v$register, \"$updated\"")
                }
            }
        }

        blockedHosts.forEach { host -> logger.info("[Found] $host blocked.") }
    }
}
