package app.ftl.patches.removeads

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructionsOrNull
import app.morphe.patcher.extensions.InstructionExtensions.removeInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter
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

// Only concrete AdMob app/unit ids. A bare "ca-app-pub-" literal (or its validation regex) is
// SDK code, often R8-repackaged outside the SDK packages; poisoning it breaks the SDK's own
// app-id check and crashes MobileAdsInitProvider at launch. The all-zero placeholder id only
// appears in SDK error messages, so it is left alone too.
private val AD_UNIT_ID = Regex("ca-app-pub-(?!0{16}[~/]0{10})\\d{16}[~/]\\d{10}")

private fun isAdString(value: String) =
    AD_URL_BLACKLIST.any { value.contains(it, ignoreCase = true) } || AD_UNIT_ID.containsMatchIn(value)

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
    val structural: Boolean = false,
)

// "onAdFailedToLoad" is public SDK callback API: R8 renames the SDK's classes and most of its
// methods, but keeps this name because app code overrides it. Any type that declares it (or
// extends a type that does) is therefore an ad-load callback, whatever it is called.
private fun isLoadFailedCallback(method: Method) =
    method.name == "onAdFailedToLoad" &&
        method.returnType == "V" &&
        method.parameterTypes.size == 1 &&
        !AccessFlags.STATIC.isSet(method.accessFlags) &&
        !AccessFlags.BRIDGE.isSet(method.accessFlags)

private fun BytecodePatchContext.collectLoadCallbackTypes(): Set<String> {
    val types = HashSet<String>()
    classDefForEach { classDef ->
        if (classDef.methods.none { isLoadFailedCallback(it) }) return@classDefForEach
        var current: ClassDef? = classDef
        var depth = 0
        while (current != null && depth++ < 32) {
            if (!types.add(current.type)) break
            current = current.superclass?.let { classDefByOrNull(it) }
        }
    }
    return types
}

// A load call looks like load(Context, String adUnitId, Request, Callback)V: three or more
// parameters, one of them a String, ending in an ad-load callback type.
private fun isStructuralLoadCall(
    ref: MethodReference,
    instruction: Instruction,
    callbackTypes: Set<String>,
): Boolean {
    if (callbackTypes.isEmpty() || ref.returnType != "V" || ref.name == "<init>") return false
    when (instruction.opcode) {
        Opcode.INVOKE_STATIC, Opcode.INVOKE_STATIC_RANGE,
        Opcode.INVOKE_VIRTUAL, Opcode.INVOKE_VIRTUAL_RANGE,
        Opcode.INVOKE_INTERFACE, Opcode.INVOKE_INTERFACE_RANGE -> Unit
        else -> return false
    }
    val params = ref.parameterTypes
    if (params.size < 3) return false
    if (params.none { it.toString() == "Ljava/lang/String;" }) return false
    return params.last().toString() in callbackTypes
}

private fun packageOf(type: String) = type.substringBeforeLast('/', "")

private fun canAccess(hostType: String, target: ClassDef) =
    AccessFlags.PUBLIC.isSet(target.accessFlags) || packageOf(hostType) == packageOf(target.type)

// Builds `private static void name(Callback cb)` on the host class. It creates a real error object
// and calls cb.onAdFailedToLoad(error), so apps that read the error (getCode/getMessage) keep
// working and flows that wait for the failure callback still continue. Returns null when the error
// type can't be built safely; the caller then just removes the load call.
private fun BytecodePatchContext.buildFailLoadHelper(
    host: ClassDef,
    callbackType: String,
    name: String,
): MutableMethod? {
    if (AccessFlags.INTERFACE.isSet(host.accessFlags)) return null

    val callbackDef = classDefByOrNull(callbackType) ?: return null
    if (!canAccess(host.type, callbackDef)) return null
    val callbackIsInterface = AccessFlags.INTERFACE.isSet(callbackDef.accessFlags)

    var failMethod: Method? = null
    var cursor: ClassDef? = callbackDef
    var depth = 0
    while (cursor != null && failMethod == null && depth++ < 32) {
        failMethod = cursor.methods.firstOrNull { isLoadFailedCallback(it) }
        if (callbackIsInterface) break
        cursor = cursor.superclass?.let { classDefByOrNull(it) }
    }
    val fail = failMethod ?: return null
    if (!AccessFlags.PUBLIC.isSet(fail.accessFlags)) return null

    val errorType = fail.parameterTypes[0].toString()
    val errorDef = classDefByOrNull(errorType) ?: return null
    if (AccessFlags.ABSTRACT.isSet(errorDef.accessFlags) || AccessFlags.INTERFACE.isSet(errorDef.accessFlags)) return null
    if (!canAccess(host.type, errorDef)) return null

    val ctor = errorDef.methods
        .filter { m ->
            m.name == "<init>" &&
                !AccessFlags.SYNTHETIC.isSet(m.accessFlags) &&
                m.parameterTypes.size <= 10 &&
                m.parameterTypes.none { it.toString() == "J" || it.toString() == "D" } &&
                (AccessFlags.PUBLIC.isSet(m.accessFlags) || packageOf(host.type) == packageOf(errorDef.type))
        }
        .sortedWith(
            compareBy(
                { it.parameterTypes.firstOrNull()?.toString() != "I" },
                { it.parameterTypes.size },
            ),
        )
        .firstOrNull() ?: return null

    val ctorParams = ctor.parameterTypes.map { it.toString() }
    var codeAssigned = false
    val setup = ctorParams.mapIndexed { i, type ->
        val reg = i + 1
        when {
            type == "I" && !codeAssigned -> {
                codeAssigned = true
                "const/4 v$reg, 0x3"
            }
            type == "Ljava/lang/String;" -> "const-string v$reg, \"\""
            else -> "const/4 v$reg, 0x0"
        }
    }

    val last = ctorParams.size
    val invokeFail = if (callbackIsInterface) "invoke-interface" else "invoke-virtual"
    val smali = buildString {
        appendLine("if-eqz p0, :done")
        appendLine("new-instance v0, $errorType")
        setup.forEach { appendLine(it) }
        appendLine("invoke-direct/range {v0 .. v$last}, $errorType-><init>(${ctorParams.joinToString("")})V")
        appendLine("$invokeFail {p0, v0}, $callbackType->${fail.name}($errorType)V")
        appendLine(":done")
        appendLine("return-void")
    }

    return ImmutableMethod(
        host.type,
        name,
        listOf(ImmutableMethodParameter(callbackType, null, null)),
        "V",
        AccessFlags.PRIVATE.value or AccessFlags.STATIC.value or AccessFlags.SYNTHETIC.value,
        null,
        null,
        MutableMethodImplementation(last + 2),
    ).toMutable().also { it.addInstructions(0, smali) }
}

val universalRemoveAdsPatch = bytecodePatch(
    name = "Remove Ads",
    description = "Cleans Apps Code From Ads Junk. Works In Most Apps Where There Isn't Any Check For Ads Loaded Or Not. " +
        "If There Is A Check You Will Be Stuck In SplashActivity Due To Custom Ads Load Checks But It Is Superior.",
    default = false,
) {
    dependsOn(hideAdLayoutsPatch, forceHideAdViewsPatch)

    execute {
        val loadCallbackTypes = collectLoadCallbackTypes()

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
                        if (isAdString(value)) {
                            needsStringPoison = true
                        }
                    }

                    if (!needsInvokeStrip) {
                        val ref = instruction.reference as? MethodReference
                        if (ref != null && (
                                (isAdOwner(ref.definingClass) &&
                                    (ref.name == "addView" || ref.name in AD_METHOD_NAMES) &&
                                    (ref.returnType == "V" || ref.returnType == "Z")) ||
                                isStructuralLoadCall(ref, instruction, loadCallbackTypes)
                                )
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
                val helperNames = HashMap<String, String>()
                val newMethods = mutableListOf<MutableMethod>()
                fun helperFor(callbackType: String): String? {
                    helperNames[callbackType]?.let { return it }
                    val name = "ftl\$failLoad\$${newMethods.size}"
                    val helper = buildFailLoadHelper(classDef, callbackType, name) ?: return null
                    newMethods.add(helper)
                    helperNames[callbackType] = name
                    return name
                }

                mutableClass.methods.forEach { method ->
                    val instructions = method.instructionsOrNull?.toList() ?: return@forEach
                    if (instructions.isEmpty()) return@forEach

                    val stripTargets = mutableListOf<StripTarget>()
                    instructions.forEachIndexed { index, instruction ->
                        if (instruction !is ReferenceInstruction) return@forEachIndexed
                        val ref = instruction.reference as? MethodReference ?: return@forEachIndexed

                        val isAddView = ref.name == "addView" && isAdOwner(ref.definingClass)
                        val isAdCall = ref.name in AD_METHOD_NAMES && isAdOwner(ref.definingClass)
                        if (!isAddView && !isAdCall) {
                            if (isStructuralLoadCall(ref, instruction, loadCallbackTypes)) {
                                stripTargets.add(
                                    StripTarget(index, false, ref.parameterTypes.last().toString(), structural = true),
                                )
                            }
                            return@forEachIndexed
                        }

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
                        if (target.structural) {
                            val callbackReg = instructions[target.index].argRegisters()?.lastOrNull()
                            val callbackType = target.callbackType
                            val helper = if (callbackReg != null && callbackType != null) helperFor(callbackType) else null
                            method.removeInstruction(target.index)
                            if (helper != null) {
                                method.addInstruction(
                                    target.index,
                                    "invoke-static/range {v$callbackReg .. v$callbackReg}, ${classDef.type}->$helper($callbackType)V",
                                )
                            }
                            return@forEach
                        }

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
                mutableClass.methods.addAll(newMethods)
            }

            if (needsStringPoison) {
                mutableClass.methods.forEach { method ->
                    (method.instructionsOrNull ?: emptyList()).forEachIndexed { index, instruction ->
                        if (instruction.opcode != Opcode.CONST_STRING) return@forEachIndexed
                        val value = ((instruction as ReferenceInstruction).reference as StringReference).string
                        if (!isAdString(value)) return@forEachIndexed
                        val register = (instruction as OneRegisterInstruction).registerA
                        method.replaceInstruction(index, "const-string v$register, \"${randomAdString()}\"")
                    }
                }
            }
        }
    }
}
