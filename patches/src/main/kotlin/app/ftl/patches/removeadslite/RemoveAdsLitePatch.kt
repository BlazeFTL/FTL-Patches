package app.ftl.patches.removeadslite

import app.ftl.patches.ads.forceHideAdViewsPatch
import app.ftl.patches.ads.hideAdLayoutsPatch
import app.ftl.patches.removeadslite.ads.admob.applyGoogleAdMobPatch
import app.ftl.patches.removeadslite.ads.applovin.applyAppLovinMaxPatch
import app.ftl.patches.removeadslite.ads.bigo.applyBigoPatch
import app.ftl.patches.removeadslite.ads.meta.applyMetaAudienceNetworkPatch
import app.ftl.patches.removeadslite.ads.mintegral.applyMintegralPatch
import app.ftl.patches.removeadslite.ads.mytarget.applyMyTargetPatch
import app.ftl.patches.removeadslite.ads.pangle.applyPanglePatch
import app.ftl.patches.removeadslite.ads.topon.applyTopOnPatch
import app.ftl.patches.removeadslite.ads.unity.applyUnityPatch
import app.ftl.patches.removeadslite.ads.vungle.applyVunglePatch
import app.ftl.patches.removeadslite.ads.yandex.applyYandexPatch
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.stringOption
import java.util.logging.Logger

// Per-SDK entry-point stubbing (adobo's "Disable mobile ads") + hardcoded
// host/URL pattern neutralization (adobo's "Block ads, trackers, and
// analytics") merged into one no-config patch. Unlike RemoveAdsPatch, nothing
// here strips or redirects invoke calls at arbitrary call sites - each SDK
// method is short-circuited inside its own class to a safe default (false/
// null/void), so the SDK's own callback handling deals with the "no ad"
// result the way it was built to. That's what avoids the splash-screen hangs
// RemoveAdsPatch can cause.
val removeAdsLitePatch = bytecodePatch(
    name = "Remove Ads Lite",
    description = "Based On (Adobo's Block Ads+Mobile Ads) Disables known ad SDK entry points and neutralizes ad/tracker/analytics " +
        "URLs, without stripping or rerouting call sites. Safer than Remove Ads for apps " +
        "that hang on splash screens under that patch, at the cost of only covering " +
        "known SDKs/URL patterns instead of every ad call generically.",
    default = false,
) {
    val redirectionIpOption by stringOption(
        key = "redirectionIp",
        default = DEFAULT_REDIRECT_IP,
        values = mapOf(
            "Default" to DEFAULT_REDIRECT_IP,
            "localhost" to "127.0.0.1",
        ),
        title = "Redirection IP",
        description = "The IP address to redirect blocked domains to.",
    ) { ipAddress ->
        val ipAddressPattern = """^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}$""".toRegex()
        !ipAddress.isNullOrEmpty() && ipAddress.matches(ipAddressPattern)
    }

    dependsOn(hostBlockPatch { redirectionIpOption!! }, hideAdLayoutsPatch, forceHideAdViewsPatch)

    val logger = Logger.getLogger(this::class.java.name)

    execute {
        val networks = mapOf(
            "AppLovin MAX" to ::applyAppLovinMaxPatch,
            "BIGO" to ::applyBigoPatch,
            "Google AdMob" to ::applyGoogleAdMobPatch,
            "Meta Audience Network" to ::applyMetaAudienceNetworkPatch,
            "Mintegral" to ::applyMintegralPatch,
            "myTarget" to ::applyMyTargetPatch,
            "Pangle" to ::applyPanglePatch,
            "TopOn" to ::applyTopOnPatch,
            "Unity" to ::applyUnityPatch,
            "Liftoff Monetize" to ::applyVunglePatch,
            "Yandex Advertising Network" to ::applyYandexPatch,
        )

        networks.forEach { (name, patch) ->
            val results = patch()
            val total = results.size
            val foundCount = results.count { it.isSuccess }

            val message = when {
                total == 0 -> "[Skipped] $name had nothing to check."
                foundCount == total -> "[Found] $name disabled."
                foundCount > 0 -> "[Found] $name partially disabled."
                else -> "[Skipped] $name was not found."
            }
            logger.info(message)
        }
    }
}
