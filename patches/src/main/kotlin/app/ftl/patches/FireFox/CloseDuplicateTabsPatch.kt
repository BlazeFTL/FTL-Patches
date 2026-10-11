package app.ftl.patches.firefox

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

private val COMPATIBILITY_FIREFOX_DUPLICATES = Compatibility(
    packageName = "org.mozilla.fenix",
    name = "Firefox Nightly",
    targets = listOf(
        AppTarget(version = "159.0a1"),
        AppTarget(version = "160.0a1", versionCode = 2016189527),
    ),
)

private const val DUP_TAB_MENU = "Lapp/ftl/extension/firefox/TabMenu;"
private const val DUP_CLOSE_ACTION = "Lapp/ftl/extension/firefox/TabCloseDuplicates;"
private const val DUP_FUNCTION0 = "Lkotlin/jvm/functions/Function0;"
private const val DUP_TOOLBAR_KT = "Lorg/mozilla/fenix/tabstray/ui/fab/TabManagerFloatingToolbarKt;"

private object TabMenuItemsFingerprint : Fingerprint(
    definingClass = DUP_TOOLBAR_KT,
    name = "FloatingToolbarActions",
    filters = listOf(
        string("tabstray.banner.threeDotButton.closeAllTabs"),
        fieldAccess(definingClass = "Lkotlin/collections/EmptyList;", name = "INSTANCE", opcode = Opcode.SGET_OBJECT),
    ),
)

private fun BytecodePatchContext.hookMenuItems() {
    val fingerprint = TabMenuItemsFingerprint
    val method = fingerprint.method
    val emptyList = fingerprint.instructionMatches[1]
    val listRegister = (emptyList.instruction as OneRegisterInstruction).registerA
    val joinIndex = emptyList.index + 1

    if (listRegister > 15) throw PatchException("Menu list register out of range: v$listRegister")
    if (method.getInstruction(joinIndex).location.labels.isEmpty()) {
        throw PatchException("Menu list join point not found after EmptyList at ${emptyList.index}")
    }
    if (!Liveness(method).isLive(joinIndex, listRegister)) {
        throw PatchException("Menu list register v$listRegister is not live at the join point")
    }

    val stateRegister = parameterRegister(method, 0)
    method.applyEdits(
        insert(
            joinIndex,
            """
                invoke-static {v$listRegister}, $DUP_TAB_MENU->withDuplicates(Ljava/lang/Object;)Ljava/util/List;
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
    if (DUP_FUNCTION0 !in interfaces) interfaces.add(DUP_FUNCTION0)
}

@Suppress("unused")
val closeDuplicateTabsPatch = bytecodePatch(
    name = "Close duplicate tabs",
    description = "Adds Close duplicate tabs to the tab tray 3 dot menu. Toggle in Mod Settings.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_FIREFOX_DUPLICATES)

    extendWith("extensions/firefox.mpe")

    execute {
        hookMenuItems()
    }
}
