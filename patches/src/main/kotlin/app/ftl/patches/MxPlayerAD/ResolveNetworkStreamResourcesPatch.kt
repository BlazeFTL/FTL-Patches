package app.ftl.patches.mxplayerad

import app.morphe.patcher.patch.ResourcePatchContext
import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

internal var networkStreamIconId: Int = 0
    private set
internal var networkStreamTitleId: Int = 0
    private set

// DOM lookup instead of regex-over-raw-text: immune to attribute order/whitespace
// differences between decoders/builds. The previous regex assumed a fixed
// type/name/id attribute order and broke even when the entry existed.
private fun ResourcePatchContext.resourceId(type: String, name: String): Int {
    document("res/values/public.xml").use { doc ->
        val nodes = doc.documentElement.getElementsByTagName("public")
        for (i in 0 until nodes.length) {
            val element = nodes.item(i) as? Element ?: continue
            if (element.getAttribute("type") == type && element.getAttribute("name") == name) {
                return element.getAttribute("id").substring(2).toLong(16).toInt()
            }
        }
    }
    error("$type/$name not found in public.xml - it may have been renamed or removed in this build.")
}

// name = null - dependency-only, like addMeTabMenuResourcePatch.
internal val resolveNetworkStreamResourcesPatch = resourcePatch(
    name = null,
    description = "Resolves the native icon/string resource ids reused for the Network Stream tile by name.",
) {
    compatibleWith(COMPATIBILITY_MX_PLAYER_AD)

    execute {
        networkStreamIconId = resourceId("drawable", "ic_me_tab_cloud_drive")
        networkStreamTitleId = resourceId("string", "network_stream")
    }
}
