package app.ftl.patches.ads

import app.morphe.patcher.extensions.InstructionExtensions.instructionsOrNull
import app.morphe.patcher.extensions.InstructionExtensions.removeInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import kotlin.random.Random

internal val AD_SDK_PACKAGE_PREFIXES = listOf(
    "Lcom/google/android/gms/ads/",
    "Lcom/google/android/gms/internal/ads/",
    "Lcom/facebook/ads/",
    "Lcom/applovin/",
    "Lcom/unity3d/ads/",
    "Lcom/unity3d/services/",
    "Lcom/ironsource/",
    "Lcom/vungle/",
    "Lcom/inmobi/",
    "Lcom/fyber/",
    "Lsg/bigo/ads/",
    "Lcom/bytedance/sdk/openadsdk/",
    "Lcom/mbridge/",
    "Lcom/mopub/",
    "Lcom/chartboost/",
    "Lcom/adcolony/",
    "Lcom/startapp/",
    "Lcom/tapjoy/",
    "Lcom/smaato/",
    "Lcom/pubmatic/",
    "Lcom/criteo/",
    "Lcom/appodeal/",
    "Lcom/yandex/mobile/ads/",
)

private val AD_METHOD_NAMES = setOf(
    "load", "loadAd", "loadAds", "loadBanner", "loadBannerAd",
    "loadInterstitial", "loadInterstitialAd", "loadRewardedVideo",
    "loadRewardedAd", "loadNativeAd", "loadNativeAds",
    "requestAd", "requestBannerAd", "requestInterstitial", "requestInterstitialAd",
    "fetchAd", "preloadAd", "showAd", "showAds", "showInterstitial",
    "showInterstitialAd", "showBanner", "showBannerAd", "showRewardedVideo",
    "showRewardedAd", "showFullscreen", "initializeAds", "initializeAdSDK",
    "beginFetchAds", "refreshAds", "pushAdsToPool",
)

private val AD_URL_BLACKLIST = listOf(
    "ca-app-pub-",
    "doubleclick.net",
    "googleadservices.com",
    "googlesyndication.com",
    "pagead2.googlesyndication.com",
    "googleads.g.doubleclick.net",
    "adservice.google.com",
    "applovin.com",
    "vungle.com/api",
    "api.inmobi.com",
    "inmobicdn.net",
    "iabtcf.com",
    "mopub.com",
    "chartboost.com",
    "unityads.unity3d.com",
    "adcolony.com",
    "startapp.com",
    "tapjoy.com",
    "pubmatic.com",
    "criteo.com",
)

private fun isAdOwner(owner: String) = AD_SDK_PACKAGE_PREFIXES.any { owner.startsWith(it) }

private fun randomAdString() =
    (1..7).map { ('a'..'z').random(Random) }.joinToString("")

val universalRemoveAdsPatch = bytecodePatch(
    name = "Universal Remove Ads",
    description = "Stubs known ad SDK entry points and poisons ad-network URL strings across the whole app.",
    default = false,
) {
    execute {
        classDefForEach { classDef ->
            val isAdSdkClass = AD_SDK_PACKAGE_PREFIXES.any { classDef.type.startsWith(it) }

            val mutableClass = mutableClassDefBy(classDef)
            mutableClass.methods.forEach { method ->
                val instructions = method.instructionsOrNull?.toList() ?: return@forEach
                if (instructions.isEmpty()) return@forEach

                val stripTargets = mutableListOf<Pair<Int, Boolean>>()
                instructions.forEachIndexed { index, instruction ->
                    if (instruction !is ReferenceInstruction) return@forEachIndexed
                    val ref = instruction.reference as? MethodReference ?: return@forEachIndexed

                    val isAddView = ref.name == "addView" && isAdOwner(ref.definingClass)
                    val isAdCall = ref.name in AD_METHOD_NAMES && isAdOwner(ref.definingClass)
                    if (!isAddView && !isAdCall) return@forEachIndexed

                    val returnsBoolean = ref.returnType == "Z"
                    val returnsVoid = ref.returnType == "V"
                    if (!returnsBoolean && !returnsVoid) return@forEachIndexed

                    stripTargets.add(index to returnsBoolean)
                }

                stripTargets.asReversed().forEach { (index, returnsBoolean) ->
                    if (returnsBoolean) {
                        val moveResult = instructions.getOrNull(index + 1)
                        if (moveResult?.opcode == Opcode.MOVE_RESULT) {
                            val register = (moveResult as OneRegisterInstruction).registerA
                            method.replaceInstruction(index + 1, "const/4 v$register, 0x0")
                        }
                    }
                    method.removeInstruction(index)
                }
            }

            if (isAdSdkClass) return@classDefForEach

            val hasMatch = classDef.methods.any { method ->
                (method.instructionsOrNull ?: emptyList()).any { instruction ->
                    instruction.opcode == Opcode.CONST_STRING &&
                        AD_URL_BLACKLIST.any { term ->
                            ((instruction as ReferenceInstruction).reference as StringReference)
                                .string.contains(term, ignoreCase = true)
                        }
                }
            }
            if (!hasMatch) return@classDefForEach

            mutableClass.methods.forEach { method ->
                (method.instructionsOrNull ?: emptyList()).forEachIndexed { index, instruction ->
                    if (instruction.opcode != Opcode.CONST_STRING) return@forEachIndexed
                    val value = ((instruction as ReferenceInstruction).reference as StringReference).string
                    if (AD_URL_BLACKLIST.none { value.contains(it, ignoreCase = true) }) return@forEachIndexed
                    val register = (instruction as OneRegisterInstruction).registerA
                    method.replaceInstruction(index, "const-string v$register, \"${randomAdString()}\"")
                }
            }
        }
    }
}
