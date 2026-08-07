package app.ftl.patches.resources

import app.morphe.patcher.patch.rawResourcePatch
import java.io.ByteArrayOutputStream

private val STRIP_CHUNKS = setOf("tEXt", "zTXt", "iTXt", "tIME")
private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

private fun stripPngMetadata(bytes: ByteArray): ByteArray {
    if (bytes.size < 8 || !bytes.copyOfRange(0, 8).contentEquals(PNG_SIGNATURE)) return bytes

    val output = ByteArrayOutputStream(bytes.size)
    output.write(PNG_SIGNATURE)

    var offset = 8
    while (offset + 8 <= bytes.size) {
        val length = ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)
        val type = String(bytes, offset + 4, 4, Charsets.US_ASCII)
        val chunkEnd = offset + 12 + length

        if (type !in STRIP_CHUNKS) {
            output.write(bytes, offset, chunkEnd - offset)
        }

        offset = chunkEnd
        if (type == "IEND") break
    }

    return output.toByteArray()
}

val drawableCleanPatch = rawResourcePatch(
    name = "Drawable clean",
    description = "Strips PNG text, timestamp and color-profile metadata from drawable resources.",
) {
    execute {
        val resDir = get("res", false)
        resDir.walkTopDown()
            .filter { it.isFile && it.extension.equals("png", ignoreCase = true) }
            .forEach { file -> file.writeBytes(stripPngMetadata(file.readBytes())) }
    }
}
