package app.ftl.patches.removeadslite.ads.applovin

import app.ftl.patches.removeadslite.util.filterMethods
import app.ftl.patches.removeadslite.util.findMutableMethodOf
import app.ftl.util.returnEarly
import app.morphe.patcher.patch.BytecodePatchContext

internal fun BytecodePatchContext.applyAppLovinMaxPatch() = buildList {
    val appLovinSdkInitMethods = setOf(
        "initialize",
        "initializeSdk",
        "isInitialized",
    )
    runCatching {
        val mutableClass = mutableClassDefBy("Lcom/applovin/sdk/AppLovinSdk;")

        mutableClass
            .filterMethods { _, method -> method.name in appLovinSdkInitMethods }
            .forEach { method ->
                mutableClass
                    .findMutableMethodOf(method)
                    .returnEarly()
            }
    }.also(::add)

    val interstitialAdDialogClassDef =
        InterstitialAdDialogToStringFingerprint.originalClassDefOrNull

    val adMethods = setOf(
        "isReady",
        "loadAd",
        "render",
        "show",
        "showAd",
        // Only present in MediationServiceImpl
        "loadThirdPartyMediatedAd",
        // Only present in MediationServiceImpl and MaxFullscreenAdImpl
        "showFullscreenAd",
        // Only present in MaxAdView
        "startAutoRefresh",
        "stopAutoRefresh",
        // Only present in MaxNativeAdView
        "renderCustomNativeAdView",
        // Only present in AppLovinInterstitialAdDialog
        "showAndRender",
    )
    setOfNotNull(
        interstitialAdDialogClassDef?.type,
        "Lcom/applovin/impl/mediation/MediationServiceImpl;",
        "Lcom/applovin/impl/mediation/ads/MaxAdViewImpl;",
        "Lcom/applovin/impl/mediation/ads/MaxFullscreenAdImpl;",
        "Lcom/applovin/impl/mediation/ads/MaxNativeAdLoaderImpl;",
        "Lcom/applovin/mediation/ads/MaxAdView;",
        "Lcom/applovin/mediation/ads/MaxAppOpenAd;",
        "Lcom/applovin/mediation/ads/MaxInterstitialAd;",
        "Lcom/applovin/mediation/ads/MaxRewardedAd;",
        "Lcom/applovin/mediation/nativeAds/MaxNativeAdLoader;",
        "Lcom/applovin/mediation/nativeAds/MaxNativeAdView;",
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
}
