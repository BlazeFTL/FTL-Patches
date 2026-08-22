package app.ftl.patches.removeadslite.ads.bigo

import app.ftl.patches.removeadslite.util.filterMethods
import app.ftl.patches.removeadslite.util.findMutableMethodOf
import app.ftl.util.returnEarly
import app.morphe.patcher.patch.BytecodePatchContext

internal fun BytecodePatchContext.applyBigoPatch() = buildList {
    val adMethods = setOf(
        "show",
        "showInAdContainer",
    )
    runCatching {
        val mutableClass = SplashAdFingerprint.classDef

        mutableClass
            .filterMethods { _, method -> method.name in adMethods }
            .forEach { method ->
                mutableClass
                    .findMutableMethodOf(method)
                    .returnEarly()
            }
    }.also(::add)

    setOf(
        BigoAdSdkInitializeFingerprint,
        AbstractAdLoaderLoadAdFingerprint,
    ).forEach { fingerprint ->
        runCatching {
            fingerprint.method.returnEarly()
        }.also(::add)
    }
}
