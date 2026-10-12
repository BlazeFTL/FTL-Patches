package app.ftl.patches.calendar

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val disableRateUsPatch = bytecodePatch(
    name = "Disable rate us prompts",
    description = "Disables the rate us feedback screen and the periodic rate us prompt.",
) {
    compatibleWith(COMPATIBILITY_CALENDAR)

    execute {
        RateUsFeedbackOnCreateFingerprint.method.addAfterSuperCall(
            """
                invoke-virtual {p0}, Landroid/app/Activity;->finish()V
                return-void
            """
        )

        ShouldShowRateUsFingerprint.method.addInstructions(
            0,
            """
                const/4 v0, 0x0
                return v0
            """
        )
    }
}
