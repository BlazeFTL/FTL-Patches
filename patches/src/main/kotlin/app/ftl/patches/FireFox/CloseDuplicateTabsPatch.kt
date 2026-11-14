package app.ftl.patches.firefox

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val DUP_TAB_MENU = "Lapp/ftl/extension/firefox/TabMenu;"
private const val DUP_CLOSE_ACTION = "Lapp/ftl/extension/firefox/TabCloseDuplicates;"
private const val DUP_TOOLBAR_KT = "Lorg/mozilla/fenix/tabstray/ui/fab/TabManagerFloatingToolbarKt;"
private const val DUP_TABS_TRAY_STATE = "Lorg/mozilla/fenix/tabstray/redux/state/TabsTrayState;"
private const val DUP_PAGE = "Lorg/mozilla/fenix/tabstray/redux/state/Page;"

private object TabMenuItemsFingerprint : Fingerprint(
    definingClass = DUP_TOOLBAR_KT,
    name = "FloatingToolbarActions",
    filters = listOf(
        string("tabstray.banner.threeDotButton.closeAllTabs"),
        fieldAccess(definingClass = DUP_PAGE, name = "SyncedTabs", opcode = Opcode.SGET_OBJECT),
    ),
)

private fun menuJoin(method: MutableMethod, emptyIndex: Int): Int {
    var index = emptyIndex + 1
    var hops = 0
    while (method.getInstruction(index).opcode.name.startsWith("goto") && hops < 4) {
        index = method.jumpTarget(index)
        hops++
    }
    if (method.getInstruction(index).location.labels.isEmpty()) {
        throw PatchException("Menu list join point not found after EmptyList at $emptyIndex")
    }
    return index
}

private fun itemActionType(method: MutableMethod, from: Int): String {
    val index = method.firstIndexFrom(from) { _, instruction ->
        val reference = (instruction as? ReferenceInstruction)?.reference as? MethodReference
        reference != null &&
            reference.name == "<init>" &&
            reference.parameterTypes.size == 6 &&
            reference.parameterTypes[1].toString() == "I" &&
            reference.parameterTypes[2].toString() == "Ljava/lang/String;" &&
            reference.parameterTypes[5].toString() == "I"
    }
    if (index < 0) throw PatchException("Menu item constructor not found")
    val reference = method.getInstruction<ReferenceInstruction>(index).reference as MethodReference
    return reference.parameterTypes[4].toString()
}

private fun BytecodePatchContext.hookMenuItems() {
    val fingerprint = TabMenuItemsFingerprint
    val method = fingerprint.method
    val stringIndex = fingerprint.instructionMatches[0].index
    val syncedIndex = fingerprint.instructionMatches[1].index

    val emptyIndex = method.firstIndexFrom(syncedIndex + 1) { i, instruction ->
        instruction.opcode == Opcode.SGET_OBJECT &&
            method.getInstruction<Instruction>(i + 1).opcode.name.startsWith("goto")
    }
    if (emptyIndex < 0) throw PatchException("Empty menu list branch not found")

    val listRegister = method.getInstruction<OneRegisterInstruction>(emptyIndex).registerA
    val joinIndex = menuJoin(method, emptyIndex)

    if (!Liveness(method).isLive(joinIndex, listRegister)) {
        throw PatchException("Menu list register v$listRegister is not live at the join point")
    }

    val actionType = itemActionType(method, stringIndex)
    val stateIndex = method.parameterTypes.indexOfFirst { it.toString() == DUP_TABS_TRAY_STATE }
    if (stateIndex < 0) throw PatchException("TabsTrayState parameter not found")
    val stateRegister = parameterRegister(method, stateIndex)

    method.applyEdits(
        insert(
            joinIndex,
            """
                invoke-static/range {v$listRegister .. v$listRegister}, $DUP_TAB_MENU->withDuplicates(Ljava/lang/Object;)Ljava/util/List;
                move-result-object v$listRegister
            """,
        ),
        insert(
            0,
            "invoke-static/range {v$stateRegister .. v$stateRegister}, " +
                "$DUP_TAB_MENU->remember(Ljava/lang/Object;)V",
        ),
    )

    val interfaces = mutableClassDefBy(DUP_CLOSE_ACTION).interfaces
    if (actionType !in interfaces) interfaces.add(actionType)
}

@Suppress("unused")
val closeDuplicateTabsPatch = bytecodePatch(
    name = "Close duplicate tabs",
    description = "Adds Close duplicate tabs to the tab tray 3 dot menu. Toggle in Mod Settings.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_FIREFOX_NIGHTLY)

    extendWith("extensions/firefox.mpe")

    execute {
        hookMenuItems()
    }
}
