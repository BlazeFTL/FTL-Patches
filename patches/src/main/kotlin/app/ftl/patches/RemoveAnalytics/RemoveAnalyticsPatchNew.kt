package app.ftl.patches.removeadsanalytics

import app.morphe.patcher.extensions.InstructionExtensions.instructionsOrNull
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.util.proxy.mutableTypes.encodedValue.MutableStringEncodedValue
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.iface.value.StringEncodedValue
import org.w3c.dom.Element

// Regex bodies below are transcribed verbatim (domain/class-name alternation
// only - the surrounding tag-structure regex from the source ruleset is
// replaced by real DOM traversal here) from the CawcaFr find/replace rules
// this patch implements. Leave the alternatives as-is, including unescaped
// dots - that matches the source ruleset's actual matching behavior.

private val AD_URL_REGEX_1 = Regex(
    """((https|http|www)(:\/\/)?(www(?:.)?)?)?(.*appsflyer(sdk)?\.com(.api)?(.*)?|audience.network(.dex)?|\.%s\.facebook\.com.*audience_network.*server_side_reward|(%s)?\.facebook\.com.*adnw_logging.*|graph\.%s\.facebook\.com.*|graph.\%s|pagead2\.googlesyndication\.com.pagead.*|\.googleapis\.com.*auth.*games_lite|\.googleapis\.com.*auth.*games(?:\.firstparty)?|app.measurement\.com(?:.*)?|firebaseinstallations.googleapis.com(.*)?|com\.google\.firebase\.analytics.*|.*\.moatads\.com.*|\.mopub\.com\/optout|(ads|analytics)\.mopub\.com.*|ad\.mail\.ru\/|(config|gateway|scar)\.unityads\.(unity3d\.com|unitychina\.cn).*|ads\.api\.vungle\.com\/|api\.vungle\.com\/|vungle\.com\/privacy\/|cdn.*vungle.com.*|\w\.crashlytics\.com\/spi.*events|settings\.crashlytics\.com\/spi.*platforms\/android\/apps\/%s\/settings|ssdk\.adkmob.*|cm\.adkmob\.com.*|.*doubleclick.net|(.*)?googleadservices(.*)?|googleads.*net.*|(.*)googlesyndication.com(.*)?|.*gstatic.*|app\.appsflyer\.com\/|%s(attr|adrevenue|conversions|launches|inapps|monitorsdk|validate|pia|gcdsdk|onelink|ars|viap|validate-and-log).%s/((install_data\/|shortlink-sdk\/)?(api/(remote-debug\/)?)?v.*|remote-debug\/exception-manager)|%scdn-%s(test)?settings.%s/android/v.*|\w\.amazon-adsystem\.com\/|(.*)?google-analytics\.com(.*)?|com\.google.android.gms.analytics(.*)?|.*googletagmanager\.com.*|cdn\d\.inner-active.*html|(.*)?fyber.com(.*)?|com\.google\.(firebase\.)?analytics(.*)?|help\.branch\.io.*|branch.app.link.*|mobile\.martadserver\.com|mediationsdk\.smartadserverapis.*|webview\.unityads.*|(.*)?ads\.vungle\.com(.*)?|privacy.vungle.com.|(cdn|api.*)\.branch\.io\/|adc3-launch-staging\.adcolony\.com.*|wd\.adcolony\.com\/logs|data\.flurry\.com.*|cfg\.flurry\.com\/sdk.*|fev\.fyber\.com\/event|googleapis\.com\/auth\/games|adservice\.google\.com.*|csi\.gstatic\.com\/csi|firebaseapp\.com|firebase-settings\.crashlytics\.com.*|marketplace-android-.*\.hyprmx\.com|(.*)?supersonicads\.com.*|graph\.facebook\.com.*|.*\.tapjoy.*\.com\/|(io\.)?(opencensus.*|opentelemetry\.).*|telemetry.*.pvp.net.*|(api.*\.)?.*amplitude\.com.*|prod\.cm.*|googlemobileadssdk.*|api.*adsdk.*|.*amazonaws\.com(.*)?|.*smartadserver\.com.*|adqualitysupport\@smaato.com|smaato.com(.*)?|.*openx\.com.*|(startup.)?mobile.yandex.*net(.*)?|com.yandex.metrica.IMetricaService.*|yandex.com.*html|YandexMetricaNativeModule|.*appmetrica.yandex.com.*|.*appnext\.com.*|(quantum4you|qsoftmobile)\.com.*|ad.api.kaffnet|(app|gdpr|subscription).*adjust.*|ssrv.adjust.*|adjust\.com.terms.*|certificate.mobile.yandex.net|zestadz|sb.scorecardresearch|revmob|r.my.com\/mobile|plus1.wapstart.ru|nexage|moolah|montexi|mobfox|boxdigital\/sdk\/ad|.*https.*startapp.*|(.*)?pubmatic.com(.*)?|herokuapp.appodeal.com(.*)?|.*chartboost.com(.*)?|(.*)?pubnative.net(.*)?|.*ads.com.click.*|(.*)?googleapis.*admob(.*)?|admob.*appspot.com(.*)?|admob.com|d.*cloudfront.net.*|sb.scorecardresearch.com(.*)?|ad.mail.ru\/.*|((med-api|afa(-api)?|resources).)?admost.(github.io|com)(.*)?|github.com.*AzureAd.*|firebaseappcheck.googleapis.com.*|(sdk|api).appbrain.com(.*)?|.*com.appbrain|analytics.us.tiktok.com.*|(firebase)((remoteconfig|installations|logging|inappmessaging)(.googleapis.com))|.*amazon-adsystem.com.*|api.onesignal.com.|config.inmobi.cn.*|schemas\.android.*inmobi\.ads|.*inmobicdn\.net\/sdk.*|.*config\.inmobi\.com\/config-server\/.*|unif-id\.ssp\.inmobi\.com\/fetch|\.inmobi\.com\/products\/sdk.*|.*inmobi.com.*|crash-metrics\.sdk\.inmobi\.com\/trace|telemetry\.sdk\.inmobi\.com\/metrics|ads.inmobi.*sdk|supply\.inmobicdn\.net.*|(assets)?.applovin\.com.*|assets(.)?applovin\.com.*|.*appl(o)?v(i)?n.com(.)?|(prod.*applovin\.com(.*)?|rt\.applovin\.com(.*)?|(dash|docs|sts)\.applovin\.com.*|ms\.(applvn|applovin)\.com(.*)?|prod.*analytics.*|prodregistry.*org.*|featureassets.org.*|compliance\.iabtechnologylab\.com.*APPLOVIN.*|developers\.applovin\.com.*|(?:.*)?vid\.applovin\.com(?:.*)?)|imasdk.googleapis.com(.*)?|mobile-data.onetrust.io.|mobile-data.|onetrust.io|zc.adswizz.com.*|sdk.*braze.com(.*)?|sondheim.braze.com(.*)?|..appbaqend.com.*|clarity.ms.*|api.mixpanel.com|.*tiktokpangle.*|.*pangle.io.*|pangleglobal.com.|gecko-pangle-sg.byteoversea.com|pangle.ads|console.firebase.google.com|(gov-)?mobile-(collector|crash).*(nr-data.net|newrelic.com)|.*(prebid.mobile.android|logTelemetryevent.*function).*|wsmetrics.batch.com(.*)?|\<.*(script|function).*(console.log|celtra|googleAdsJsInterface|omsdk).*|.*(console.log|celtra|googleAdsJsInterface|omsdk|applovin).*(script|function|window|url).*)"""
)

private val AD_URL_REGEX_2 = Regex(
    """((https|http|www|www.*)(:\/\/)?(www|www.*)?)?(api.*advertising.*com.*|adc3-launch\.adcolony\.com.*|audience_network(.dex)?|gamma.*advertising.*com.*|schemas\.applovin\.com\/android\/.*|\.applovin\.com\/privacy\/|\.facebook\.com\/adnw_logging\/|graph\.\%s|mediation\.fyber\.com\/mediate|\.googleapis\.com\/auth\/games(.*)?|firebaseappcheck.googleapis.com(.*)?|firebase-settings\.crashlytics\.com(.*)?|com.google.firebase.analytics.FirebaseAnalytics|firebaseinstallations.googleapis.com(.*)?|marketplace-android-.*\.hyprmx\.com|.*inmobi\.com.*|\/config\/secure\.cfg|(.*)?supersonicads\.com(.*)?|.*tapjoy.*\.com\/|.*unityads\.unity3d\.com(.*)?|.*sdk.mediation.unity3d.com.*|(.*)?vungle\.com.*|.*pangle.*|.*mintegral.*|prod.*(advertising|analytics).*|prodregistry.*org.*|featureassets.org.*|(.*)?log.*inmobi.*|(api.*adsdk.*)|mobile\.smartadserver\.com|.*ads.*vungle.*|.*amazonaws.com.*|(app|gdpr|subscription|ssrv)(.*)?adjust.*|adqualitysupport.smaato.com|ad.mail.ru.*|mobile.yandexadexchange.net|startapp\.com.*|googlemobileadssdk.*|pagead2\.googlesyndication\.com.pagead.*|adservice.google.com(.*)?|(.*)?pubmatic.com(.*)?|(.*)?pubnative.net(.*)?|(.*)?admob.com(.*)?|sb.scorecardresearch.com(.*)?|cdn.appnext.com(?:.*)|admost.(github.io|com)(.*)?|.*amplitude.com.|(cdn|api|api2).branch.io.|.*amazon-adsystem.com.*|api.onesignal.com.|zc.adswizz.com.*|sdk.*braze.com|..appbaqend.com.*|wsmetrics.batch.com(.*)?|(gov-)?mobile-(collector|crash).*(nr-data.net|newrelic.com)|.*tiktokpangle.*)"""
)

// A literal placeholder, same as the source ruleset's own replacement value -
// no randomization, nothing generated at patch time.
private const val NEUTRALIZED_VALUE = "http://127.0.0.1/source_code=@BlazeFTL/"

private fun matchesAdUrlRuleset(value: String) =
    AD_URL_REGEX_1.matches(value) || AD_URL_REGEX_2.matches(value)

// Two-fragment scheme-splitting obfuscation: a URL is assembled at runtime
// from two adjacent const-strings carrying different scheme fragments
// ("hts/" vs "tp:/"). Source ruleset only trips when the pair uses two
// *different* fragment variants back to back.
private val SCHEME_FRAGMENT_PATTERN = Regex("^(hts/|tp:/)")

private fun schemeFragmentOf(value: String) = SCHEME_FRAGMENT_PATTERN.find(value)?.value

private val MANIFEST_COMPONENT_REGEX_A = Regex(
    """(com\.)?(yandex.metrica.*|yandex.mobile.ads.*|mbridge.msdk.*|io.appmetrica.analytics.*|bytedance.sdk.*|bytedance.(sdk|applog).*|ironsource.mediationsdk.*|huawei.(agconnect|hms).*|huawei.openalliance.ad.*|my.tracker.campaign.Campaign.*|appsflyer.*|google.android.gms.(analytics.*|TagManager(.*)?|measurement.*)|chartboost.sdk.*|io.presage.*|ogury.core.*|clevertap.android.*|com.taboola.android.*|optimizely.ab.android.*|google.android.datatransport.*|google.firebase.analytics.*|io.invertase.firebase.(crashlytics|messaging|app).ReactNativeFirebase.*|contentsquare.android.*|startapp.sdk.*|adjust.sdk.*|smartadserver.android.library.*|appnexus.opensdk.|tv.teads.sdk.*|yoc.visx.sdk.*|moengage.*|amazon.device.iap.*|amazon.device.ads.*|androidx.work.impl.diagnostics.*|inmobi.(commons|ads|androidsdk).*|org.altbeacon.*|com.adxcorp.ads.*|.*amazonaws.*|google.ads.*|tradplus.ads.*|anythink.(core|basead|expressad).*|org.acra.(sender.JobSenderService|sender.LegacySenderService|attachment.AcraContentProvider|receiver.*)|singular.sdk.SingularInstallReceiver|heytap.msp.push.service.(.*data.*)|sensorsdata.analytics.*|wandoujia.zendesk.*|com.appbrain.AppBrain.*|cleveradssolutions.internal.*|io.bidmachine.*|com.instabug.*|moloco.sdk.*|tp.adx.sdk.*|tradplus.crosspro.*|io.sentry.kotlin.multiplatform.SentryContextProvider|onesignal.notification(DismissReceiver|OpenedReceiver(.*)?)|onesignal(.notifications.(services|receivers))?.(FCMBroadcastReceiver|HmsMessageServiceOneSignal|FCMIntentService|NotificationOpenedActivityHMS|UpgradeReceiver|BootUpReceiver)|braze.(push|ui|braze).*|appodeal.(ads|consent).*|net.pubnative.*|tech.crackle.cracklertbsdk.vast.*|tech.crackle.core_sdk.ads.*|.*appnext\.core.*|jio.jioads.*|alibaba.analytics.*|xiaomi.push.service.receivers.(NetworkStatusReceiver|PingReceiver)|sina.weibo.sdk.(share.WbShareTransActivity|web.WeiboSdkWebActivity)|taobao.(((agoo|accs).(ChannelService.*|AgooCommondReceiver|data.MsgDistributeService|internal.AccsJobService|TaobaoMessageIntentReceiverService))|(highway.receiver.SendEventReceiver|process.interaction.service.*))|taobao.accs.(ServiceReceiver|EventReceiver)|taobao.android.jarviswe.receiver.*|flatads.sdk.*|com.leanplum.*|io.huq.sourcekit.*)""",
    RegexOption.IGNORE_CASE
)

private val MANIFEST_COMPONENT_REGEX_E = Regex(
    """(com\.)?((BIND_GET_INSTALL_REFERRER_SERVICE|android.billingclient.api.ProxyBillingActivity(V2)?|android.permission.(AD_ID|AD_SERVICES_CONFIG|ACCESS_ADSERVICES_(AD_ID|ATTRIBUTION|TOPICS))|google.android.(gms.oss.licenses.(OssLicensesMenuActivity|OssLicensesActivity)|ads.mediationtestsuite.activities.(HomeActivity|NetworkDetailActivity|ConfigurationItemDetailActivity|ConfigurationItemsSearchActivity)|gms.permission.AD_ID|gms.ads.(AdActivity|OutOfContextTestingActivity|NotificationHandlerActivity)|finsky.permission.BIND_GET_INSTALL_REFERRER_SERVICE|tv.ads.controls.FallbackImageActivity)|amazon.device.ads.*|facebook.ads.*|amazon.aps.ads.*|applovin.adview.(AppLovinInterstitialActivity|AppLovinFullscreenThemedActivity)|huawei.appmarket.service.commondata.permission.GET_COMMON_DATA|inmobi.(cmp|choice|signals|commons|ads|androidsdk|rendering).*|unity3d.(services.)?ads.(adunit|adplayer).(AdUnitActivity|FullScreenWebViewDisplay|AdUnitTransparentActivity|AdUnitTransparentSoftwareActivity|AdUnitSoftwareActivity)|vungle.ads.internal.*|io.bidmachine.*|ironsource.(sdk.)?(controller.)?(OpenUrlActivity|InterstitialActivity|mediationsdk.testSuite.TestSuiteActivity|controller.ControllerActivity)|pubmatic.((sdk.)?(common.browser.POBInternalBrowserActivity|webrendering.mraid.POBVideoPlayerActivity|webrendering.ui.POBFullScreenActivity))|fyber.ads.ofw.OfferWallActivity|fyber.inneractive.sdk.activities.(InneractiveInternalBrowserActivity|InneractiveFullscreenAdActivity|InneractiveRichMediaVideoPlayerActivityCore|InternalStoreWebpageActivity|FyberReportAdActivity)|mbridge.msdk.(newreward.player.MBRewardVideoActivity|interstitial.view.MBInterstitialActivity|out.LoadingActivity|activity.MBCommonActivity|reward.player.MBRewardVideoActivity)|adcolony.sdk.(AdColonyAdViewActivity|AdColonyInterstitialActivity)|tapjoy.(TJWebViewActivity|TJContentActivity|TJAdUnitActivity)|smaato.sdk.*|bytedance.(sdk|applog).*|startapp.sdk.ads(base.consent.ConsentActivity|.(interstitials.OverlayActivity|list3d.List3DActivity))|current.android.feature.ads.report.AdStackReportActivity|my.target.common.MyTargetActivity|kidoz.sdk.api.ui_views.interstitial.KidozAdActivity|chartboost.sdk.(view.)?(CBImpressionActivity|internal.clickthrough.EmbeddedBrowserActivity|FullScreenAdActivity)|tv.superawesome.sdk.publisher.(SAInterstitialAd|SAVideoActivity|managed.SAManagedAdActivity)|ogury.cm.ConsentActivity|ogury.ad.interstitial.ui.Interstitial(Activity|Android8TransparentActivity|Android8RotableActivity)?|yandex.mobile.ads.*|yandex.metrica.*|braintreepayments.api.(threedsecure.ThreeDSecureWebViewActivity|GooglePaymentActivity|AndroidPayActivity|BraintreeBrowserSwitchActivity)|sns.payments.(offers.push.PaymentOfferPushActivity|google.recharge.GooglePurchaseCurrencyActivity)|mopub.*|criteo.publisher.*|io.adjoe.sdk.AdjoeActivity|mobilefuse.sdk.*|rendering.splashad.MobileFuseSplashAdActivity|wortise.ads.(appopen.AppOpenActivity|interstitial.InterstitialActivity)|facebook.ads.AudienceNetworkContentProvider|google.android.gms.measurement.AppMeasurement(Receiver|Service|JobService)|google.android.gms.(analytic.*|ads.*|TagManager(.*)?)|android.adservices.AD_SERVICES_CONFIG|loopme.views.activity.(BaseActivity|MraidVideoActivity)|io.didomi.sdk.(notice.ctv.TVNoticeDialogActivity|preferences.ctv.TVPreferencesDialogActivity)|adadapted.android.sdk.core.view.AaWebViewPopupActivity|applovin.sdk.AppLovinInitProvider|ironsource.lifecycle.*|PreloadInfoContentProvider)|yandex.mobile.ads.*|explorestack.iab.(mraid.Mraid(DialogActivity|Activity)|vast.activity.VastActivity)|cn.thinkingdata.analytics.utils.broadcast.TDReceiver|org.acra.(sender.JobSenderService|sender.LegacySenderService|attachment.AcraContentProvider|receiver.*)|io.sentry.android.core.(SentryInitProvider|SentryPerformanceProvider)|io.sentry.kotlin.multiplatform.SentryContextProvider|io.invertase.firebase.(crashlytics|messaging|app).ReactNativeFirebase.*|adjust.sdk.SystemLifecycleContentProvider|appnext.ads.*|my.target.common.MyTargetContentProvider|appnext.banners.BannerActivity|appnext.core.result.*|.*appnext\.(com|core).*|sg.bigo.ads.*|helpshift.(activities|unityproxy).*|anzu.sdk.*|huawei.(agconnect|hms).*|huawei.openalliance.ad.*|my.tracker.campaign.Campaign.*|vk.api.sdk.*|hyprmx.android.sdk.*|google.ads.*|clevertap.android.*|appsee.AppseeBackgroundUploader|bugsnag.android.internal.*|com.taboola.android.*|tappx.sdk.android.*|gomfactory.adpie.sdk.*|igaworks.ssp.part.*|kakao.adfit.*|mobon.sdk.*|.*admixer.*|coupang.ads.*|vungle.(warren|ads).*|net.pubnative.*|tech.crackle.cracklertbsdk.vast.*|tech.crackle.core_sdk.ads.*|applovin.*|microsoft.appcenter.loader.AppCenterLoader|google.android.datatransport.*|contentsquare.android.*|startapp.sdk.*|co.notix.interstitial.InterstitialActivity|io.presage.*|gameanalytics.*|five_corp.ad.*|smartadserver.android.library.*|admost.sdk.*|net.nend.android.internal.ui.activities.*|mngads.(sdk|service).*|yoc.visx.sdk.*|tv.teads.sdk.*|appnexus.opensdk.*|org.prebid.mobile.*|moengage.*|adswizz.interactivead.*|io.appmetrica.analytics.*|onesignal.notification(DismissReceiver|OpenedReceiver(.*)?)|onesignal.((notifications|core).(receivers|services|activities).)?(FCMBroadcastReceiver|FCMIntentService|NotificationDismissReceiver|NotificationOpenedActivity(.*)?)|feedad.*|rtb.sdk.*|org.altbeacon.*|com.adxcorp.ads.*|.*amazonaws.*|(.*)?amazon.*metrics(.*)?|miui.systemAdSolution.*|sina.weibo.sdk.component.WeiboSdkBrowser|tradplus.ads.*|anythink.(core|basead|expressad).*|benchmark.*|heytap.msp.push.service.(.*data.*)|wandoujia.zendesk.*|com.appbrain.AppBrain.*|cleveradssolutions.internal.*|com.instabug.*|moloco.sdk.*|tp.adx.sdk.*|tradplus.crosspro.*|uxcam.service.HttpPostService|braze.(push|ui|braze).*|appodeal.(ads|consent).*|tutelatechnologies.sdk.framework.TutelaSDKService|mbridge.msdk.*|jio.jioads.*|alibaba.analytics.*|xiaomi.mipush.sdk.(PushMessageHandler|MessageHandleService)|xiaomi.push.service.(XMPushService|XMJobService)|sina.weibo.sdk.(share.WbShareTransActivity|web.WeiboSdkWebActivity)|taobao.((agoo|accs).(ChannelService.*|AgooCommondReceiver|data.MsgDistributeService|internal.AccsJobService|TaobaoMessageIntentReceiverService)|highway.receiver.SendEventReceiver|process.interaction.service.*)|flatads.sdk.*|com.leanplum.*|io.huq.sourcekit.*)""",
    RegexOption.IGNORE_CASE
)

private val MANIFEST_META_REGEX = Regex(
    """(com\.)?(((google|firebase)_(performance|analytics|crashlytics)_(default_allow_(analytics_storage|ad_(personalization_signals|storage|user_data)?)|((adid_|ssaid_)?collection_|automatic_screen_reporting_|deferred_deep_link_)(enabled|deactivated)))|(in_app_messaging_auto_collection_enabled|firebase_inapp_messaging_auto_data_collection_enabled|firebase_crash_collection_enabled)|app_data_collection_default_enabled|unity3d.services.core.configuration.AdsSdkInitializer|google.android.gms.ads.*|android.ext.adservices|io.sentry.(auto-init|release|ndk.scope.*|proguard.*|traces.*|attach.*)|contentsquare.android.*|bugsnag.android.*|yandex.mobile.ads.*|bytedance.sdk.pangle.*|bytedance.(sdk|applog).*|google.android.play.billingclient.*|clevertap.*|(.*)?amazon.client.metrics.api(.*)?|facebook.sdk.(.*LogAppEvent.*|Advertiser.*|.*LogEnabled)|firebase_performance_logcat_enabled|delivery_metrics_exported_to_big_query_enabled|BaiduMobAd.*|sensorsdata.analytics.*|moloco.sdk.*|huawei.hms.(client.service.*|min_api_level.*)|io.sentry.gradle-plugin-integrations|onesignal.(BadgeCount|Notification.*)|mobilefuse.sdk.disable_auto_init|io.branch.sdk.(BranchKey|TestMode).*|appsflyer.*)""",
    RegexOption.IGNORE_CASE
)

private val MANIFEST_INTENT_REGEX = Regex(
    """(com\.)?(google.android.apps.play.billing.*|android.vending.billing.*|applovin.*|appsflyer.*|com.singular.preinstall.*|huawei.hms.core(.*)?)""",
    RegexOption.IGNORE_CASE
)

private val MANIFEST_PACKAGE_REGEX = Regex(
    """(com\.)?(appnext.core|pubmatic.openwrapapp|huawei.(hms|hff|hwid.*))""",
    RegexOption.IGNORE_CASE
)

private fun Instruction.asStringLiteralOrNull(): String? {
    if (opcode != Opcode.CONST_STRING && opcode != Opcode.CONST_STRING_JUMBO) return null
    return ((this as ReferenceInstruction).reference as StringReference).string
}

// name = null: brought in by removeAdsAnalyticsPatch via dependsOn, no
// separate toggle of its own.
val stripAdAnalyticsManifestComponentsPatch = resourcePatch(
    name = null,
    description = "Removes ad/analytics SDK activity, service, receiver, provider, " +
        "meta-data, permission, and package-visibility declarations from the manifest.",
) {
    execute {
        document("AndroidManifest.xml").use { document ->
            val manifestRoot = document.documentElement
            val application = manifestRoot.getElementsByTagName("application").item(0) as? Element

            application?.let { app ->
                val doomed = mutableListOf<Element>()
                val kids = app.childNodes
                for (i in 0 until kids.length) {
                    val el = kids.item(i) as? Element ?: continue
                    val nameAttr = el.getAttribute("android:name")
                    if (nameAttr.isEmpty()) continue

                    val hit = when (el.tagName) {
                        "activity", "receiver", "service", "provider" ->
                            MANIFEST_COMPONENT_REGEX_A.matches(nameAttr) || MANIFEST_COMPONENT_REGEX_E.matches(nameAttr)
                        "activity-alias" -> MANIFEST_COMPONENT_REGEX_A.matches(nameAttr)
                        "property" -> MANIFEST_COMPONENT_REGEX_E.matches(nameAttr)
                        "meta-data", "uses-library" -> MANIFEST_META_REGEX.matches(nameAttr)
                        else -> false
                    }
                    if (hit) doomed += el
                }
                doomed.forEach { app.removeChild(it) }
            }

            val rootDoomed = mutableListOf<Element>()
            val rootKids = manifestRoot.childNodes
            for (i in 0 until rootKids.length) {
                val el = rootKids.item(i) as? Element ?: continue

                if (el.tagName == "uses-permission") {
                    val nameAttr = el.getAttribute("android:name")
                    if (nameAttr.isNotEmpty() && MANIFEST_COMPONENT_REGEX_E.matches(nameAttr)) rootDoomed += el
                    continue
                }
                if (el.tagName != "queries") continue

                val queryDoomed = mutableListOf<Element>()
                val queryKids = el.childNodes
                for (j in 0 until queryKids.length) {
                    val qEl = queryKids.item(j) as? Element ?: continue
                    when (qEl.tagName) {
                        "package" -> {
                            val nameAttr = qEl.getAttribute("android:name")
                            if (nameAttr.isNotEmpty() && MANIFEST_PACKAGE_REGEX.matches(nameAttr)) queryDoomed += qEl
                        }
                        "intent" -> {
                            val innerKids = qEl.childNodes
                            var hit = false
                            for (k in 0 until innerKids.length) {
                                val innerEl = innerKids.item(k) as? Element ?: continue
                                if (innerEl.tagName != "action" && innerEl.tagName != "package") continue
                                val nameAttr = innerEl.getAttribute("android:name")
                                if (nameAttr.isNotEmpty() && MANIFEST_INTENT_REGEX.matches(nameAttr)) hit = true
                            }
                            if (hit) queryDoomed += qEl
                        }
                    }
                }
                queryDoomed.forEach { el.removeChild(it) }
            }
            rootDoomed.forEach { manifestRoot.removeChild(it) }
        }
    }
}

val removeAdsAnalyticsPatch = bytecodePatch(
    name = "Remove Analytics New",
    description = "Corrupts analytics network URLs and split-scheme string obfuscation " +
        "inside the code, and strips matching manifest components.",
    default = false,
) {
    dependsOn(stripAdAnalyticsManifestComponentsPatch)

    execute {
        classDefForEach { classDef ->
            var needsWork = false

            for (field in classDef.fields) {
                val literal = (field.initialValue as? StringEncodedValue)?.value ?: continue
                if (matchesAdUrlRuleset(literal)) { needsWork = true; break }
            }

            if (!needsWork) {
                outerMethods@ for (method in classDef.methods) {
                    val ops = method.instructionsOrNull ?: continue
                    for (op in ops) {
                        val literal = op.asStringLiteralOrNull() ?: continue
                        if (matchesAdUrlRuleset(literal)) { needsWork = true; break@outerMethods }
                    }
                }
            }

            if (!needsWork) {
                outerPairs@ for (method in classDef.methods) {
                    val ops = (method.instructionsOrNull ?: continue).toList()
                    for (i in 0 until ops.size - 1) {
                        val first = ops[i].asStringLiteralOrNull()?.let { schemeFragmentOf(it) } ?: continue
                        val second = ops[i + 1].asStringLiteralOrNull()?.let { schemeFragmentOf(it) } ?: continue
                        if (first != second) { needsWork = true; break@outerPairs }
                    }
                }
            }

            if (!needsWork) return@classDefForEach

            val mutableClass = mutableClassDefBy(classDef)

            mutableClass.fields.forEach { field ->
                val encoded = field.initialValue as? MutableStringEncodedValue ?: return@forEach
                if (matchesAdUrlRuleset(encoded.value)) encoded.setValue(NEUTRALIZED_VALUE)
            }

            mutableClass.methods.forEach { method ->
                val ops = method.instructionsOrNull?.toList() ?: return@forEach

                for (index in ops.indices) {
                    val literal = ops[index].asStringLiteralOrNull() ?: continue
                    if (!matchesAdUrlRuleset(literal)) continue
                    val register = (ops[index] as OneRegisterInstruction).registerA
                    method.replaceInstruction(index, "const-string v$register, \"$NEUTRALIZED_VALUE\"")
                }

                for (index in 0 until ops.size - 1) {
                    val firstLiteral = ops[index].asStringLiteralOrNull() ?: continue
                    val secondLiteral = ops[index + 1].asStringLiteralOrNull() ?: continue
                    val firstFragment = schemeFragmentOf(firstLiteral) ?: continue
                    val secondFragment = schemeFragmentOf(secondLiteral) ?: continue
                    if (firstFragment == secondFragment) continue

                    val reg1 = (ops[index] as OneRegisterInstruction).registerA
                    val reg2 = (ops[index + 1] as OneRegisterInstruction).registerA
                    method.replaceInstruction(index, "const-string v$reg1, \"$NEUTRALIZED_VALUE\"")
                    method.replaceInstruction(index + 1, "const-string v$reg2, \"$NEUTRALIZED_VALUE\"")
                }
            }
        }
    }
}
