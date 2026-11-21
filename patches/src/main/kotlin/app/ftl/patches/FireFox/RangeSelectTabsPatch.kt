/*
 * Long press to select tab range.
 *
 * Nightly renames Compose/Kotlin/AC classes. Nothing obfuscated is pinned. How each piece is found
 * (use this to find it again in a new build):
 *
 *   RangeLongClickFingerprint
 *     invoke(Object)Object that calls TabManagementFragment.getTabsTrayStore$app and creates
 *     TabsTrayAction$TabItemLongClicked. It is a merged R8 lambda class (switch on a byte field), so the hook
 *     goes right before the getTabsTrayStore$app call, where the fragment and the item are plain registers.
 *
 *   RangeTabClickFingerprint
 *     TabManagementFragment.performTabClick(TabsTrayItem$Tab)V (Fenix, named, private final).
 *
 *   RangeGroupClickFingerprint (optional)
 *     invoke(Object)Object that creates TabGroupAction$TabGroupClicked.
 *
 *   RangeGestureFingerprint
 *     invoke(PointerInputScope, Continuation)Object:  iget-boolean <Z field> ; if-eqz (immediately) ;
 *     static call (PointerInputScope, Function1, F0, F0, F2, Continuation)Object = detectDragGesturesAfterLongPress.
 *     Matches the Fenix reorderable list gesture, the android-components interactable list gesture and the
 *     legacy grid gesture. Only classes holding a LazyListState field are hooked (state type = constructor
 *     parameter 0 of Fenix ListReorderState). Types of Function0/Function2/scope/continuation are read from
 *     the detect call itself.
 *     The if-eqz target must be  sget-object Unit ; return-object  (checked in hookGesture).
 */
package app.ftl.patches.firefox

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.newInstance
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import java.util.logging.Logger

private const val RS_SELECT = "Lapp/ftl/extension/firefox/RangeSelect;"
private const val RS_START = "Lapp/ftl/extension/firefox/RangeSelectStart;"
private const val RS_NOOP = "Lapp/ftl/extension/firefox/RangeSelectNoOp;"

private const val RS_OBJ = "Ljava/lang/Object;"
private const val RS_FUNCTION1 = "Lkotlin/jvm/functions/Function1;"

private const val RS_FRAGMENT = "Lorg/mozilla/fenix/tabstray/ui/TabManagementFragment;"
private const val RS_TAB = "Lorg/mozilla/fenix/tabstray/data/TabsTrayItem\$Tab;"
private const val RS_LONG_CLICKED = "Lorg/mozilla/fenix/tabstray/redux/action/TabsTrayAction\$TabItemLongClicked;"
private const val RS_GROUP_CLICKED = "Lorg/mozilla/fenix/tabstray/redux/action/TabGroupAction\$TabGroupClicked;"
private const val RS_LIST_REORDER = "Lorg/mozilla/fenix/tabstray/browser/compose/ListReorderState;"

private object RangeLongClickFingerprint : Fingerprint(
    name = "invoke",
    returnType = RS_OBJ,
    parameters = listOf(RS_OBJ),
    filters = listOf(
        methodCall(definingClass = RS_FRAGMENT, name = "getTabsTrayStore\$app"),
        newInstance(RS_LONG_CLICKED),
    ),
)

private object RangeTabClickFingerprint : Fingerprint(
    definingClass = RS_FRAGMENT,
    name = "performTabClick",
    returnType = "V",
    parameters = listOf(RS_TAB),
)

private object RangeGroupClickFingerprint : Fingerprint(
    name = "invoke",
    returnType = RS_OBJ,
    parameters = listOf(RS_OBJ),
    filters = listOf(
        newInstance(RS_GROUP_CLICKED),
    ),
)

private object RangeGestureFingerprint : Fingerprint(
    name = "invoke",
    returnType = RS_OBJ,
    parameters = listOf("L", "L"),
    filters = listOf(
        fieldAccess(type = "Z", opcode = Opcode.IGET_BOOLEAN),
        opcode(Opcode.IF_EQZ, MatchAfterImmediately()),
        methodCall(
            returnType = RS_OBJ,
            parameters = listOf("L", RS_FUNCTION1, "L", "L", "L", "L"),
        ),
    ),
)

private fun BytecodePatchContext.hookLongClick(unit: String) {
    val fingerprint = RangeLongClickFingerprint
    val method = fingerprint.method
    val storeCall = fingerprint.instructionMatches[0]
    val index = storeCall.index
    val fragment = (storeCall.instruction as FiveRegisterInstruction).registerC
    val item = parameterRegister(method, 0)
    val scratch = Liveness(method).takeLow(1, index)[0]

    method.applyEdits(
        insert(
            index,
            """
                ${method.staticCall(index, "$RS_SELECT->longPress(Ljava/lang/Object;Ljava/lang/Object;)Z", listOf(fragment, item))}
                move-result v$scratch
                if-eqz v$scratch, :ftl_stock
                sget-object v$scratch, $unit
                return-object v$scratch
            """,
            mapOf("ftl_stock" to { index }),
        ),
    )
}

private fun BytecodePatchContext.hookClick(unit: String) {
    val tab = RangeTabClickFingerprint.method
    val tabScratch = Liveness(tab).takeLow(1, 0)[0]
    tab.applyEdits(
        insert(
            0,
            """
                invoke-static {}, $RS_SELECT->consume()Z
                move-result v$tabScratch
                if-eqz v$tabScratch, :ftl_click
                return-void
            """,
            mapOf("ftl_click" to { 0 }),
        ),
    )

    try {
        val group = RangeGroupClickFingerprint.method
        val groupScratch = Liveness(group).takeLow(1, 0)[0]
        group.applyEdits(
            insert(
                0,
                """
                    invoke-static {}, $RS_SELECT->consume()Z
                    move-result v$groupScratch
                    if-eqz v$groupScratch, :ftl_click
                    sget-object v$groupScratch, $unit
                    return-object v$groupScratch
                """,
                mapOf("ftl_click" to { 0 }),
            ),
        )
    } catch (e: Exception) {
        Logger.getLogger("RangeSelectTabsPatch")
            .info("Skipped group click hook: ${e::class.simpleName}: ${e.message?.lineSequence()?.firstOrNull()}")
    }
}

private class DetectTypes(val function0: String, val function2: String)

private fun hookGesture(
    method: MutableMethod,
    gate: Int,
    detectIndex: Int,
    unit: String,
): DetectTypes {
    val detect = method.getInstruction<ReferenceInstruction>(detectIndex).reference as MethodReference
    val detectParameters = detect.parameterTypes.map { it.toString() }
    val function0 = detectParameters[2]
    val function2 = detectParameters[4]
    val detectSmali = "${detect.definingClass}->${detect.name}(${detectParameters.joinToString("")})${detect.returnType}"

    val target = method.jumpTarget(gate)
    val unitInstruction = method.getInstruction(target)
    val back = method.getInstruction(target + 1)
    if (unitInstruction.opcode != Opcode.SGET_OBJECT || back.opcode != Opcode.RETURN_OBJECT) {
        throw PatchException("Unexpected select-mode branch target: ${unitInstruction.opcode}, ${back.opcode}")
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
                check-cast v$end, $function0
                move-object/from16 v$cancel, v$end
                move-object/from16 v$drag, v$end
                check-cast v$drag, $function2
                move-object/from16 v$scope, p1
                move-object/from16 v$continuation, p2
                invoke-static/range {v$scope .. v$continuation}, $detectSmali
                move-result-object v$scope
                return-object v$scope
            """,
            mapOf("ftl_orig" to { target }),
        ),
    )
    return DetectTypes(function0, function2)
}

private fun BytecodePatchContext.hookGestures(): DetectTypes {
    val listState = mutableClassDefBy(RS_LIST_REORDER).methods.firstOrNull { it.name == "<init>" }
        ?.parameterTypes?.getOrNull(0)?.toString()
        ?: throw PatchException("LazyListState type not found in ListReorderState constructor")

    val gestures = RangeGestureFingerprint.matchAll().filter { match ->
        mutableClassDefBy(match.method.definingClass).fields.any { it.type == listState }
    }
    if (gestures.size != 2) {
        throw PatchException(
            "Expected 2 list gesture lambdas, found ${gestures.size}: " +
                gestures.joinToString { it.method.definingClass },
        )
    }
    val unit = resolveUnit()
    var types: DetectTypes? = null
    gestures.forEach {
        types = hookGesture(it.method, it.instructionMatches[1].index, it.instructionMatches[2].index, unit)
    }
    return types!!
}

@Suppress("unused")
val rangeSelectTabsPatch = bytecodePatch(
    name = "Long press to select tab range",
    description = "In tab tray selection mode, long press a second tab to select every tab between. " +
        "Toggle in Mod Settings.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_FIREFOX_NIGHTLY)

    extendWith("extensions/firefox.mpe")

    execute {
        installTabAccessors()
        installScrollAccessors()

        val unit = resolveUnit()
        val types = hookGestures()
        mutableClassDefBy(RS_START).interfaces.let { if (RS_FUNCTION1 !in it) it.add(RS_FUNCTION1) }
        mutableClassDefBy(RS_NOOP).interfaces.let {
            if (types.function0 !in it) it.add(types.function0)
            if (types.function2 !in it) it.add(types.function2)
        }
        hookLongClick(unit)
        hookClick(unit)
    }
}
