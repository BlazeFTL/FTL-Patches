/*
 * Close duplicate tabs.
 *
 * Anchor: Fenix FloatingToolbarActions (named) + test tag literal "tabstray.banner.threeDotButton.closeAllTabs".
 * The menu list is built by a when(selectedPage) whose branches all jump to one join label that stores
 * the list into the next register. Join = the jump target hit by the most goto instructions
 * (not a name or register, so it survives R8 renames and register reshuffles).
 * Function0 (menu item onClick) is the most frequent object parameter type of FloatingToolbarActions.
 */
package app.ftl.patches.firefox

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction

private const val DUP_TAB_MENU = "Lapp/ftl/extension/firefox/TabMenu;"
private const val DUP_CLOSE_ACTION = "Lapp/ftl/extension/firefox/TabCloseDuplicates;"
private const val DUP_TOOLBAR_KT = "Lorg/mozilla/fenix/tabstray/ui/fab/TabManagerFloatingToolbarKt;"
private const val DUP_TRAY_STATE = "Lorg/mozilla/fenix/tabstray/redux/state/TabsTrayState;"

private object TabMenuItemsFingerprint : Fingerprint(
    definingClass = DUP_TOOLBAR_KT,
    name = "FloatingToolbarActions",
    filters = listOf(string("tabstray.banner.threeDotButton.closeAllTabs")),
)

private fun MutableMethod.menuJoin(after: Int): Pair<Int, Int> {
    val instructions = implementation!!.instructions.toList()
    val hits = HashMap<Int, Int>()
    for (i in after + 1 until instructions.size) {
        if (instructions[i].opcode.name.startsWith("goto")) {
            hits.merge(jumpTarget(i), 1, Int::plus)
        }
    }
    val best = hits.entries.maxByOrNull { it.value }
    if (best == null || best.value < 3) throw PatchException("Menu list join point not found after index $after")

    val join = instructions[best.key]
    if (!join.opcode.name.startsWith("move-object") || join !is TwoRegisterInstruction) {
        throw PatchException("Unexpected instruction at menu join: ${join.opcode}")
    }
    return best.key to join.registerB
}

private fun BytecodePatchContext.hookMenuItems() {
    val fingerprint = TabMenuItemsFingerprint
    val method = fingerprint.method
    val closeAll = fingerprint.instructionMatches[0].index
    val (joinIndex, listRegister) = method.menuJoin(closeAll)

    if (!Liveness(method).isLive(joinIndex, listRegister)) {
        throw PatchException("Menu list register v$listRegister is not live at the join point")
    }

    val stateIndex = method.parameterTypes.indexOfFirst { it.toString() == DUP_TRAY_STATE }
    if (stateIndex < 0) throw PatchException("TabsTrayState parameter not found in FloatingToolbarActions")
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

    val function0 = method.parameterTypes.map { it.toString() }
        .filter { it.startsWith("L") && it != DUP_TRAY_STATE }
        .groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
        ?: throw PatchException("Function0 type not found in FloatingToolbarActions parameters")

    val interfaces = mutableClassDefBy(DUP_CLOSE_ACTION).interfaces
    if (function0 !in interfaces) interfaces.add(function0)
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
        installTabAccessors()
        hookMenuItems()
    }
}
