package app.ftl.patches.removeadslite.ads.applovin

import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.AccessFlags

internal object InterstitialAdDialogToStringFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC),
    returnType = "Ljava/lang/String;",
    strings = listOf("AppLovinInterstitialAdDialog{}")
)
