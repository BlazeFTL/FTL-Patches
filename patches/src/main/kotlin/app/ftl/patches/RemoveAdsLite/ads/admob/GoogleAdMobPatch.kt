package app.ftl.patches.removeadslite.ads.admob

import app.ftl.util.returnEarly
import app.morphe.patcher.patch.BytecodePatchContext

internal fun BytecodePatchContext.applyGoogleAdMobPatch() = buildList {
    setOf(
        abstractAdViewAdapterFingerprints,
        mediationFingerprints,
    ).flatten().forEach { fingerprint ->
        runCatching {
            fingerprint.method.returnEarly()
        }.also(::add)
    }

    setOf(
        AdLoadFingerprint,
        AdLoaderLoadAdFingerprint,
        ShowFingerprint,
    ).forEach { fingerprint ->
        runCatching {
            fingerprint.matchAll().forEach { match ->
                match.method.returnEarly()
            }
        }.also(::add)
    }

    setOf(
        AppOpenAdLoadFingerprint,
        BaseAdViewFingerprint,
        BannerAdFingerprint,
    ).forEach { fingerprint ->
        runCatching {
            fingerprint.method.returnEarly()
        }.also(::add)
    }
}
