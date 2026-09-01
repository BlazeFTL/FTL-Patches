package app.ftl.patches.wpsapppro

import app.morphe.patcher.extensions.InstructionExtensions.instructionsOrNull
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

// Pro is gated by checking where the app was installed from. Every call site does:
//   invoke-virtual {pm, pkg}, Landroid/content/pm/PackageManager;->getInstallerPackageName(Ljava/lang/String;)Ljava/lang/String;
//   move-result-object vX
// and later compares vX against "com.android.vending" (the Play Store's package name).
// The reference dump shows 4 call sites doing this - one in the app's own MainActivity,
// and 3 more in top-level obfuscated classes (Lbu;, Lgn;, Lvr;) that reshuffle every
// build. None of those obfuscated names are pinned; instead every method in the app is
// scanned for the one thing that never changes: the unobfuscated Android SDK call
// itself. Wherever it's found, the move-result-object right after it is replaced with
// a hardcoded "com.android.vending", so every installer-source check reports Play Store
// regardless of where the app was actually installed from.
private fun MethodReference.isGetInstallerPackageNameCall() =
    definingClass == "Landroid/content/pm/PackageManager;" &&
        name == "getInstallerPackageName" &&
        parameterTypes.singleOrNull() == "Ljava/lang/String;" &&
        returnType == "Ljava/lang/String;"

val removeLicenseVerificationPatch = bytecodePatch(
    name = "Remove License Verification",
    description = "Spoofs every PackageManager.getInstallerPackageName() check to report " +
        "\"com.android.vending\", so Pro features unlock regardless of install source.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_WPS_APP_PRO)

    execute {
        classDefForEach { classDef ->
            val hasTarget = classDef.methods.any { method ->
                val instructions = (method.instructionsOrNull ?: emptyList()).toList()
                instructions.indices.any { i ->
                    val instruction = instructions[i]
                    instruction.opcode == Opcode.INVOKE_VIRTUAL &&
                        ((instruction as? ReferenceInstruction)?.reference as? MethodReference)
                            ?.isGetInstallerPackageNameCall() == true &&
                        instructions.getOrNull(i + 1)?.opcode == Opcode.MOVE_RESULT_OBJECT
                }
            }
            if (!hasTarget) return@classDefForEach

            mutableClassDefBy(classDef).methods.forEach { method ->
                val instructions = method.instructionsOrNull?.toList() ?: return@forEach

                instructions.indices.forEach { i ->
                    val instruction = instructions[i]
                    if (instruction.opcode != Opcode.INVOKE_VIRTUAL) return@forEach
                    val reference = (instruction as? ReferenceInstruction)?.reference as? MethodReference
                        ?: return@forEach
                    if (!reference.isGetInstallerPackageNameCall()) return@forEach

                    val moveResult = instructions.getOrNull(i + 1) ?: return@forEach
                    if (moveResult.opcode != Opcode.MOVE_RESULT_OBJECT) return@forEach

                    val register = (moveResult as OneRegisterInstruction).registerA
                    method.replaceInstruction(i + 1, "const-string v$register, \"com.android.vending\"")
                }
            }
        }
    }
}
