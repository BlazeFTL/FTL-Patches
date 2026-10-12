package app.ftl.patches.calendar

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

@Suppress("unused")
val unlockPremiumPatch = bytecodePatch(
    name = "Unlock premium",
    description = "Unlocks premium features and hides the premium entry in settings.",
) {
    compatibleWith(COMPATIBILITY_CALENDAR)

    execute {
        IsPremiumUserFingerprint.method.apply {
            val list = implementation!!.instructions
            var patched = 0
            for (i in 0 until list.size - 1) {
                val a = list[i]
                val b = list[i + 1]
                if (a.opcode != Opcode.CONST_4 || b.opcode != Opcode.RETURN) continue
                if ((a as NarrowLiteralInstruction).narrowLiteral != 0) continue
                val reg = (a as OneRegisterInstruction).registerA
                if ((b as OneRegisterInstruction).registerA != reg) continue
                replaceInstruction(i, "const/4 v$reg, 0x1")
                patched++
            }
            if (patched == 0) throw PatchException("isPremiumUser: const 0 + return not found")
        }

        GetActiveSubscriptionFingerprint.method.addInstructions(
            0,
            """
                const-string v0, "no_ads"
                return-object v0
            """
        )

        SettingOnResumeFingerprint.method.apply {
            val list = implementation!!.instructions
            val premiumCall = indexOfCall("isPremiumUser")
            val adContainer = indexOfFieldGet("llAdContainer", premiumCall)
            val setVisibility = indexOfCall("setVisibility", adContainer)
            val call = list[setVisibility] as FiveRegisterInstruction
            val view = call.registerC
            val flag = call.registerD

            addInstructions(
                setVisibility + 1,
                """
                    invoke-direct {p0}, $SETTING_ACTIVITY->getBinding()$SETTING_BINDING
                    move-result-object v$view
                    iget-object v$view, v$view, $SETTING_BINDING->clPremium:Landroidx/constraintlayout/widget/ConstraintLayout;
                    invoke-virtual {v$view, v$flag}, Landroid/view/View;->setVisibility(I)V
                """
            )
        }
    }
}
