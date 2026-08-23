package app.ftl.patches.removeadslite.ads.vungle

import app.ftl.patches.removeadslite.util.filterMethods
import app.ftl.patches.removeadslite.util.findMutableMethodOf
import app.ftl.util.returnEarly
import app.morphe.patcher.patch.BytecodePatchContext

internal fun BytecodePatchContext.applyVunglePatch() = buildList {
    val adMethods = setOf(
        "load",
        "loadAd",
        "canPlayAd",
    )

    setOf(
        "Lcom/vungle/ads/internal/AdInternal;",
        "Lcom/vungle/ads/BaseFullscreenAd;",
        "Lcom/vungle/ads/BaseAd;",
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
