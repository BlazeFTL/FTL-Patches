package app.ftl.patches.firefox

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.Match
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.newInstance
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import java.util.logging.Logger

private const val RS_SELECT = "Lapp/ftl/extension/firefox/RangeSelect;"
private const val RS_START = "Lapp/ftl/extension/firefox/RangeSelectStart;"
private const val RS_NOOP = "Lapp/ftl/extension/firefox/RangeSelectNoOp;"
private const val RS_MOD_LAMBDA = "Lapp/ftl/extension/firefox/ModLambda;"

private const val RS_OBJ = "Ljava/lang/Object;"
private const val RS_FUNCTION1 = "Lkotlin/jvm/functions/Function1;"
private const val RS_POINTER_HANDLER = "Landroidx/compose/ui/input/pointer/PointerInputEventHandler;"

private const val RS_FRAGMENT = "Lorg/mozilla/fenix/tabstray/ui/TabManagementFragment;"
private const val RS_TAB = "Lorg/mozilla/fenix/tabstray/data/TabsTrayItem\$Tab;"
private const val RS_LONG_CLICKED = "Lorg/mozilla/fenix/tabstray/redux/action/TabsTrayAction\$TabItemLongClicked;"
private const val RS_GROUP_CLICKED = "Lorg/mozilla/fenix/tabstray/redux/action/TabGroupAction\$TabGroupClicked;"

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

private object GestureFingerprint : Fingerprint(
    interfaces = listOf(RS_POINTER_HANDLER),
    name = "invoke",
    returnType = RS_OBJ,
    parameters = listOf("L", "L"),
    filters = listOf(
        fieldAccess(type = "Z", opcode = Opcode.IGET_BOOLEAN),
        opcode(Opcode.IF_EQZ, MatchAfterImmediately()),
        methodCall(
            parameters = listOf("L", RS_FUNCTION1, "L", "L", "L", "L"),
            returnType = RS_OBJ,
            opcodes = listOf(Opcode.INVOKE_STATIC, Opcode.INVOKE_STATIC_RANGE),
        ),
    ),
)

private class Gesture(
    val match: Match,
    val gate: Int,
    val target: Int,
    val detect: MethodReference,
    val unit: FieldReference,
) {
    val types = detect.parameterTypes.map { it.toString() }
}

private fun FieldReference.smali() = "$definingClass->$name:$type"

private fun BytecodePatchContext.findGestures(): List<Gesture> {
    val matches = GestureFingerprint.matchAllOrNull()
        ?: throw PatchException("No drag gesture handler matched")

    val gestures = matches.mapNotNull { match ->
        val method = match.method
        val gate = match.instructionMatches[1].index
        val call = match.instructionMatches[2].index
        val target = method.jumpTarget(gate)
        val unit = method.getInstruction<Instruction>(target)
        val back = method.getInstruction<Instruction>(target + 1)
        if (unit.opcode != Opcode.SGET_OBJECT || back.opcode != Opcode.RETURN_OBJECT) return@mapNotNull null
        Gesture(
            match,
            gate,
            target,
            method.getInstruction<ReferenceInstruction>(call).reference as MethodReference,
            (unit as ReferenceInstruction).reference as FieldReference,
        )
    }
    if (gestures.isEmpty()) throw PatchException("No drag gesture handler has the select-mode branch")

    val first = gestures.first()
    if (first.types.size != 6 || first.types[2] != first.types[3]) {
        throw PatchException("Unexpected drag gesture signature: ${first.types}")
    }
    for (gesture in gestures) {
        if (gesture.types != first.types || gesture.unit.smali() != first.unit.smali()) {
            throw PatchException("Drag gesture handlers disagree: ${gesture.types} vs ${first.types}")
        }
    }
    return gestures
}

private fun BytecodePatchContext.installInterfaces(types: List<String>) {
    val start = mutableClassDefBy(RS_START).interfaces
    if (types[1] !in start) start.add(types[1])
    val noop = mutableClassDefBy(RS_NOOP).interfaces
    for (type in listOf(types[2], types[4])) if (type !in noop) noop.add(type)
}

private fun BytecodePatchContext.installUnit(unit: FieldReference) {
    val method = mutableClassDefBy(RS_MOD_LAMBDA).methods.first { it.name == "unit" }
    method.applyEdits(
        insert(
            0,
            """
                sget-object v0, ${unit.smali()}
                return-object v0
            """,
        ),
    )
}

private fun BytecodePatchContext.hookLongClick(unit: FieldReference) {
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
                sget-object v$scratch, ${unit.smali()}
                return-object v$scratch
            """,
            mapOf("ftl_stock" to { 0 }),
        ),
    )
}

private fun BytecodePatchContext.hookClick(unit: FieldReference) {
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
                    sget-object v$groupScratch, ${unit.smali()}
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

private fun BytecodePatchContext.hookGesture(gesture: Gesture) {
    val method = gesture.match.method
    val target = gesture.target
    val types = gesture.types
    val detect = gesture.detect
    val call = "${detect.definingClass}->${detect.name}(${types.joinToString("")})${detect.returnType}"

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
                check-cast v$start, ${types[1]}
                invoke-static {}, $RS_SELECT->noop()Ljava/lang/Object;
                move-result-object v$end
                check-cast v$end, ${types[2]}
                move-object/from16 v$cancel, v$end
                move-object/from16 v$drag, v$end
                check-cast v$drag, ${types[4]}
                move-object/from16 v$scope, p1
                move-object/from16 v$continuation, p2
                invoke-static/range {v$scope .. v$continuation}, $call
                move-result-object v$scope
                return-object v$scope
            """,
            mapOf("ftl_orig" to { target }),
        ),
    )
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
        val gestures = findGestures()
        val unit = gestures.first().unit
        installInterfaces(gestures.first().types)
        installUnit(unit)
        hookLongClick(unit)
        hookClick(unit)
        gestures.forEach { hookGesture(it) }
    }
}
