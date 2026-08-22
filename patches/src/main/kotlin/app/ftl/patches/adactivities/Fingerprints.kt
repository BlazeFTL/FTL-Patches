package app.ftl.patches.adactivities

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall

private val ON_CREATE_BUNDLE = listOf("Landroid/os/Bundle;")

// Real, unobfuscated Activity;->onCreate(Bundle)V super call. Anchors every
// fingerprint below on the one instruction guaranteed to exist and be
// unobfuscated in each ad activity's onCreate, regardless of SDK/app build.
private val activityOnCreateSuperCall = methodCall(
    smali = "Landroid/app/Activity;->onCreate(Landroid/os/Bundle;)V",
)

// Each of these 4 is the SDK's own public, manifest-declared Activity class,
// not a synthetic/obfuscated name, so pinning definingClass is safe.

internal object BigoAdSplashOnCreateFingerprint : Fingerprint(
    definingClass = "Lsg/bigo/ads/ad/splash/AdSplashActivity;",
    name = "onCreate",
    returnType = "V",
    parameters = ON_CREATE_BUNDLE,
    filters = listOf(activityOnCreateSuperCall),
)

internal object AppLovinFullscreenOnCreateFingerprint : Fingerprint(
    definingClass = "Lcom/applovin/adview/AppLovinFullscreenActivity;",
    name = "onCreate",
    returnType = "V",
    parameters = ON_CREATE_BUNDLE,
    filters = listOf(activityOnCreateSuperCall),
)

internal object GoogleAdActivityOnCreateFingerprint : Fingerprint(
    definingClass = "Lcom/google/android/gms/ads/AdActivity;",
    name = "onCreate",
    returnType = "V",
    parameters = ON_CREATE_BUNDLE,
    filters = listOf(activityOnCreateSuperCall),
)

internal object InMobiAdActivityOnCreateFingerprint : Fingerprint(
    definingClass = "Lcom/inmobi/ads/rendering/InMobiAdActivity;",
    name = "onCreate",
    returnType = "V",
    parameters = ON_CREATE_BUNDLE,
    filters = listOf(activityOnCreateSuperCall),
)

// Vungle's activity is an obfuscated single-letter leaf class
// (was Lcom/vungle/ads/internal/ui/l; in the build this was checked against) -
// re-minified every SDK release, the exact "Lo/mg -> Lo/sg" trap. Never pin
// that name or definingClass. Instead: class must extend Activity directly,
// and onCreate must contain the "AdActivity" log-tag literal the SDK hardcodes
// (seen 4x unminified in the same onCreate body - a real string, not a symbol).
// Verified unique app-wide for the build this was checked against. If a future
// build throws an ambiguous/no-match error here, re-check with the compare
// tool and narrow further (e.g. add more of its onCreate string literals).
internal object VungleAdActivityOnCreateFingerprint : Fingerprint(
    name = "onCreate",
    returnType = "V",
    parameters = ON_CREATE_BUNDLE,
    strings = listOf("AdActivity"),
    filters = listOf(activityOnCreateSuperCall),
    custom = { _, classDef -> classDef.superclass == "Landroid/app/Activity;" },
)
