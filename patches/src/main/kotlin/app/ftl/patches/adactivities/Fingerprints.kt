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

// Meta Audience Network's own manifest-declared Activity, unobfuscated
// (com.facebook.ads.AudienceNetworkActivity) - renders interstitial/rewarded.
internal object MetaAudienceNetworkActivityOnCreateFingerprint : Fingerprint(
    definingClass = "Lcom/facebook/ads/AudienceNetworkActivity;",
    name = "onCreate",
    returnType = "V",
    parameters = ON_CREATE_BUNDLE,
    filters = listOf(activityOnCreateSuperCall),
)

// Unity Ads' own manifest-declared Activity, unobfuscated
// (com.unity3d.services.ads.adunit.AdUnitActivity).
internal object UnityAdUnitActivityOnCreateFingerprint : Fingerprint(
    definingClass = "Lcom/unity3d/services/ads/adunit/AdUnitActivity;",
    name = "onCreate",
    returnType = "V",
    parameters = ON_CREATE_BUNDLE,
    filters = listOf(activityOnCreateSuperCall),
)

// Mintegral's own manifest-declared Activities, unobfuscated
// (com.mbridge.msdk.reward.player.MBRewardVideoActivity /
// com.mbridge.msdk.interstitial.view.MBInterstitialActivity).
internal object MintegralRewardVideoActivityOnCreateFingerprint : Fingerprint(
    definingClass = "Lcom/mbridge/msdk/reward/player/MBRewardVideoActivity;",
    name = "onCreate",
    returnType = "V",
    parameters = ON_CREATE_BUNDLE,
    filters = listOf(activityOnCreateSuperCall),
)

internal object MintegralInterstitialActivityOnCreateFingerprint : Fingerprint(
    definingClass = "Lcom/mbridge/msdk/interstitial/view/MBInterstitialActivity;",
    name = "onCreate",
    returnType = "V",
    parameters = ON_CREATE_BUNDLE,
    filters = listOf(activityOnCreateSuperCall),
)

// Pangle/Bytedance's own manifest-declared Activities, unobfuscated
// (com.bytedance.sdk.openadsdk.activity.TT*Activity). Express/non-express
// variants are separate declared classes, not the same class re-minified.
internal object PangleFullScreenVideoActivityOnCreateFingerprint : Fingerprint(
    definingClass = "Lcom/bytedance/sdk/openadsdk/activity/TTFullScreenVideoActivity;",
    name = "onCreate",
    returnType = "V",
    parameters = ON_CREATE_BUNDLE,
    filters = listOf(activityOnCreateSuperCall),
)

internal object PangleFullScreenExpressVideoActivityOnCreateFingerprint : Fingerprint(
    definingClass = "Lcom/bytedance/sdk/openadsdk/activity/TTFullScreenExpressVideoActivity;",
    name = "onCreate",
    returnType = "V",
    parameters = ON_CREATE_BUNDLE,
    filters = listOf(activityOnCreateSuperCall),
)

internal object PangleRewardVideoActivityOnCreateFingerprint : Fingerprint(
    definingClass = "Lcom/bytedance/sdk/openadsdk/activity/TTRewardVideoActivity;",
    name = "onCreate",
    returnType = "V",
    parameters = ON_CREATE_BUNDLE,
    filters = listOf(activityOnCreateSuperCall),
)

internal object PangleRewardExpressVideoActivityOnCreateFingerprint : Fingerprint(
    definingClass = "Lcom/bytedance/sdk/openadsdk/activity/TTRewardExpressVideoActivity;",
    name = "onCreate",
    returnType = "V",
    parameters = ON_CREATE_BUNDLE,
    filters = listOf(activityOnCreateSuperCall),
)

internal object PangleInterstitialActivityOnCreateFingerprint : Fingerprint(
    definingClass = "Lcom/bytedance/sdk/openadsdk/activity/TTInterstitialActivity;",
    name = "onCreate",
    returnType = "V",
    parameters = ON_CREATE_BUNDLE,
    filters = listOf(activityOnCreateSuperCall),
)

internal object PangleInterstitialExpressActivityOnCreateFingerprint : Fingerprint(
    definingClass = "Lcom/bytedance/sdk/openadsdk/activity/TTInterstitialExpressActivity;",
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
