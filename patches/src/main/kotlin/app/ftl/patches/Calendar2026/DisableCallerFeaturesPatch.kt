package app.ftl.patches.calendar2026

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

@Suppress("unused")
val disableCallerFeaturesPatch = bytecodePatch(
    name = "Disable caller related features",
    description = "Disables caller ID features, the phone state receiver and the permission settings entry."
) {
    compatibleWith(COMPATIBILITY_CALENDAR)

    execute {
        listOf(
            CompleteCallFeatureFingerprint,
            MissedCallFeatureFingerprint,
            NoAnswerFeatureFingerprint
        ).forEach {
            it.method.addInstructions(
                0,
                """
                    const/4 v0, 0x0
                    return v0
                """
            )
        }

        CallerPhoneStateReceiverFingerprint.method.addInstruction(0, "return-void")

        SettingOnCreateFingerprint.let {
            val match = it.instructionMatches.first()
            val register = match.getInstruction<OneRegisterInstruction>().registerA
            it.method.addInstructions(
                match.index + 1,
                """
                    const/16 v0, 0x8
                    invoke-virtual {v$register, v0}, Landroid/view/View;->setVisibility(I)V
                """
            )
        }
    }
}
