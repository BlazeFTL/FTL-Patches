package app.ftl.patches.DexDebugInfo

import app.morphe.patcher.patch.bytecodePatch
import java.nio.ByteBuffer
import java.nio.ByteOrder

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

        if ((b and 0x80) == 0) {
            return result to p
        }

        shift += 7
        require(shift <= 28) { "Invalid ULEB128" }
    }
}

private fun clearDexDebugInfo(buffer: ByteBuffer): Int {
    buffer.order(ByteOrder.LITTLE_ENDIAN)

    require(buffer.limit() >= 0x70) { "Invalid DEX" }

    val magic = ByteArray(4)
    buffer.position(0)
    buffer.get(magic)

    require(
        magic[0] == 'd'.code.toByte() &&
        magic[1] == 'e'.code.toByte() &&
        magic[2] == 'x'.code.toByte() &&
        magic[3] == '\n'.code.toByte()
    ) {
        "Invalid DEX magic"
    }

    val classDefsSize = u32(buffer, 0x60)
    val classDefsOff = u32(buffer, 0x64)

    require(
        classDefsSize >= 0 &&
        classDefsOff >= 0 &&
        classDefsOff <= buffer.limit() - classDefsSize * 32
    ) {
        "Invalid class_defs"
    }

    var changed = 0

    fun clearCodeDebugInfo(codeOff: Int) {
        if (codeOff == 0) return
        require(codeOff >= 0 && codeOff <= buffer.limit() - 16) {
            "Invalid code_item offset"
        }

        val debugInfoOff = u32(buffer, codeOff + 8)

        if (debugInfoOff != 0) {
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
                p = uleb(buffer, p).second
                p = uleb(buffer, p).second

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
        if (u32(buffer, off + 8) != -1) {
            buffer.putInt(off + 8, -1)
            changed++
        }

        // class_def_item.class_data_off
        val classDataOff = u32(buffer, off + 24)
        parseClassData(classDataOff)
    }

    return changed
}

private fun patchMappedDex(mappedFile: Any): Int {
    val bufferMethod = mappedFile.javaClass.methods.firstOrNull {
        it.name == "getBuffer" && it.parameterCount == 0
    } ?: error("MappedFile buffer API not found")

    val forceMethod = mappedFile.javaClass.methods.firstOrNull {
        it.name == "force" && it.parameterCount == 0
    } ?: error("MappedFile force API not found")

    val buffer = bufferMethod.invoke(mappedFile) as ByteBuffer
    val changed = clearDexDebugInfo(buffer)

    if (changed != 0) {
        forceMethod.invoke(mappedFile)
    }

    return changed
}

val removeAllDexDebugInfoPatch = bytecodePatch(
    name = "Remove all DEX debug info",
    description = "Removes source-file references and debug-info references from every original DEX without creating mutable Smali methods.",
    default = true,
) {
    execute {
        // Current Morphe keeps original DEX files in a private map of writable
        // memory mappings. Access it reflectively so this patch can modify the
        // raw DEX bytes without constructing MutableClass/MutableMethod objects.
        val mappingsField = javaClass.getDeclaredField("originalDexMappings")
        mappingsField.isAccessible = true

        @Suppress("UNCHECKED_CAST")
        val mappings = mappingsField.get(this) as Map<Any, Any>

        mappings.values.forEach { mappedFile ->
            patchMappedDex(mappedFile)
        }
    }
}
