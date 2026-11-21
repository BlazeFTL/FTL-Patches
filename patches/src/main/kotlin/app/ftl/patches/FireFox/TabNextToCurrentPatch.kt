/*
 * New tab next to current tab + add-to-end button.
 *
 * Nightly renames Compose/Kotlin/AC classes, so nothing obfuscated is pinned. How each piece is found
 * (use this to find it again in a new build):
 *
 * 1. Reducer (TabReduceFingerprint)
 *    Static method of the class whose source file is TabListReducer.kt that contains the literal
 *    "The parent does not exist". Inside it: iget-object <String> (tab.parentId) ; if-eqz  -> add-tab branch.
 *    The if-eqz target is  invoke-static {tab, list}, <plus>(Object, Collection)ArrayList.
 *    selectedTabId = the BrowserState field resolved from the "BrowserState(tabs=" toString (see Accessors.kt).
 *
 * 2. FAB call site
 *    TabManagerFloatingToolbarKt (Fenix, named) has a static lambda body that calls
 *    FloatingToolbarFAB(TabsTrayState, F0, F0, F0, F0, Composer, I) with invoke-static/range.
 *    That lambda builds Box(weight, CenterEnd) { FloatingToolbarFAB(...) }. Extra buttons are extra
 *    FloatingActionButton calls in the same Box, shifted left with Modifier.padding(end = N dp),
 *    so no Row/Compose layout node code is generated.
 *
 * 3. Compose references (all harvested from existing Fenix code, never typed):
 *    from FloatingToolbarFAB: content lambda ctor (I,String,Ref), rememberComposableLambda (I,X,Composer),
 *      Modifier.Companion sget (the one stored into the Ref element), LocalColorScheme sget + consume call,
 *      first two iget-wide of the colour scheme, FloatingActionButtonColors(JJ) ctor, elevation(I) call,
 *      FloatingActionButton (last invoke-static/range with 8 params).
 *    from the call-site method: Composer.startReplaceGroup(I)V + endReplaceGroup()V (adjacent invoke-interface),
 *      PaddingKt (static call with params Modifier,F,F,I) -> padding(Modifier,F,F,F,F) in that class.
 */
package app.ftl.patches.firefox

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

private const val TAB_ORDER = "Lapp/ftl/extension/firefox/TabOrder;"
private const val TAB_END_CLICK = "Lapp/ftl/extension/firefox/TabEndClick;"
private const val TAB_FAB = "Lapp/ftl/extension/firefox/TabFab;"
private const val TAB_SCROLL = "Lapp/ftl/extension/firefox/TabScroll;"

private const val TOOLBAR_KT = "Lorg/mozilla/fenix/tabstray/ui/fab/TabManagerFloatingToolbarKt;"
private const val TABS_TRAY_STATE = "Lorg/mozilla/fenix/tabstray/redux/state/TabsTrayState;"
private const val TABS_PAGE = "Lorg/mozilla/fenix/tabstray/redux/state/Page;"

private const val STRING = "Ljava/lang/String;"
private const val OBJECT = "Ljava/lang/Object;"
private const val COLLECTION = "Ljava/util/Collection;"
private const val ARRAY_LIST = "Ljava/util/ArrayList;"

private const val FAB_GROUP_KEY_END = 0x5a11b0c1
private const val FAB_CONTENT_KEY = 0x5a11b0c3

private object TabReduceFingerprint : Fingerprint(
    returnType = "L",
    parameters = listOf("L", "L"),
    filters = listOf(
        fieldAccess(type = STRING, opcode = Opcode.IGET_OBJECT),
        opcode(Opcode.IF_EQZ, MatchAfterImmediately()),
        string("The parent does not exist"),
    ),
    custom = { method, classDef ->
        classDef.sourceFile == "TabListReducer.kt" && AccessFlags.STATIC.isSet(method.accessFlags)
    },
)

private object FabCallFingerprint : Fingerprint(
    definingClass = TOOLBAR_KT,
    filters = listOf(
        methodCall(definingClass = TOOLBAR_KT, name = "FloatingToolbarFAB", opcode = Opcode.INVOKE_STATIC_RANGE),
    ),
)

private object FabMethodFingerprint : Fingerprint(
    definingClass = TOOLBAR_KT,
    name = "FloatingToolbarFAB",
)

private fun List<Instruction>.methodAt(index: Int): MethodReference? =
    (getOrNull(index) as? ReferenceInstruction)?.reference as? MethodReference

private fun List<Instruction>.fieldAt(index: Int): FieldReference? =
    (getOrNull(index) as? ReferenceInstruction)?.reference as? FieldReference

private fun MethodReference.params(): List<String> = parameterTypes.map { it.toString() }

private fun MethodReference.smali(): String = "$definingClass->$name(${params().joinToString("")})$returnType"

private fun hookNotFound(what: String): Nothing = throw PatchException("$what not found")

private fun BytecodePatchContext.hookTabInsert() {
    val model = resolveTabModel()
    val fingerprint = TabReduceFingerprint
    val method = fingerprint.method
    val parentGet = fingerprint.instructionMatches[0]
    val parentCheck = fingerprint.instructionMatches[1]
    val tabRegister = (parentGet.instruction as TwoRegisterInstruction).registerB

    val instructions = method.implementation!!.instructions.toList()
    val selectedIndex = instructions.indexOfFirst { instruction ->
        val reference = (instruction as? ReferenceInstruction)?.reference as? FieldReference
        instruction.opcode == Opcode.IGET_OBJECT && reference != null && fieldString(reference) == model.selected
    }
    if (selectedIndex < 0 || selectedIndex >= parentCheck.index) {
        throw PatchException("selectedTabId read not found before add-tab branch")
    }
    val selectedRegister = method.getInstruction<OneRegisterInstruction>(selectedIndex).registerA

    val appendIndex = method.jumpTarget(parentCheck.index)
    val append = method.getInstruction<ReferenceInstruction>(appendIndex)
    val appendReference = append.reference as? MethodReference
    if (append.opcode != Opcode.INVOKE_STATIC || appendReference == null ||
        appendReference.params() != listOf(OBJECT, COLLECTION) || appendReference.returnType != ARRAY_LIST
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

private class FabParts(
    val modifierInstance: String,
    val refType: String,
    val refElement: String,
    val contentLambda: String,
    val remember: String,
    val localColors: String,
    val consume: MethodReference,
    val colorScheme: String,
    val colorA: String,
    val colorB: String,
    val colorsType: String,
    val elevation: String,
    val fabCall: String,
    val composer: String,
    val function0: String,
    val modifierType: String,
)

private fun BytecodePatchContext.harvestFab(): FabParts {
    val fab = FabMethodFingerprint.method
    val ins = fab.implementation!!.instructions.toList()

    val contentInit = ins.indices.firstOrNull { i ->
        val ref = ins.methodAt(i)
        ins[i].opcode == Opcode.INVOKE_DIRECT && ref != null && ref.name == "<init>" &&
            ref.params().let { it.size == 3 && it[0] == "I" && it[1] == STRING }
    } ?: hookNotFound("FAB content lambda constructor")
    val contentRef = ins.methodAt(contentInit)!!
    val refType = contentRef.params()[2]

    val rememberIndex = ins.indices.firstOrNull { i ->
        i > contentInit && ins[i].opcode == Opcode.INVOKE_STATIC &&
            ins.methodAt(i)!!.params().let { it.size == 3 && it[0] == "I" }
    } ?: hookNotFound("rememberComposableLambda call")

    val putIndex = ins.indices.firstOrNull { i ->
        ins[i].opcode == Opcode.IPUT_OBJECT && ins.fieldAt(i)?.definingClass == refType
    } ?: hookNotFound("Ref.element write")
    val modifier = ins.fieldAt(putIndex - 1)
    if (ins[putIndex - 1].opcode != Opcode.SGET_OBJECT || modifier == null) hookNotFound("Modifier.Companion read")

    val wide = ins.indices.filter { ins[it].opcode == Opcode.IGET_WIDE }
    if (wide.size < 2) hookNotFound("colour scheme reads")
    val colorA = ins.fieldAt(wide[0])!!
    val colorB = ins.fieldAt(wide[1])!!
    if (colorA.definingClass != colorB.definingClass) throw PatchException("Colour reads from different classes")

    val cast = (wide[0] downTo 0).firstOrNull { ins[it].opcode == Opcode.CHECK_CAST }
        ?: hookNotFound("colour scheme cast")
    val castType = ((ins[cast] as ReferenceInstruction).reference as TypeReference).type
    val consume = ins.methodAt(cast - 2)
    val localColors = ins.fieldAt(cast - 3)
    if (castType != colorA.definingClass || ins[cast - 1].opcode != Opcode.MOVE_RESULT_OBJECT ||
        consume == null || localColors == null || ins[cast - 3].opcode != Opcode.SGET_OBJECT
    ) {
        throw PatchException("Unexpected LocalColorScheme read sequence")
    }

    val colorsInit = ins.indices.firstOrNull { i ->
        i > wide[1] && ins[i].opcode == Opcode.INVOKE_DIRECT && ins.methodAt(i)!!.name == "<init>" &&
            ins.methodAt(i)!!.params() == listOf("J", "J")
    } ?: hookNotFound("FloatingActionButtonColors constructor")
    val colorsType = ins.methodAt(colorsInit)!!.definingClass

    val elevation = ins.indices.firstOrNull { i ->
        i > colorsInit && ins[i].opcode == Opcode.INVOKE_STATIC && ins.methodAt(i)!!.params() == listOf("I")
    }?.let { ins.methodAt(it) } ?: hookNotFound("FloatingActionButton elevation call")

    val fabCallIndex = ins.indices.lastOrNull { i ->
        ins[i].opcode == Opcode.INVOKE_STATIC_RANGE && ins.methodAt(i)!!.params().size == 8
    } ?: hookNotFound("FloatingActionButton call")
    val fabCall = ins.methodAt(fabCallIndex)!!
    val fabParams = fabCall.params()
    if (fabParams[2] != colorsType || fabParams[3] != elevation.returnType || fabParams[6] != "I" || fabParams[7] != "I") {
        throw PatchException("Unexpected FloatingActionButton signature: ${fabCall.smali()}")
    }

    val remember = ins.methodAt(rememberIndex)!!
    val composer = fabParams[5]
    if (remember.params()[2] != composer) throw PatchException("rememberComposableLambda composer mismatch")

    return FabParts(
        modifierInstance = fieldString(modifier!!),
        refType = refType,
        refElement = fieldString(ins.fieldAt(putIndex)!!),
        contentLambda = contentRef.definingClass,
        remember = remember.smali(),
        localColors = fieldString(localColors),
        consume = consume,
        colorScheme = castType,
        colorA = fieldString(colorA),
        colorB = fieldString(colorB),
        colorsType = colorsType,
        elevation = elevation.smali(),
        fabCall = fabCall.smali(),
        composer = composer,
        function0 = fabParams[4],
        modifierType = fabParams[0],
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
    val parameters = reference.params()
    val parts = harvestFab()
    if (parameters.size != 7 || parameters[0] != TABS_TRAY_STATE || parameters[5] != parts.composer ||
        reference.returnType != "V" || range.registerCount != 7
    ) {
        throw PatchException("Unexpected FloatingToolbarFAB signature: ${reference.smali()}")
    }

    val calleeIns = callMethod.implementation!!.instructions.toList()

    val groupPair = (0 until calleeIns.size - 1).firstOrNull { i ->
        val a = calleeIns.methodAt(i)
        val b = calleeIns.methodAt(i + 1)
        calleeIns[i].opcode == Opcode.INVOKE_INTERFACE && calleeIns[i + 1].opcode == Opcode.INVOKE_INTERFACE &&
            a != null && b != null && a.definingClass == parts.composer && b.definingClass == parts.composer &&
            a.params() == listOf("I") && a.returnType == "V" && b.params().isEmpty() && b.returnType == "V"
    } ?: hookNotFound("Composer replace group calls")
    val startGroup = calleeIns.methodAt(groupPair)!!.smali()
    val endGroup = calleeIns.methodAt(groupPair + 1)!!.smali()

    val modifierType = parts.modifierType
    val paddingCall = calleeIns.indices.firstOrNull { i ->
        val ref = calleeIns.methodAt(i)
        calleeIns[i].opcode == Opcode.INVOKE_STATIC && ref != null &&
            ref.params() == listOf(modifierType, "F", "F", "I") && ref.returnType == modifierType
    } ?: hookNotFound("PaddingKt call")
    val paddingClass = calleeIns.methodAt(paddingCall)!!.definingClass
    val padding = mutableClassDefBy(paddingClass).methods.firstOrNull {
        AccessFlags.STATIC.isSet(it.accessFlags) && it.returnType == modifierType &&
            it.parameterTypes.map { type -> type.toString() } == listOf(modifierType, "F", "F", "F", "F")
    } ?: hookNotFound("padding(Modifier, F, F, F, F)")
    val paddingSmali = "$paddingClass->${padding.name}($modifierType" + "FFFF)$modifierType"

    val weightIndex = (callIndex - 1 downTo 0).firstOrNull { i ->
        calleeIns[i].opcode == Opcode.CONST_HIGH16 && (calleeIns[i] as NarrowLiteralInstruction).narrowLiteral == 0x3f800000
    } ?: hookNotFound("FAB box weight")
    val weightRegister = callMethod.getInstruction<OneRegisterInstruction>(weightIndex).registerA

    val consume = parts.consume
    val consumeSmali = "${parts.composer}->${consume.name}(${consume.params().joinToString("")})${consume.returnType}"
    val function0 = parts.function0
    val composer = parts.composer

    addStaticMethod(
        TAB_FAB,
        "button",
        listOf(function0, composer, "I", "F"),
        "V",
        16,
        """
            new-instance v6, ${parts.refType}
            invoke-direct {v6}, ${parts.refType}-><init>()V
            sget-object v0, ${parts.modifierInstance}
            iput-object v0, v6, ${parts.refElement}
            const-string v0, "Tab button"
            new-instance v2, ${parts.contentLambda}
            invoke-direct {v2, p2, v0, v6}, ${parts.contentLambda}-><init>(ILjava/lang/String;${parts.refType})V
            const v0, $FAB_CONTENT_KEY
            invoke-static {v0, v2, p1}, ${parts.remember}
            move-result-object v1
            sget-object v2, ${parts.localColors}
            invoke-interface {p1, v2}, $consumeSmali
            move-result-object v2
            check-cast v2, ${parts.colorScheme}
            iget-wide v8, v2, ${parts.colorA}
            iget-wide v10, v2, ${parts.colorB}
            new-instance v2, ${parts.colorsType}
            invoke-direct {v2, v8, v9, v10, v11}, ${parts.colorsType}-><init>(JJ)V
            const/16 v3, 0xf
            invoke-static {v3}, ${parts.elevation}
            move-result-object v3
            sget-object v0, ${parts.modifierInstance}
            const/4 v4, 0x0
            invoke-static {v0, v4, v4, p3, v4}, $paddingSmali
            move-result-object v0
            move-object v4, p0
            move-object v5, p1
            const/16 v6, 0x36
            const/4 v7, 0x4
            invoke-static/range {v0 .. v7}, ${parts.fabCall}
            return-void
        """,
    )

    val kinds = (0..2).joinToString("\n") { kind ->
        """
            const/4 v0, 0x$kind
            invoke-static {v3, v0}, $TAB_FAB->shown(II)Z
            move-result v1
            if-eqz v1, :ftl_skip$kind
            invoke-static {v0, p1}, $TAB_FAB->click(ILjava/lang/Object;)Ljava/lang/Object;
            move-result-object v1
            check-cast v1, $function0
            invoke-static {v0}, $TAB_FAB->icon(I)I
            move-result v2
            invoke-static {v3, v0}, $TAB_FAB->padding(II)F
            move-result v4
            invoke-static {v1, p2, v2, v4}, $TAB_FAB->button($function0${composer}IF)V
            :ftl_skip$kind
        """.trimIndent()
    }

    addStaticMethod(
        TAB_FAB,
        "extras",
        listOf(TABS_TRAY_STATE, function0, composer),
        "V",
        8,
        """
            const v0, $FAB_GROUP_KEY_END
            invoke-interface {p2, v0}, $startGroup
            invoke-static {p0}, $TAB_SCROLL->tray(Ljava/lang/Object;)V
            invoke-virtual {p0}, $TABS_TRAY_STATE->getSelectedPage()$TABS_PAGE
            move-result-object v0
            invoke-static {v0}, $TAB_FAB->plan(Ljava/lang/Object;)I
            move-result v3
        """.trimIndent() + "\n" + kinds + "\n" + """
            invoke-interface {p2}, $endGroup
            return-void
        """.trimIndent(),
    )

    val interfaces = mutableClassDefBy(TAB_END_CLICK).interfaces
    if (function0 !in interfaces) interfaces.add(function0)

    callMethod.replaceInstruction(weightIndex, "const/high16 v$weightRegister, 0x40000000")

    val state = range.startRegister
    val newTab = range.startRegister + 1
    val composerRegister = range.startRegister + 5
    callMethod.applyEdits(
        insert(
            callIndex,
            callMethod.staticCall(
                callIndex,
                "$TAB_FAB->extras($TABS_TRAY_STATE$function0$composer)V",
                listOf(state, newTab, composerRegister),
            ),
        ),
    )
}

@Suppress("unused")
val tabNextToCurrentPatch = bytecodePatch(
    name = "New tab next to current tab",
    description = "Opens new tabs right after the current tab and adds an add-to-end button (+ with a " +
        "down arrow) beside + that opens a tab at the end. Both are toggles in Mod Settings.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_FIREFOX_NIGHTLY)

    dependsOn(tabIconsPatch)

    extendWith("extensions/firefox.mpe")

    execute {
        installTabAccessors()
        hookTabInsert()
        installTabFab()
    }
}
