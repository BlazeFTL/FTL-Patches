package app.ftl.patches.firefox

import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.builder.BuilderOffsetInstruction
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ThreeRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import java.util.BitSet

internal class Liveness(private val method: MutableMethod) {

    private val instructions = method.implementation!!.instructions.toList()
    private val registerCount = method.implementation!!.registerCount
    private val liveIn = Array(instructions.size + 1) { BitSet(registerCount) }
    private val everything = BitSet(registerCount).also { it.set(0, registerCount) }

    init {
        var changed = true
        while (changed) {
            changed = false
            for (i in instructions.indices.reversed()) {
                val out = BitSet(registerCount)
                var all = false
                successors(i).forEach { s ->
                    if (s == ALL) all = true else if (s < liveIn.size) out.or(liveIn[s])
                }
                val (defs, uses) = defsUses(instructions[i])
                val result = if (all) everything.clone() as BitSet else out
                if (!all) defs.forEach { if (it in 0 until registerCount) result.clear(it) }
                uses.forEach { if (it in 0 until registerCount) result.set(it) }
                if (result != liveIn[i]) {
                    liveIn[i] = result
                    changed = true
                }
            }
        }
    }

    fun isLive(index: Int, register: Int) = liveIn[index].get(register)

    fun free(index: Int, joins: List<Int> = emptyList(), exclude: Set<Int> = emptySet()): List<Int> =
        (0 until registerCount).filter { r ->
            r !in exclude && !liveIn[index].get(r) && joins.none { liveIn[it].get(r) }
        }

    fun takeLow(count: Int, index: Int, joins: List<Int> = emptyList(), exclude: Set<Int> = emptySet()): List<Int> {
        val picked = free(index, joins, exclude).filter { it < 16 }.take(count)
        if (picked.size < count) throw IllegalStateException("not enough free registers at $index")
        return picked
    }

    fun takeRun(length: Int, index: Int, joins: List<Int> = emptyList(), exclude: Set<Int> = emptySet()): Int {
        val free = free(index, joins, exclude).toSet()
        for (base in registerCount - length downTo 0) {
            if ((base until base + length).all { it in free }) return base
        }
        throw IllegalStateException("no free register run at $index")
    }

    private fun successors(i: Int): List<Int> {
        val instruction = instructions[i]
        val name = instruction.opcode.name
        if (name.contains("switch")) return listOf(ALL)
        val result = ArrayList<Int>(2)
        if (instruction is BuilderOffsetInstruction) result.add(instruction.target.location.index)
        val terminal = name.startsWith("return") || name == "throw" || name.startsWith("goto")
        if (!terminal) result.add(i + 1)
        return result
    }

    private fun defsUses(instruction: Instruction): Pair<List<Int>, List<Int>> {
        val name = instruction.opcode.name
        val defs = ArrayList<Int>()
        val uses = ArrayList<Int>()

        fun a() = (instruction as OneRegisterInstruction).registerA
        fun b() = (instruction as TwoRegisterInstruction).registerB
        fun c() = (instruction as ThreeRegisterInstruction).registerC
        fun pair(list: MutableList<Int>, register: Int, wide: Boolean) {
            list.add(register)
            if (wide) list.add(register + 1)
        }

        val wideName = name.contains("wide") || name.endsWith("-long") || name.endsWith("-double") ||
            name.contains("-long/") || name.contains("-double/")

        when {
            name == "nop" || name.startsWith("goto") -> Unit
            name.startsWith("move-result") -> pair(defs, a(), wideName)
            name == "move-exception" -> defs.add(a())
            name.startsWith("move") -> {
                pair(defs, a(), wideName)
                pair(uses, b(), wideName)
            }
            name == "return-void" -> Unit
            name.startsWith("return") -> pair(uses, a(), wideName)
            name.startsWith("const") -> pair(defs, a(), name.startsWith("const-wide"))
            name.startsWith("monitor") || name == "throw" || name == "fill-array-data" ||
                name.contains("switch") || name == "check-cast" -> uses.add(a())
            name == "instance-of" || name == "array-length" || name == "new-array" -> {
                defs.add(a())
                uses.add(b())
            }
            name == "new-instance" -> defs.add(a())
            name.startsWith("if-") -> {
                uses.add(a())
                if (!name.endsWith("z")) uses.add(b())
            }
            name.startsWith("cmp") -> {
                defs.add(a())
                pair(uses, b(), true)
                pair(uses, c(), true)
            }
            name.startsWith("aget") -> {
                pair(defs, a(), wideName)
                uses.add(b())
                uses.add(c())
            }
            name.startsWith("aput") -> {
                pair(uses, a(), wideName)
                uses.add(b())
                uses.add(c())
            }
            name.startsWith("iget") -> {
                pair(defs, a(), wideName)
                uses.add(b())
            }
            name.startsWith("iput") -> {
                pair(uses, a(), wideName)
                uses.add(b())
            }
            name.startsWith("sget") -> pair(defs, a(), wideName)
            name.startsWith("sput") -> pair(uses, a(), wideName)
            name.startsWith("invoke") || name.startsWith("filled-new-array") -> {
                if (instruction is RegisterRangeInstruction) {
                    for (r in instruction.startRegister until instruction.startRegister + instruction.registerCount) uses.add(r)
                } else if (instruction is FiveRegisterInstruction) {
                    val registers = listOf(
                        instruction.registerC, instruction.registerD, instruction.registerE,
                        instruction.registerF, instruction.registerG,
                    )
                    uses.addAll(registers.take(instruction.registerCount))
                }
            }
            name.contains("-to-") -> {
                val source = name.substringBefore("-to-")
                val target = name.substringAfter("-to-")
                pair(defs, a(), target == "long" || target == "double")
                pair(uses, b(), source == "long" || source == "double")
            }
            name.startsWith("neg-") || name.startsWith("not-") -> {
                pair(defs, a(), wideName)
                pair(uses, b(), wideName)
            }
            name.endsWith("/2addr") -> {
                pair(defs, a(), wideName)
                pair(uses, a(), wideName)
                pair(uses, b(), wideName)
            }
            name.endsWith("/lit8") || name.endsWith("/lit16") -> {
                defs.add(a())
                uses.add(b())
            }
            else -> {
                pair(defs, a(), wideName)
                pair(uses, b(), wideName)
                pair(uses, c(), wideName)
            }
        }
        return defs to uses
    }

    private companion object {
        const val ALL = -1
    }
}
