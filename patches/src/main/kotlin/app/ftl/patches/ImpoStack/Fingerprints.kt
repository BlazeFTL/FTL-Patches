package app.ftl.patches.impostack

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.string

/**
 * Matches the isPremium(Context)Z gate.
 *
 * R8/D8 aggressively reorders instructions and strips modifiers like `final`.
 * My previous fingerprint failed because it required an exact sequence:
 * `string("sL_01")` -> `literal(0)` -> `methodCall`. If the compiler emitted
 * `const/4` before `const-string`, or stripped the `final` flag, the match failed.
 *
 * This simplified fingerprint relies only on structural anchors:
 * - Returns a primitive boolean ("Z")
 * - Takes a single Context parameter
 * - Contains the stable, unobfuscated preference key "sL_01"
 * This is unique enough to pinpoint the method and immune to compiler reordering.
 */
internal object IsPremiumFingerprint : Fingerprint(
    returnType = "Z",
    parameters = listOf("Landroid/content/Context;"),
    filters = listOf(
        string("sL_01"),
    ),
)
