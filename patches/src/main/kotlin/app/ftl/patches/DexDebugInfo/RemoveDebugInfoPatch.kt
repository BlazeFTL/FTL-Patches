package app.ftl.patches.DexDebugInfo

import app.morphe.patcher.patch.rawResourcePatch
import java.io.File

private fun readUleb128(data: ByteArray, offset: Int): Pair<Int, Int> {
    var result = 0
    var shift = 0
    var pos = offset

    while (true) {
        val b = data[pos].toInt() and 0xFF
        result = result or ((b and 0x7F) shl shift)
        pos++
        if ((b and 0x80) == 0) return result to pos
        shift += 7
        require(shift <= 28) { "Invalid ULEB128" }
    }
}

private fun readUleb128p1(data: ByteArray, offset: Int): Pair<Int, Int> {
    val (value, next) = readUleb128(data, offset)
    return value - 1 to next
}

private fun readUInt(data: ByteArray, offset: Int): Long {
    return (data[offset].toLong() and 0xFF) or
        ((data[offset + 1].toLong() and 0xFF) shl 8) or
        ((data[offset + 2].toLong() and 0xFF) shl 16) or
        ((data[offset + 3].toLong() and 0xFF) shl 24)
}

private fun readUshort(data: ByteArray, offset: Int): Int {
    return (data[offset].toInt() and 0xFF) or
        ((data[offset + 1].toInt() and 0xFF) shl 8)
}

private fun writeUInt(data: ByteArray, offset: Int, value: Long) {
    data[offset] = value.toInt().toByte()
    data[offset + 1] = (value ushr 8).toByte()
    data[offset + 2] = (value ushr 16).toByte()
    data[offset + 3] = (value ushr 24).toByte()
}

private fun writeUshort(data: ByteArray, offset: Int, value: Int) {
    data[offset] = value.toByte()
    data[offset + 1] = (value ushr 8).toByte()
}

private fun patchDex(data: ByteArray): Int {
    require(data.size >= 0x70) { "Invalid DEX: too small" }
    require(data[0] == 'd'.code.toByte() &&
            data[1] == 'e'.code.toByte() &&
            data[2] == 'x'.code.toByte() &&
            data[3] == '\n'.code.toByte()) {
        "Invalid DEX magic"
    }

    val fileSize = readUInt(data, 0x20).toInt()
    require(fileSize <= data.size) { "Invalid DEX: file size" }

    val classDefsSize = readUInt(data, 0x60).toInt()
    val classDefsOff = readUInt(data, 0x64).toInt()

    require(classDefsOff >= 0 && classDefsOff + classDefsSize * 32 <= data.size) {
        "Invalid DEX: class_defs"
    }

    var changed = 0

    fun clearDebugInfo(codeOff: Int) {
        if (codeOff == 0 || codeOff + 12 > data.size) return

        // code_item:
        // 00 registers_size
        // 02 ins_size
        // 04 outs_size
        // 06 tries_size
        // 08 debug_info_off
        // 0C insns_size
        val debugInfoOff = readUInt(data, codeOff + 8).toInt()
        if (debugInfoOff != 0) {
            writeUInt(data, codeOff + 8, 0)
            changed++
        }
    }

    fun parseClassData(classDataOff: Int) {
        if (classDataOff == 0) return
        require(classDataOff in 0 until data.size) { "Invalid class_data_off" }

        var p = classDataOff

        val (staticFieldsSize, p1) = readUleb128(data, p)
        p = p1
        val (instanceFieldsSize, p2) = readUleb128(data, p)
        p = p2
        val (directMethodsSize, p3) = readUleb128(data, p)
        p = p3
        val (virtualMethodsSize, p4) = readUleb128(data, p)
        p = p4

        repeat(staticFieldsSize) {
            val (_, nextField) = readUleb128(data, p)
            p = nextField
            val (_, nextFlags) = readUleb128(data, p)
            p = nextFlags
        }

        repeat(instanceFieldsSize) {
            val (_, nextField) = readUleb128(data, p)
            p = nextField
            val (_, nextFlags) = readUleb128(data, p)
            p = nextFlags
        }

        fun parseMethods(count: Int) {
            var methodIndex = 0

            repeat(count) {
                val (methodIndexDiff, nextMethod) = readUleb128(data, p)
                p = nextMethod
                methodIndex += methodIndexDiff

                val (_, nextFlags) = readUleb128(data, p)
                p = nextFlags

                val (codeOff, nextCode) = readUleb128(data, p)
                p = nextCode

                clearDebugInfo(codeOff)
            }
        }

        parseMethods(directMethodsSize)
        parseMethods(virtualMethodsSize)
    }

    repeat(classDefsSize) { index ->
        val classDefOff = classDefsOff + index * 32

        // class_def_item.source_file_idx at +8.
        val sourceFileIdx = readUInt(data, classDefOff + 8).toInt()
        if (sourceFileIdx != 0xFFFFFFFF) {
            writeUInt(data, classDefOff + 8, 0xFFFFFFFFL)
            changed++
        }

        // class_def_item.class_data_off at +24.
        val classDataOff = readUInt(data, classDefOff + 24).toInt()
        parseClassData(classDataOff)
    }

    return changed
}

private fun patchDexFile(file: File): Int {
    val data = file.readBytes()
    val changed = patchDex(data)
    if (changed != 0) file.writeBytes(data)
    return changed
}

val removeAllDexDebugInfoPatch = rawResourcePatch(
    name = "Remove all DEX debug info",
    description = "Removes source-file references and debug-info references from every DEX file.",
    default = true,
) {
    execute {
        var dexIndex = 1

        while (true) {
            val path = if (dexIndex == 1) "classes.dex" else "classes$dexIndex.dex"

            val file = runCatching { get(path) }.getOrNull() ?: break

            patchDexFile(file)
            dexIndex++
        }
    }
}
