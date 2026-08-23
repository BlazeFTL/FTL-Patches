package app.ftl.patches.removeadslite.ads.mytarget

import app.ftl.patches.removeadslite.util.filterMethods
import app.ftl.patches.removeadslite.util.findMutableMethodOf
import app.ftl.util.returnEarly
import app.morphe.patcher.patch.BytecodePatchContext

internal fun BytecodePatchContext.applyMyTargetPatch() = buildList {
    val adLoaderAdMethods = setOf(
        "handleResult",
        "load",
        "loadFromBid",
        "show",
    )
    adLoaderFingerprints.forEach { fingerprint ->
        runCatching {
            val mutableClass = fingerprint.classDef

            mutableClass
                .filterMethods { _, method -> method.name in adLoaderAdMethods }
                .forEach { method ->
                    mutableClass
                        .findMutableMethodOf(method)
                        .returnEarly()
                }
        }.also(::add)
    }

    val promoCardRecyclerViewAdMethods = setOf(
        "renderCard",
        "setAdapter",
        "setPromoCardAdapter",
    )
    runCatching {
        val mutableClass = PromoCardRecyclerViewSetAdapterFingerprint.classDef

        mutableClass
            .filterMethods { _, method -> method.name in promoCardRecyclerViewAdMethods }
            .forEach { method ->
                mutableClass
                    .findMutableMethodOf(method)
                    .returnEarly()
            }
    }.also(::add)

    setOf(
        OnAdLoadExecutorFingerprint,
        MyTargetManagerInitSdkFingerprint,
    ).forEach { fingerprint ->
        runCatching {
            fingerprint.method.returnEarly()
        }.also(::add)
    }
}
