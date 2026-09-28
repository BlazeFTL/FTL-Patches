package app.ftl.patches.mxplayerad

import app.morphe.patcher.patch.rawResourcePatch
import java.io.RandomAccessFile

/**
 * One native lib to patch: [path] inside the APK, byte [offset] to overwrite, and the
 * replacement machine code ([bytes]) — an unconditional "branch to self" for that ISA,
 * which hangs the calling thread at that instruction instead of letting the original
 * code run.
 */
private data class SoPatch(val path: String, val offset: Long, val bytes: ByteArray)

private val SO_PATCHES = listOf(
    // arm64-v8a: AArch64 "b #0" -> word 0x14000000, little-endian.
    SoPatch(
        path = "lib/arm64-v8a/libc++_shared.so",
        offset = 0x0009f2e8L,
        bytes = byteArrayOf(0x00, 0x00, 0x00, 0x14),
    ),
    // armeabi-v7a: Thumb "b ." -> halfword 0xE7FE, little-endian.
    SoPatch(
        path = "lib/armeabi-v7a/libc++_shared.so",
        offset = 0x0006aabeL,
        bytes = byteArrayOf(0xFE.toByte(), 0xE7.toByte()),
    ),
)

val disableSignatureVerificationPatch = rawResourcePatch(
    name = "Disable signature verification",
    description = "Patches libc++_shared.so (arm64-v8a and armeabi-v7a) to branch-to-self at the " +
        "signature check call site, hanging that code path instead of letting it fail the app.",
) {
    compatibleWith(COMPATIBILITY_MX_PLAYER_AD)

    execute {
        val apkEntries = listApkEntries("lib/").toSet()

        for ((path, offset, bytes) in SO_PATCHES) {
            if (path !in apkEntries) continue

            RandomAccessFile(get(path), "rw").use { raf ->
                raf.seek(offset)
                raf.write(bytes)
            }
        }
    }
}
