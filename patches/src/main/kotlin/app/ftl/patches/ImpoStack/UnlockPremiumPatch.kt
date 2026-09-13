package app.ftl.patches.impostack

import app.morphe.patcher.extensions.InstructionExtensions.instructionsOrNull
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

// isPremium(Context)Z ends as:
//   const-string v0, "sL_01"
//   const/4 v1, 0x0
//   invoke-interface {p1, v0, v1}, Landroid/content/SharedPreferences;->getBoolean(Ljava/lang/String;Z)Z
//   move-result p1
//   return p1
// The move-result right after the getBoolean call is replaced with a hardcoded
// const/4 0x1 into the same register, so the stored preference is read but its
// value is discarded and isPremium always reports true - every premium gate in
// the app unlocks regardless of the "sL_01" flag. Only the stable SDK call
// shape (SharedPreferences.getBoolean) is scanned for inside the fingerprinted
// method, never an obfuscated name.
private fun MethodReference.isSharedPreferencesGetBooleanCall() =
    definingClass == "Landroid/content/SharedPreferences;" &&
        name == "getBoolean" &&
        parameterTypes.toList() == listOf("Ljava/lang/String;", "Z") &&
        returnType == "Z"

@Suppress("unused")
val unlockPremiumPatch = bytecodePatch(
    name = "Unlock Premium",
    description = "Forces SecurityTracker.isPremium() to always return true, so every feature gated by the \"sL_01\" premium flag stays unlocked.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_IMPOSTACK)

    execute {
        val method = IsPremiumFingerprint.method
        val instructions = method.instructionsOrNull?.toList() ?: return@execute

        val moveResultIndex = instructions.indices
            .firstOrNull { i ->
                val instruction = instructions[i]
                instruction.opcode == Opcode.INVOKE_INTERFACE &&
                    ((instruction as? ReferenceInstruction)?.reference as? MethodReference)
                        ?.isSharedPreferencesGetBooleanCall() == true &&
                    instructions.getOrNull(i + 1)?.opcode == Opcode.MOVE_RESULT
            }
            ?.plus(1)
            ?: return@execute

        val register = (instructions[moveResultIndex] as OneRegisterInstruction).registerA
        method.replaceInstruction(moveResultIndex, "const/4 v$register, 0x1")
    }
}
