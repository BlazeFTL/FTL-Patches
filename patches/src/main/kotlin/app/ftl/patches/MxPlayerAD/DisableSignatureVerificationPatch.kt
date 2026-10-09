package app.ftl.patches.mxplayerad

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.resourcePatch
import java.io.File

private val MX_PLAYER_AD_CPU_COMPAT = Compatibility(
    packageName = "com.mxtech.videoplayer.ad",
    name = "MX Player",
    targets = listOf(AppTarget(version = "3.3.0")),
)

private class Hunk(
    val name: String,
    before: String,
    original: String,
    replacement: String,
    after: String,
) {
    private fun hex(s: String) = s.replace(" ", "").chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    val prefixSize = hex(before).size
    val replacementBytes = hex(replacement)
    val stock = hex(before) + hex(original) + hex(after)
    val patched = hex(before) + replacementBytes + hex(after)
}

private val ARM64_HUNKS = listOf(
    Hunk("arm64 flag 1->0", "f31700b9 800000b5", "36008052", "16008052", "0a000014 f5b30094"),
    Hunk("arm64 bl->nop", "ff4300b9", "21f0ff97", "1f2003d5", "e03b40b9 1f040071"),
)

private val ARM32_HUNKS = listOf(
    Hunk("arm32 flag 1->0", "010090e1", "0100a0e3", "0000a0e3", "14008de5 0b00000a"),
)

private val NATIVE_LIBS = mapOf(
    "lib/arm64-v8a/libmx-bh.so" to ARM64_HUNKS,
    "lib/armeabi-v7a/libmx-bh.so" to ARM32_HUNKS,
)

private fun ByteArray.indicesOf(pattern: ByteArray): List<Int> {
    val result = ArrayList<Int>()
    val last = size - pattern.size
    var i = 0
    while (i <= last) {
        var j = 0
        while (j < pattern.size && this[i + j] == pattern[j]) j++
        if (j == pattern.size) result.add(i)
        i++
    }
    return result
}

private fun applyHunks(file: File, libName: String, hunks: List<Hunk>) {
    val data = file.readBytes()
    for (hunk in hunks) {
        val stockAt = data.indicesOf(hunk.stock)
        when {
            stockAt.size == 1 ->
                System.arraycopy(hunk.replacementBytes, 0, data, stockAt[0] + hunk.prefixSize, hunk.replacementBytes.size)
            stockAt.isEmpty() && data.indicesOf(hunk.patched).size == 1 -> Unit
            else -> throw PatchException(
                "$libName: '${hunk.name}' expected exactly 1 stock match, found ${stockAt.size}. " +
                    "Library is not the stock MX Player 3.3.0 build (replaced by another patch?)."
            )
        }
    }
    file.writeBytes(data)
}

@Suppress("unused")
val fixHighCpuUsagePatch = resourcePatch(
    name = "Kill Signature Verification",
    description = "Patches libmx-bh.so to kill signature verification.",
    default = true,
) {
    compatibleWith(MX_PLAYER_AD_CPU_COMPAT)

    execute {
        val entries = listApkEntries("lib/").toSet()
        var patchedAny = false

        NATIVE_LIBS.forEach { (path, hunks) ->
            if (path !in entries) return@forEach
            applyHunks(get(path), path, hunks)
            patchedAny = true
        }

        if (!patchedAny) throw PatchException("No libmx-bh.so found for arm64-v8a or armeabi-v7a.")
    }
}
