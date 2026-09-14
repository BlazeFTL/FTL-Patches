package app.ftl.patches.mxplayer

import app.morphe.patches.all.misc.hex.hexPatch
import app.morphe.patcher.extensions.InstructionExtensions.removeInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.returnEarly

private val mxPlayerProAntiTamperPatch = hexPatch(block = {
    val libPath = "lib/arm64-v8a/libmx-bh.so"

    // Replace anti-tamper thread function with ret (return immediately)
    // libmx-bh.so spawns Thread-6 that checks APK signature integrity.
    // When it detects re-signing, it calls std::terminate() → abort().
    // Original bytes at 0x2fd88 (24 bytes, unique in binary):
    //   fd 7b be a9  stp x29, x30, [sp, #-0x20]!
    //   f4 4f 01 a9  stp x20, x19, [sp, #0x10]
    //   fd 03 00 91  mov x29, sp
    //   f3 03 00 aa  mov x19, x0
    //   d6 ba 00 94  bl <check_function>
    //   61 02 40 f9  ldr x1, [x19, #0x0]
    // Replace with ret + NOPs so the thread returns immediately
    "fd 7b be a9 f4 4f 01 a9 fd 03 00 91 f3 03 00 aa d6 ba 00 94 61 02 40 f9" asPatternTo
        "C0 03 5F D6 1F 20 03 D5 1F 20 03 D5 1F 20 03 D5 1F 20 03 D5 1F 20 03 D5" inFile libPath
})

@Suppress("unused")
val mxPlayerProLicensePatch = bytecodePatch(
    name = "MX Player Pro License",
    description = "Bypasses license verification and signature checks in MX Player Pro.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_MX_PLAYER_PRO)

    dependsOn(mxPlayerProAntiTamperPatch)

    execute {
        // 1. Skip license check initiation — prevents async verification from starting
        LicenseCheckStartFingerprint.method.returnEarly()

        // 2. Always report as licensed — feature gates pass immediately
        IsLicensedFingerprint.method.returnEarly(true)

        // 3. Neuter license failure handler — if verification somehow runs and fails,
        //    the app won't kill itself or show error dialogs
        LicenseFailureFingerprint.method.returnEarly()

        // 4. Skip native signature verification in App.f()
        //    Remove BH.d() call (passes certificate to libhello.so) but keep BH.w() (API URL init)
        NativeSignatureInitFingerprint.let {
            val bhDCallIndex = it.instructionMatches[0].index
            it.method.removeInstruction(bhDCallIndex)
        }

        // 5. Disable emulator detection — always report NOT emulator
        //    Called from native code via @Keep annotation
        EmulatorDetectionFingerprint.method.returnEarly(false)
    }
}
