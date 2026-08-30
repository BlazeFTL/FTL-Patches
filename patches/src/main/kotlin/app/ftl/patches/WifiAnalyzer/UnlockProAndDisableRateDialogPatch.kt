package app.ftl.patches.wifianalyzer

import app.ftl.util.returnEarly
import app.morphe.patcher.extensions.InstructionExtensions.removeInstruction
import app.morphe.patcher.patch.bytecodePatch

val unlockProAndDisableRateDialogPatch = bytecodePatch(
    name = "Unlock Pro & Disable Rate Dialog",
    description = "Forces the premium check to always return true, unlocking every Pro " +
        "feature, and stops the in-app \"rate us\" dialog from ever triggering.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_WIFI_ANALYZER_PRO)

    execute {
        // isPremiumCheck() gates every Pro feature app-wide (15+ call sites in
        // MainActivity alone) - forcing it true alone unlocks Pro everywhere.
        IsPremiumCheckFingerprint.method.returnEarly(true)

        // Drop the one-shot gate (`if (!alreadyTriggered) trigger()`) so the
        // unconditional `goto` right after it always runs instead, regardless
        // of the underlying flag's value.
        RateDialogTriggerFingerprint.let { fingerprint ->
            fingerprint.method.removeInstruction(fingerprint.instructionMatches[0].index)
        }
    }
}
