package app.ftl.patches.DexDebugInfo

import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.bytecodePatch
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.zip.Adler32

private fun readUInt(buffer: ByteBuffer, offset: Int): Int =
    buffer.getInt(offset)

private fun readUleb128(buffer: ByteBuffer, start: Int): Pair<Int, Int> {
    var result = 0
    var shift = 0
    var pos = start

    while (true) {
        val value = buffer.get(pos).toInt() and 0xFF
        pos++
        result = result or ((value and 0x7F) shl shift)

        if ((value and 0x80) == 0) {
            return result to pos
        }

        shift += 7
        require(shift <= 28) { "Invalid ULEB128" }
    }
}

private fun patchDex(buffer: ByteBuffer): Int {
    buffer.order(ByteOrder.LITTLE_ENDIAN)

    require(buffer.limit() >= 0x70) { "Invalid DEX" }

    require(
        buffer.get(0) == 'd'.code.toByte() &&
        buffer.get(1) == 'e'.code.toByte() &&
        buffer.get(2) == 'x'.code.toByte() &&
        buffer.get(3) == '\n'.code.toByte()
    ) {
        "Invalid DEX magic"
    }

    val classDefsSize = readUInt(buffer, 0x60)
    val classDefsOff = readUInt(buffer, 0x64)

    require(classDefsSize >= 0)
    require(classDefsOff >= 0)
    require(classDefsSize <= (buffer.limit() - classDefsOff) / 32)

    var changed = 0

    fun clearCodeDebugInfo(codeOff: Int) {
        if (codeOff == 0) return

        require(codeOff >= 0 && codeOff <= buffer.limit() - 16)

        // code_item.debug_info_off
        if (readUInt(buffer, codeOff + 8) != 0) {
            buffer.putInt(codeOff + 8, 0)
            changed++
        }
    }

    fun parseClassData(classDataOff: Int) {
        if (classDataOff == 0) return

        require(classDataOff >= 0 && classDataOff < buffer.limit())

        var pos = classDataOff

        val (staticFieldsSize, p1) = readUleb128(buffer, pos)
        pos = p1
        val (instanceFieldsSize, p2) = readUleb128(buffer, pos)
        pos = p2
        val (directMethodsSize, p3) = readUleb128(buffer, pos)
        pos = p3
        val (virtualMethodsSize, p4) = readUleb128(buffer, pos)
        pos = p4

        repeat(staticFieldsSize + instanceFieldsSize) {
            pos = readUleb128(buffer, pos).second
            pos = readUleb128(buffer, pos).second
        }

        repeat(directMethodsSize + virtualMethodsSize) {
            pos = readUleb128(buffer, pos).second // method_idx_diff
            pos = readUleb128(buffer, pos).second // access_flags

            val (codeOff, next) = readUleb128(buffer, pos)
            pos = next

            clearCodeDebugInfo(codeOff)
        }
    }

    repeat(classDefsSize) { index ->
        val classDefOff = classDefsOff + index * 32
        val classDataOff = readUInt(buffer, classDefOff + 24)
        parseClassData(classDataOff)
    }

    return changed
}

private fun updateDexHeader(buffer: ByteBuffer) {
    val fileSize = buffer.limit()
    val chunk = ByteArray(8192)

    val sha1 = MessageDigest.getInstance("SHA-1")
    var pos = 32

    while (pos < fileSize) {
        val count = minOf(chunk.size, fileSize - pos)
        val view = buffer.duplicate()
        view.position(pos)
        view.limit(pos + count)
        view.get(chunk, 0, count)
        sha1.update(chunk, 0, count)
        pos += count
    }

    buffer.position(12)
    buffer.put(sha1.digest())

    val adler = Adler32()
    pos = 12

    while (pos < fileSize) {
        val count = minOf(chunk.size, fileSize - pos)
        val view = buffer.duplicate()
        view.position(pos)
        view.limit(pos + count)
        view.get(chunk, 0, count)
        adler.update(chunk, 0, count)
        pos += count
    }

    buffer.putInt(8, adler.value.toInt())
}

private fun patchOriginalDexMappings(context: BytecodePatchContext): Int {
    val field = BytecodePatchContext::class.java.getDeclaredField("originalDexMappings")
    field.isAccessible = true

    @Suppress("UNCHECKED_CAST")
    val mappings = field.get(context) as Map<Any, Any>

    var totalChanged = 0

    mappings.values.forEach { mappedFile ->
        val getBuffer = mappedFile.javaClass.getMethod("getBuffer")
        val force = mappedFile.javaClass.getMethod("force")

        val buffer = getBuffer.invoke(mappedFile) as ByteBuffer
        buffer.order(ByteOrder.LITTLE_ENDIAN)

        val changed = patchDex(buffer)

        if (changed > 0) {
            updateDexHeader(buffer)
            force.invoke(mappedFile)
            totalChanged += changed
        }
    }

    return totalChanged
}

val removeAllDexDebugInfoPatch = bytecodePatch(
    name = "Remove all DEX debug info",
    description = "Removes every method debug_info reference from every original DEX without creating MutableMethodImplementation objects.",
    default = true,
) {
    execute {
        val changed = patchOriginalDexMappings(this)
        println("Remove all DEX debug info: cleared $changed debug_info references")
    }
}
