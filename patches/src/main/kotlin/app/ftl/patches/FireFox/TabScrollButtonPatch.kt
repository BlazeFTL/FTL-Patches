/*
 * Tab scroll buttons (jump to first/last tab).
 *
 * UI: the buttons are drawn by TabFab.extras (see TabNextToCurrentPatch.kt), this patch only supplies behaviour.
 * Hook: TabLayoutKt (Fenix, named) private static TabLayoutScrollHelper*(...)V receives the list/grid state
 *   as its only non-Composer object parameter. Found by name prefix and 7 parameters, not by obfuscated names.
 * Runtime state access goes through accessors generated in installScrollAccessors (Accessors.kt).
 */
package app.ftl.patches.firefox

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch

private const val SB_TAB_LAYOUT_KT = "Lorg/mozilla/fenix/tabstray/ui/tabpage/TabLayoutKt;"
private const val SB_TAB_SCROLL = "Lapp/ftl/extension/firefox/TabScroll;"
private const val SB_SCROLL_CLICK = "Lapp/ftl/extension/firefox/TabScrollClick;"

private object ScrollEntryFingerprint : Fingerprint(
    definingClass = SB_TAB_LAYOUT_KT,
    returnType = "V",
    custom = { method, _ ->
        method.name.startsWith("TabLayoutScrollHelper") && method.parameterTypes.size == 7
    },
)

private fun BytecodePatchContext.hookScrollState() {
    val method = ScrollEntryFingerprint.method
    val composer = resolveComposeTypes().composer
    val index = method.parameterTypes.indexOfFirst {
        val type = it.toString()
        type.startsWith("L") && type != composer
    }
    if (index < 0) throw PatchException("ScrollableState parameter not found in ${method.name}")
    val register = parameterRegister(method, index)

    method.applyEdits(
        insert(
            0,
            "invoke-static/range {v$register .. v$register}, $SB_TAB_SCROLL->track(Ljava/lang/Object;)V",
        ),
    )
}

private fun BytecodePatchContext.installScrollClick() {
    val interfaces = mutableClassDefBy(SB_SCROLL_CLICK).interfaces
    val function0 = resolveComposeTypes().function0
    if (function0 !in interfaces) interfaces.add(function0)

    addStaticMethod(SB_TAB_SCROLL, "ftlInstalled", listOf(), "Z", 1, """
        const/4 v0, 0x1
        return v0
    """)
}

@Suppress("unused")
val tabScrollButtonPatch = bytecodePatch(
    name = "Tab scroll button",
    description = "Adds up and down buttons beside + in the tab tray that jump to the first and last tab. " +
        "Toggle in Mod Settings.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_FIREFOX_NIGHTLY)

    dependsOn(tabNextToCurrentPatch)

    extendWith("extensions/firefox.mpe")

    execute {
        installTabAccessors()
        installScrollAccessors()
        hookScrollState()
        installScrollClick()
    }
}
