package your.patches.category

import app.morphe.patcher.patch.rawResourcePatch
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.zip.Adler32

private const val CHECKSUM_OFF = 8
private const val SIGNATURE_OFF = 12
private const val SIGNATURE_SIZE = 20
private const val CLASS_DEFS_SIZE_OFF = 96
private const val CLASS_DEFS_OFF_OFF = 100
private const val CLASS_DEF_ITEM_SIZE = 32
private const val CLASS_DEF_CLASS_DATA_OFF = 24
private const val CODE_ITEM_DEBUG_INFO_OFF = 8

private fun readUleb128(buf: ByteBuffer, pos: Int): Pair<Int, Int> {
    var result = 0
    var shift = 0
    var p = pos
    while (true) {
        val b = buf.get(p++).toInt() and 0xFF
        result = result or ((b and 0x7F) shl shift)
        if (b and 0x80 == 0) break
        shift += 7
    }
    return result to p
}

private fun skipUleb128(buf: ByteBuffer, pos: Int): Int {
    var p = pos
    while (buf.get(p++).toInt() and 0x80 != 0) { /* skip */ }
    return p
}

private fun stripDebugInfo(file: File): Boolean {
    val bytes = file.readBytes()
    val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

    val classDefsSize = buf.getInt(CLASS_DEFS_SIZE_OFF)
    val classDefsOff = buf.getInt(CLASS_DEFS_OFF_OFF)
    var changed = false

    for (i in 0 until classDefsSize) {
        val entryOff = classDefsOff + i * CLASS_DEF_ITEM_SIZE
        val classDataOff = buf.getInt(entryOff + CLASS_DEF_CLASS_DATA_OFF)
        if (classDataOff == 0) continue

        var pos = classDataOff
        val (staticFieldsSize, p1) = readUleb128(buf, pos); pos = p1
        val (instanceFieldsSize, p2) = readUleb128(buf, pos); pos = p2
        val (directMethodsSize, p3) = readUleb128(buf, pos); pos = p3
        val (virtualMethodsSize, p4) = readUleb128(buf, pos); pos = p4

        repeat(staticFieldsSize + instanceFieldsSize) {
            pos = skipUleb128(buf, pos)
            pos = skipUleb128(buf, pos)
        }

        repeat(directMethodsSize + virtualMethodsSize) {
            pos = skipUleb128(buf, pos)
            pos = skipUleb128(buf, pos)
            val (codeOff, p5) = readUleb128(buf, pos); pos = p5

            if (codeOff != 0 && buf.getInt(codeOff + CODE_ITEM_DEBUG_INFO_OFF) != 0) {
                buf.putInt(codeOff + CODE_ITEM_DEBUG_INFO_OFF, 0)
                changed = true
            }
        }
    }

    if (!changed) return false

    val sha1 = MessageDigest.getInstance("SHA-1")
    sha1.update(bytes, SIGNATURE_OFF + SIGNATURE_SIZE, bytes.size - SIGNATURE_OFF - SIGNATURE_SIZE)
    sha1.digest().copyInto(bytes, SIGNATURE_OFF)

    val adler = Adler32()
    adler.update(bytes, SIGNATURE_OFF, bytes.size - SIGNATURE_OFF)
    buf.putInt(CHECKSUM_OFF, adler.value.toInt())

    file.writeBytes(bytes)
    return true
}

val removeDebugInfoPatch = rawResourcePatch(
    name = "Remove debug info",
    description = "Zeroes debug_info_off on every method's code_item across all classes*.dex files, stripping line numbers, local variable tables, and source file names.",
    default = true
) {
    execute {
        var index = 1
        while (index <= 500) {
            val name = if (index == 1) "classes.dex" else "classes$index.dex"
            val file = get(name)
            if (!file.exists()) break
            stripDebugInfo(file)
            index++
        }
    }
}
