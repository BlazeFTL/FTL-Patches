package app.ftl.patches.firefox

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import java.util.logging.Logger
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter

private const val COMPOSER = "Landroidx/compose/runtime/Composer;"
private const val ACCESS_POINT = "Lorg/mozilla/fenix/components/menu/MenuAccessPoint;"
private const val REMEMBER_LAMBDA =
    "Landroidx/compose/runtime/internal/ComposableLambdaKt;->rememberComposableLambda" +
        "(ILkotlin/Function;$COMPOSER)Landroidx/compose/runtime/internal/ComposableLambdaImpl;"
private const val MENU_GROUP =
    "$MENU_GROUP_KT->MenuGroup(Landroidx/compose/runtime/internal/ComposableLambdaImpl;${COMPOSER}I)V"
private const val LIBRARY_GROUP =
    "$MAIN_MENU_KT->LibraryMenuGroup(ZLkotlin/jvm/functions/Function0;Lkotlin/jvm/functions/Function0;" +
        "Lkotlin/jvm/functions/Function0;Lkotlin/jvm/functions/Function0;${COMPOSER}I)V"
private const val DIVIDER = "$MOD_COMPOSE->divider(Ljava/lang/Object;)V"
private const val OBJ5 = "Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;"

private const val EXTENSION_PIN = "Lapp/ftl/extension/firefox/ExtensionPin;"
private const val PIN_PREFIX = "Lapp/ftl/extension/firefox/"
private const val FUNCTION0 = "Lkotlin/jvm/functions/Function0;"
private const val TOOLBAR_EVENT =
    "Lmozilla/components/compose/browser/toolbar/store/BrowserToolbarInteraction\$BrowserToolbarEvent;"

private fun BytecodePatchContext.installLambdaInterfaces() {
    listOf(
        MOD_LAMBDA to "Lkotlin/jvm/functions/Function2;",
        MOD_CLICK to FUNCTION0,
        "${PIN_PREFIX}PinObserver;" to "Lkotlin/jvm/functions/Function1;",
        "${PIN_PREFIX}PinClick;" to FUNCTION0,
        "${PIN_PREFIX}PinLong;" to FUNCTION0,
        "${PIN_PREFIX}PinEvent;" to TOOLBAR_EVENT,
        "${PIN_PREFIX}PinCont;" to "Lkotlin/coroutines/Continuation;",
    ).forEach { (type, function) ->
        val interfaces = mutableClassDefBy(type).interfaces
        if (function !in interfaces) interfaces.add(function)
    }

    val continuation = mutableClassDefBy("${PIN_PREFIX}PinCont;")
    val context = ImmutableMethod(
        continuation.type,
        "getContext",
        emptyList(),
        "Lkotlin/coroutines/CoroutineContext;",
        AccessFlags.PUBLIC.value or AccessFlags.FINAL.value,
        null,
        null,
        MutableMethodImplementation(2),
    ).toMutable()
    context.addInstructions(
        0,
        """
            sget-object v0, Lkotlin/coroutines/EmptyCoroutineContext;->INSTANCE:Lkotlin/coroutines/EmptyCoroutineContext;
            return-object v0
        """,
    )
    continuation.methods.add(context)
}

private fun BytecodePatchContext.installComposeMethods() {
    val cls = mutableClassDefBy(MOD_COMPOSE)
    COMPOSE_METHODS.forEach { spec ->
        cls.methods.removeAll { it.name == spec.name }
        val method = ImmutableMethod(
            MOD_COMPOSE,
            spec.name,
            spec.parameters.map { ImmutableMethodParameter(it, null, null) },
            "V",
            AccessFlags.PUBLIC.value or AccessFlags.STATIC.value,
            null,
            null,
            MutableMethodImplementation(spec.registers),
        ).toMutable()
        method.addInstructionsWithLabels(0, spec.smali.trimIndent())
        cls.methods.add(method)
    }
}

private fun MutableMethod.lastReturnObject() =
    implementation!!.instructions.indexOfLast { it.opcode == Opcode.RETURN_OBJECT }

private fun MutableMethod.lastReturnVoid() =
    implementation!!.instructions.indexOfLast { it.opcode == Opcode.RETURN_VOID }

private fun MutableMethod.branchTarget(index: Int) = jumpTarget(index)

private fun BytecodePatchContext.installWindowHooks() {
    listOf(DialogFragmentCreateDialogFingerprint, MenuFragmentCreateDialogFingerprint).forEach {
        it.method.apply {
            val at = lastReturnObject()
            val register = getInstruction<OneRegisterInstruction>(at).registerA
            addInstructions(
                at,
                "invoke-static/range {v$register .. v$register}, $OLD_MENU->onCreateDialog(Landroid/app/Dialog;)V",
            )
        }
    }

    DialogFragmentViewCreatedFingerprint.let {
        it.method.addInstructions(
            it.instructionMatches[0].index + 1,
            """
                iget-object v0, p0, Landroidx/fragment/app/DialogFragment;->mDialog:Landroid/app/Dialog;
                invoke-static {v0}, $OLD_MENU->onViewCreated(Landroid/app/Dialog;)V
            """,
        )
    }

    listOf(
        DialogFragmentWidthFingerprint to 240,
        MenuFragmentWidthFingerprint to 300,
    ).forEach { (fingerprint, dp) ->
        fingerprint.method.applyEdits(
            returnWhenOld(
                0,
                0,
                """
                    invoke-virtual {p0}, Landroidx/fragment/app/Fragment;->getResources()Landroid/content/res/Resources;
                    move-result-object v0
                    const/16 v1, $dp
                    invoke-static {v0, v1}, $OLD_MENU->menuWidth(Landroid/content/res/Resources;I)I
                    move-result v0
                    return v0
                """,
            ),
        )
    }

    listOf(DialogFragmentShowFingerprint, MenuFragmentShowFingerprint).forEach {
        it.method.apply {
            addInstructions(
                lastReturnVoid(),
                "invoke-static {}, $OLD_MENU->afterShow()V",
            )
            addInstructions(
                0,
                "invoke-static {p1}, $OLD_MENU->onShowStart(Landroid/content/DialogInterface;)V",
            )
        }
    }

    listOf(DialogFragmentInsetsFingerprint, MenuFragmentInsetsFingerprint).forEach {
        it.method.applyEdits(
            returnWhenOld(
                0,
                0,
                """
                    const/4 v0, 0x0
                    invoke-virtual {p1, v0, v0, v0, v0}, Landroid/view/View;->setPadding(IIII)V
                    return-object p2
                """,
            ),
        )
    }
}

private val logger = Logger.getLogger("OldMenuPatch")

private const val LAMBDA_IMPL = "Landroidx/compose/runtime/internal/ComposableLambdaImpl;"

private fun unsupported(what: String): Nothing =
    throw PatchException("$what differs in this Firefox build. Patch needs an update for it.")

private fun MutableMethod.requireParameters(what: String, expected: List<String>) {
    if (parameterTypes.map { it.toString() } != expected) unsupported(what)
}

private fun BytecodePatchContext.requireFields(type: String, expected: Map<String, String>) {
    val fields = mutableClassDefBy(type).fields.associate { it.name to it.type }
    expected.forEach { (name, fieldType) -> if (fields[name] != fieldType) unsupported("$type->$name") }
}

private fun optional(name: String, block: () -> Unit) {
    try {
        block()
    } catch (e: Exception) {
        logger.info("Skipped $name: ${e::class.simpleName}: ${e.message?.lineSequence()?.firstOrNull()}")
    }
}

private class ClonedCall(val start: Int, val scratch: Int, val smali: String)

private fun cloneLibraryCall(
    method: MutableMethod,
    callIndex: Int,
    composer: Int,
    live: Liveness,
    insertAt: Int,
): ClonedCall {
    val call = method.getInstruction<RegisterRangeInstruction>(callIndex)
    val reference = (call as ReferenceInstruction).reference as MethodReference
    val first = call.startRegister
    val count = call.registerCount

    val loads = HashMap<Int, Instruction>()
    var start = callIndex
    while (start > 0) {
        val previous = method.getInstruction<Instruction>(start - 1)
        val dest = (previous as? OneRegisterInstruction)?.registerA ?: break
        val loadOpcode = previous.opcode.name.let { n -> n.startsWith("const") || n.startsWith("iget") }
        if (!loadOpcode || dest !in first until first + count || dest in loads) break
        loads[dest] = previous
        start--
    }

    val scratch = live.takeLow(1, insertAt, exclude = setOf(0, composer))[0]
    val base = live.takeRun(count, insertAt, exclude = setOf(0, composer, scratch))
    val lines = ArrayList<String>()
    for (register in first until first + count) {
        val target = base + (register - first)
        val load = loads[register]
        when {
            load == null && register == composer -> lines += "move-object/from16 v$target, v$composer"
            load == null -> unsupported("LibraryMenuGroup call")
            load.opcode.name.startsWith("const") ->
                lines += "const v$target, 0x${Integer.toHexString((load as NarrowLiteralInstruction).narrowLiteral)}"
            load.opcode.name.startsWith("iget") -> {
                val field = (load as ReferenceInstruction).reference as FieldReference
                val ref = "${field.definingClass}->${field.name}:${field.type}"
                val name = load.opcode.name
                lines += "$name v$scratch, v0, $ref"
                lines += if (name == "iget-object") "move-object/from16 v$target, v$scratch"
                else "move/from16 v$target, v$scratch"
            }
            else -> unsupported("LibraryMenuGroup call")
        }
    }
    val params = reference.parameterTypes.joinToString("")
    lines += "invoke-static/range {v$base .. v${base + count - 1}}, " +
        "${reference.definingClass}->${reference.name}($params)${reference.returnType}"
    return ClonedCall(start, scratch, lines.joinToString("\n"))
}

private const val TEXT_KT = "Landroidx/compose/material3/TextKt;"
private const val ICON_LIST_ITEM_OWNER = "Lorg/mozilla/fenix/compose/list/ListItemKt;"
private const val WEB_EXTENSION_ITEM = "Lorg/mozilla/fenix/components/menu/store/WebExtensionMenuItem;"
private const val PIN_STORE_CALL = "$EXTENSION_PIN->remember(Ljava/lang/Object;Ljava/lang/Object;)V"
private const val PIN_LIST_CALL = "$EXTENSION_PIN->addPinned(Ljava/util/ArrayList;Ljava/lang/Object;)V"
private const val PIN_WRAP_CALL = "$EXTENSION_PIN->wrap(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"
private const val PIN_LONG_CALL = "$EXTENSION_PIN->longOf(Ljava/lang/Object;)Ljava/lang/Object;"
private const val PIN_HANDLE_CALL = "$EXTENSION_PIN->handle(Ljava/lang/Object;Ljava/lang/Object;)V"

private fun BytecodePatchContext.prepareEndActions(): () -> Unit {
    val fingerprint = ToolbarEndActionsFingerprint
    val method = fingerprint.method
    val start = fingerprint.instructionMatches[0].index
    val created = method.firstIndexFrom(start) { _, instruction ->
        instruction.opcode == Opcode.NEW_INSTANCE && instruction.refersToType("Ljava/util/ArrayList;")
    }
    if (created < 0) unsupported("Toolbar end actions list")
    val list = method.getInstruction<OneRegisterInstruction>(created).registerA
    val constructed = method.firstIndexFrom(created + 1) { _, instruction ->
        instruction.opcode == Opcode.INVOKE_DIRECT && instruction.calls("<init>") &&
            instruction.callRegisters().firstOrNull() == list
    }
    if (constructed < 0) unsupported("Toolbar end actions list constructor")
    val at = constructed + 1
    val owner = Liveness(method).holder(method.parameterBase(), at, "Toolbar middleware")
    val call = method.staticCall(at, PIN_LIST_CALL, listOf(list, owner))
    return { method.applyEdits(insert(at, call)) }
}

private fun BytecodePatchContext.prepareToolbarMiddleware(): () -> Unit {
    val fingerprint = ToolbarMiddlewareFingerprint
    val method = fingerprint.method
    val m = fingerprint.instructionMatches
    val live = Liveness(method)
    val owner = method.parameterBase()
    val menuClicked = m[4].index
    val rememberAt = m[2].index + 1

    val rememberCall = method.staticCall(
        rememberAt,
        PIN_STORE_CALL,
        listOf(live.holder(parameterRegister(method, 0), rememberAt, "Toolbar store"), live.holder(owner, rememberAt, "Toolbar middleware")),
    )

    val action = method.lowHolder(live, parameterRegister(method, 2), menuClicked, "Toolbar action")
    val next = method.lowHolder(live, parameterRegister(method, 1), menuClicked, "Toolbar next")
    val middleware = method.lowHolder(live, owner, menuClicked, "Toolbar middleware")
    val skip = method.lastIndexBefore(menuClicked, 8) { it.opcode.name.startsWith("goto") }
    if (skip < 0) unsupported("Toolbar middleware exit")
    val end = method.jumpTarget(skip)
    val scratch = live.takeLow(1, menuClicked, joins = listOf(end), exclude = setOf(action, next, middleware))[0]

    return {
        method.applyEdits(
            insert(rememberAt, rememberCall),
            insert(
                menuClicked,
                """
                    instance-of v$scratch, v$action, ${PIN_PREFIX}PinEvent;
                    if-eqz v$scratch, :ftl_next
                    invoke-static {v$action, v$middleware}, $PIN_HANDLE_CALL
                    invoke-interface {v$next, v$action}, Lkotlin/jvm/functions/Function1;->invoke(Ljava/lang/Object;)Ljava/lang/Object;
                    goto/16 :ftl_end
                """,
                mapOf(
                    "ftl_next" to { menuClicked },
                    "ftl_end" to { end },
                ),
            ),
        )
    }
}

private fun BytecodePatchContext.prepareExtensionItems(): () -> Unit {
    val fingerprint = WebExtensionMenuItemsPinFingerprint
    val method = fingerprint.method
    val at = fingerprint.instructionMatches[0].index

    val castAt = method.lastIndexBefore(at, 12) {
        it.opcode == Opcode.CHECK_CAST && it.refersToType(FUNCTION0)
    }
    if (castAt < 0) unsupported("Extension menu click handler")
    val click = method.getInstruction<OneRegisterInstruction>(castAt).registerA

    var item = -1
    var itemAt = -1
    for (i in at - 1 downTo maxOf(0, at - 80)) {
        val register = method.callArgument(i, WEB_EXTENSION_ITEM)
        if (register != null) {
            item = register
            itemAt = i
            break
        }
    }
    if (item < 0) unsupported("Extension menu item")
    if (Liveness(method).redefinedBetween(item, itemAt + 1, at)) unsupported("Extension menu item register")

    val call = method.staticCall(at, PIN_WRAP_CALL, listOf(click, item))
    return {
        method.applyEdits(
            insert(
                at,
                """
                    $call
                    move-result-object v$click
                    check-cast v$click, $FUNCTION0
                """,
            ),
        )
    }
}

private fun BytecodePatchContext.prepareIconListItem(): () -> Unit {
    val method = IconListItemFingerprint.method
    val callAt = method.firstIndexFrom(0) { _, instruction ->
        val reference = (instruction as? ReferenceInstruction)?.reference as? MethodReference
        reference != null && reference.definingClass == ICON_LIST_ITEM_OWNER &&
            (reference.name == "ListItem" || reference.name.startsWith("ListItem-"))
    }
    if (callAt < 0) unsupported("Icon list item call")
    val functions = method.callParameters(callAt).filter { it.first == FUNCTION0 }
    if (functions.size < 2) unsupported("Icon list item click arguments")
    val source = functions[0].second
    val target = functions[1].second
    if (source == target) unsupported("Icon list item click registers")
    if (Liveness(method).isLive(callAt + 1, target)) unsupported("Icon list item long click register")
    val call = method.staticCall(callAt, PIN_LONG_CALL, listOf(source))
    return {
        method.applyEdits(
            insert(
                callAt,
                """
                    $call
                    move-result-object v$target
                    check-cast v$target, $FUNCTION0
                """,
            ),
        )
    }
}

private fun BytecodePatchContext.installPinHooks() {
    val edits = try {
        listOf(
            prepareEndActions(),
            prepareToolbarMiddleware(),
            prepareExtensionItems(),
        )
    } catch (e: Exception) {
        logger.warning("Extension pinning skipped: ${e::class.simpleName}: ${e.message?.lineSequence()?.firstOrNull()}")
        return
    }
    val longPress = try {
        prepareIconListItem()
    } catch (e: Exception) {
        logger.warning("Extension pin long press skipped: ${e::class.simpleName}: ${e.message?.lineSequence()?.firstOrNull()}")
        null
    }
    edits.forEach { it() }
    longPress?.invoke()
}

private fun BytecodePatchContext.installMenuTweaks() {
    optional("BadgeHide") {
        BadgeHideFingerprint.method.applyEdits(
            insert(
                0,
                """
                    invoke-static/range {p0 .. p0}, $OLD_MENU->hideBadge(Ljava/lang/String;)Z
                    move-result v0
                    if-eqz v0, :ftl_stock
                    return-void
                """,
                mapOf("ftl_stock" to { 0 }),
            ),
        )
    }

    optional("MenuNavigation") {
        MenuNavigationFingerprint.let {
            val at = it.instructionMatches[0].index
            val register = it.method.getInstruction<OneRegisterInstruction>(at).registerA
            it.method.applyEdits(
                swap(
                    at,
                    register,
                    """
                        invoke-static {}, $OLD_MENU->navPadding()F
                        move-result v$register
                    """,
                ),
            )
        }
    }

    optional("BottomSheetHandle") {
        BottomSheetHandleFingerprint.method.applyEdits(returnWhenOld(0, 0, "return-void"))
    }

    optional("ExtensionsMenuItem") {
        ExtensionsMenuItemFingerprint.let {
            it.method.applyEdits(swap(it.instructionMatches[0].index, 5, "const/16 v5, 0x0"))
        }
    }
    optional("WebExtensionMenuItems") {
        WebExtensionMenuItemsFingerprint.let {
            it.method.applyEdits(zeroTo(it.instructionMatches[0].index))
        }
    }

    optional("IPProtectionMenuItem") {
        IPProtectionMenuItemFingerprint.let {
            it.method.applyEdits(
                surface(it.instructionMatches[0].index),
                floatTo(it.instructionMatches[1].index, 0x42400000),
            )
        }
    }
    optional("IPProtectionBadge") {
        IPProtectionBadgeFingerprint.let {
            val badge = it.instructionMatches[0].index
            it.method.applyEdits(
                swap(
                    badge,
                    6,
                    "invoke-static {v1}, $OLD_MENU->hideBadge(Ljava/lang/String;)Z\nmove-result v6\nif-eqz v6, :ftl_stock",
                    count = 1,
                ),
                floatTo(it.instructionMatches[1].index, 0x40000000),
            )
        }
    }

    MainMenuAddonsFingerprint.let {
        val m = it.instructionMatches
        it.method.applyEdits(
            zeroTo(m[0].index),
            swap(
                m[1].index,
                1,
                """
                    const v1, 0x7a11b001
                    invoke-virtual {v4, v1}, Landroidx/compose/runtime/GapComposer;->startReplaceGroup(I)V
                    sget-boolean v1, $OLD_MENU->extensionsActive:Z
                    if-eqz v1, :ftl_row
                    invoke-static {v4, v9}, $MOD_COMPOSE->managerRow(Ljava/lang/Object;Ljava/lang/Object;)V
                    :ftl_row
                    const/4 v1, 0x0
                    invoke-virtual {v4, v1}, Landroidx/compose/runtime/GapComposer;->end(Z)V
                """,
                count = 0,
            ),
            insert(
                m[4].index,
                """
                    sget-boolean v0, $OLD_MENU->extensionsActive:Z
                    if-nez v0, :ftl_skip
                """,
                mapOf("ftl_skip" to { m[5].index + 3 }),
            ),
        )
    }

    MainMenuLibraryGroupFingerprint.method.let { method ->
        val types = method.parameterTypes.map { type -> type.toString() }
        val composerAt = types.indexOf(COMPOSER)
        val functionsAt = composerAt - 4
        if (functionsAt !in 1..2 ||
            types.subList(functionsAt, composerAt).any { type -> type != FUNCTION0 } ||
            types.subList(0, functionsAt).any { type -> type != "Z" }
        ) {
            unsupported("LibraryMenuGroup")
        }
        val flagged = functionsAt == 2
        val first = if (flagged) 0 else functionsAt
        val target = if (flagged) {
            "libraryGroupFlagged(ZZ$OBJ5)V"
        } else {
            "libraryGroup($OBJ5)V"
        }
        method.applyEdits(
            returnWhenOld(
                0,
                0,
                """
                    invoke-static/range {p$first .. p$composerAt}, $MOD_COMPOSE->$target
                    return-void
                """,
            ),
        )
    }

    MainMenuFingerprint.let {
        val params = it.method.parameterTypes.map { type -> type.toString() }
        if (params.getOrNull(0) != ACCESS_POINT || params.getOrNull(7) != "Z") unsupported("MainMenu")
        val m = it.instructionMatches
        it.method.applyEdits(
            zeroTo(m[0].index),
            swap(
                m[1].index,
                3,
                """
                    move/from16 v3, p7
                    invoke-static {v0, v3}, $OLD_MENU->bottomPadding(FZ)F
                    move-result v0
                """,
                count = 0,
            ),
            swap(
                m[6].index,
                0,
                """
                    move-object/from16 v0, p0
                    move/from16 v1, p7
                    invoke-static {v0, v1}, $OLD_MENU->setExtensionsActive(Ljava/lang/Object;Z)V
                """,
                count = 0,
            ),
        )
    }

    optional("MoreExtensionsMenuItem") {
        MoreExtensionsMenuItemFingerprint.let {
            it.method.applyEdits(surface(it.instructionMatches[0].index))
        }
    }

    listOf(MainMenuNavigationLambdaFingerprint, MainMenuDividerLambdaFingerprint).forEach {
        val ifEq = it.instructionMatches[1].index
        val scratch = Liveness(it.method).takeLow(1, ifEq + 1)[0]
        it.method.applyEdits(
            insert(
                ifEq + 1,
                """
                    sget-boolean v$scratch, $OLD_MENU->extensionsActive:Z
                    if-nez v$scratch, :ftl_skip
                """,
                mapOf("ftl_skip" to { branchTarget(ifEq) }),
            ),
        )
    }

    MainMenuColumnLambdaFingerprint.let {
        val m = it.instructionMatches
        val method = it.method
        val lambda = method.definingClass
        requireFields(
            lambda,
            mapOf(
                "f${'$'}1" to ACCESS_POINT,
                "f${'$'}10" to "Z",
                "f${'$'}15" to FUNCTION0,
                "f${'$'}23" to LAMBDA_IMPL,
            ),
        )
        val composer = method.getInstruction<FiveRegisterInstruction>(m[0].index).registerC
        val live = Liveness(method)
        val extensionsAt = m[2].index + 1
        val libraryAt = m[4].index + 6
        val libraryCall = m[7].index
        val (r1, r2, r3) = live.takeLow(3, extensionsAt, exclude = setOf(composer, 0))
        val library = cloneLibraryCall(method, libraryCall, composer, live, libraryAt)
        val divider = "invoke-static {v$composer}, $DIVIDER"
        method.applyEdits(
            swap(
                extensionsAt,
                r1,
                """
                    iget-boolean v$r1, v0, $lambda->f${'$'}10:Z
                    if-eqz v$r1, :ftl_stock
                    iget-object v$r2, v0, $lambda->f${'$'}1:$ACCESS_POINT
                    sget-object v$r1, $ACCESS_POINT->Browser:$ACCESS_POINT
                    if-ne v$r2, v$r1, :ftl_stock
                    const v$r1, 0x6e7a3b10
                    invoke-interface {v$composer, v$r1}, $COMPOSER->startReplaceGroup(I)V
                    iget-object v$r2, v0, $lambda->f${'$'}23:$LAMBDA_IMPL
                    iget-object v$r3, v0, $lambda->f${'$'}15:$FUNCTION0
                    invoke-static {v$r2, v$r3}, $MOD_LAMBDA->extensionsPage(Ljava/lang/Object;Ljava/lang/Object;)$MOD_LAMBDA
                    move-result-object v$r1
                    const v$r2, 0x6e7a3b11
                    invoke-static {v$r2, v$r1, v$composer}, $REMEMBER_LAMBDA
                    move-result-object v$r1
                    const/4 v$r2, 0x6
                    invoke-static {v$r1, v$composer, v$r2}, $MENU_GROUP
                    invoke-interface {v$composer}, $COMPOSER->endReplaceGroup()V
                    goto/16 :ftl_end
                """,
                count = 0,
                fallThrough = false,
                labels = mapOf("ftl_end" to { implementation!!.instructions.size - 2 }),
            ),
            swap(libraryAt, library.scratch, "$divider\n${library.smali}", count = 0),
            swap(m[6].index + 4, live.takeLow(1, m[6].index + 4)[0], divider, count = 0),
            swap(
                library.start,
                live.takeLow(1, library.start)[0],
                divider,
                count = libraryCall - library.start + 1,
            ),
            insert(
                m[8].index + 1,
                "invoke-static {v$composer}, $MOD_COMPOSE->modRow(Ljava/lang/Object;)V",
            ),
            swap(m[9].index - 2, live.takeLow(1, m[9].index - 2)[0], divider, count = 0),
        )
    }

    optional("MenuGroup") {
        MenuGroupFingerprint.let { it.method.applyEdits(zeroTo(it.instructionMatches[0].index)) }
    }

    optional("Badge") {
        BadgeFingerprint.let {
            it.method.applyEdits(
                floatTo(it.instructionMatches[0].index, 0x41400000),
                floatTo(it.instructionMatches[1].index, 0x40000000),
            )
        }
    }

    optional("MenuBadgeItem") {
        MenuBadgeItemFingerprint.let {
            val m = it.instructionMatches
            it.method.applyEdits(
                surface(m[0].index),
                floatTo(m[1].index, 0x40000000),
                floatTo(m[2].index, 0x41400000),
            )
        }
    }

    optional("MenuItem") {
        MenuItemFingerprint.let {
            val m = it.instructionMatches
            it.method.applyEdits(
                swap(m[0].index, 5, "const/16 v5, 0x0"),
                surface(m[1].index),
                floatTo(m[2].index, 0x42200000),
                floatTo(m[3].index, 0x42200000),
            )
        }
    }

    optional("MenuTextItem") {
        MenuTextItemFingerprint.let {
            val m = it.instructionMatches
            it.method.applyEdits(
                floatTo(m[0].index, 0x42200000),
                floatTo(m[1].index, 0x42200000),
                surface(m[2].index),
            )
        }
    }

    optional("WebExtensionMenuItem") {
        WebExtensionMenuItemFingerprint.let {
            val m = it.instructionMatches
            it.method.applyEdits(
                surface(m[0].index),
                swap(
                    m[1].index,
                    27,
                    """
                        const v27, 0x1fe47c
                        if-eqz v2, :ftl_empty
                        invoke-virtual {v2}, Ljava/lang/String;->length()I
                        move-result v13
                        if-nez v13, :ftl_filled
                        :ftl_empty
                        const/16 v22, 0x0
                        :ftl_filled
                    """,
                ),
                floatTo(m[2].index, 0x42400000),
            )
        }
    }

    optional("MenuItemIconLambda") {
        MenuItemIconLambdaFingerprint.let {
            val iget = it.instructionMatches[0].index
            val register = it.method.getInstruction<OneRegisterInstruction>(iget).registerA
            it.method.applyEdits(
                insert(
                    iget + 1,
                    """
                        invoke-static {v$register}, $OLD_MENU->trailingIcon(Ljava/lang/Object;)Ljava/lang/Object;
                        move-result-object v$register
                        check-cast v$register, Lkotlin/jvm/functions/Function0;
                    """,
                ),
            )
        }
    }

    optional("MenuNavItem") {
        MenuNavItemFingerprint.let {
            val m = it.instructionMatches
            it.method.applyEdits(
                swap(m[0].index - 1, 41, ""),
                swap(m[1].index, 43, ""),
            )
        }
    }

    optional("MenuFrame") {
        MenuFrameFingerprint.let { it.method.applyEdits(zeroTo(it.instructionMatches[0].index)) }
    }

    optional("AccountMenuItem") {
        AccountMenuItemFingerprint.let {
            val m = it.instructionMatches
            it.method.applyEdits(surface(m[0].index), floatTo(m[1].index, 0x42400000))
        }
    }

    optional("AccountSubtitleLambda") {
        AccountSubtitleLambdaFingerprint.let {
            val method = it.method
            val iget = it.instructionMatches[1].index
            val callAt = method.firstIndexFrom(iget + 1) { _, instruction ->
                val reference = (instruction as? ReferenceInstruction)?.reference as? MethodReference
                reference != null && reference.definingClass == TEXT_KT && reference.name.startsWith("Text-")
            }
            if (callAt < 0) unsupported("Account subtitle text")
            val text = method.callParameters(callAt).firstOrNull()
            if (text == null || text.first != "Ljava/lang/String;") unsupported("Account subtitle text argument")
            val register = text.second
            val call = method.staticCall(
                callAt,
                "$OLD_MENU->accountSubtitle(Ljava/lang/String;)Ljava/lang/String;",
                listOf(register),
            )
            method.applyEdits(
                insert(
                    callAt,
                    """
                        $call
                        move-result-object v$register
                        if-eqz v$register, :ftl_skip
                    """,
                    mapOf("ftl_skip" to { callAt + 1 }),
                ),
            )
        }
    }
}

@Suppress("unused")
val oldMenuPatch = bytecodePatch(
    name = "Old style 3 dot menu And Extension Pinning",
    description = "Adds \"Mod Settings\" to the 3 dot menu: switch between the stock bottom sheet " +
        "menu and the old style popup menu, and pin extensions to the search bar.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_FIREFOX_NIGHTLY)

    dependsOn(modIconPatch)

    extendWith("extensions/firefox.mpe")

    execute {
        installLambdaInterfaces()
        installComposeMethods()
        installWindowHooks()
        installMenuTweaks()
        installPinHooks()
    }
}
