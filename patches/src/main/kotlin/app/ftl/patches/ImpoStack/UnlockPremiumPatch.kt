package app.ftl.patches.impostack

import app.ftl.util.returnEarly
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val unlockPremiumPatch = bytecodePatch(
    name = "Unlock Premium",
    description = "Forces SecurityTracker.isPremium() to always return true, so every feature gated by the \"st_01\" premium flag stays unlocked.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_IMPOSTACK)

    execute {
        // Instantly return true at the start of the method.
        // This bypasses the SharedPreferences lookup and any potential null checks,
        // and perfectly matches the repo's standard idiom for boolean gates.
        IsPremiumFingerprint.method.returnEarly(true)
    }
}
