package app.ftl.patches.calendar

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.removeInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

@Suppress("unused")
val boostSplashPatch = bytecodePatch(
    name = "Boost splash screen",
    description = "Skips splash ads and the consent request and opens the app immediately.",
) {
    compatibleWith(COMPATIBILITY_CALENDAR)

    execute {
        SplashConsentCallbackFingerprint.method.addInstructions(
            0,
            """
                invoke-direct {p0}, $SPLASH_ACTIVITY->proceed()V
                return-void
            """
        )

        SplashOnCreateFingerprint.method.apply {
            val initService = indexOfCall("InitStartService", owner = SPLASH_ACTIVITY)
            if (implementation!!.instructions[initService + 1].opcode != Opcode.RETURN_VOID) {
                throw PatchException("Splash onCreate: return-void not found after InitStartService")
            }
            addInstructions(initService + 1, "invoke-direct {p0}, $SPLASH_ACTIVITY->proceed()V")

            val list = implementation!!.instructions
            val consent = indexOfCall("requestConsent")
            val callbackReg = (list[consent] as FiveRegisterInstruction).registerD
            val init = consent - 1
            val initRef = (list[init] as? ReferenceInstruction)?.reference as? MethodReference
            if (initRef == null || initRef.name != "<init>") {
                throw PatchException("Splash onCreate: consent callback constructor not found")
            }
            var created = init - 1
            while (created >= 0 &&
                !(list[created].opcode == Opcode.NEW_INSTANCE &&
                    (list[created] as OneRegisterInstruction).registerA == callbackReg)
            ) created--
            if (created < 0) throw PatchException("Splash onCreate: consent callback new-instance not found")

            removeInstruction(consent)
            removeInstruction(init)
            removeInstruction(created)
        }
    }
}
