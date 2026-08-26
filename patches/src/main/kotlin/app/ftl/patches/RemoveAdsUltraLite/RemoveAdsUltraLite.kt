package app.ftl.patches.removeadsultralite

import app.ftl.patches.removeads.hideAdLayoutsPatch
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import java.util.logging.Logger

private val AD_ACTIVITY_ON_CREATE_FINGERPRINTS = listOf(
    "BIGO" to BigoAdSplashOnCreateFingerprint,
    "AppLovin MAX" to AppLovinFullscreenOnCreateFingerprint,
    "Google AdMob" to GoogleAdActivityOnCreateFingerprint,
    "InMobi" to InMobiAdActivityOnCreateFingerprint,
    "Liftoff Monetize (Vungle)" to VungleAdActivityOnCreateFingerprint,
    "Meta Audience Network" to MetaAudienceNetworkActivityOnCreateFingerprint,
    "Unity Ads" to UnityAdUnitActivityOnCreateFingerprint,
    "Mintegral (Reward Video)" to MintegralRewardVideoActivityOnCreateFingerprint,
    "Mintegral (Interstitial)" to MintegralInterstitialActivityOnCreateFingerprint,
    "Pangle (Full Screen Video)" to PangleFullScreenVideoActivityOnCreateFingerprint,
    "Pangle (Full Screen Express Video)" to PangleFullScreenExpressVideoActivityOnCreateFingerprint,
    "Pangle (Reward Video)" to PangleRewardVideoActivityOnCreateFingerprint,
    "Pangle (Reward Express Video)" to PangleRewardExpressVideoActivityOnCreateFingerprint,
    "Pangle (Interstitial)" to PangleInterstitialActivityOnCreateFingerprint,
    "Pangle (Interstitial Express)" to PangleInterstitialExpressActivityOnCreateFingerprint,
)

// Injects finish() immediately after super.onCreate() in each known ad
// activity, so nothing below it (layout inflate, ad render, impression
// tracking) ever runs. Uses invoke-virtual/range for the finish() call even
// though it only takes one register: p0 can resolve to a register number
// above v15 on methods with a large register count (e.g. InMobiAdActivity,
// .registers 33 -> p0 = v31), which the non-range invoke-virtual encoding
// can't address (35c format, 4-bit register operands, v0-v15 only). /range
// has no such limit and costs nothing on the low-register methods either.
// Complements HideAdLayoutsPatch (zeroes the containers these SDKs inflate
// into) and is independent of RemoveAdsLite (SDK entry-point stubbing) -
// this catches ad activities that still launch even when load/show itself
// was already stubbed elsewhere.
val callFinishOnAdActivitiesPatch = bytecodePatch(
    name = "Remove Ads Ultra Lite",
    description = "Call finish on ad activities. Use Where Remove Ads " +
        "And Remove Ads Lite (Adobo) Caused Problem. Its In Very Early Stage So Test " +
        "And Provide FeedBack If You Still See Ads In Some App.",
    default = false,
) {
    dependsOn(hideAdLayoutsPatch)

    val logger = Logger.getLogger(this::class.java.name)

    execute {
        AD_ACTIVITY_ON_CREATE_FINGERPRINTS.forEach { (sdkName, fingerprint) ->
            runCatching {
                val superCallIndex = fingerprint.instructionMatches.first().index

                fingerprint.method.addInstructions(
                    superCallIndex + 1,
                    """
                        invoke-virtual/range {p0 .. p0}, Landroid/app/Activity;->finish()V
                        return-void
                    """.trimIndent(),
                )
            }.onFailure {
                logger.info("[Skipped] $sdkName ad activity not found.")
            }
        }
    }
}
