package app.ftl.patches.adfreepure

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.util.MethodUtil
import java.util.logging.Logger

@Suppress("unused")
val adFreePurePatch = bytecodePatch(
    name = "Ad-Free & Pure Player (Experimental)",
    description = "Strips all banner ads on the main folder list, full-screen interstitial video ads upon pausing or exiting videos, and online OTT feed promotions by neutralizing ad SDK calls.",
    default = false,
) {
    execute {
        val logger = Logger.getLogger(this::class.java.name)
        val targetName = "Universal"
        logger.info("[$targetName] Executing comprehensive Ad-Blocking engine...")
        var hookedPoints = 0

        classDefForEach { classDef ->
            val type = classDef.type
            val tl = type.lowercase()

            if (tl.startsWith("lkotlin/") ||
                tl.startsWith("lkotlinx/") ||
                tl.startsWith("ljava/") ||
                tl.startsWith("ljavax/") ||
                tl.startsWith("lorg/intellij/") ||
                tl.startsWith("landroidx/core/") ||
                tl.startsWith("landroidx/annotation/")
            ) {
                return@classDefForEach
            }

            val isGoogleMobileAds = tl.contains("com/google/android/gms/ads") || tl.contains("com/google/unity/ads")
            val isUnityAds = tl.contains("com/unity3d/ads") || tl.contains("com/unity3d/services/ads")
            val isAppLovin = tl.contains("com/applovin")
            val isIronSource = tl.contains("com/ironsource")
            val isFacebookAds = tl.contains("com/facebook/ads")
            val isInMobi = tl.contains("com/inmobi/ads")
            val isMintegral = tl.contains("com/mbridge/msdk") || tl.contains("com/mintegral")
            val isGenericAdSdk = tl.contains("/ads/") || tl.contains("/admob/") || tl.contains("admanager")

            val isAdSdkClass = isGoogleMobileAds || isUnityAds || isAppLovin || isIronSource ||
                isFacebookAds || isInMobi || isMintegral || isGenericAdSdk

            if (!isAdSdkClass) return@classDefForEach

            val mutableClass by lazy { mutableClassDefBy(classDef) }

            for (method in classDef.methods.toList()) {
                if (method.implementation == null) continue
                val mName = method.name
                val mNameLower = mName.lowercase()
                val retType = method.returnType

                val isShowOrLoadMethod = (
                    mNameLower == "show" ||
                        mNameLower == "showad" ||
                        mNameLower == "showinterstitial" ||
                        mNameLower == "showinterstitialad" ||
                        mNameLower == "showallads" ||
                        mNameLower == "displayad" ||
                        mNameLower == "displaybanner" ||
                        mNameLower == "loadad" ||
                        mNameLower == "loadnextad" ||
                        mNameLower == "loadinterstitial" ||
                        mNameLower == "showappopenad"
                    ) && retType == "V"

                if (isShowOrLoadMethod) {
                    try {
                        val mutableMethod = mutableClass.methods.first {
                            MethodUtil.methodSignaturesMatch(it, method)
                        }
                        mutableMethod.addInstructions(
                            0,
                            """
                            return-void
                            """.trimIndent(),
                        )
                        hookedPoints++
                        logger.fine("[$targetName AdBlock] Neutralized presentation: ${classDef.type}->${method.name}")
                    } catch (e: Exception) {
                        logger.fine("[$targetName AdBlock] Skip method hook on ${method.name}: ${e.message}")
                    }
                }

                val isAdReadyCheck = (
                    mNameLower == "isready" ||
                        mNameLower == "isadready" ||
                        mNameLower == "isloaded" ||
                        mNameLower == "isadloaded" ||
                        mNameLower == "isadavailable" ||
                        mNameLower == "hasad" ||
                        mNameLower == "hasvideoad" ||
                        mNameLower == "isinterstitialready"
                    ) && retType == "Z"

                if (isAdReadyCheck) {
                    try {
                        val mutableMethod = mutableClass.methods.first {
                            MethodUtil.methodSignaturesMatch(it, method)
                        }
                        mutableMethod.addInstructions(
                            0,
                            """
                            const/4 v0, 0x0
                            return v0
                            """.trimIndent(),
                        )
                        hookedPoints++
                        logger.fine("[$targetName AdBlock] Forced ad readiness to false: ${classDef.type}->${method.name}")
                    } catch (e: Exception) {
                        logger.fine("[$targetName AdBlock] Skip readiness hook on ${method.name}: ${e.message}")
                    }
                }
            }
        }

        logger.info("[$targetName AdBlock] Total real ad-blocking hooks applied: $hookedPoints")
        logger.info("[$targetName Ads] Total ad-blocking hooks applied: $hookedPoints")
    }
}
