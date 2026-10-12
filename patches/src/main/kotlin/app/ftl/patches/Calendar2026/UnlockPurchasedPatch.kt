package app.ftl.patches.calendar2026

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val unlockPurchasedPatch = bytecodePatch(
    name = "Unlock purchased state",
    description = "Makes the app treat itself as purchased."
) {
    compatibleWith(COMPATIBILITY_CALENDAR)

    execute {
        IsPurchasedFingerprint.method.addInstructions(
            0,
            """
                const/4 v0, 0x1
                return v0
            """
        )
    }
}
