package app.ftl.patches.ads

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.fingerprint.Fingerprint
import app.morphe.patcher.patch.bytecodePatch

internal object AdViewLoadAdFingerprint : Fingerprint(
    name = "loadAd",
    definingClass = "Lcom/google/android/gms/ads/AdView;",
    returnType = "V",
)

internal object InterstitialAdLoadFingerprint : Fingerprint(
    name = "load",
    definingClass = "Lcom/google/android/gms/ads/interstitial/InterstitialAd;",
    returnType = "V",
)

internal object RewardedAdLoadFingerprint : Fingerprint(
    name = "load",
    definingClass = "Lcom/google/android/gms/ads/rewarded/RewardedAd;",
    returnType = "V",
)

internal object FacebookAdViewLoadAdFingerprint : Fingerprint(
    name = "loadAd",
    definingClass = "Lcom/facebook/ads/AdView;",
    returnType = "V",
)

internal object FacebookInterstitialLoadAdFingerprint : Fingerprint(
    name = "loadAd",
    definingClass = "Lcom/facebook/ads/InterstitialAd;",
    returnType = "V",
)

internal object AppLovinMaxInterstitialLoadFingerprint : Fingerprint(
    name = "loadAd",
    definingClass = "Lcom/applovin/mediation/MaxInterstitialAd;",
    returnType = "V",
)

internal object AppLovinMaxRewardedLoadFingerprint : Fingerprint(
    name = "loadAd",
    definingClass = "Lcom/applovin/mediation/MaxRewardedAd;",
    returnType = "V",
)

internal object UnityAdsLoadFingerprint : Fingerprint(
    name = "load",
    definingClass = "Lcom/unity3d/services/core/api/UnityAdsLoad;",
    returnType = "V",
)

internal object IronSourceLoadInterstitialFingerprint : Fingerprint(
    name = "loadInterstitial",
    definingClass = "Lcom/ironsource/mediationsdk/IronSource;",
    returnType = "V",
)

val removeAdsPatch = bytecodePatch(
    name = "Remove ads",
    description = "Neuters ad-load entry points for Google Mobile Ads, Meta Audience Network, AppLovin MAX, Unity Ads and IronSource.",
) {
    execute {
        AdViewLoadAdFingerprint.method.addInstructions(0, "return-void")
        InterstitialAdLoadFingerprint.method.addInstructions(0, "return-void")
        RewardedAdLoadFingerprint.method.addInstructions(0, "return-void")
        FacebookAdViewLoadAdFingerprint.method.addInstructions(0, "return-void")
        FacebookInterstitialLoadAdFingerprint.method.addInstructions(0, "return-void")
        AppLovinMaxInterstitialLoadFingerprint.method.addInstructions(0, "return-void")
        AppLovinMaxRewardedLoadFingerprint.method.addInstructions(0, "return-void")
        UnityAdsLoadFingerprint.method.addInstructions(0, "return-void")
        IronSourceLoadInterstitialFingerprint.method.addInstructions(0, "return-void")
    }
}
