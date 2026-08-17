package app.ftl.morphe.patches.analytics

import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction21c
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction21c
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableStringReference
import com.android.tools.smali.dexlib2.util.MethodUtil
import kotlin.random.Random

// Adjust package/imports above to match Morphe's actual dexlib2 fork paths.
// Everything below only touches string-pool references and one real,
// unobfuscated SDK method (java.security.Signature.verify) — no obfuscated
// identifiers pinned anywhere, so it needs no per-app fingerprints.

private val CLASS_NAME_BLOCKLIST = Regex(
    "(audience_network|com\\.google\\.analytics|com\\.google\\.android\\.gms\\.analytics|" +
    "com\\.google\\.firebase\\.analytics|com\\.google\\.firebase\\.firebase_analytics|" +
    "com\\.yandex\\.metrica\\.IMetricaService).*"
)

private val HOST_BLOCKLIST = Regex(
    "(api\\.branch\\.io|crashlytics\\.com|wzrkt\\.com|appboy\\.com|appsflyer\\.com|" +
    "google-analytics\\.com|measurement\\.com|data\\.flurry\\.com|googletagmanager\\.com|" +
    "hockeyapp\\.net|scorecardresearch\\.com|YandexMetricaNativeModule|amplitude\\.com|" +
    "azure\\.com|firebaseapp\\.com|startappservice\\.com|startappexchange\\.com|smaato\\.com|" +
    "api\\.crittercism\\.com|appmetrica\\.yandex\\.ru|app\\.adjust\\.com|cloudfront\\.net|" +
    "amazonaws\\.com|akamaitechnologies\\.com|microsoft\\.applications\\.telemetry|" +
    "skype\\.telemetry\\.com|skype\\.android\\.analytics\\.com|skype\\.android\\.crash\\.com|" +
    "chartboost\\.com|my\\.target\\.com|umeng\\.com|lsdsl\\.ml)"
)

private val AD_SDK_BLOCKLIST = Regex(
    "https?://.*(61\\.145\\.124\\.238|ad\\.api\\.kaffnet|ad\\.mail\\.ru|ad\\.myinstashot\\.com|" +
    "adc3-launch|adbuddiz|adcolony|addapptr|adincube|adjust|adkmob|adknowledge|admarvel|admob|" +
    "admost|adnw_logging|adsafeprotected|adsdk|adsert|adserver|adservice|advertising|adview|" +
    "adz\\.wattpad|aerserv|airpush|altamob|alta\\.eqmob|amazon-adsystem|amazonaws|analytics|" +
    "appAdForce|appboy|appbrain|appenda|appia|applifier\\.com|applovin|applvn|appnext|" +
    "appnexus|appodeal|apprupt|apsalar|appsdt|appsflyer|audience_network|avocarrot|azure|" +
    "boxdigital/sdk/ad|branch|ca-app-pub|certificate\\.mobile\\.yandex\\.net|chartboost|" +
    "cloudfront|code\\.google\\.com/p/android/issues/detail|crashlytics|csi\\.gstatic\\.com|" +
    "doubleclick\\.net|dsp\\.batmobil|duapps|firebaseapp|flurry|fyber|g\\.doubleclick|" +
    "google/android/gms/internal|google\\.com/safebrowsing/clientreport|googleapis\\.com/auth/games|" +
    "googleads|googlesyndication|graph\\.facebook|greystripe|heyzap|hockeyapp|hyprmx|InlineAd|" +
    "inmobi|inneractive|instreamatic|integralads|ironsource|jirbo|jumptap|kochava|Leadbolt|" +
    "localytics|loopme|madnet\\.ru|mdotm|measurement|mediabrix|metrica|millennialmedia|mngads|" +
    "moat|mobclix|mobfox|mobvista|montexi|moolah|mopub|mp\\.mydas\\.mobi|my/target|" +
    "NativeInterstitial|net\\.rayjump|network_ads_common|nexage|onelouder/adlib|openx|" +
    "pagead/ads|plus1\\.wapstart\\.ru|pubmatic|pubnative|r\\.my\\.com/mobile|revmob|" +
    "sb\\.scorecardresearch|smaato/SOMA|startapp|startup\\.mobile\\.yandex\\.net|supersonicads|" +
    "tagmanager|tapas|tapjoy|udm\\.scorecardresearch|unity3d/ads|unityads|vdopia|vungle|" +
    "wzrkt|xtify|yandexadexchange|zestadz).*"
)

private fun String.isBlockedLiteral(): Boolean =
    length >= 4 && (CLASS_NAME_BLOCKLIST.matches(this) ||
        HOST_BLOCKLIST.containsMatchIn(this) ||
        AD_SDK_BLOCKLIST.matches(this))

private fun randomReplacement(len: Int = 7): String {
    val charset = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
    return (1..len).map { charset[Random.nextInt(charset.length)] }.joinToString("")
}

/**
 * Universal analytics/ad-SDK string neutralizer.
 * Walks every method in every class, rewrites any CONST_STRING /
 * CONST_STRING_JUMBO whose value matches a known tracker/ad-SDK literal.
 * Call from your BytecodePatch's execute(context: BytecodePatchContext).
 */
fun BytecodePatchContext.removeAnalyticsStrings() {
    for (classDef in classes) {
        for (method in classDef.methods) {
            if (MethodUtil.isAbstract(method)) continue
            val impl = method.implementation ?: continue
            val mutableMethod = proxy(classDef).mutableClass.methods
                .first { it.name == method.name && it.parameterTypes == method.parameterTypes }
            val mutImpl = mutableMethod.implementation ?: continue

            mutImpl.instructions.forEachIndexed { index, instruction ->
                if (instruction.opcode != Opcode.CONST_STRING &&
                    instruction.opcode != Opcode.CONST_STRING_JUMBO
                ) return@forEachIndexed

                val ref = (instruction as Instruction21c).reference as? StringReference ?: return@forEachIndexed
                if (!ref.string.isBlockedLiteral()) return@forEachIndexed

                mutImpl.replaceInstruction(
                    index,
                    BuilderInstruction21c(
                        instruction.opcode,
                        instruction.registerA,
                        ImmutableStringReference(randomReplacement())
                    )
                )
            }
        }
    }
}

/**
 * Anti-tamper defeat: forces java.security.Signature.verify(byte[])
 * to always report success. Real, unobfuscated SDK method — safe to
 * pin directly.
 */
fun BytecodePatchContext.bypassSignatureVerify() {
    for (classDef in classes) {
        for (method in classDef.methods) {
            val impl = method.implementation ?: continue
            val mutableMethod = proxy(classDef).mutableClass.methods
                .first { it.name == method.name && it.parameterTypes == method.parameterTypes }
            val mutImpl = mutableMethod.implementation ?: continue
            val instructions = mutImpl.instructions

            for (index in instructions.indices) {
                val insn = instructions[index]
                if (insn.opcode != Opcode.INVOKE_VIRTUAL) continue
                val ref = (insn as? com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction35c)
                    ?.reference as? com.android.tools.smali.dexlib2.iface.reference.MethodReference
                    ?: continue
                if (ref.definingClass != "Ljava/security/Signature;" ||
                    ref.name != "verify" ||
                    ref.parameterTypes.singleOrNull() != "[B" ||
                    ref.returnType != "Z"
                ) continue

                val moveResultIndex = index + 1
                val moveResult = instructions.getOrNull(moveResultIndex) ?: continue
                if (moveResult.opcode != Opcode.MOVE_RESULT) continue
                val destRegister =
                    (moveResult as com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction11x).registerA

                mutImpl.addInstruction(
                    moveResultIndex + 1,
                    com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction11n(
                        Opcode.CONST_4,
                        destRegister,
                        1
                    )
                )
            }
        }
    }
}

/**
 * ResourcePatch step: strips <receiver>/<service> declarations whose
 * android:name starts with com.google.firebase from AndroidManifest.xml,
 * disabling Firebase's auto-init components at the registration level.
 * Call from your ResourcePatch's execute(context: ResourcePatchContext).
 */
fun ResourcePatchContext.stripFirebaseManifestComponents() {
    val manifest = get("AndroidManifest.xml")
    var text = manifest.readText()

    text = text.replace(
        Regex("""<receiver\s+android:exported="[^"]+"\s+android:name="com\.google\.firebase[^"]*"\s*/>"""),
        ""
    )
    text = text.replace(
        Regex(
            """<service\s+android:exported="[^"]+"\s+android:name="com\.google\.firebase[^"]*">""" +
            """\s*<intent-filter\s+android:priority="[^"]+">""" +
            """\s*<action\s+android:name="com\.google\.firebase[^"]*"\s*/>""" +
            """\s*</intent-filter>\s*</service>"""
        ),
        ""
    )

    manifest.writeText(text)
}
