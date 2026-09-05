package app.ftl.patches.removeadslite.ads.pangle

import app.ftl.util.returnEarly
import app.morphe.patcher.patch.BytecodePatchContext

internal fun BytecodePatchContext.applyPanglePatch() = buildList {
    runCatching {
        SdkLoadAdFactoryFingerprint.method.returnEarly()
    }.also(::add)

    setOf(
        LoadAdFingerprint,
        ShowFingerprint,
    ).forEach { fingerprint ->
        runCatching {
            fingerprint.matchAll().forEach { match ->
                match.method.returnEarly()
            }
        }.also(::add)
    }
}
