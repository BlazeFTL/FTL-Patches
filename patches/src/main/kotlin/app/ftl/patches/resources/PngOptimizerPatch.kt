package app.ftl.patches.resources

import app.morphe.patcher.patch.rawResourcePatch
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
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
private val STRIPPABLE_CHUNK_TYPES = setOf(
    "tEXt", "zTXt", "iTXt", "tIME",
    "pHYs",   // Physical pixel dimensions (DPI) — irrelevant on Android
    "hIST",   // Histogram — purely informational
    "sPLT",   // Suggested palette — optional
)

private class PngChunk(val type: String, val data: ByteArray)

private sealed class OptimizeResult {
    data class Success(val bytes: ByteArray, val saved: Int) : OptimizeResult()
    data class Skipped(val reason: String) : OptimizeResult()
}

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
    while (offset + 12 <= bytes.size) {
        val length = readInt(bytes, offset)
        val type = String(bytes, offset + 4, 4, Charsets.US_ASCII)
        val dataStart = offset + 8
        val dataEnd = dataStart + length
        if (length < 0 || dataEnd + 4 > bytes.size) return null

        // Validate CRC to catch truncated or corrupted files
        val storedCrc = readInt(bytes, dataEnd)
        val computedCrc = CRC32().apply {
            update(bytes, offset + 4, 4 + length)
        }.value.toInt()
        if (storedCrc != computedCrc) {
            logger.fine("PNG CRC mismatch at chunk $type, skipping file")
            return null
        }

        chunks += PngChunk(type, bytes.copyOfRange(dataStart, dataEnd))
        offset = dataEnd + 4
    }
    return chunks
}

private fun writeChunk(out: ByteArrayOutputStream, type: String, data: ByteArray) {
    writeInt(out, data.size)
    val typeAndData = ByteArrayOutputStream(4 + data.size).use { baos ->
        baos.write(type.toByteArray(Charsets.US_ASCII))
        baos.write(data)
        baos.toByteArray()
    }
    out.write(typeAndData)
    val crc = CRC32().apply { update(typeAndData) }.value.toInt()
    writeInt(out, crc)
}

private fun inflate(data: ByteArray): ByteArray {
    val inflater = Inflater()
    inflater.setInput(data)
    return ByteArrayOutputStream(data.size * 3).use { out ->
        val buffer = ByteArray(8192)
        while (!inflater.finished()) {
            val count = inflater.inflate(buffer)
            if (count == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
            out.write(buffer, 0, count)
        }
        inflater.end()
        out.toByteArray()
    }
}

private fun deflate(data: ByteArray): ByteArray {
    val deflater = Deflater(Deflater.BEST_COMPRESSION, false)
    deflater.setInput(data)
    deflater.finish()
    return ByteArrayOutputStream(data.size).use { out ->
        val buffer = ByteArray(8192)
        while (!deflater.finished()) {
            val count = deflater.deflate(buffer)
            out.write(buffer, 0, count)
        }
        deflater.end()
        out.toByteArray()
    }
}

/**
 * Losslessly re-encodes a PNG: recompresses the IDAT stream at maximum zlib
 * compression and drops metadata chunks that carry no rendering information.
 * Unknown/private chunks (including 9-patch npTc/npLc) are always preserved
 * untouched, since we never interpret pixel data — only the raw decompressed
 * byte stream is round-tripped through inflate/deflate, which is lossless
 * regardless of color type, bit depth, or interlacing.
 *
 * Filter bytes are left untouched since pixel data is not decoded, so savings
 * are purely from better zlib compression and metadata stripping.
 */
private fun optimizePng(original: ByteArray): OptimizeResult {
    val chunks = parseChunks(original)
        ?: return OptimizeResult.Skipped("parse failed (corrupt or not a PNG)")

    val idatData = ByteArrayOutputStream().use { out ->
        chunks.filter { it.type == "IDAT" }.forEach { out.write(it.data) }
        out.toByteArray()
    }
    if (idatData.isEmpty()) return OptimizeResult.Skipped("no IDAT chunks")

    val raw = try {
        inflate(idatData)
    } catch (e: Exception) {
        return OptimizeResult.Skipped("inflate failed: ${e.message}")
    }
    val recompressed = deflate(raw)

    val out = ByteArrayOutputStream(original.size).use { baos ->
        baos.write(PNG_SIGNATURE)

        var idatWritten = false
        for (chunk in chunks) {
            when {
                chunk.type == "IDAT" -> {
                    if (!idatWritten) {
                        writeChunk(baos, "IDAT", recompressed)
                        idatWritten = true
                    }
                }
                chunk.type in STRIPPABLE_CHUNK_TYPES -> Unit // Drop.
                else -> writeChunk(baos, chunk.type, chunk.data)
            }
        }
        baos.toByteArray()
    }

    return if (out.size < original.size) {
        OptimizeResult.Success(out, original.size - out.size)
    } else {
        OptimizeResult.Skipped("already optimal")
    }
}

val pngOptimizerPatch = rawResourcePatch(
    name = "Png Optimizer",
    description = "Compresses PNG images without losing quality and strips hidden metadata (DPI, timestamps, text) to make the app smaller. Only rewrites files when the result is actually smaller.",
    default = false,
) {
    execute {
        val roots = listOf("res", "assets")
            .map { get(it, false) }
            .filter { it.isDirectory }
        if (roots.isEmpty()) return@execute

        val pngFiles = roots.flatMap { root ->
            root.walkTopDown()
                .filter {
                    it.isFile &&
                    it.extension.equals("png", ignoreCase = true) &&
                    it.length() >= 512 &&
                    it.length() <= 10_000_000
                }
                .toList()
        }

        val optimizedCount = AtomicInteger(0)
        val alreadyOptimalCount = AtomicInteger(0)
        val parseFailedCount = AtomicInteger(0)
        val skippedCount = AtomicInteger(0)
        val freedBytes = AtomicLong(0L)

        pngFiles.parallelStream().forEach { file ->
            val original = file.readBytes()
            val result = try {
                optimizePng(original)
            } catch (e: Exception) {
                logger.warning("PNG optimizer: error on ${file.name} (${e.message})")
                null
            }

            when (result) {
                is OptimizeResult.Success -> {
                    file.writeBytes(result.bytes)
                    optimizedCount.incrementAndGet()
                    freedBytes.addAndGet(result.saved.toLong())
                }
                is OptimizeResult.Skipped -> {
                    when {
                        result.reason == "already optimal" -> alreadyOptimalCount.incrementAndGet()
                        result.reason.startsWith("parse") -> parseFailedCount.incrementAndGet()
                        else -> skippedCount.incrementAndGet()
                    }
                }
                null -> skippedCount.incrementAndGet()
            }
        }

        logger.info(
            "PNG optimizer: optimized=${optimizedCount.get()}, already-optimal=${alreadyOptimalCount.get()}, " +
            "corrupt=${parseFailedCount.get()}, skipped=${skippedCount.get()}, freed=${freedBytes.get() / 1024}KB"
        )
    }
}
