package app.ftl.patches.apkcleanup

import app.morphe.patcher.patch.resourcePatch
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.logging.Logger
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.Inflater
import kotlin.math.abs

private val logger = Logger.getLogger("PngOptimizerPatch")

private val PNG_SIGNATURE = byteArrayOf(
    0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(),
    0x0D, 0x0A, 0x1A, 0x0A,
)

private val STRIPPABLE_CHUNK_TYPES = setOf(
    "tEXt", "zTXt", "iTXt", "tIME",
    "pHYs",
    "hIST",
    "sPLT",
)

private val COLORSPACE_CHUNK_TYPES = setOf("gAMA", "cHRM", "sRGB", "iCCP")

private const val MAX_PALETTE_COLORS = 64

private class PngChunk(val type: String, val data: ByteArray)

private class ImageHeader(val width: Int, val height: Int, val bitDepth: Int, val colorType: Int, val interlace: Int)

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

private fun parseIhdr(data: ByteArray): ImageHeader {
    val width = readInt(data, 0)
    val height = readInt(data, 4)
    val bitDepth = data[8].toInt() and 0xFF
    val colorType = data[9].toInt() and 0xFF
    val interlace = data[12].toInt() and 0xFF
    return ImageHeader(width, height, bitDepth, colorType, interlace)
}

private fun samplesForColorType(colorType: Int): Int = when (colorType) {
    0 -> 1
    2 -> 3
    3 -> 1
    4 -> 2
    6 -> 4
    else -> -1
}

private fun paeth(a: Int, b: Int, c: Int): Int {
    val p = a + b - c
    val pa = abs(p - a)
    val pb = abs(p - b)
    val pc = abs(p - c)
    return if (pa <= pb && pa <= pc) a else if (pb <= pc) b else c
}

private fun unfilter(raw: ByteArray, height: Int, stride: Int, bpp: Int): ByteArray {
    val out = ByteArray(height * stride)
    var prevRowStart = -1
    var pos = 0
    for (y in 0 until height) {
        val filterType = raw[pos].toInt() and 0xFF
        pos++
        val rowStart = y * stride
        for (i in 0 until stride) {
            val rawByte = raw[pos + i].toInt() and 0xFF
            val a = if (i >= bpp) out[rowStart + i - bpp].toInt() and 0xFF else 0
            val b = if (prevRowStart >= 0) out[prevRowStart + i].toInt() and 0xFF else 0
            val c = if (prevRowStart >= 0 && i >= bpp) out[prevRowStart + i - bpp].toInt() and 0xFF else 0
            val value = when (filterType) {
                0 -> rawByte
                1 -> rawByte + a
                2 -> rawByte + b
                3 -> rawByte + (a + b) / 2
                4 -> rawByte + paeth(a, b, c)
                else -> throw IllegalStateException("Unsupported PNG filter type $filterType")
            }
            out[rowStart + i] = (value and 0xFF).toByte()
        }
        pos += stride
        prevRowStart = rowStart
    }
    return out
}

private fun adaptiveFilterEncode(samples: ByteArray, width: Int, height: Int, bpp: Int): ByteArray {
    val stride = width * bpp
    var prevRow = ByteArray(stride)
    val candidates = Array(5) { ByteArray(stride) }

    return ByteArrayOutputStream(height * (stride + 1)).use { out ->
        for (y in 0 until height) {
            val row = samples.copyOfRange(y * stride, y * stride + stride)

            System.arraycopy(row, 0, candidates[0], 0, stride)
            for (i in 0 until stride) {
                val a = if (i >= bpp) row[i - bpp].toInt() and 0xFF else 0
                candidates[1][i] = ((row[i].toInt() and 0xFF) - a).toByte()
            }
            for (i in 0 until stride) {
                val b = prevRow[i].toInt() and 0xFF
                candidates[2][i] = ((row[i].toInt() and 0xFF) - b).toByte()
            }
            for (i in 0 until stride) {
                val a = if (i >= bpp) row[i - bpp].toInt() and 0xFF else 0
                val b = prevRow[i].toInt() and 0xFF
                candidates[3][i] = ((row[i].toInt() and 0xFF) - (a + b) / 2).toByte()
            }
            for (i in 0 until stride) {
                val a = if (i >= bpp) row[i - bpp].toInt() and 0xFF else 0
                val b = prevRow[i].toInt() and 0xFF
                val c = if (i >= bpp) prevRow[i - bpp].toInt() and 0xFF else 0
                candidates[4][i] = ((row[i].toInt() and 0xFF) - paeth(a, b, c)).toByte()
            }

            var bestType = 0
            var bestScore = Long.MAX_VALUE
            for (t in 0 until 5) {
                var score = 0L
                for (v in candidates[t]) {
                    val sv = v.toInt() and 0xFF
                    score += if (sv < 128) sv else 256 - sv
                }
                if (score < bestScore) {
                    bestScore = score
                    bestType = t
                }
            }

            out.write(bestType)
            out.write(candidates[bestType])
            prevRow = row
        }
        out.toByteArray()
    }
}

private fun assemblePng(
    width: Int,
    height: Int,
    bitDepth: Int,
    colorType: Int,
    plte: ByteArray?,
    trns: ByteArray?,
    ancillary: List<PngChunk>,
    sampleBytes: ByteArray,
    bpp: Int,
): ByteArray {
    val ihdr = ByteArrayOutputStream(13).use { o ->
        writeInt(o, width)
        writeInt(o, height)
        o.write(bitDepth)
        o.write(colorType)
        o.write(0)
        o.write(0)
        o.write(0)
        o.toByteArray()
    }
    val idat = deflate(adaptiveFilterEncode(sampleBytes, width, height, bpp))

    return ByteArrayOutputStream(idat.size + 256).use { out ->
        out.write(PNG_SIGNATURE)
        writeChunk(out, "IHDR", ihdr)
        if (plte != null) writeChunk(out, "PLTE", plte)
        if (trns != null) writeChunk(out, "tRNS", trns)
        for (chunk in ancillary) writeChunk(out, chunk.type, chunk.data)
        writeChunk(out, "IDAT", idat)
        writeChunk(out, "IEND", ByteArray(0))
        out.toByteArray()
    }
}

private fun decodeToRgba(chunks: List<PngChunk>, header: ImageHeader): IntArray? {
    if (header.interlace != 0 || header.bitDepth != 8) return null
    val samples = samplesForColorType(header.colorType)
    if (samples <= 0 || header.colorType == 3) return null

    val idatData = ByteArrayOutputStream().use { out ->
        chunks.filter { it.type == "IDAT" }.forEach { out.write(it.data) }
        out.toByteArray()
    }
    if (idatData.isEmpty()) return null

    val raw = try {
        inflate(idatData)
    } catch (e: Exception) {
        return null
    }
    val stride = header.width * samples
    val expected = (stride + 1) * header.height
    if (raw.size < expected) return null

    val sampleBytes = unfilter(raw, header.height, stride, samples)
    val pixelCount = header.width * header.height
    val rgba = IntArray(pixelCount)

    when (header.colorType) {
        0 -> for (p in 0 until pixelCount) {
            val g = sampleBytes[p].toInt() and 0xFF
            rgba[p] = (g shl 24) or (g shl 16) or (g shl 8) or 0xFF
        }
        2 -> for (p in 0 until pixelCount) {
            val o = p * 3
            val r = sampleBytes[o].toInt() and 0xFF
            val g = sampleBytes[o + 1].toInt() and 0xFF
            val b = sampleBytes[o + 2].toInt() and 0xFF
            rgba[p] = (r shl 24) or (g shl 16) or (b shl 8) or 0xFF
        }
        4 -> for (p in 0 until pixelCount) {
            val o = p * 2
            val g = sampleBytes[o].toInt() and 0xFF
            val a = sampleBytes[o + 1].toInt() and 0xFF
            rgba[p] = (g shl 24) or (g shl 16) or (g shl 8) or a
        }
        6 -> for (p in 0 until pixelCount) {
            val o = p * 4
            val r = sampleBytes[o].toInt() and 0xFF
            val g = sampleBytes[o + 1].toInt() and 0xFF
            val b = sampleBytes[o + 2].toInt() and 0xFF
            val a = sampleBytes[o + 3].toInt() and 0xFF
            rgba[p] = (r shl 24) or (g shl 16) or (b shl 8) or a
        }
        else -> return null
    }
    return rgba
}

private fun buildPalette(pixels: IntArray, maxColors: Int): IntArray {
    val counts = LinkedHashMap<Int, Int>()
    for (p in pixels) counts[p] = (counts[p] ?: 0) + 1
    if (counts.size <= maxColors) return counts.keys.toIntArray()

    fun channel(color: Int, shift: Int) = (color ushr shift) and 0xFF

    class Box(val colors: MutableList<Int>, val weights: MutableList<Int>)

    val boxes = mutableListOf(Box(counts.keys.toMutableList(), counts.values.toMutableList()))

    while (boxes.size < maxColors) {
        var largestIndex = -1
        var largestSize = 1
        for (i in boxes.indices) {
            if (boxes[i].colors.size > largestSize) {
                largestSize = boxes[i].colors.size
                largestIndex = i
            }
        }
        if (largestIndex < 0) break
        val box = boxes[largestIndex]

        var bestShift = 24
        var bestRange = -1
        for (shift in intArrayOf(24, 16, 8)) {
            var lo = 255
            var hi = 0
            for (c in box.colors) {
                val v = channel(c, shift)
                if (v < lo) lo = v
                if (v > hi) hi = v
            }
            if (hi - lo > bestRange) {
                bestRange = hi - lo
                bestShift = shift
            }
        }

        val order = box.colors.indices.sortedBy { channel(box.colors[it], bestShift) }
        val sortedColors = order.map { box.colors[it] }
        val sortedWeights = order.map { box.weights[it] }

        val total = sortedWeights.sum()
        var cumulative = 0
        var splitAt = 1
        for (i in sortedWeights.indices) {
            cumulative += sortedWeights[i]
            if (cumulative >= total / 2) {
                splitAt = i + 1
                break
            }
        }
        splitAt = splitAt.coerceIn(1, sortedColors.size - 1)

        boxes[largestIndex] = Box(
            sortedColors.subList(0, splitAt).toMutableList(),
            sortedWeights.subList(0, splitAt).toMutableList(),
        )
        boxes.add(
            Box(
                sortedColors.subList(splitAt, sortedColors.size).toMutableList(),
                sortedWeights.subList(splitAt, sortedWeights.size).toMutableList(),
            ),
        )
    }

    return IntArray(boxes.size) { i ->
        val box = boxes[i]
        var rSum = 0L
        var gSum = 0L
        var bSum = 0L
        var aSum = 0L
        var wSum = 0L
        for (j in box.colors.indices) {
            val c = box.colors[j]
            val w = box.weights[j].toLong()
            rSum += channel(c, 24) * w
            gSum += channel(c, 16) * w
            bSum += channel(c, 8) * w
            aSum += channel(c, 0) * w
            wSum += w
        }
        val r = (rSum / wSum).toInt()
        val g = (gSum / wSum).toInt()
        val b = (bSum / wSum).toInt()
        val a = (aSum / wSum).toInt()
        (r shl 24) or (g shl 16) or (b shl 8) or a
    }
}

private fun nearestPaletteIndex(color: Int, palette: IntArray): Int {
    val r = (color ushr 24) and 0xFF
    val g = (color ushr 16) and 0xFF
    val b = (color ushr 8) and 0xFF
    val a = color and 0xFF
    var best = 0
    var bestDist = Int.MAX_VALUE
    for (i in palette.indices) {
        val pc = palette[i]
        val dr = r - ((pc ushr 24) and 0xFF)
        val dg = g - ((pc ushr 16) and 0xFF)
        val db = b - ((pc ushr 8) and 0xFF)
        val da = a - (pc and 0xFF)
        val dist = dr * dr + dg * dg + db * db + da * da
        if (dist < bestDist) {
            bestDist = dist
            best = i
        }
    }
    return best
}

private fun quantizeIndices(pixels: IntArray, palette: IntArray): ByteArray {
    val cache = HashMap<Int, Int>()
    val indices = ByteArray(pixels.size)
    for (i in pixels.indices) {
        val c = pixels[i]
        val idx = cache.getOrPut(c) { nearestPaletteIndex(c, palette) }
        indices[i] = idx.toByte()
    }
    return indices
}

private fun assembleIndexedPng(
    width: Int,
    height: Int,
    palette: IntArray,
    indices: ByteArray,
    colorspaceChunks: List<PngChunk>,
): ByteArray {
    val hasAlpha = palette.any { (it and 0xFF) != 0xFF }
    val plte = ByteArrayOutputStream(palette.size * 3).use { o ->
        for (c in palette) {
            o.write((c ushr 24) and 0xFF)
            o.write((c ushr 16) and 0xFF)
            o.write((c ushr 8) and 0xFF)
        }
        o.toByteArray()
    }
    val trns = if (hasAlpha) {
        ByteArrayOutputStream(palette.size).use { o ->
            for (c in palette) o.write(c and 0xFF)
            o.toByteArray()
        }
    } else {
        null
    }
    return assemblePng(width, height, 8, 3, plte, trns, colorspaceChunks, indices, 1)
}

/** Re-picks the optimal per-row filter and recompresses at max zlib level -- the optipng role. */
private fun structuralOptimize(bytes: ByteArray): ByteArray? {
    val chunks = parseChunks(bytes) ?: return null
    val ihdrChunk = chunks.firstOrNull { it.type == "IHDR" } ?: return null
    val header = parseIhdr(ihdrChunk.data)
    if (header.interlace != 0 || header.bitDepth != 8) return null
    val samples = samplesForColorType(header.colorType)
    if (samples <= 0) return null

    val idatData = ByteArrayOutputStream().use { out ->
        chunks.filter { it.type == "IDAT" }.forEach { out.write(it.data) }
        out.toByteArray()
    }
    if (idatData.isEmpty()) return null

    val raw = try {
        inflate(idatData)
    } catch (e: Exception) {
        return null
    }
    val stride = header.width * samples
    val expected = (stride + 1) * header.height
    if (raw.size < expected) return null

    val sampleBytes = unfilter(raw, header.height, stride, samples)
    val plte = chunks.firstOrNull { it.type == "PLTE" }?.data
    val trns = chunks.firstOrNull { it.type == "tRNS" }?.data
    val ancillary = chunks.filter {
        it.type !in setOf("IHDR", "PLTE", "tRNS", "IDAT", "IEND") && it.type !in STRIPPABLE_CHUNK_TYPES
    }

    return assemblePng(
        header.width,
        header.height,
        header.bitDepth,
        header.colorType,
        plte,
        trns,
        ancillary,
        sampleBytes,
        samples,
    )
}

/** Metadata-strip + max recompression only, no re-filtering -- fallback for what [structuralOptimize] can't decode. */
private fun legacyRecompressAndStrip(bytes: ByteArray): ByteArray? {
    val chunks = parseChunks(bytes) ?: return null
    val idatData = ByteArrayOutputStream().use { out ->
        chunks.filter { it.type == "IDAT" }.forEach { out.write(it.data) }
        out.toByteArray()
    }
    if (idatData.isEmpty()) return null
    val raw = try {
        inflate(idatData)
    } catch (e: Exception) {
        return null
    }
    val recompressed = deflate(raw)

    return ByteArrayOutputStream(bytes.size).use { baos ->
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
                chunk.type in STRIPPABLE_CHUNK_TYPES -> Unit
                else -> writeChunk(baos, chunk.type, chunk.data)
            }
        }
        baos.toByteArray()
    }
}

/**
 * pngquant-equivalent (unconditional 64-color quantization, kept only if smaller) followed by
 * an optipng-equivalent structural pass -- except for nine-patch files, which only get
 * quantized, matching how these are handled upstream.
 */
private fun optimizePng(original: ByteArray, isNinePatch: Boolean): OptimizeResult {
    val chunks = parseChunks(original) ?: return OptimizeResult.Skipped("parse failed (corrupt or not a PNG)")
    val ihdrChunk = chunks.firstOrNull { it.type == "IHDR" } ?: return OptimizeResult.Skipped("missing IHDR")
    val header = parseIhdr(ihdrChunk.data)

    var working = original

    if (header.colorType != 3) {
        val rgba = decodeToRgba(chunks, header)
        if (rgba != null) {
            try {
                val palette = buildPalette(rgba, MAX_PALETTE_COLORS)
                val indices = quantizeIndices(rgba, palette)
                val colorspaceChunks = chunks.filter { it.type in COLORSPACE_CHUNK_TYPES }
                val quantized = assembleIndexedPng(header.width, header.height, palette, indices, colorspaceChunks)
                if (quantized.size < working.size) working = quantized
            } catch (e: Exception) {
                logger.fine("PNG quantize skipped (${header.width}x${header.height}): ${e.message}")
            }
        }
    }

    if (!isNinePatch) {
        val structural = try {
            structuralOptimize(working)
        } catch (e: Exception) {
            null
        }
        working = if (structural != null && structural.size < working.size) {
            structural
        } else {
            legacyRecompressAndStrip(working)?.takeIf { it.size < working.size } ?: working
        }
    }

    return if (working.size < original.size) {
        OptimizeResult.Success(working, original.size - working.size)
    } else {
        OptimizeResult.Skipped("already optimal")
    }
}

// PngOptimizerPatch.kt
val pngOptimizerPatch = resourcePatch(
    name = "Png Optimizer",
    description = "Quantizes PNG images to a 64-color palette (matching pngquant) and re-picks " +
        "the optimal per-row filter before recompressing at maximum zlib compression (matching " +
        "optipng), stripping hidden metadata along the way. Nine-patch (.9.png) files are only " +
        "quantized, never re-filtered, mirroring how these are handled upstream. Quantization " +
        "is skipped for images it can't safely decode (16-bit depth, interlaced, or " +
        "already-indexed); those still get the filter/recompression pass. A file is only " +
        "rewritten when the result is actually smaller.",
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
            val isNinePatch = file.name.endsWith(".9.png", ignoreCase = true)
            val result = try {
                optimizePng(original, isNinePatch)
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
                        result.reason.startsWith("parse") || result.reason.startsWith("missing") ->
                            parseFailedCount.incrementAndGet()
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
