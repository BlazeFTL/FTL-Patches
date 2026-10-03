package app.ftl.patches.firefox

import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction

internal typealias Target = MutableMethod.() -> Int

internal class Edit(val index: Int, val apply: MutableMethod.() -> Unit)

internal fun MutableMethod.applyEdits(vararg edits: Edit) {
    edits.sortedByDescending { it.index }.forEach { it.apply(this) }
}

internal fun MutableMethod.swapAt(
    index: Int,
    scratch: Int,
    old: String,
    count: Int = 1,
    fallThrough: Boolean = true,
    labels: Map<String, Target> = emptyMap(),
) {
    val first = getInstruction(index)
    val after = getInstruction(index + count)
    val extra = labels.map { (name, target) -> ExternalLabel(name, getInstruction(target())) }
    val skip = if (fallThrough && count > 0) "goto :ftl_after" else ""
    addInstructionsWithLabels(
        index,
        """
            invoke-static {}, $MOD_SETTINGS->oldMenu()Z
            move-result v$scratch
            if-eqz v$scratch, :ftl_stock
            $old
            $skip
        """.trimIndent(),
        ExternalLabel("ftl_stock", first),
        ExternalLabel("ftl_after", after),
        *extra.toTypedArray(),
    )
}

internal fun swap(
    index: Int,
    scratch: Int,
    old: String,
    count: Int = 1,
    fallThrough: Boolean = true,
    labels: Map<String, Target> = emptyMap(),
) = Edit(index) { swapAt(index, scratch, old, count, fallThrough, labels) }

internal fun returnWhenOld(index: Int, scratch: Int, old: String) =
    swap(index, scratch, old, count = 0, fallThrough = false)

internal fun insert(index: Int, smali: String, labels: Map<String, Target> = emptyMap()) = Edit(index) {
    val extra = labels.map { (name, target) -> ExternalLabel(name, getInstruction(target())) }
    addInstructionsWithLabels(index, smali.trimIndent(), *extra.toTypedArray())
}

internal fun floatTo(index: Int, bits: Int) = Edit(index) {
    val register = getInstruction<OneRegisterInstruction>(index).registerA
    swapAt(index, register, "const/high16 v$register, 0x${Integer.toHexString(bits)}")
}

internal fun zeroTo(index: Int) = Edit(index) {
    val register = getInstruction<OneRegisterInstruction>(index).registerA
    swapAt(index, register, "const/16 v$register, 0x0")
}

internal fun surface(index: Int) = Edit(index) {
    val instruction = getInstruction<TwoRegisterInstruction>(index)
    val a = instruction.registerA
    val b = instruction.registerB
    swapAt(index, if (a == b) a + 1 else a, "iget-wide v$a, v$b, $COLOR_SCHEME->surface:J")
}
