package app.ftl.patches.DexDebugInfo

import app.morphe.patcher.patch.bytecodePatch
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.zip.Adler32

private const val NO_INDEX = -1

private fun u32(buffer: ByteBuffer, offset: Int): Int =
    buffer.getInt(offset)

private fun uleb(buffer: ByteBuffer, start: Int): Pair<Int, Int> {
    var result = 0
    var shift = 0
    var p = start

    while (true) {
        val b = buffer.get(p).toInt() and 0xFF
        result = result or ((b and 0x7F) shl shift)
        p++

        if ((b and 0x80) == 0) return result to p

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

    val classDefsSize = u32(buffer, 0x60)
    val classDefsOff = u32(buffer, 0x64)

    require(classDefsSize >= 0) { "Invalid class_defs_size" }
    require(classDefsOff >= 0) { "Invalid class_defs_off" }
    require(classDefsSize <= (buffer.limit() - classDefsOff) / 32) {
        "Invalid class_defs range"
    }

    var changed = 0

    fun clearCodeDebugInfo(codeOff: Int) {
        if (codeOff == 0) return

        require(codeOff >= 0 && codeOff <= buffer.limit() - 16) {
            "Invalid code_item offset"
        }

        // code_item.debug_info_off
        if (u32(buffer, codeOff + 8) != 0) {
            buffer.putInt(codeOff + 8, 0)
            changed++
        }
    }

    fun parseClassData(classDataOff: Int) {
        if (classDataOff == 0) return

        require(classDataOff >= 0 && classDataOff < buffer.limit()) {
            "Invalid class_data_off"
        }

        var p = classDataOff

        val (staticFields, p1) = uleb(buffer, p)
        p = p1
        val (instanceFields, p2) = uleb(buffer, p)
        p = p2
        val (directMethods, p3) = uleb(buffer, p)
        p = p3
        val (virtualMethods, p4) = uleb(buffer, p)
        p = p4

        repeat(staticFields) {
            p = uleb(buffer, p).second
            p = uleb(buffer, p).second
        }

        repeat(instanceFields) {
            p = uleb(buffer, p).second
            p = uleb(buffer, p).second
        }

        fun parseMethods(count: Int) {
            repeat(count) {
                p = uleb(buffer, p).second // method_idx_diff
                p = uleb(buffer, p).second // access_flags

                val (codeOff, next) = uleb(buffer, p)
                p = next

                clearCodeDebugInfo(codeOff)
            }
        }

        parseMethods(directMethods)
        parseMethods(virtualMethods)
    }

    repeat(classDefsSize) { index ->
        val off = classDefsOff + index * 32

        // class_def_item.source_file_idx
        if (u32(buffer, off + 8) != NO_INDEX) {
            buffer.putInt(off + 8, NO_INDEX)
            changed++
        }

        // class_def_item.class_data_off
        parseClassData(u32(buffer, off + 24))
    }

    return changed
}

private fun updateDexHeader(buffer: ByteBuffer) {
    val limit = buffer.limit()
    val chunk = ByteArray(32)

    // SHA-1 signature: bytes 32..EOF -> header[12..31].
    val sha1 = MessageDigest.getInstance("SHA-1")
    var pos = 32

    while (pos < limit) {
        val count = minOf(chunk.size, limit - pos)
        val view = buffer.duplicate()
        view.position(pos)
        view.limit(pos + count)
        view.get(chunk, 0, count)
        sha1.update(chunk, 0, count)
        pos += count
    }

    val signature = sha1.digest()
    for (i in signature.indices) {
        buffer.put(12 + i, signature[i])
    }

    // Adler-32 checksum: bytes 12..EOF -> header[8..11].
    val adler = Adler32()
    pos = 12

    while (pos < limit) {
        val count = minOf(chunk.size, limit - pos)
        val view = buffer.duplicate()
        view.position(pos)
        view.limit(pos + count)
        view.get(chunk, 0, count)
        adler.update(chunk, 0, count)
        pos += count
    }

    buffer.putInt(8, adler.value.toInt())
}

private fun patchMappedFile(mappedFile: Any): Int {
    val bufferMethod = mappedFile.javaClass.getMethod("getBuffer")
    val forceMethod = mappedFile.javaClass.getMethod("force")

    val buffer = bufferMethod.invoke(mappedFile) as ByteBuffer
    val changed = patchDex(buffer)

    if (changed != 0) {
        updateDexHeader(buffer)
        forceMethod.invoke(mappedFile)
    }

    return changed
}

val removeAllDexDebugInfoPatch = bytecodePatch(
    name = "Remove all DEX debug info",
    description = "Removes .source and all method debug_info data from every original DEX without creating mutable Smali methods.",
    default = true,
) {
    execute {
        val field = javaClass.getDeclaredField("originalDexMappings")
        field.isAccessible = true

        @Suppress("UNCHECKED_CAST")
        val mappings = field.get(this) as Map<Any, Any>

        var totalChanged = 0

        mappings.values.forEach { mappedFile ->
            totalChanged += patchMappedFile(mappedFile)
        }

        println("Remove all DEX debug info: changed $totalChanged DEX fields")
    }
}
