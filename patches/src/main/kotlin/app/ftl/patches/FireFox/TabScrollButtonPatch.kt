package app.ftl.patches.firefox

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter

private const val SB_TAB_SCROLL = "Lapp/ftl/extension/firefox/TabScroll;"
private const val SB_TAB_SCROLL_CLICK = "Lapp/ftl/extension/firefox/TabScrollClick;"
private const val SB_TAB_FAB = "Lapp/ftl/extension/firefox/TabFab;"
private const val SB_FUNCTION0 = "Lkotlin/jvm/functions/Function0;"
private const val SB_COMPOSER = "Landroidx/compose/runtime/Composer;"
private const val SB_SCROLLABLE_STATE = "Landroidx/compose/foundation/gestures/ScrollableState;"

private const val SB_TAB_LAYOUT_KT = "Lorg/mozilla/fenix/tabstray/ui/tabpage/TabLayoutKt;"
private const val SB_SCROLL_HELPER_LAMBDA =
    "Lorg/mozilla/fenix/tabstray/ui/tabpage/TabLayoutKt\$TabLayoutScrollHelper"
private const val SB_TOOLBAR_KT = "Lorg/mozilla/fenix/tabstray/ui/fab/TabManagerFloatingToolbarKt;"
private const val SB_TOOLBAR_LAMBDA =
    "Lorg/mozilla/fenix/tabstray/ui/fab/TabManagerFloatingToolbarKt\$\$ExternalSyntheticLambda"
private const val SB_TABS_TRAY_STATE = "Lorg/mozilla/fenix/tabstray/redux/state/TabsTrayState;"
private const val SB_TABS_PAGE = "Lorg/mozilla/fenix/tabstray/redux/state/Page;"

private object TabScrollEntryFingerprint : Fingerprint(
    definingClass = SB_TAB_LAYOUT_KT,
    returnType = "V",
    parameters = listOf(SB_SCROLLABLE_STATE, "I", "F", "Z", "Z", SB_COMPOSER, "I"),
    filters = listOf(
        methodCall(
            definingClass = SB_SCROLL_HELPER_LAMBDA,
            name = "<init>",
            opcode = Opcode.INVOKE_DIRECT_RANGE,
        ),
    ),
)

private object ScrollFabContentFingerprint : Fingerprint(
    definingClass = SB_TOOLBAR_KT,
    name = "FloatingToolbarFAB",
    filters = listOf(
        methodCall(
            definingClass = SB_TOOLBAR_LAMBDA,
            name = "<init>",
            parameters = listOf("I", "Ljava/lang/String;", "Lkotlin/jvm/internal/Ref\$ObjectRef;"),
        ),
    ),
)

private fun BytecodePatchContext.scrollFieldRef(owner: String, name: String): String {
    val field = mutableClassDefBy(owner).fields.firstOrNull { it.name == name }
        ?: throw PatchException("Field not found: $owner->$name")
    return "$owner->$name:${field.type}"
}

private fun BytecodePatchContext.hookScrollState() {
    val method = TabScrollEntryFingerprint.method
    val state = parameterRegister(method, 0)
    method.addInstructions(
        0,
        "invoke-static/range {v$state .. v$state}, $SB_TAB_SCROLL->track(Ljava/lang/Object;)V",
    )
}

private fun BytecodePatchContext.installScrollFab() {
    val contentInit = ScrollFabContentFingerprint.instructionMatches[0]
        .getInstruction<ReferenceInstruction>().reference as MethodReference
    val contentLambda = contentInit.definingClass

    val smali = $$$"""
        new-instance v6, Lkotlin/jvm/internal/Ref$ObjectRef;
        invoke-direct {v6}, Lkotlin/jvm/internal/Ref$ObjectRef;-><init>()V
        sget-object v0, Landroidx/compose/ui/Modifier$Companion;->$$INSTANCE:Landroidx/compose/ui/Modifier$Companion;
        iput-object v0, v6, Lkotlin/jvm/internal/Ref$ObjectRef;->element:Ljava/lang/Object;
        const-string v0, "Scroll tabs"
        move/from16 v1, p2
        new-instance v2, $$${contentLambda}
        invoke-direct {v2, v1, v0, v6}, $$${contentLambda}-><init>(ILjava/lang/String;Lkotlin/jvm/internal/Ref$ObjectRef;)V
        move/from16 v0, p3
        invoke-static {v0, v2, p1}, Landroidx/compose/runtime/internal/ComposableLambdaKt;->rememberComposableLambda(ILkotlin/Function;Landroidx/compose/runtime/Composer;)Landroidx/compose/runtime/internal/ComposableLambdaImpl;
        move-result-object v1
        sget-object v2, Landroidx/compose/material3/ColorSchemeKt;->LocalColorScheme:Landroidx/compose/runtime/StaticProvidableCompositionLocal;
        invoke-interface {p1, v2}, Landroidx/compose/runtime/Composer;->consume(Landroidx/compose/runtime/ProvidableCompositionLocal;)Ljava/lang/Object;
        move-result-object v2
        check-cast v2, Landroidx/compose/material3/ColorScheme;
        iget-wide v8, v2, Landroidx/compose/material3/ColorScheme;->secondaryContainer:J
        iget-wide v10, v2, Landroidx/compose/material3/ColorScheme;->onSurface:J
        new-instance v2, Lmozilla/components/compose/base/button/FloatingActionButtonColors;
        invoke-direct {v2, v8, v9, v10, v11}, Lmozilla/components/compose/base/button/FloatingActionButtonColors;-><init>(JJ)V
        const/16 v3, 0xf
        invoke-static {v3}, Landroidx/compose/material3/FloatingActionButtonDefaults;->elevation-xZ9-QkE(I)Landroidx/compose/material3/FloatingActionButtonElevation;
        move-result-object v3
        sget-object v0, Landroidx/compose/ui/Modifier$Companion;->$$INSTANCE:Landroidx/compose/ui/Modifier$Companion;
        move-object v4, p0
        move-object v5, p1
        const/16 v6, 0x36
        const/4 v7, 0x4
        invoke-static/range {v0 .. v7}, Lmozilla/components/compose/base/button/FloatingActionButtonKt;->FloatingActionButton(Landroidx/compose/ui/Modifier;Landroidx/compose/runtime/internal/ComposableLambdaImpl;Lmozilla/components/compose/base/button/FloatingActionButtonColors;Landroidx/compose/material3/FloatingActionButtonElevation;Lkotlin/jvm/functions/Function0;Landroidx/compose/runtime/Composer;II)V
        return-void
    """

    val holder = mutableClassDefBy(SB_TAB_FAB)
    holder.methods.removeAll { it.name == "scrollFab" }
    val method = ImmutableMethod(
        SB_TAB_FAB,
        "scrollFab",
        listOf(SB_FUNCTION0, SB_COMPOSER, "I", "I").map { ImmutableMethodParameter(it, null, null) },
        "V",
        AccessFlags.PUBLIC.value or AccessFlags.STATIC.value,
        null,
        null,
        MutableMethodImplementation(18),
    ).toMutable()
    method.addInstructionsWithLabels(0, smali.trimIndent())
    holder.methods.add(method)

    val interfaces = mutableClassDefBy(SB_TAB_SCROLL_CLICK).interfaces
    if (SB_FUNCTION0 !in interfaces) interfaces.add(SB_FUNCTION0)
}

private fun BytecodePatchContext.insertScrollButton() {
    val row = mutableClassDefBy(SB_TAB_FAB).methods.firstOrNull { it.name == "row" }
        ?: throw PatchException("TabFab.row not found. Apply \"New tab next to current tab\" first")
    val at = row.implementation!!.instructions.indexOfFirst { instruction ->
        val reference = (instruction as? ReferenceInstruction)?.reference as? FieldReference
        instruction.opcode == Opcode.IGET_OBJECT && reference != null && reference.name == "selectedPage"
    }
    if (at < 0) throw PatchException("selectedPage read not found in TabFab.row")

    val selectedPage = scrollFieldRef(SB_TABS_TRAY_STATE, "selectedPage")
    val normalTabs = scrollFieldRef(SB_TABS_PAGE, "NormalTabs")
    val privateTabs = scrollFieldRef(SB_TABS_PAGE, "PrivateTabs")

    val smali = $$$"""
        invoke-static {p0}, $$${SB_TAB_SCROLL}->tray(Ljava/lang/Object;)V
        iget-object v0, p0, $$${selectedPage}
        sget-object v1, $$${normalTabs}
        if-eq v0, v1, :ftl_s_page
        sget-object v1, $$${privateTabs}
        if-ne v0, v1, :ftl_s_skip
        :ftl_s_page
        invoke-static {}, $$${SB_TAB_SCROLL}->enabled()Z
        move-result v0
        if-eqz v0, :ftl_s_skip
        const v2, 0x5a11b0c4
        invoke-interface {v12, v2}, Landroidx/compose/runtime/Composer;->startReplaceGroup(I)V
        invoke-static {}, $$${SB_TAB_SCROLL}->up()$$${SB_TAB_SCROLL_CLICK}
        move-result-object v2
        invoke-static {}, $$${SB_TAB_SCROLL}->upIcon()I
        move-result v3
        const v4, 0x5a11b0c6
        invoke-static {v2, v12, v3, v4}, $$${SB_TAB_FAB}->scrollFab(Lkotlin/jvm/functions/Function0;Landroidx/compose/runtime/Composer;II)V
        sget-object v2, Landroidx/compose/ui/Modifier$Companion;->$$INSTANCE:Landroidx/compose/ui/Modifier$Companion;
        const/high16 v3, 0x41800000
        invoke-static {v2, v3}, Landroidx/compose/foundation/layout/SizeKt;->width-3ABfNKs(Landroidx/compose/ui/Modifier;F)Landroidx/compose/ui/Modifier;
        move-result-object v2
        invoke-static {v12, v2}, Landroidx/compose/foundation/layout/SpacerKt;->Spacer(Landroidx/compose/runtime/Composer;Landroidx/compose/ui/Modifier;)V
        invoke-static {}, $$${SB_TAB_SCROLL}->down()$$${SB_TAB_SCROLL_CLICK}
        move-result-object v2
        invoke-static {}, $$${SB_TAB_SCROLL}->downIcon()I
        move-result v3
        const v4, 0x5a11b0c7
        invoke-static {v2, v12, v3, v4}, $$${SB_TAB_FAB}->scrollFab(Lkotlin/jvm/functions/Function0;Landroidx/compose/runtime/Composer;II)V
        sget-object v2, Landroidx/compose/ui/Modifier$Companion;->$$INSTANCE:Landroidx/compose/ui/Modifier$Companion;
        const/high16 v3, 0x41800000
        invoke-static {v2, v3}, Landroidx/compose/foundation/layout/SizeKt;->width-3ABfNKs(Landroidx/compose/ui/Modifier;F)Landroidx/compose/ui/Modifier;
        move-result-object v2
        invoke-static {v12, v2}, Landroidx/compose/foundation/layout/SpacerKt;->Spacer(Landroidx/compose/runtime/Composer;Landroidx/compose/ui/Modifier;)V
        invoke-interface {v12}, Landroidx/compose/runtime/Composer;->endReplaceGroup()V
        goto :ftl_s_done
        :ftl_s_skip
        const v2, 0x5a11b0c5
        invoke-interface {v12, v2}, Landroidx/compose/runtime/Composer;->startReplaceGroup(I)V
        invoke-interface {v12}, Landroidx/compose/runtime/Composer;->endReplaceGroup()V
        :ftl_s_done
        nop
    """

    row.applyEdits(insert(at, smali))
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
        hookScrollState()
        installScrollFab()
        insertScrollButton()
    }
}
