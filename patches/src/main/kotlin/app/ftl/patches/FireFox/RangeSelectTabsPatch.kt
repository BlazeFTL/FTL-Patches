/*
 * Long press to select tab range.
 *
 * SUPPORT POLICY: one Firefox Nightly build only (161.0a1). No fallbacks, no multi-version matching.
 * On a new Nightly: fix every fingerprint against the new smali, delete anything for the old build.
 *
 * SMALI NEEDED WHEN A FINGERPRINT BREAKS (send these from the NEW build):
 *
 *   RangeLongClickFingerprint
 *     Need: every class containing  TabsTrayAction$TabItemLongClicked;-><init>
 *           + TabManagementFragment.smali (getter getTabsTrayStore$app).
 *     Constraint: must be a Function1 lambda invoke(Object)Object whose fields hold the fragment.
 *                 RangeSelect.store() reflects over the lambda's fields looking for getTabsTrayStore$app.
 *                 If the creator no longer holds the fragment, RangeSelect.java must change too.
 *     161.0a1: UNVERIFIED. No class in the supplied smali creates TabItemLongClicked
 *              (TabManagementFragment lambdas, RenderTabsTray, getEntryProvider, redux, tabgroups/flow all checked).
 *              Creator lives in a package not yet supplied (try tabstray/ui subpackages, tabstray/controller).
 *
 *   RangeClickFingerprint
 *     Need: class containing  new-instance TabGroupAction$TabGroupClicked
 *     161.0a1: TabManagementFragment$$ExternalSyntheticLambda43 (unique). Lambda numbers shift between builds.
 *
 *   InteractableGestureFingerprint
 *     Need: InteractableListKt$detectListPressAndDrag$1
 *     161.0a1: mozilla/components/compose/draggable/ (moved out of org/mozilla/fenix/tabstray/browser/compose/interactable/).
 *
 *   ReorderableGestureFingerprint
 *     Need: ReorderableListKt$detectListPressAndDrag$1
 *     161.0a1: org/mozilla/fenix/tabstray/browser/compose/.
 *
 *   Both gesture fingerprints: invoke(PointerInputScope, Continuation)Object with
 *     iget-boolean $shouldLongPressToDrag ; if-eqz (immediately) ; call DragGestureDetectorKt.detectDragGesturesAfterLongPress.
 *     The if-eqz target must be  sget-object Unit ; return-object  (checked in hookGesture).
 *
 * Quick find in a decoded dump:  grep -rl '<needle>' .
 */

package app.ftl.patches.firefox

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.instanceOf
import app.morphe.patcher.methodCall
import app.morphe.patcher.newInstance
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode

private const val RS_SELECT = "Lapp/ftl/extension/firefox/RangeSelect;"
private const val RS_START = "Lapp/ftl/extension/firefox/RangeSelectStart;"
private const val RS_NOOP = "Lapp/ftl/extension/firefox/RangeSelectNoOp;"

private const val RS_OBJ = "Ljava/lang/Object;"
private const val RS_FUNCTION0 = "Lkotlin/jvm/functions/Function0;"
private const val RS_FUNCTION1 = "Lkotlin/jvm/functions/Function1;"
private const val RS_FUNCTION2 = "Lkotlin/jvm/functions/Function2;"
private const val RS_POINTER_SCOPE = "Landroidx/compose/ui/input/pointer/PointerInputScope;"
private const val RS_CONTINUATION = "Lkotlin/coroutines/Continuation;"
private const val RS_DRAG_KT = "Landroidx/compose/foundation/gestures/DragGestureDetectorKt;"

private const val RS_FRAGMENT = "Lorg/mozilla/fenix/tabstray/ui/TabManagementFragment;"
private const val RS_FRAGMENT_LAMBDA = "Lorg/mozilla/fenix/tabstray/ui/TabManagementFragment\$\$ExternalSyntheticLambda"
private const val RS_TAB = "Lorg/mozilla/fenix/tabstray/data/TabsTrayItem\$Tab;"
private const val RS_LONG_CLICKED = "Lorg/mozilla/fenix/tabstray/redux/action/TabsTrayAction\$TabItemLongClicked;"
private const val RS_GROUP_CLICKED = "Lorg/mozilla/fenix/tabstray/redux/action/TabGroupAction\$TabGroupClicked;"

private const val RS_DETECT = "$RS_DRAG_KT->detectDragGesturesAfterLongPress(" +
    "$RS_POINTER_SCOPE$RS_FUNCTION1$RS_FUNCTION0$RS_FUNCTION0$RS_FUNCTION2$RS_CONTINUATION)$RS_OBJ"

// Long press on a tab item -> dispatches TabItemLongClicked. definingClass intentionally NOT pinned:
// the creator lambda moved between builds. Match by shape: invoke(Object)Object that calls the
// fragment's store getter and creates TabItemLongClicked. See header for what to send if this fails.
private object RangeLongClickFingerprint : Fingerprint(
    name = "invoke",
    returnType = RS_OBJ,
    parameters = listOf(RS_OBJ),
    filters = listOf(
        methodCall(definingClass = RS_FRAGMENT, name = "getTabsTrayStore\$app"),
        newInstance(RS_LONG_CLICKED),
    ),
)

// Tap on a tab item -> Tab: forwards to Function1; TabGroup: dispatches TabGroupClicked.
// Fragment lambda number changes per build; matched by prefix + instance-of Tab + TabGroupClicked.
private object RangeClickFingerprint : Fingerprint(
    definingClass = RS_FRAGMENT_LAMBDA,
    name = "invoke",
    returnType = RS_OBJ,
    parameters = listOf(RS_OBJ),
    filters = listOf(
        instanceOf(RS_TAB),
        newInstance(RS_GROUP_CLICKED),
    ),
)

// 161.0a1: class lives in android-components (mozilla/components/compose/draggable).
private object InteractableGestureFingerprint : Fingerprint(
    definingClass = "Lmozilla/components/compose/draggable/" +
        "InteractableListKt\$detectListPressAndDrag\$1;",
    name = "invoke",
    returnType = RS_OBJ,
    parameters = listOf(RS_POINTER_SCOPE, RS_CONTINUATION),
    filters = listOf(
        fieldAccess(type = "Z", opcode = Opcode.IGET_BOOLEAN),
        opcode(Opcode.IF_EQZ, MatchAfterImmediately()),
        methodCall(definingClass = RS_DRAG_KT, name = "detectDragGesturesAfterLongPress"),
    ),
)

// 161.0a1: class lives in org/mozilla/fenix/tabstray/browser/compose.
private object ReorderableGestureFingerprint : Fingerprint(
    definingClass = "Lorg/mozilla/fenix/tabstray/browser/compose/" +
        "ReorderableListKt\$detectListPressAndDrag\$1;",
    name = "invoke",
    returnType = RS_OBJ,
    parameters = listOf(RS_POINTER_SCOPE, RS_CONTINUATION),
    filters = listOf(
        fieldAccess(type = "Z", opcode = Opcode.IGET_BOOLEAN),
        opcode(Opcode.IF_EQZ, MatchAfterImmediately()),
        methodCall(definingClass = RS_DRAG_KT, name = "detectDragGesturesAfterLongPress"),
    ),
)

private fun BytecodePatchContext.hookLongClick() {
    val method = RangeLongClickFingerprint.method
    val item = parameterRegister(method, 0)
    val self = item - 1
    val scratch = Liveness(method).takeLow(1, 0)[0]

    method.applyEdits(
        insert(
            0,
            """
                invoke-static/range {v$self .. v$item}, $RS_SELECT->longPress(Ljava/lang/Object;Ljava/lang/Object;)Z
                move-result v$scratch
                if-eqz v$scratch, :ftl_stock
                sget-object v$scratch, Lkotlin/Unit;->INSTANCE:Lkotlin/Unit;
                return-object v$scratch
            """,
            mapOf("ftl_stock" to { 0 }),
        ),
    )
}

private fun BytecodePatchContext.hookClick() {
    val method = RangeClickFingerprint.method
    val scratch = Liveness(method).takeLow(1, 0)[0]

    method.applyEdits(
        insert(
            0,
            """
                invoke-static {}, $RS_SELECT->consume()Z
                move-result v$scratch
                if-eqz v$scratch, :ftl_click
                sget-object v$scratch, Lkotlin/Unit;->INSTANCE:Lkotlin/Unit;
                return-object v$scratch
            """,
            mapOf("ftl_click" to { 0 }),
        ),
    )
}

private fun BytecodePatchContext.hookGesture(fingerprint: Fingerprint) {
    val method = fingerprint.method
    val gate = fingerprint.instructionMatches[1].index
    val target = method.jumpTarget(gate)

    val unit = method.getInstruction(target)
    val back = method.getInstruction(target + 1)
    if (unit.opcode != Opcode.SGET_OBJECT || back.opcode != Opcode.RETURN_OBJECT) {
        throw PatchException("Unexpected select-mode branch target: ${unit.opcode}, ${back.opcode}")
    }

    val scopeParameter = parameterRegister(method, 0)
    val continuationParameter = parameterRegister(method, 1)
    val base = Liveness(method).takeRun(
        7,
        target,
        exclude = setOf(scopeParameter, continuationParameter),
    )
    if (base + 6 > 255) throw PatchException("Register out of range: v${base + 6}")

    val self = base
    val scope = base + 1
    val start = base + 2
    val end = base + 3
    val cancel = base + 4
    val drag = base + 5
    val continuation = base + 6

    method.applyEdits(
        insert(
            target,
            """
                invoke-static {}, $MOD_SETTINGS->rangeSelect()Z
                move-result v$self
                if-eqz v$self, :ftl_orig
                move-object/from16 v$self, p0
                invoke-static/range {v$self .. v$self}, $RS_SELECT->start(Ljava/lang/Object;)Ljava/lang/Object;
                move-result-object v$start
                check-cast v$start, $RS_FUNCTION1
                invoke-static {}, $RS_SELECT->noop()Ljava/lang/Object;
                move-result-object v$end
                check-cast v$end, $RS_FUNCTION0
                move-object/from16 v$cancel, v$end
                move-object/from16 v$drag, v$end
                check-cast v$drag, $RS_FUNCTION2
                move-object/from16 v$scope, p1
                move-object/from16 v$continuation, p2
                invoke-static/range {v$scope .. v$continuation}, $RS_DETECT
                move-result-object v$scope
                return-object v$scope
            """,
            mapOf("ftl_orig" to { target }),
        ),
    )
}

private fun BytecodePatchContext.installInterfaces() {
    val start = mutableClassDefBy(RS_START).interfaces
    if (RS_FUNCTION1 !in start) start.add(RS_FUNCTION1)
    val noop = mutableClassDefBy(RS_NOOP).interfaces
    if (RS_FUNCTION0 !in noop) noop.add(RS_FUNCTION0)
    if (RS_FUNCTION2 !in noop) noop.add(RS_FUNCTION2)
}

@Suppress("unused")
val rangeSelectTabsPatch = bytecodePatch(
    name = "Long press to select tab range",
    description = "In tab tray selection mode, long press a second tab to select every tab between. " +
        "Toggle in Mod Settings.",
    default = true,
) {
    // COMPATIBILITY_FIREFOX_NIGHTLY must list the single supported build only (161.0a1).
    compatibleWith(COMPATIBILITY_FIREFOX_NIGHTLY)

    extendWith("extensions/firefox.mpe")

    execute {
        // Single supported build: every fingerprint must match, any miss fails the patch loudly.
        installInterfaces()
        hookLongClick()
        hookClick()
        hookGesture(InteractableGestureFingerprint)
        hookGesture(ReorderableGestureFingerprint)
    }
}
