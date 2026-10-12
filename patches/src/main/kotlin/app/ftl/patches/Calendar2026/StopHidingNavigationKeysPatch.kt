package app.ftl.patches.calendar2026

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val stopHidingNavigationKeysPatch = bytecodePatch(
    name = "Stop hiding navigation keys",
    description = "Stops the app from hiding the system navigation bar."
) {
    compatibleWith(COMPATIBILITY_CALENDAR)

    execute {
        SplashHideSystemUiFingerprint.method.addInstruction(0, "return-void")
        MainHideNavigationBarFingerprint.method.addInstruction(0, "return-void")
    }
}
