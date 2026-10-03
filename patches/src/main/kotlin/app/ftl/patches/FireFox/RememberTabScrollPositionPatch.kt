package app.ftl.patches.firefox

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private val COMPATIBILITY_FIREFOX_SCROLL = Compatibility(
    packageName = "org.mozilla.fenix",
    name = "Firefox Nightly",
    targets = listOf(AppTarget(version = "159.0a1")),
)

private const val TAB_LAYOUT_KT = "Lorg/mozilla/fenix/tabstray/ui/tabpage/TabLayoutKt;"
private const val SCROLL_HELPER_LAMBDA =
    "Lorg/mozilla/fenix/tabstray/ui/tabpage/TabLayoutKt\$TabLayoutScrollHelper"
private const val COMPOSER = "Landroidx/compose/runtime/Composer;"
private const val SCROLLABLE_STATE = "Landroidx/compose/foundation/gestures/ScrollableState;"
private const val LIST_STATE = "Landroidx/compose/foundation/lazy/LazyListState;"
private const val GRID_STATE = "Landroidx/compose/foundation/lazy/grid/LazyGridState;"
private const val EXTENSION = "Lapp/ftl/extension/firefox/ScrollMemory;"

private val SCROLL_HELPER_INIT_PARAMETERS = listOf(
    "I", "Z", "Z", SCROLLABLE_STATE, "I", "Lkotlin/coroutines/Continuation;",
)

private object RememberLazyGridStateFingerprint : Fingerprint(
    definingClass = TAB_LAYOUT_KT,
    filters = listOf(
        methodCall(
            smali = "Landroidx/compose/foundation/lazy/grid/LazyGridStateKt;->" +
                "rememberLazyGridState($COMPOSER)$GRID_STATE",
            opcode = Opcode.INVOKE_STATIC,
        ),
        opcode(Opcode.MOVE_RESULT_OBJECT, MatchAfterImmediately()),
    ),
)

private object RememberLazyListStateFingerprint : Fingerprint(
    definingClass = TAB_LAYOUT_KT,
    filters = listOf(
        methodCall(
            smali = "Landroidx/compose/foundation/lazy/LazyListStateKt;->" +
                "rememberLazyListState($COMPOSER)$LIST_STATE",
            opcode = Opcode.INVOKE_STATIC,
        ),
        opcode(Opcode.MOVE_RESULT_OBJECT, MatchAfterImmediately()),
    ),
)

private object TabLayoutScrollHelperFingerprint : Fingerprint(
    definingClass = TAB_LAYOUT_KT,
    returnType = "V",
    parameters = listOf(SCROLLABLE_STATE, "I", "F", "Z", "Z", COMPOSER, "I"),
    filters = listOf(
        methodCall(
            definingClass = SCROLL_HELPER_LAMBDA,
            name = "<init>",
            opcode = Opcode.INVOKE_DIRECT_RANGE,
        ),
    ),
)

// Tab tray rebuilds its LazyList/LazyGrid state on every open, so scroll resets to the top and
// then jumps to the selected tab. Two hooks:
//  1. Each rememberLazyGridState/rememberLazyListState result in TabLayoutKt goes through
//     ScrollMemory.pick(), which returns the state from the previous composition (keyed by
//     composite key) instead of the fresh one, so the old scroll position survives.
//  2. TabLayoutScrollHelper builds a lambda that scrolls to the selected tab. Its index arg is
//     passed through ScrollMemory.resolveScrollIndex(), which returns -1 (no scroll) when the
//     state was restored and the selected tab is already visible.
// Only unobfuscated androidx/Fenix names and the shape of the Kotlin-generated lambda
// constructor are used; no register numbers or obfuscated names are pinned.
val rememberTabScrollPositionPatch = bytecodePatch(
    name = "Remember tab scroll position",
    description = "Keeps the tab tray scroll position when it is closed and reopened.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_FIREFOX_SCROLL)

    extendWith("extensions/firefox.mpe")

    execute {
        fun hookRememberedState(fingerprint: Fingerprint, stateType: String, expected: Int) {
            val matches = fingerprint.matchAll()
            if (matches.size != expected) {
                throw PatchException("Expected $expected $stateType call sites, found ${matches.size}")
            }

            matches.forEach { match ->
                val call = match.instructionMatches[0]
                val result = match.instructionMatches[1]
                val composer = call.getInstruction<FiveRegisterInstruction>().registerC
                val state = result.getInstruction<OneRegisterInstruction>().registerA
                if (composer > 15 || state > 15) {
                    throw PatchException("Register out of range for non-range invoke: v$composer, v$state")
                }

                match.method.addInstructions(
                    result.index + 1,
                    """
                        invoke-static { v$composer, v$state }, $EXTENSION->pick(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;
                        move-result-object v$state
                        check-cast v$state, $stateType
                    """,
                )
            }
        }

        hookRememberedState(RememberLazyGridStateFingerprint, GRID_STATE, 2)
        hookRememberedState(RememberLazyListStateFingerprint, LIST_STATE, 2)

        TabLayoutScrollHelperFingerprint.let { fingerprint ->
            val init = fingerprint.instructionMatches[0]
            val range = init.getInstruction<RegisterRangeInstruction>()
            val reference = init.getInstruction<ReferenceInstruction>().reference as MethodReference
            if (range.registerCount != 7 || reference.parameterTypes.map { it.toString() } != SCROLL_HELPER_INIT_PARAMETERS) {
                throw PatchException("Unexpected TabLayoutScrollHelper lambda constructor: $reference")
            }

            val index = range.startRegister + 1
            val last = range.startRegister + 4
            fingerprint.method.addInstructions(
                init.index,
                """
                    invoke-static/range { v$index .. v$last }, $EXTENSION->resolveScrollIndex(IZZLjava/lang/Object;)I
                    move-result v$index
                """,
            )
        }
    }
}
