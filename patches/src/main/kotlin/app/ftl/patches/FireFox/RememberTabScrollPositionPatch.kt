/*
 * Remember tab scroll position.
 *
 * Tab tray rebuilds its LazyList/LazyGrid state on every open. Two hooks:
 *  1. Each remembered list/grid state in TabLayoutKt goes through ScrollMemory.pick(), which returns the state
 *     from the previous composition (keyed by composite key) instead of the fresh one.
 *  2. TabLayoutScrollHelper builds a lambda that scrolls to the selected tab. Its index arg goes through
 *     ScrollMemory.resolveScrollIndex(), which returns -1 (no scroll) when the state was restored and the
 *     selected tab is already visible.
 *
 * How each piece is found without obfuscated names (use this to find it again):
 *  - state types: constructor parameter 0 of Fenix ListReorderState (list) and GridReorderState (grid).
 *  - remember call: static call in TabLayoutKt with params (Composer) returning that state type, followed by
 *    move-result-object. Exactly 2 per state type.
 *  - helper hook: TabLayoutKt.TabLayoutScrollHelper*(...) contains invoke-direct/range of a
 *    TabLayoutKt$TabLayoutScrollHelper*.<init>(I, Z, Z, ScrollableState, I, Continuation).
 *  - Composer.getCompositeKeyHashCode and state item readers are generated accessors (Accessors.kt).
 */
package app.ftl.patches.firefox

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val RM_TAB_LAYOUT_KT = "Lorg/mozilla/fenix/tabstray/ui/tabpage/TabLayoutKt;"
private const val RM_SCROLL_HELPER_LAMBDA =
    "Lorg/mozilla/fenix/tabstray/ui/tabpage/TabLayoutKt\$TabLayoutScrollHelper"
private const val RM_LIST_REORDER = "Lorg/mozilla/fenix/tabstray/browser/compose/ListReorderState;"
private const val RM_GRID_REORDER = "Lorg/mozilla/fenix/tabstray/browser/compose/legacy/GridReorderState;"
private const val RM_EXTENSION = "Lapp/ftl/extension/firefox/ScrollMemory;"

private object RememberScrollHelperFingerprint : Fingerprint(
    definingClass = RM_TAB_LAYOUT_KT,
    returnType = "V",
    custom = { method, _ ->
        method.name.startsWith("TabLayoutScrollHelper") && method.parameterTypes.size == 7
    },
    filters = listOf(
        methodCall(
            definingClass = RM_SCROLL_HELPER_LAMBDA,
            name = "<init>",
            opcode = Opcode.INVOKE_DIRECT_RANGE,
        ),
    ),
)

private fun BytecodePatchContext.stateType(owner: String): String =
    mutableClassDefBy(owner).methods.firstOrNull { it.name == "<init>" }
        ?.parameterTypes?.getOrNull(0)?.toString()
        ?: throw PatchException("State type not found in $owner constructor")

private fun BytecodePatchContext.hookRememberedState(stateType: String, expected: Int) {
    val composer = resolveComposeTypes().composer
    val layout = mutableClassDefBy(RM_TAB_LAYOUT_KT)
    var hooked = 0

    for (method in layout.methods) {
        val instructions = method.implementation?.instructions?.toList() ?: continue
        // Highest index first so earlier inserts do not move later ones.
        for (i in instructions.indices.reversed()) {
            val instruction = instructions[i]
            if (instruction.opcode != Opcode.INVOKE_STATIC) continue
            val reference = (instruction as ReferenceInstruction).reference as? MethodReference ?: continue
            if (reference.returnType != stateType || reference.parameterTypes.size != 1 ||
                reference.parameterTypes[0].toString() != composer
            ) continue
            val result = instructions.getOrNull(i + 1)
            if (result == null || result.opcode != Opcode.MOVE_RESULT_OBJECT) continue

            val composerRegister = (instruction as FiveRegisterInstruction).registerC
            val state = (result as OneRegisterInstruction).registerA
            val call = method.staticCall(
                i + 2,
                "$RM_EXTENSION->pick(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                listOf(composerRegister, state),
            )
            method.addInstructions(
                i + 2,
                """
                    $call
                    move-result-object v$state
                    check-cast v$state, $stateType
                """,
            )
            hooked++
        }
    }
    if (hooked != expected) throw PatchException("Expected $expected $stateType remember calls, hooked $hooked")
}

private fun BytecodePatchContext.hookScrollIndex() {
    val fingerprint = RememberScrollHelperFingerprint
    val init = fingerprint.instructionMatches[0]
    val range = init.getInstruction<RegisterRangeInstruction>()
    val reference = init.getInstruction<ReferenceInstruction>().reference as MethodReference
    val parameters = reference.parameterTypes.map { it.toString() }
    if (range.registerCount != 7 || parameters.size != 6 ||
        parameters.take(3) != listOf("I", "Z", "Z") || parameters[4] != "I" || parameters[3] == "I"
    ) {
        throw PatchException("Unexpected TabLayoutScrollHelper lambda constructor: $reference")
    }

    val index = range.startRegister + 1
    val last = range.startRegister + 4
    fingerprint.method.addInstructions(
        init.index,
        """
            invoke-static/range { v$index .. v$last }, $RM_EXTENSION->resolveScrollIndex(IZZLjava/lang/Object;)I
            move-result v$index
        """,
    )
}

@Suppress("unused")
val rememberTabScrollPositionPatch = bytecodePatch(
    name = "Remember tab scroll position",
    description = "Keeps the tab tray scroll position when it is closed and reopened.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_FIREFOX_NIGHTLY)

    extendWith("extensions/firefox.mpe")

    execute {
        installScrollAccessors()
        hookRememberedState(stateType(RM_GRID_REORDER), 2)
        hookRememberedState(stateType(RM_LIST_REORDER), 2)
        hookScrollIndex()
    }
}
