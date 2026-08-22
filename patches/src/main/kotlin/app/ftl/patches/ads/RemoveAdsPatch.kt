package app.ftl.patches.ads

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructionsOrNull
import app.morphe.patcher.extensions.InstructionExtensions.removeInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
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

private val ASYNC_LOAD_METHOD_NAMES = setOf(
    "load", "loadAd", "loadAds", "loadBanner", "loadBannerAd",
    "loadInterstitial", "loadInterstitialAd", "loadRewardedVideo",
    "loadRewardedAd", "loadNativeAd", "loadNativeAds",
    "requestAd", "requestBannerAd", "requestInterstitial", "requestInterstitialAd",
    "fetchAd", "preloadAd", "initializeAds", "initializeAdSDK",
    "beginFetchAds", "refreshAds", "pushAdsToPool",
)

private val SHOW_METHOD_NAMES = setOf(
    "showAd", "showAds", "showInterstitial", "showInterstitialAd",
    "showBanner", "showBannerAd", "showRewardedVideo", "showRewardedAd",
    "showFullscreen",
)

private val AD_METHOD_NAMES = ASYNC_LOAD_METHOD_NAMES + SHOW_METHOD_NAMES

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

private fun findFailureMethod(classDef: ClassDef): Method? {
    val candidates = classDef.methods.filter { m ->
        !AccessFlags.BRIDGE.isSet(m.accessFlags) &&
        !AccessFlags.SYNTHETIC.isSet(m.accessFlags) &&
        !AccessFlags.STATIC.isSet(m.accessFlags) &&
        m.name != "<init>" && m.name != "<clinit>"
    }
    if (candidates.size < 2) return null

    return candidates.firstOrNull { m ->
        Regex("fail|error", RegexOption.IGNORE_CASE).containsMatchIn(m.name) &&
        m.parameterTypes.size <= 2 &&
        m.returnType == "V"
    }
}

private fun Instruction.argRegisters(): List<Int>? = when (this) {
    is FiveRegisterInstruction -> when (registerCount) {
        0 -> emptyList()
        1 -> listOf(registerC)
        2 -> listOf(registerC, registerD)
        3 -> listOf(registerC, registerD, registerE)
        4 -> listOf(registerC, registerD, registerE, registerF)
        5 -> listOf(registerC, registerD, registerE, registerF, registerG)
        else -> null
    }
    is RegisterRangeInstruction -> (startRegister until startRegister + registerCount).toList()
    else -> null
}

private data class StripTarget(
    val index: Int,
    val returnsBoolean: Boolean,
    val callbackType: String?,
)

val universalRemoveAdsPatch = bytecodePatch(
    name = "Remove Ads",
    description = "Stubs known ad SDK entry points and poisons ad-network URL strings across the whole app. " +
        "Async load calls are redirected into the callback's own failure method instead of being deleted, " +
        "Currently In Some Apps Makes Them Stuck In SplashActivity Due To Their Custom Check Of Ads Load But It Is Superior.",
    default = false,
) {
    dependsOn(hideAdLayoutsPatch, forceHideAdViewsPatch)

    execute {
        classDefForEach { classDef ->
            val isAdSdkClass = AD_SDK_PACKAGE_PREFIXES.any { classDef.type.startsWith(it) }

            var needsInvokeStrip = false
            var needsStringPoison = false

            for (method in classDef.methods) {
                val instructions = method.instructionsOrNull ?: continue
                for (instruction in instructions) {
                    if (instruction !is ReferenceInstruction) continue

                    if (!isAdSdkClass && !needsStringPoison && instruction.opcode == Opcode.CONST_STRING) {
                        val value = (instruction.reference as StringReference).string
                        if (AD_URL_BLACKLIST.any { value.contains(it, ignoreCase = true) }) {
                            needsStringPoison = true
                        }
                    }

                    if (!needsInvokeStrip) {
                        val ref = instruction.reference as? MethodReference
                        if (ref != null && isAdOwner(ref.definingClass) &&
                            (ref.name == "addView" || ref.name in AD_METHOD_NAMES) &&
                            (ref.returnType == "V" || ref.returnType == "Z")
                        ) {
                            needsInvokeStrip = true
                        }
                    }
                }
                if (needsInvokeStrip && (isAdSdkClass || needsStringPoison)) break
            }

            if (!needsInvokeStrip && !needsStringPoison) return@classDefForEach

            val mutableClass = mutableClassDefBy(classDef)

            if (needsInvokeStrip) {
                mutableClass.methods.forEach { method ->
                    val instructions = method.instructionsOrNull?.toList() ?: return@forEach
                    if (instructions.isEmpty()) return@forEach

                    val stripTargets = mutableListOf<StripTarget>()
                    instructions.forEachIndexed { index, instruction ->
                        if (instruction !is ReferenceInstruction) return@forEachIndexed
                        val ref = instruction.reference as? MethodReference ?: return@forEachIndexed

                        val isAddView = ref.name == "addView" && isAdOwner(ref.definingClass)
                        val isAdCall = ref.name in AD_METHOD_NAMES && isAdOwner(ref.definingClass)
                        if (!isAddView && !isAdCall) return@forEachIndexed

                        val returnsBoolean = ref.returnType == "Z"
                        val returnsVoid = ref.returnType == "V"
                        if (!returnsBoolean && !returnsVoid) return@forEachIndexed

                        val callbackType = if (ref.name in ASYNC_LOAD_METHOD_NAMES) {
                            ref.parameterTypes.lastOrNull()?.toString()
                        } else {
                            null
                        }

                        stripTargets.add(StripTarget(index, returnsBoolean, callbackType))
                    }

                    stripTargets.asReversed().forEach { target ->
                        if (target.returnsBoolean) {
                            val moveResult = instructions.getOrNull(target.index + 1)
                            if (moveResult?.opcode == Opcode.MOVE_RESULT) {
                                val register = (moveResult as OneRegisterInstruction).registerA
                                method.replaceInstruction(target.index + 1, "const/4 v$register, 0x0")
                            }
                        }

                        val redirected = target.callbackType?.let { type ->
                            val argRegs0 = instructions[target.index].argRegisters()
                            if (argRegs0.isNullOrEmpty()) return@let false
                            val calleeReg = argRegs0.last()
                            val scratchPool = argRegs0.dropLast(1)

                            val calleeClassDef = classDefByOrNull(type) ?: return@let false
                            val failMethod = findFailureMethod(calleeClassDef) ?: return@let false
                            val params = failMethod.parameterTypes
                            if (params.any { it == "J" || it == "D" }) return@let false
                            if (params.size > scratchPool.size) return@let false

                            val scratchRegs = scratchPool.takeLast(params.size)
                            val nullMoves = scratchRegs.joinToString("\n") { r -> "const/4 v$r, 0x0" }
                            val argRegsStr = (listOf(calleeReg) + scratchRegs).joinToString(", ") { "v$it" }
                            val paramDescriptor = params.joinToString("") { it.toString() }

                            method.removeInstruction(target.index)
                            method.addInstruction(
                                target.index,
                                (if (nullMoves.isNotEmpty()) "$nullMoves\n" else "") +
                                    "invoke-virtual {$argRegsStr}, ${calleeClassDef.type}->${failMethod.name}($paramDescriptor)V",
                            )
                            true
                        } ?: false

                        if (!redirected) {
                            method.removeInstruction(target.index)
                        }
                    }
                }
            }

            if (needsStringPoison) {
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
}
