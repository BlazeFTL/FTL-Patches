package app.ftl.patches.mxplayerad

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.filePathOption
import app.morphe.patcher.patch.rawResourcePatch
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

private const val CODEC_LIB_NAME = "libffmpeg.mx.so"

private val CODEC_SUFFIX_TO_ABI = mapOf(
    "neon64" to "arm64-v8a",
    "neon" to "armeabi-v7a",
    "x86_64" to "x86_64",
    "x86" to "x86",
)

private val CODEC_ENTRY_REGEX = Regex("""^libffmpeg\.mx\.so\.(neon64|neon|x86_64|x86)(\..+)?$""")

val replaceFfmpegCodecPatch = rawResourcePatch(
    name = "Replace FFmpeg codec",
    description = "Replaces libffmpeg.mx.so in every lib/<abi>/ folder present in the APK with the " +
        "matching file from a selected codec zip (EAC3 support). neon64 -> arm64-v8a, " +
        "neon -> armeabi-v7a, x86 -> x86, x86_64 -> x86_64. ABI folders missing from the APK or " +
        "from the zip are skipped.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_MX_PLAYER_AD)

    val codecZip = filePathOption(
        key = "ffmpegCodecZip",
        title = "FFmpeg codec zip",
        description = "Zip containing libffmpeg.mx.so.neon64.*, libffmpeg.mx.so.neon.*, " +
            "libffmpeg.mx.so.x86.* and/or libffmpeg.mx.so.x86_64.* (any subfolder depth).",
        required = true,
        allowedExtensions = listOf("zip"),
    )

    execute {
        val zipPath = codecZip.file?.takeIf { it.isFile }
            ?: throw PatchException("FFmpeg codec zip not selected or file not found.")

        val apkAbis = listApkEntries("lib/")
            .mapNotNull { it.split('/').getOrNull(1)?.takeIf { abi -> abi.isNotEmpty() } }
            .toSet()

        var replaced = 0

        ZipFile(zipPath).use { zip ->
            val sources = LinkedHashMap<String, ZipEntry>()
            for (entry in zip.entries().toList()) {
                if (entry.isDirectory) continue
                val match = CODEC_ENTRY_REGEX.matchEntire(entry.name.substringAfterLast('/')) ?: continue
                sources.putIfAbsent(CODEC_SUFFIX_TO_ABI.getValue(match.groupValues[1]), entry)
            }

            for ((abi, entry) in sources) {
                if (abi !in apkAbis) continue
                zip.getInputStream(entry).use { input ->
                    get("lib/$abi/$CODEC_LIB_NAME").outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                replaced++
            }
        }

        if (replaced == 0) {
            throw PatchException(
                "No matching libffmpeg.mx.so.<neon64|neon|x86|x86_64> entry in the zip for any " +
                    "lib/<abi>/ folder in this APK (APK ABIs: ${apkAbis.joinToString()}).",
            )
        }
    }
}
