package app.ftl.patches.firefox

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter

private val COMPATIBILITY_FIREFOX_TABS = Compatibility(
    packageName = "org.mozilla.fenix",
    name = "Firefox Nightly",
    targets = listOf(
        AppTarget(version = "159.0a1"),
        AppTarget(version = "160.0a1", versionCode = 2016189527),
    ),
)

private const val TAB_ORDER = "Lapp/ftl/extension/firefox/TabOrder;"
private const val TAB_END_CLICK = "Lapp/ftl/extension/firefox/TabEndClick;"
private const val TAB_FAB = "Lapp/ftl/extension/firefox/TabFab;"

private const val TAB_OBJ = "Ljava/lang/Object;"
private const val TAB_FUNCTION0 = "Lkotlin/jvm/functions/Function0;"
private const val TAB_COMPOSER = "Landroidx/compose/runtime/Composer;"

private const val TAB_LIST_REDUCER = "Lmozilla/components/browser/state/reducer/TabListReducer;"
private const val TAB_BROWSER_STATE = "Lmozilla/components/browser/state/state/BrowserState;"
private const val TAB_SESSION_STATE = "Lmozilla/components/browser/state/state/TabSessionState;"
private const val TAB_LIST_ACTION = "Lmozilla/components/browser/state/action/TabListAction;"

private const val TOOLBAR_KT = "Lorg/mozilla/fenix/tabstray/ui/fab/TabManagerFloatingToolbarKt;"
private const val TOOLBAR_LAMBDA = "Lorg/mozilla/fenix/tabstray/ui/fab/TabManagerFloatingToolbarKt\$\$ExternalSyntheticLambda"
private const val TABS_TRAY_STATE = "Lorg/mozilla/fenix/tabstray/redux/state/TabsTrayState;"
private const val TABS_PAGE = "Lorg/mozilla/fenix/tabstray/redux/state/Page;"

private object TabReduceFingerprint : Fingerprint(
    definingClass = TAB_LIST_REDUCER,
    name = "reduce",
    returnType = TAB_BROWSER_STATE,
    parameters = listOf(TAB_BROWSER_STATE, TAB_LIST_ACTION),
    filters = listOf(
        fieldAccess(definingClass = TAB_SESSION_STATE, name = "parentId", opcode = Opcode.IGET_OBJECT),
        opcode(Opcode.IF_EQZ, MatchAfterImmediately()),
    ),
)

private object FabCallFingerprint : Fingerprint(
    definingClass = TOOLBAR_LAMBDA,
    name = "invoke",
    returnType = TAB_OBJ,
    parameters = listOf(TAB_OBJ, TAB_OBJ, TAB_OBJ),
    filters = listOf(
        methodCall(definingClass = TOOLBAR_KT, name = "FloatingToolbarFAB", opcode = Opcode.INVOKE_STATIC_RANGE),
    ),
)

private object FabContentLambdaFingerprint : Fingerprint(
    definingClass = TOOLBAR_KT,
    name = "FloatingToolbarFAB",
    filters = listOf(
        methodCall(
            definingClass = TOOLBAR_LAMBDA,
            name = "<init>",
            parameters = listOf("I", "Ljava/lang/String;", "Lkotlin/jvm/internal/Ref\$ObjectRef;"),
        ),
    ),
)

private fun BytecodePatchContext.fieldRef(owner: String, name: String): String {
    val field = mutableClassDefBy(owner).fields.firstOrNull { it.name == name }
        ?: throw PatchException("Field not found: $owner->$name")
    return "$owner->$name:${field.type}"
}

private fun BytecodePatchContext.hookTabInsert() {
    val fingerprint = TabReduceFingerprint
    val method = fingerprint.method
    val parentGet = fingerprint.instructionMatches[0]
    val parentCheck = fingerprint.instructionMatches[1]
    val tabRegister = (parentGet.instruction as TwoRegisterInstruction).registerB

    val selectedIndex = method.implementation!!.instructions.indexOfFirst { instruction ->
        val reference = (instruction as? ReferenceInstruction)?.reference as? FieldReference
        instruction.opcode == Opcode.IGET_OBJECT && reference != null &&
            reference.definingClass == TAB_BROWSER_STATE && reference.name == "selectedTabId"
    }
    if (selectedIndex < 0 || selectedIndex >= parentCheck.index) {
        throw PatchException("selectedTabId read not found before add-tab branch")
    }
    val selectedRegister = method.getInstruction<OneRegisterInstruction>(selectedIndex).registerA

    val appendIndex = method.jumpTarget(parentCheck.index)
    val append = method.getInstruction<ReferenceInstruction>(appendIndex)
    val appendReference = append.reference as? MethodReference
    if (append.opcode != Opcode.INVOKE_STATIC || appendReference == null ||
        appendReference.name != "plus" || appendReference.parameterTypes.size != 2
    ) {
        throw PatchException("Unexpected add-tab append instruction: ${append.opcode} ${append.reference}")
    }
    val appendArguments = append as FiveRegisterInstruction
    if (appendArguments.registerC != tabRegister) {
        throw PatchException("Append call does not take the new tab register v$tabRegister")
    }
    val listRegister = appendArguments.registerD

    if (maxOf(tabRegister, listRegister, selectedRegister) > 15) {
        throw PatchException("Register out of range for non-range invoke")
    }
    if (!Liveness(method).isLive(appendIndex, selectedRegister)) {
        throw PatchException("selectedTabId register v$selectedRegister is not live at the append point")
    }

    method.replaceInstruction(
        appendIndex,
        "invoke-static {v$tabRegister, v$listRegister, v$selectedRegister}, " +
            "$TAB_ORDER->add(Ljava/lang/Object;Ljava/util/Collection;Ljava/lang/String;)Ljava/util/ArrayList;",
    )
}

private fun BytecodePatchContext.installTabFab() {
    val calls = FabCallFingerprint.matchAll()
    if (calls.size != 1) throw PatchException("Expected 1 FloatingToolbarFAB call site, found ${calls.size}")
    val call = calls[0]
    val callIndex = call.instructionMatches[0].index
    val callMethod = call.method
    val reference = callMethod.getInstruction<ReferenceInstruction>(callIndex).reference as MethodReference
    val range = callMethod.getInstruction<RegisterRangeInstruction>(callIndex)
    val parameters = reference.parameterTypes.map { it.toString() }
    if (parameters.size != 7 || parameters[0] != TABS_TRAY_STATE || parameters[5] != TAB_COMPOSER ||
        reference.returnType != "V" || range.registerCount != 7
    ) {
        throw PatchException("Unexpected FloatingToolbarFAB signature: $reference")
    }

    val contentInit = FabContentLambdaFingerprint.instructionMatches[0]
        .getInstruction<ReferenceInstruction>().reference as MethodReference
    val contentLambda = contentInit.definingClass

    val layoutNodeConstructor = fieldRef("Landroidx/compose/ui/node/LayoutNode;", "Constructor")
    val nodeCompanion = "Landroidx/compose/ui/node/ComposeUiNode\$Companion;"
    val setMeasurePolicy = fieldRef(nodeCompanion, "SetMeasurePolicy")
    val setLocals = fieldRef(nodeCompanion, "SetResolvedCompositionLocals")
    val setKeyHash = fieldRef(nodeCompanion, "SetCompositeKeyHash")
    val setModifier = fieldRef(nodeCompanion, "SetModifier")
    val arrangementStart = fieldRef("Landroidx/compose/foundation/layout/Arrangement;", "Start")
    val centerVertically = fieldRef("Landroidx/compose/ui/Alignment\$Companion;", "CenterVertically")
    val normalTabs = fieldRef(TABS_PAGE, "NormalTabs")
    val selectedPage = fieldRef(TABS_TRAY_STATE, "selectedPage")

    val lambdaInstructions = callMethod.implementation!!.instructions
    val centerEnd = lambdaInstructions.indexOfFirst { instruction ->
        val field = (instruction as? ReferenceInstruction)?.reference as? FieldReference
        instruction.opcode == Opcode.SGET_OBJECT && field != null && field.name == "CenterEnd"
    }
    if (centerEnd < 0 || centerEnd > callIndex) throw PatchException("FAB box alignment (CenterEnd) not found")
    var weightIndex = -1
    for (i in centerEnd - 1 downTo 0) {
        val instruction = lambdaInstructions[i]
        if (instruction.opcode == Opcode.CONST_HIGH16 &&
            (instruction as NarrowLiteralInstruction).narrowLiteral == 0x3f800000
        ) {
            weightIndex = i
            break
        }
    }
    if (weightIndex < 0) throw PatchException("FAB box weight not found")
    val weightRegister = callMethod.getInstruction<OneRegisterInstruction>(weightIndex).registerA
    callMethod.replaceInstruction(weightIndex, "const/high16 v$weightRegister, 0x40000000")

    val fabSignature = "(${parameters.joinToString("")})V"
    val rowSmali = $$$"""
        move-object/from16 v12, p5
        sget-object v0, Landroidx/compose/ui/Modifier$Companion;->$$INSTANCE:Landroidx/compose/ui/Modifier$Companion;
        sget-object v1, $$${arrangementStart}
        sget-object v2, $$${centerVertically}
        const/16 v3, 0x30
        invoke-static {v1, v2, v12, v3}, Landroidx/compose/foundation/layout/RowKt;->rowMeasurePolicy(Landroidx/compose/foundation/layout/Arrangement$Horizontal;Landroidx/compose/ui/Alignment$Vertical;Landroidx/compose/runtime/Composer;I)Landroidx/compose/foundation/layout/RowMeasurePolicy;
        move-result-object v1
        invoke-interface {v12}, Landroidx/compose/runtime/Composer;->getCompositeKeyHashCode()J
        move-result-wide v2
        invoke-static {v2, v3}, Ljava/lang/Long;->hashCode(J)I
        move-result v2
        invoke-interface {v12}, Landroidx/compose/runtime/Composer;->getCurrentCompositionLocalMap()Landroidx/compose/runtime/PersistentCompositionLocalMap;
        move-result-object v3
        invoke-static {v12, v0}, Landroidx/compose/ui/ComposedModifierKt;->materializeModifier(Landroidx/compose/runtime/Composer;Landroidx/compose/ui/Modifier;)Landroidx/compose/ui/Modifier;
        move-result-object v4
        invoke-interface {v12}, Landroidx/compose/runtime/Composer;->getApplier()Landroidx/compose/runtime/Applier;
        move-result-object v5
        if-eqz v5, :ftl_invalid
        invoke-interface {v12}, Landroidx/compose/runtime/Composer;->startReusableNode()V
        invoke-interface {v12}, Landroidx/compose/runtime/Composer;->getInserting()Z
        move-result v5
        sget-object v6, $$${layoutNodeConstructor}
        if-eqz v5, :ftl_use
        invoke-interface {v12, v6}, Landroidx/compose/runtime/Composer;->createNode(Lkotlin/jvm/functions/Function0;)V
        goto :ftl_created
        :ftl_use
        invoke-interface {v12}, Landroidx/compose/runtime/Composer;->useNode()V
        :ftl_created
        sget-object v5, $$${setMeasurePolicy}
        invoke-static {v12, v1, v5}, Landroidx/compose/runtime/Updater;->set-impl(Landroidx/compose/runtime/Composer;Ljava/lang/Object;Lkotlin/jvm/functions/Function2;)V
        sget-object v1, $$${setLocals}
        invoke-static {v12, v3, v1}, Landroidx/compose/runtime/Updater;->set-impl(Landroidx/compose/runtime/Composer;Ljava/lang/Object;Lkotlin/jvm/functions/Function2;)V
        invoke-static {v2}, Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;
        move-result-object v2
        sget-object v3, $$${setKeyHash}
        invoke-static {v12, v2, v3}, Landroidx/compose/runtime/Updater;->set-impl(Landroidx/compose/runtime/Composer;Ljava/lang/Object;Lkotlin/jvm/functions/Function2;)V
        invoke-static {v12}, Landroidx/compose/runtime/Updater;->reconcile-impl(Landroidx/compose/runtime/Composer;)V
        sget-object v2, $$${setModifier}
        invoke-static {v12, v4, v2}, Landroidx/compose/runtime/Updater;->set-impl(Landroidx/compose/runtime/Composer;Ljava/lang/Object;Lkotlin/jvm/functions/Function2;)V
        iget-object v0, p0, $$${selectedPage}
        sget-object v1, $$${normalTabs}
        if-ne v0, v1, :ftl_skip
        invoke-static {}, $$${TAB_FAB}->endButton()Z
        move-result v0
        if-eqz v0, :ftl_skip
        const v2, 0x5a11b0c1
        invoke-interface {v12, v2}, Landroidx/compose/runtime/Composer;->startReplaceGroup(I)V
        invoke-static {p1}, $$${TAB_END_CLICK}->of(Ljava/lang/Object;)$$${TAB_END_CLICK}
        move-result-object v2
        invoke-static {v2, v12}, $$${TAB_FAB}->fab(Lkotlin/jvm/functions/Function0;Landroidx/compose/runtime/Composer;)V
        sget-object v2, Landroidx/compose/ui/Modifier$Companion;->$$INSTANCE:Landroidx/compose/ui/Modifier$Companion;
        const/high16 v3, 0x41800000
        invoke-static {v2, v3}, Landroidx/compose/foundation/layout/SizeKt;->width-3ABfNKs(Landroidx/compose/ui/Modifier;F)Landroidx/compose/ui/Modifier;
        move-result-object v2
        invoke-static {v12, v2}, Landroidx/compose/foundation/layout/SpacerKt;->Spacer(Landroidx/compose/runtime/Composer;Landroidx/compose/ui/Modifier;)V
        invoke-interface {v12}, Landroidx/compose/runtime/Composer;->endReplaceGroup()V
        goto :ftl_shown
        :ftl_skip
        const v2, 0x5a11b0c2
        invoke-interface {v12, v2}, Landroidx/compose/runtime/Composer;->startReplaceGroup(I)V
        invoke-interface {v12}, Landroidx/compose/runtime/Composer;->endReplaceGroup()V
        :ftl_shown
        invoke-static/range {p0 .. p6}, $$${TOOLBAR_KT}->FloatingToolbarFAB$$${fabSignature}
        invoke-interface {v12}, Landroidx/compose/runtime/Composer;->endNode()V
        return-void
        :ftl_invalid
        invoke-static {}, Landroidx/compose/runtime/ComposablesKt;->invalidApplier()V
        const/4 v0, 0x0
        throw v0
    """

    val fabSmali = $$$"""
        new-instance v6, Lkotlin/jvm/internal/Ref$ObjectRef;
        invoke-direct {v6}, Lkotlin/jvm/internal/Ref$ObjectRef;-><init>()V
        sget-object v0, Landroidx/compose/ui/Modifier$Companion;->$$INSTANCE:Landroidx/compose/ui/Modifier$Companion;
        iput-object v0, v6, Lkotlin/jvm/internal/Ref$ObjectRef;->element:Ljava/lang/Object;
        sget v0, Lorg/mozilla/fenix/R$string;->add_tab:I
        invoke-static {v0, p1}, Landroidx/compose/ui/res/StringResources_androidKt;->stringResource(ILandroidx/compose/runtime/Composer;)Ljava/lang/String;
        move-result-object v0
        invoke-static {}, $$${TAB_FAB}->endIcon()I
        move-result v1
        new-instance v2, $$${contentLambda}
        invoke-direct {v2, v1, v0, v6}, $$${contentLambda}-><init>(ILjava/lang/String;Lkotlin/jvm/internal/Ref$ObjectRef;)V
        const v0, 0x5a11b0c3
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

    val holder = mutableClassDefBy(TAB_FAB)
    listOf(
        Triple("fab", listOf(TAB_FUNCTION0, TAB_COMPOSER), 16) to fabSmali,
        Triple("row", parameters, 20) to rowSmali,
    ).forEach { (spec, smali) ->
        val (name, types, registers) = spec
        holder.methods.removeAll { it.name == name }
        val method = ImmutableMethod(
            TAB_FAB,
            name,
            types.map { ImmutableMethodParameter(it, null, null) },
            "V",
            AccessFlags.PUBLIC.value or AccessFlags.STATIC.value,
            null,
            null,
            MutableMethodImplementation(registers),
        ).toMutable()
        method.addInstructionsWithLabels(0, smali.trimIndent())
        holder.methods.add(method)
    }

    val interfaces = mutableClassDefBy(TAB_END_CLICK).interfaces
    if (TAB_FUNCTION0 !in interfaces) interfaces.add(TAB_FUNCTION0)

    callMethod.replaceInstruction(
        callIndex,
        "invoke-static/range {v${range.startRegister} .. v${range.startRegister + range.registerCount - 1}}, " +
            "$TAB_FAB->row$fabSignature",
    )
}

@Suppress("unused")
val tabNextToCurrentPatch = bytecodePatch(
    name = "New tab next to current tab",
    description = "Opens new tabs right after the current tab and adds an add-to-end button (+ with a " +
        "down arrow) beside + that opens a tab at the end. Both are toggles in Mod Settings.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_FIREFOX_TABS)

    dependsOn(tabIconsPatch)

    extendWith("extensions/firefox.mpe")

    execute {
        hookTabInsert()
        installTabFab()
    }
}
