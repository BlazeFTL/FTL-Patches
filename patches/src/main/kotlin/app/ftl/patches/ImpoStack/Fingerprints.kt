package app.ftl.patches.impostack

import app.morphe.patcher.Fingerprint

/**
 * SecurityTracker.isPremium(Context)Z is unobfuscated in this version.
 * We can match it directly by its defining class and method name.
 * This avoids any issues with R8/D8 instruction reordering or string 
 * literal changes (the actual pref key here is "st_01").
 */
internal object IsPremiumFingerprint : Fingerprint(
    definingClass = "Lcom/bytedz/printflow/SecurityTracker;",
    name = "isPremium",
)
