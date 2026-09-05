package app.ftl.patches.bangladictionary

import app.ftl.util.returnEarly
import app.morphe.patcher.patch.bytecodePatch

val unlockProPatch = bytecodePatch(
    name = "Unlock Pro",
    description = "Forces the purchase check to always return true, unlocking Pro.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_BANGLA_DICTIONARY)

    execute {
        IsPurchasedFingerprint.method.returnEarly(true)
    }
}
