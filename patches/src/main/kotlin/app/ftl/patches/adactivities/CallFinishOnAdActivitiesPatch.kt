package app.ftl.patches.adactivities

import app.ftl.patches.ads.hideAdLayoutsPatch
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import java.util.logging.Logger

private val AD_ACTIVITY_ON_CREATE_FINGERPRINTS = listOf(
    "BIGO" to BigoAdSplashOnCreateFingerprint,
    "AppLovin MAX" to AppLovinFullscreenOnCreateFingerprint,
    "Google AdMob" to GoogleAdActivityOnCreateFingerprint,
    "InMobi" to InMobiAdActivityOnCreateFingerprint,
    "Liftoff Monetize (Vungle)" to VungleAdActivityOnCreateFingerprint,
)

// Injects finish() immediately after super.onCreate() in each known ad
// activity, so nothing below it (layout inflate, ad render, impression
// tracking) ever runs. p0 is used for "this" - the inline smali compiler
// resolves it to the real register regardless of the method's register
// count, so this is safe even on Vungle/InMobi's high-register methods.
// Complements HideAdLayoutsPatch (zeroes the containers these SDKs inflate
// into) and is independent of RemoveAdsLite (SDK entry-point stubbing) -
// this catches ad activities that still launch even when load/show itself
// was already stubbed elsewhere.
val callFinishOnAdActivitiesPatch = bytecodePatch(
    name = "Remove Ads Ultra Lite",
    description = "Call finish on ad activities, Forces known ad SDK activities (AdMob, AppLovin MAX, BIGO, InMobi, " +
        "Liftoff/Vungle) to finish() immediately after super.onCreate(), before they " +
        "inflate or render anything. Its Even Weaker Than Remove Ads Lite But Wont Make The App Crash Or Stuck(More Safer)",
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
                        invoke-virtual {p0}, Landroid/app/Activity;->finish()V
                        return-void
                    """.trimIndent(),
                )
            }.onFailure {
                logger.info("[Skipped] $sdkName ad activity not found.")
            }
        }
    }
}
