package app.ftl.patches.resources

import app.morphe.patcher.patch.rawResourcePatch
import java.io.ByteArrayOutputStream
import java.util.logging.Logger
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.Inflater

private val logger = Logger.getLogger("PngOptimizerPatch")

private val PNG_SIGNATURE = byteArrayOf(
    0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(),
    0x0D, 0x0A, 0x1A, 0x0A,
)

// Metadata chunks that carry no rendering information and are safe to drop.
private val STRIPPABLE_CHUNK_TYPES = setOf("tEXt", "zTXt", "iTXt", "tIME")

private class PngChunk(val type: String, val data: ByteArray)

private fun readInt(bytes: ByteArray, offset: Int): Int =
    ((bytes[offset].toInt() and 0xFF) shl 24) or
        ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
        ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
        (bytes[offset + 3].toInt() and 0xFF)

private fun writeInt(out: ByteArrayOutputStream, value: Int) {
    out.write((value ushr 24) and 0xFF)
    out.write((value ushr 16) and 0xFF)
    out.write((value ushr 8) and 0xFF)
    out.write(value and 0xFF)
}

private fun parseChunks(bytes: ByteArray): List<PngChunk>? {
    if (bytes.size < 8 || !PNG_SIGNATURE.contentEquals(bytes.copyOfRange(0, 8))) return null

    val chunks = mutableListOf<PngChunk>()
    var offset = 8
    while (offset + 8 <= bytes.size) {
        val length = readInt(bytes, offset)
        val type = String(bytes, offset + 4, 4, Charsets.US_ASCII)
        val dataStart = offset + 8
        val dataEnd = dataStart + length
        if (length < 0 || dataEnd + 4 > bytes.size) return null // Truncated/corrupt, bail out.
        chunks += PngChunk(type, bytes.copyOfRange(dataStart, dataEnd))
        offset = dataEnd + 4 // Skip the trailing CRC.
    }
    return chunks
}

private fun writeChunk(out: ByteArrayOutputStream, type: String, data: ByteArray) {
    writeInt(out, data.size)
    val typeAndData = ByteArrayOutputStream(4 + data.size).apply {
        write(type.toByteArray(Charsets.US_ASCII))
        write(data)
    }.toByteArray()
    out.write(typeAndData)
    val crc = CRC32().apply { update(typeAndData) }.value.toInt()
    writeInt(out, crc)
}

private fun inflate(data: ByteArray): ByteArray {
    val inflater = Inflater()
    inflater.setInput(data)
    val out = ByteArrayOutputStream(data.size * 3)
    val buffer = ByteArray(8192)
    while (!inflater.finished()) {
        val count = inflater.inflate(buffer)
        if (count == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
        out.write(buffer, 0, count)
    }
    inflater.end()
    return out.toByteArray()
}

private fun deflate(data: ByteArray): ByteArray {
    val deflater = Deflater(Deflater.BEST_COMPRESSION, false)
    deflater.setInput(data)
    deflater.finish()
    val out = ByteArrayOutputStream(data.size)
    val buffer = ByteArray(8192)
    while (!deflater.finished()) {
        val count = deflater.deflate(buffer)
        out.write(buffer, 0, count)
    }
    deflater.end()
    return out.toByteArray()
}

/**
 * Losslessly re-encodes a PNG: recompresses the IDAT stream at maximum zlib
 * compression and drops metadata chunks that carry no rendering information.
 * Unknown/private chunks (including 9-patch npTc/npLc) are always preserved
 * untouched, since we never interpret pixel data — only the raw decompressed
 * byte stream is round-tripped through inflate/deflate, which is lossless
 * regardless of color type, bit depth, or interlacing.
 *
 * @return the re-encoded bytes, or null if the file couldn't be parsed, its
 * IDAT stream couldn't be inflated, or the result wasn't smaller (in which
 * case the original is left untouched).
 */
private fun optimizePng(original: ByteArray): ByteArray? {
    val chunks = parseChunks(original) ?: return null

    val idatData = ByteArrayOutputStream().apply {
        chunks.filter { it.type == "IDAT" }.forEach { write(it.data) }
    }.toByteArray()
    if (idatData.isEmpty()) return null

    val raw = try {
        inflate(idatData)
    } catch (e: Exception) {
        return null
    }
    val recompressed = deflate(raw)

    val out = ByteArrayOutputStream(original.size)
    out.write(PNG_SIGNATURE)

    var idatWritten = false
    for (chunk in chunks) {
        when {
            chunk.type == "IDAT" -> {
                if (!idatWritten) {
                    writeChunk(out, "IDAT", recompressed)
                    idatWritten = true
                }
            }
            chunk.type in STRIPPABLE_CHUNK_TYPES -> Unit // Drop.
            else -> writeChunk(out, chunk.type, chunk.data)
        }
    }

    val result = out.toByteArray()
    return result.takeIf { it.size < original.size }
}

val pngOptimizerPatch = rawResourcePatch(
    name = "Png optimizer",
    description = "Losslessly recompresses png resources: re-deflates image data at maximum compression and strips non-rendering metadata (tEXt/zTXt/iTXt/tIME). Pure JVM, no native binaries — files are only rewritten when the result is smaller.",
    default = false,
) {
    execute {
        val roots = listOf("res", "assets")
            .map { get(it, false) }
            .filter { it.isDirectory }
        if (roots.isEmpty()) return@execute

        var optimizedCount = 0
        var skippedCount = 0
        var freedBytes = 0L

        roots.forEach { root ->
            root.walkTopDown()
                .filter { it.isFile && it.extension.equals("png", ignoreCase = true) }
                .forEach { file ->
                    val original = file.readBytes()
                    val optimized = try {
                        optimizePng(original)
                    } catch (e: Exception) {
                        logger.warning("Png optimizer: skipped ${file.name} (${e.message})")
                        null
                    }

                    if (optimized != null) {
                        file.writeBytes(optimized)
                        optimizedCount++
                        freedBytes += original.size - optimized.size
                    } else {
                        skippedCount++
                    }
                }
        }

        logger.info("Png optimizer: optimized=$optimizedCount, skipped=$skippedCount, freed=${freedBytes / 1024}Kb")
    }
}
