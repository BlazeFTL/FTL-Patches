package app.ftl.patches.ads

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructionsOrNull
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference

internal object AdViewLoadAdFingerprint : Fingerprint(
    name = "loadAd",
    definingClass = "Lcom/google/android/gms/ads/AdView;",
    returnType = "V",
)

internal object InterstitialAdLoadFingerprint : Fingerprint(
    name = "load",
    definingClass = "Lcom/google/android/gms/ads/interstitial/InterstitialAd;",
    returnType = "V",
)

internal object RewardedAdLoadFingerprint : Fingerprint(
    name = "load",
    definingClass = "Lcom/google/android/gms/ads/rewarded/RewardedAd;",
    returnType = "V",
)

internal object FacebookAdViewLoadAdFingerprint : Fingerprint(
    name = "loadAd",
    definingClass = "Lcom/facebook/ads/AdView;",
    returnType = "V",
)

internal object FacebookInterstitialLoadAdFingerprint : Fingerprint(
    name = "loadAd",
    definingClass = "Lcom/facebook/ads/InterstitialAd;",
    returnType = "V",
)

internal object AppLovinMaxInterstitialLoadFingerprint : Fingerprint(
    name = "loadAd",
    definingClass = "Lcom/applovin/mediation/MaxInterstitialAd;",
    returnType = "V",
)

internal object AppLovinMaxRewardedLoadFingerprint : Fingerprint(
    name = "loadAd",
    definingClass = "Lcom/applovin/mediation/MaxRewardedAd;",
    returnType = "V",
)

internal object UnityAdsLoadFingerprint : Fingerprint(
    name = "load",
    definingClass = "Lcom/unity3d/services/core/api/UnityAdsLoad;",
    returnType = "V",
)

internal object IronSourceLoadInterstitialFingerprint : Fingerprint(
    name = "loadInterstitial",
    definingClass = "Lcom/ironsource/mediationsdk/IronSource;",
    returnType = "V",
)

internal val AD_STRING_BLACKLIST = listOf(
    "61.145.124.238",
    "ad.api.kaffnet",
    "ad.mail.ru",
    "ad.myinstashot.com",
    "adbuddiz",
    "adc3-launch",
    "adcolony",
    "addapptr",
    "adincube",
    "adjust",
    "adkmob",
    "adknowledge",
    "admarvel",
    "admob",
    "Admob",
    "admost",
    "adnw_logging",
    "adsafeprotected",
    "adsdk",
    "adsert",
    "adserver",
    "adservice",
    "advertising",
    "adview",
    "adz.wattpad",
    "aerserv",
    "airpush",
    "alta.eqmob",
    "altamob",
    "amazon-adsystem",
    "amazonaws",
    "analytics",
    "appAdForce",
    "appboy",
    "appbrain",
    "appenda",
    "appia",
    "applifier.com",
    "applovin",
    "applvn",
    "appnext",
    "appnexus",
    "appodeal",
    "apprupt",
    "appsdt",
    "appsflyer",
    "apsalar",
    "audience_network",
    "avocarrot",
    "azure",
    "boxdigital/sdk/ad",
    "branch",
    "ca-app-pub",
    "certificate.mobile.yandex.net",
    "chartboost",
    "cloudfront",
    "code.google.com/p/android/issues/detail",
    "crashlytics",
    "csi.gstatic.com",
    "doubleclick.net",
    "dsp.batmobil",
    "duapps",
    "firebaseapp",
    "flurry",
    "fyber",
    "g.doubleclick",
    "google.com/safebrowsing/clientreport",
    "google/android/gms/internal",
    "googleads",
    "googleapis.com/auth/games",
    "googlesyndication",
    "graph.facebook",
    "greystripe",
    "heyzap",
    "hockeyapp",
    "hyprmx",
    "InlineAd",
    "inmobi",
    "inneractive",
    "instreamatic",
    "integralads",
    "ironsource",
    "jirbo",
    "jumptap",
    "kochava",
    "Leadbolt",
    "localytics",
    "loopme",
    "madnet.ru",
    "mdotm",
    "measurement",
    "mediabrix",
    "metrica",
    "millennialmedia",
    "mngads",
    "moat",
    "mobclix",
    "mobfox",
    "mobvista",
    "montexi",
    "moolah",
    "mopub",
    "mp.mydas.mobi",
    "my/target",
    "NativeInterstitial",
    "net.rayjump",
    "network_ads_common",
    "nexage",
    "onelouder/adlib",
    "openx",
    "pagead/ads",
    "plus1.wapstart.ru",
    "pubmatic",
    "pubnative",
    "r.my.com/mobile",
    "revmob",
    "sb.scorecardresearch",
    "smaato/SOMA",
    "startapp",
    "startup.mobile.yandex.net",
    "supersonicads",
    "tagmanager",
    "tapas",
    "tapjoy",
    "udm.scorecardresearch",
    "unity3d/ads",
    "unityads",
    "vdopia",
    "vungle",
    "www.dummy",
    "wzrkt",
    "xtify",
    "yandexadexchange",
    "zestadz",
)

private const val AD_STRING_REPLACEMENT = "Remove-Fucking-Ads"

// SDK's own bytecode: entry points already stubbed via fingerprints above.
// Poisoning strings inside the SDK's own internals breaks its init-time
// validation (e.g. GMS MobileAdsInitProvider checks its app-id string format
// and throws IllegalStateException if it's malformed).
private val AD_SDK_PACKAGE_PREFIXES = listOf(
    "Lcom/google/android/gms/ads/",
    "Lcom/google/android/gms/internal/ads/",
    "Lcom/facebook/ads/",
    "Lcom/applovin/",
    "Lcom/unity3d/services/",
    "Lcom/unity3d/ads/",
    "Lcom/ironsource/",
)

val removeAdsPatch = bytecodePatch(
    name = "Remove Ads",
    description = "Disables ad SDKs, ruins ad network request URLs, and hides empty ad containers across the app.",
    default = false,
) {
    dependsOn(hideAdLayoutsPatch)

    execute {
        AdViewLoadAdFingerprint.methodOrNull?.addInstructions(0, "return-void")
        InterstitialAdLoadFingerprint.methodOrNull?.addInstructions(0, "return-void")
        RewardedAdLoadFingerprint.methodOrNull?.addInstructions(0, "return-void")
        FacebookAdViewLoadAdFingerprint.methodOrNull?.addInstructions(0, "return-void")
        FacebookInterstitialLoadAdFingerprint.methodOrNull?.addInstructions(0, "return-void")
        AppLovinMaxInterstitialLoadFingerprint.methodOrNull?.addInstructions(0, "return-void")
        AppLovinMaxRewardedLoadFingerprint.methodOrNull?.addInstructions(0, "return-void")
        UnityAdsLoadFingerprint.methodOrNull?.addInstructions(0, "return-void")
        IronSourceLoadInterstitialFingerprint.methodOrNull?.addInstructions(0, "return-void")

        classDefForEach { classDef ->
            if (AD_SDK_PACKAGE_PREFIXES.any { classDef.type.startsWith(it) }) return@classDefForEach

            val hasMatch = classDef.methods.any { method ->
                (method.instructionsOrNull ?: emptyList()).any { instruction ->
                    instruction.opcode == Opcode.CONST_STRING &&
                        AD_STRING_BLACKLIST.any { term ->
                            ((instruction as ReferenceInstruction).reference as StringReference)
                                .string.contains(term, ignoreCase = true)
                        }
                }
            }
            if (!hasMatch) return@classDefForEach

            mutableClassDefBy(classDef).methods.forEach { method ->
                (method.instructionsOrNull ?: emptyList()).forEachIndexed { index, instruction ->
                    if (instruction.opcode != Opcode.CONST_STRING) return@forEachIndexed
                    val value = ((instruction as ReferenceInstruction).reference as StringReference).string
                    if (AD_STRING_BLACKLIST.none { value.contains(it, ignoreCase = true) }) return@forEachIndexed
                    val register = (instruction as OneRegisterInstruction).registerA
                    method.replaceInstruction(index, "const-string v$register, \"$AD_STRING_REPLACEMENT\"")
                }
            }
        }
    }
}
