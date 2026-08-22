package app.ftl.patches.removeadslite.ads.topon

import app.ftl.patches.removeadslite.util.filterMethods
import app.ftl.patches.removeadslite.util.findMutableMethodOf
import app.ftl.util.returnEarly
import app.morphe.patcher.patch.BytecodePatchContext

internal fun BytecodePatchContext.applyTopOnPatch() = buildList {
    val adMethods = setOf(
        "entryAdScenario",
        "load",
        "isAdReady",
        "controlShow",
        "show",
        // Only present in Interstitial, SplashAd, and customized BannerView
        "setNativeAdCustomRender",
        // Only present in BannerView and SplashAd
        "loadAd",
        // Only present in NativeAd
        "getNativeAd",
        "makeAdRequest",
        "renderAdContainer",
        "prepare",
        // Only present in InStream
        "setOnIMAEventListener",
    )

    setOf(
        "Lcom/anythink/banner/api/ATBannerView;",
        "Lcom/anythink/interstitial/api/ATInterstitial;",
        "Lcom/anythink/interstitial/api/ATInterstitialAutoAd;",
        "Lcom/anythink/nativead/api/ATNative;",
        "Lcom/anythink/rewardvideo/api/ATRewardVideoAd;",
        "Lcom/anythink/rewardvideo/api/ATRewardVideoAutoAd;",
        "Lcom/anythink/splashad/api/ATSplashAd;",
    ).forEach { definingClass ->
        runCatching {
            val mutableClass = mutableClassDefBy(definingClass)

            mutableClass
                .filterMethods { _, method -> method.name in adMethods }
                .forEach { method ->
                    mutableClass
                        .findMutableMethodOf(method)
                        .returnEarly()
                }
        }.also(::add)
    }

    setOf(
        AtSdkInitFingerprint,
        AtRewardedVideoAdLoadManagerShowFingerprint,
    ).forEach { fingerprint ->
        runCatching {
            fingerprint.method.returnEarly()
        }.also(::add)
    }
}
