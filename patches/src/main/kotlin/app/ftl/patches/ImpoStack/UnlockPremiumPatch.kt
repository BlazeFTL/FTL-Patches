package app.ftl.patches.impostack

import app.morphe.patcher.extensions.InstructionExtensions.instructionsOrNull
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

// We check for the exact SDK method shape, but allow both INVOKE_INTERFACE
// and INVOKE_INTERFACE_RANGE since R8 can emit either depending on register count.
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

        // Scan for the getBoolean call and its trailing MOVE_RESULT.
        // We do this dynamically instead of a static fingerprint filter
        // because getBoolean's exact instruction index varies per build.
        val moveResultIndex = instructions.indices
            .firstOrNull { i ->
                val instruction = instructions[i]
                val opcode = instruction.opcode
                (opcode == Opcode.INVOKE_INTERFACE || opcode == Opcode.INVOKE_INTERFACE_RANGE) &&
                    ((instruction as? ReferenceInstruction)?.reference as? MethodReference)
                        ?.isSharedPreferencesGetBooleanCall() == true &&
                    instructions.getOrNull(i + 1)?.opcode == Opcode.MOVE_RESULT
            }
            ?.plus(1)
            ?: return@execute

        // Replace the move-result with a hardcoded const/4 0x1
        val register = (instructions[moveResultIndex] as OneRegisterInstruction).registerA
        method.replaceInstruction(moveResultIndex, "const/4 v$register, 0x1")
    }
}
