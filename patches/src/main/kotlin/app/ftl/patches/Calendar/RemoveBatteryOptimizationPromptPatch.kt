package app.ftl.patches.calendar

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val removeBatteryOptimizationPromptPatch = bytecodePatch(
    name = "Remove battery optimization prompt",
    description = "Removes the battery optimization request on the dashboard.",
) {
    compatibleWith(COMPATIBILITY_CALENDAR)

    execute {
        CheckBatteryOptimizationFingerprint.method.addInstructions(0, "return-void")
    }
}
