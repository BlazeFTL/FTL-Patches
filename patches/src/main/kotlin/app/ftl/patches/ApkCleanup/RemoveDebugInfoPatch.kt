package app.ftl.patches.apkcleanup

import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableClass
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
            pos = readUleb128(buffer, pos).second
            pos = readUleb128(buffer, pos).second

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
    name = "Remove Debug Info",
    description = "Removes debug information (line numbers, variable names, source file references) from every class in the .dex files to reduce overall APK size.",
    default = false,
) {
    execute {
        val rawChanged = patchOriginalDexMappings(this)

        var sourceCleared = 0
        var lineCleared = 0

        classDefForEach { classDef ->
            if (classDef !is MutableClass) return@classDefForEach

            if (classDef.sourceFile != null) {
                classDef.setSourceFile(null)
                sourceCleared++
            }

            classDef.methods.forEach { method ->
                val impl = method.implementation ?: return@forEach
                var cleared = false
                impl.instructions.forEach { insn ->
                    val items = insn.location.debugItems
                    if (items.isNotEmpty()) {
                        items.clear()
                        cleared = true
                    }
                }
                if (cleared) lineCleared++
            }
        }

        println("Remove all DEX debug info: raw pass cleared $rawChanged debug_info references; in-memory pass cleared .source from $sourceCleared classes, .line from $lineCleared methods")
    }
}
