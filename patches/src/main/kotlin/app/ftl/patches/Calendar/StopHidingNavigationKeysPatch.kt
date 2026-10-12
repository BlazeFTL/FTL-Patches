package app.ftl.patches.calendar

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.removeInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode

@Suppress("unused")
val stopHidingNavigationKeysPatch = bytecodePatch(
    name = "Stop hiding navigation keys",
    description = "Keeps the system navigation keys visible.",
) {
    compatibleWith(COMPATIBILITY_CALENDAR)

    execute {
        HideNavBarOnlyFingerprint.method.addInstructions(
            0,
            """
                invoke-virtual {p0}, $BASE_ACT->showNavBarOnly()V
                return-void
            """
        )

        SplashOnCreateFingerprint.method.apply {
            val hide = indexOfCall("hideNavigationBar")
            if (implementation!!.instructions[hide - 1].opcode != Opcode.SGET_OBJECT) {
                throw PatchException("Splash onCreate: CommonClass.INSTANCE load not found before hideNavigationBar")
            }
            removeInstruction(hide)
            removeInstruction(hide - 1)
        }
    }
}
