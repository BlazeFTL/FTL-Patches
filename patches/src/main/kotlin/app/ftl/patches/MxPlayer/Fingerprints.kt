package app.ftl.patches.mxplayer

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.AccessFlags

// Licensor.a() — entry point for all license verification
// Triggers Google Play or MX server license check on Activity resume
object LicenseCheckStartFingerprint : Fingerprint(
    returnType = "V",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    parameters = listOf("Landroid/content/ContextWrapper;"),
    filters = listOf(
        string("com.android.vending.CHECK_LICENSE"),
        methodCall(definingClass = "Landroid/content/Context;", name = "checkPermission"),
    )
)

// Licensor.b() — returns whether app is currently licensed
// Checked by App.J() and feature gates throughout the app
object IsLicensedFingerprint : Fingerprint(
    returnType = "Z",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    parameters = listOf(),
    filters = listOf(
        string("com.android.vending.CHECK_LICENSE"),
        methodCall(definingClass = "Landroid/content/Context;", name = "checkPermission"),
        methodCall(definingClass = "Lcom/mxtech/app/Apps;", name = "i"),
        methodCall(definingClass = "Lcom/mxtech/app/Apps;", name = "b"),
    )
)

// Licensor.c() — license failure handler
// Removes gps_manage/direct_manage from prefs, kills activities, shows error dialog
object LicenseFailureFingerprint : Fingerprint(
    returnType = "V",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    parameters = listOf("L", "I", "I"),
    filters = listOf(
        string("gps_manage"),
        string("direct_manage"),
        methodCall(definingClass = "Lcom/mxtech/videoplayer/pro/App;", name = "M"),
    )
)

// App.f() — native signature init entry point
// Passes encrypted certificate fingerprint to libhello.so via BH.d()
// Then passes API URL to BH.w()
object NativeSignatureInitFingerprint : Fingerprint(
    definingClass = "Lcom/mxtech/videoplayer/pro/App;",
    name = "f",
    returnType = "V",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    parameters = listOf(),
    filters = listOf(
        methodCall(definingClass = "Lcom/ddx/axx/axx/axx/BH;", name = "d"),
        string("https://androidapi.mxplay.com"),
        methodCall(definingClass = "Lcom/ddx/axx/axx/axx/BH;", name = "w"),
    )
)

// BH.c() — emulator detection called from native code via @Keep
// Checks Build.*, file system markers, installed emulator packages
object EmulatorDetectionFingerprint : Fingerprint(
    definingClass = "Lcom/ddx/axx/axx/axx/BH;",
    name = "c",
    returnType = "Z",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    parameters = listOf("Landroid/content/Context;"),
)
