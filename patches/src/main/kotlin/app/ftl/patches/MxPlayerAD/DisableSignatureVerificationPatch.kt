package app.ftl.patches.mxplayerad

import app.morphe.patcher.patch.rawResourcePatch
import java.io.RandomAccessFile

/**
 * One native lib to patch: [path] inside the APK, byte [offset] to overwrite, and the
 * replacement machine code ([bytes]). Either an unconditional "branch to self" for that ISA,
 * which hangs the calling thread at that instruction instead of letting the original code run,
 * or an immediate return at a function entry, which turns the whole function into a no-op.
 */
private data class SoPatch(val path: String, val offset: Long, val bytes: ByteArray)

private val SO_PATCHES = listOf(
    // arm64-v8a: file offset = vaddr 0x9f2e8 - .text delta 0x4000 (separate PT_LOAD segment).
    // Lands on a CBZ. AArch64 "b #0" -> word 0x14000000, little-endian.
    SoPatch(
        path = "lib/arm64-v8a/libc++_shared.so",
        offset = 0x0009b2e8L,
        bytes = byteArrayOf(0x00, 0x00, 0x00, 0x14),
    ),
    // armeabi-v7a: file offset = vaddr 0x6aabe - .text delta 0x1000. Lands on a BNE.
    // Thumb "b ." -> halfword 0xE7FE, little-endian.
    SoPatch(
        path = "lib/armeabi-v7a/libc++_shared.so",
        offset = 0x00069abeL,
        bytes = byteArrayOf(0xFE.toByte(), 0xE7.toByte()),
    ),
    // arm64-v8a libmx-bh.so: function entry (stock "sub sp, sp, #0x180" -> "ret").
    // AArch64 "ret" -> word 0xD65F03C0, little-endian.
    SoPatch(
        path = "lib/arm64-v8a/libmx-bh.so",
        offset = 0x0002e48cL,
        bytes = byteArrayOf(0xC0.toByte(), 0x03, 0x5F, 0xD6.toByte()),
    ),
    // arm64-v8a libmx-bh.so: function entry (stock "sub sp, sp, #0x30" -> "ret").
    SoPatch(
        path = "lib/arm64-v8a/libmx-bh.so",
        offset = 0x0002f724L,
        bytes = byteArrayOf(0xC0.toByte(), 0x03, 0x5F, 0xD6.toByte()),
    ),
    // armeabi-v7a libmx-bh.so: function entry (stock "push {r4-r11, lr}" -> "bx lr").
    // ARM "bx lr" -> word 0xE12FFF1E, little-endian.
    SoPatch(
        path = "lib/armeabi-v7a/libmx-bh.so",
        offset = 0x000199f8L,
        bytes = byteArrayOf(0x1E, 0xFF.toByte(), 0x2F, 0xE1.toByte()),
    ),
    // armeabi-v7a libmx-bh.so: function entry (stock "push {r4, r10, r11, lr}" -> "bx lr").
    SoPatch(
        path = "lib/armeabi-v7a/libmx-bh.so",
        offset = 0x0001ac3cL,
        bytes = byteArrayOf(0x1E, 0xFF.toByte(), 0x2F, 0xE1.toByte()),
    ),
)

val disableSignatureVerificationPatch = rawResourcePatch(
    name = "Disable signature verification",
    description = "Patches libc++_shared.so (branch-to-self at the signature check call site) and " +
        "libmx-bh.so (immediate return at two function entries) on arm64-v8a and armeabi-v7a, " +
        "so signature verification no longer fails the app.",
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
