package app.ftl.patches.calendar

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.removeInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

@Suppress("unused")
val disableCallerFeaturesPatch = bytecodePatch(
    name = "Disable caller features",
    description = "Disables the after call screen and hides its entry in settings.",
) {
    compatibleWith(COMPATIBILITY_CALENDAR)

    execute {
        AfterCallOnCreateFingerprint.method.addAfterSuperCall(
            """
                invoke-virtual {p0}, Landroid/app/Activity;->finish()V
                return-void
            """
        )

        AfterCallNetworkStateChangedFingerprint.method.addInstructions(0, "return-void")

        SettingInitClickFingerprint.method.apply {
            val list = implementation!!.instructions
            val field = indexOfFieldGet("clAfterCallFeature")
            val click = indexOfCall("setOnClickListener", field)
            val call = list[click] as FiveRegisterInstruction
            val view = call.registerC
            val listener = call.registerD

            var created = click - 1
            while (created > field &&
                !(list[created].opcode == Opcode.NEW_INSTANCE &&
                    (list[created] as OneRegisterInstruction).registerA == listener)
            ) created--
            if (created <= field) throw PatchException("initClick: click listener new-instance not found")

            replaceInstruction(
                click,
                "invoke-virtual {v$view, v$listener}, Landroid/view/View;->setVisibility(I)V"
            )
            for (i in click - 1 downTo created + 1) removeInstruction(i)
            replaceInstruction(created, "const/16 v$listener, 0x8")
        }
    }
}
