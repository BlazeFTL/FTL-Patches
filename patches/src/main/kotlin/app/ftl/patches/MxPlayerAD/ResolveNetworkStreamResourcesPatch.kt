package app.ftl.patches.mxplayerad

import app.morphe.patcher.patch.ResourcePatchContext
import app.morphe.patcher.patch.resourcePatch

internal var networkStreamIconId: Int = 0
    private set
internal var networkStreamTitleId: Int = 0
    private set

// Reads the currently-patched APK's own decompiled public.xml rather than trusting a
// number pulled from one reference build - native resource ids reshuffle across MX
// Player versions the same way the injected Me-tab ones did (see
// DisableBottomBarAndAddMeTabPatch's history), just driven by MX Player's own resource
// changes instead of ours. Regex is exact-match on the closing quote after `name`, so
// e.g. "ic_me_tab_cloud_drive" can't accidentally match a longer similarly-prefixed name.
private fun ResourcePatchContext.resourceId(type: String, name: String): Int {
    val text = get("res/values/public.xml", false).readText()
    val match = Regex("""<public type="$type" name="$name" id="(0x[0-9a-fA-F]+)"""").find(text)
        ?: error("$type/$name not found in public.xml - it may have been renamed or removed in this build.")
    return match.groupValues[1].substring(2).toLong(16).toInt()
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
