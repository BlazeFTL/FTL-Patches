package app.ftl.patches.removeadslite.ads.meta

import app.ftl.patches.removeadslite.util.filterMethods
import app.ftl.patches.removeadslite.util.findMutableMethodOf
import app.ftl.util.returnEarly
import app.morphe.patcher.patch.BytecodePatchContext

internal fun BytecodePatchContext.applyMetaAudienceNetworkPatch() = buildList {
    val adMethods = setOf(
        "loadAd",
        // Only present in interstitial ads
        "isAdLoaded",
        "show",
    )

    setOf(
        "Lcom/facebook/ads/AdView;",
        "Lcom/facebook/ads/NativeAdBase;",
        "Lcom/facebook/ads/RewardedInterstitialAd;",
        "Lcom/facebook/ads/RewardedVideoAd;",
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

    runCatching {
        InitializeFingerprint.method.returnEarly()
    }.also(::add)
}
