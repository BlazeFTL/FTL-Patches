package app.ftl.patches.wpswpatester

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructionsOrNull
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.value.BooleanEncodedValue

/*
 * The Pro flag is a plain static boolean on App, read/written directly via
 * sget-boolean/sput-boolean from 12+ unrelated classes throughout the dex -
 * there's no single getter/setter method to fingerprint. The field's own
 * name (currently "e") is a single-letter identifier reassigned by the
 * shrinker every build, so it is never hardcoded (same rule as an obfuscated
 * class/method name). Instead the field is re-resolved each build from
 * characteristics that are unlikely to change:
 *   - declared directly on APP_CLASS (the app's own real class - safe to pin)
 *   - type Z, mutable (not final), not volatile, explicit initial value false
 *   - among fields sharing those characteristics, the one with the most
 *     total sget/sput references app-wide (this build: 34 vs. 13 and 12 for
 *     the next two candidates) - the dominant Pro/feature gate is read far
 *     more often than the two flags only set alongside it.
 *
 * Once resolved, every sget-boolean/sput-boolean of that field anywhere in
 * the dex is forced to 0x1: a get is followed immediately by a forcing
 * const, a put is preceded by one so the value actually stored is always
 * true regardless of what the app computed.
 */

private data class FlagHit(val insertAt: Int, val register: Int)

private fun isFlagAccess(instruction: Instruction, fieldName: String): Boolean {
    if (instruction.opcode != Opcode.SGET_BOOLEAN && instruction.opcode != Opcode.SPUT_BOOLEAN) return false
    val ref = (instruction as? ReferenceInstruction)?.reference as? FieldReference ?: return false
    return ref.name == fieldName && ref.definingClass == APP_CLASS
}

val unlockProPatch = bytecodePatch(
    name = "Unlock Pro",
    description = "Forces the Pro flag true at every read and write site in the dex, unlocking Pro.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_WPS_WPA_TESTER)

    execute {
        val appClassDef = classDefBy(APP_CLASS)

        val candidateFields = appClassDef.staticFields.filter { f ->
            f.type == "Z" &&
                !AccessFlags.FINAL.isSet(f.accessFlags) &&
                !AccessFlags.VOLATILE.isSet(f.accessFlags) &&
                (f.initialValue as? BooleanEncodedValue)?.value == false
        }
        if (candidateFields.isEmpty()) {
            throw PatchException("No candidate Pro-flag field found on $APP_CLASS")
        }

        val referenceCounts = candidateFields.associateWith { 0 }.toMutableMap()
        classDefForEach { classDef ->
            classDef.methods.forEach { method ->
                method.instructionsOrNull?.forEach { instruction ->
                    if (instruction.opcode != Opcode.SGET_BOOLEAN && instruction.opcode != Opcode.SPUT_BOOLEAN) {
                        return@forEach
                    }
                    val ref = (instruction as? ReferenceInstruction)?.reference as? FieldReference ?: return@forEach
                    if (ref.definingClass != APP_CLASS) return@forEach

                    candidateFields.firstOrNull { it.name == ref.name }?.let { match ->
                        referenceCounts[match] = referenceCounts.getValue(match) + 1
                    }
                }
            }
        }

        val targetFieldName = referenceCounts.maxByOrNull { it.value }?.key?.name
            ?: throw PatchException("Could not determine which Pro-flag field is referenced most on $APP_CLASS")

        classDefForEach { classDef ->
            val hasFlagAccess = classDef.methods.any { method ->
                method.instructionsOrNull?.any { isFlagAccess(it, targetFieldName) } == true
            }
            if (!hasFlagAccess) return@classDefForEach

            val mutableClass = mutableClassDefBy(classDef)
            mutableClass.methods.forEach { method ->
                val instructions = method.instructionsOrNull?.toList() ?: return@forEach

                val hits = mutableListOf<FlagHit>()
                instructions.forEachIndexed { index, instruction ->
                    if (!isFlagAccess(instruction, targetFieldName)) return@forEachIndexed
                    val register = (instruction as OneRegisterInstruction).registerA

                    when (instruction.opcode) {
                        // Read: force the register right after it's loaded.
                        Opcode.SGET_BOOLEAN -> hits += FlagHit(index + 1, register)
                        // Write: force the register right before it's stored.
                        Opcode.SPUT_BOOLEAN -> hits += FlagHit(index, register)
                        else -> Unit
                    }
                }

                // Highest index first so earlier insertion points stay valid.
                hits.sortedByDescending { it.insertAt }.forEach { hit ->
                    // const/16, not const/4: hit.register can exceed 15 in
                    // methods with large register counts (seen up to v19 here).
                    method.addInstruction(hit.insertAt, "const/16 v${hit.register}, 0x1")
                }
            }
        }
    }
}
