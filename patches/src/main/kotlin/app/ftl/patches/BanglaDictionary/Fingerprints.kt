package app.ftl.patches.bangladictionary

import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.AccessFlags

/**
 * The purchase-status getter, read by the app to decide whether Pro is
 * unlocked. The whole app ships without obfuscation - class name, method
 * name, and the SharedPreferences key it reads back are all real, stable
 * app code - so the method is pinned directly by signature instead of
 * needing an instruction-based fingerprint.
 */
internal object IsPurchasedFingerprint : Fingerprint(
    definingClass = "Lcom/bappi/utils/Utils;",
    name = "isPurchased",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC, AccessFlags.FINAL),
    returnType = "Z",
    parameters = listOf("Landroid/content/SharedPreferences;"),
)
