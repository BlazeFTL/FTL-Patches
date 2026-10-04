package app.ftl.patches.firefox

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
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

private fun BytecodePatchContext.installPinHooks() {
    ToolbarEndActionsFingerprint.let {
        it.method.applyEdits(
            insert(
                it.instructionMatches[1].index + 2,
                "invoke-static {v0, p0}, $EXTENSION_PIN->addPinned(Ljava/util/ArrayList;Ljava/lang/Object;)V",
            ),
        )
    }

    ToolbarMiddlewareFingerprint.let {
        val m = it.instructionMatches
        val menuClicked = m[4].index
        it.method.applyEdits(
            insert(
                m[2].index + 1,
                "invoke-static {v1, v0}, $EXTENSION_PIN->remember(Ljava/lang/Object;Ljava/lang/Object;)V",
            ),
            insert(
                menuClicked,
                """
                    instance-of v4, v3, ${PIN_PREFIX}PinEvent;
                    if-eqz v4, :ftl_next
                    invoke-static {v3, v0}, $EXTENSION_PIN->handle(Ljava/lang/Object;Ljava/lang/Object;)V
                    invoke-interface {v2, v3}, Lkotlin/jvm/functions/Function1;->invoke(Ljava/lang/Object;)Ljava/lang/Object;
                    goto/16 :ftl_end
                """,
                mapOf(
                    "ftl_next" to { menuClicked },
                    "ftl_end" to { jumpTarget(menuClicked - 1) },
                ),
            ),
        )
    }

    WebExtensionMenuItemsPinFingerprint.let {
        it.method.applyEdits(
            insert(
                it.instructionMatches[0].index,
                """
                    sget-object v6, Landroidx/compose/ui/platform/AndroidCompositionLocals_androidKt;->LocalContext:Landroidx/compose/runtime/StaticProvidableCompositionLocal;
                    invoke-virtual {v14, v6}, Landroidx/compose/runtime/GapComposer;->consume(Landroidx/compose/runtime/ProvidableCompositionLocal;)Ljava/lang/Object;
                    move-result-object v6
                    check-cast v6, Landroid/content/Context;
                    invoke-static {v13, v8, v6}, $EXTENSION_PIN->wrap(Ljava/lang/Object;Ljava/lang/Object;Landroid/content/Context;)Ljava/lang/Object;
                    move-result-object v13
                    check-cast v13, $FUNCTION0
                """,
            ),
        )
    }

    IconListItemFingerprint.let {
        it.method.applyEdits(
            insert(
                it.instructionMatches[1].index + 1,
                """
                    invoke-static {v15}, $EXTENSION_PIN->longOf(Ljava/lang/Object;)Ljava/lang/Object;
                    move-result-object v16
                    check-cast v16, $FUNCTION0
                """,
            ),
        )
    }
}

private fun BytecodePatchContext.installMenuTweaks() {
    MenuNavigationFingerprint.let {
        it.method.applyEdits(
            swap(
                it.instructionMatches[0].index,
                2,
                """
                    invoke-static {}, $OLD_MENU->navPadding()F
                    move-result v2
                """,
            ),
        )
    }

    BottomSheetHandleFingerprint.method.applyEdits(returnWhenOld(0, 0, "return-void"))

    ExtensionsMenuItemFingerprint.let {
        it.method.applyEdits(swap(it.instructionMatches[0].index, 5, "const/16 v5, 0x0"))
    }
    WebExtensionMenuItemsFingerprint.let {
        it.method.applyEdits(zeroTo(it.instructionMatches[0].index))
    }

    IPProtectionMenuItemFingerprint.let {
        it.method.applyEdits(
            surface(it.instructionMatches[0].index),
            floatTo(it.instructionMatches[1].index, 0x42400000),
        )
    }
    IPProtectionBadgeFingerprint.let {
        val badge = it.instructionMatches[0].index
        it.method.applyEdits(
            swap(badge - 1, 6, "", count = 2),
            floatTo(it.instructionMatches[1].index, 0x40000000),
        )
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

    MainMenuLibraryGroupFingerprint.method.applyEdits(
        returnWhenOld(
            0,
            0,
            """
                invoke-static/range {p1 .. p5}, $MOD_COMPOSE->libraryGroup($OBJ5)V
                return-void
            """,
        ),
    )

    MainMenuFingerprint.let {
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

    MoreExtensionsMenuItemFingerprint.let {
        it.method.applyEdits(surface(it.instructionMatches[0].index))
    }

    listOf(MainMenuNavigationLambdaFingerprint, MainMenuDividerLambdaFingerprint).forEach {
        val ifEq = it.instructionMatches[1].index
        it.method.applyEdits(
            insert(
                ifEq + 1,
                """
                    sget-boolean p1, $OLD_MENU->extensionsActive:Z
                    if-nez p1, :ftl_skip
                """,
                mapOf("ftl_skip" to { branchTarget(ifEq) }),
            ),
        )
    }

    MainMenuColumnLambdaFingerprint.let {
        val m = it.instructionMatches
        val lambda = it.method.definingClass
        it.method.applyEdits(
            swap(
                m[2].index + 1,
                1,
                """
                    iget-boolean v1, v0, $lambda->f${'$'}10:Z
                    if-eqz v1, :ftl_stock
                    iget-object v2, v0, $lambda->f${'$'}1:$ACCESS_POINT
                    sget-object v1, $ACCESS_POINT->Browser:$ACCESS_POINT
                    if-ne v2, v1, :ftl_stock
                    const v1, 0x6e7a3b10
                    invoke-interface {v7, v1}, $COMPOSER->startReplaceGroup(I)V
                    iget-object v2, v0, $lambda->f${'$'}23:Landroidx/compose/runtime/internal/ComposableLambdaImpl;
                    iget-object v3, v0, $lambda->f${'$'}15:Lkotlin/jvm/functions/Function0;
                    invoke-static {v2, v3}, $MOD_LAMBDA->extensionsPage(Ljava/lang/Object;Ljava/lang/Object;)$MOD_LAMBDA
                    move-result-object v1
                    const v2, 0x6e7a3b11
                    invoke-static {v2, v1, v7}, $REMEMBER_LAMBDA
                    move-result-object v1
                    const/4 v2, 0x6
                    invoke-static {v1, v7, v2}, $MENU_GROUP
                    invoke-interface {v7}, $COMPOSER->endReplaceGroup()V
                    goto/16 :ftl_end
                """,
                count = 0,
                fallThrough = false,
                labels = mapOf("ftl_end" to { implementation!!.instructions.size - 2 }),
            ),
            swap(
                m[4].index + 6,
                5,
                """
                    invoke-static {v7}, $DIVIDER
                    iget-boolean v5, v0, $lambda->f${'$'}24:Z
                    iget-object v6, v0, $lambda->f${'$'}25:Lkotlin/jvm/functions/Function0;
                    iget-object v8, v0, $lambda->f${'$'}26:Lkotlin/jvm/functions/Function0;
                    iget-object v9, v0, $lambda->f${'$'}27:Lkotlin/jvm/functions/Function0;
                    iget-object v10, v0, $lambda->f${'$'}28:Lkotlin/jvm/functions/Function0;
                    move/from16 v15, v5
                    move-object/from16 v16, v6
                    move-object/from16 v17, v8
                    move-object/from16 v18, v9
                    move-object/from16 v19, v10
                    move-object/from16 v20, v7
                    const/16 v21, 0x0
                    invoke-static/range {v15 .. v21}, $LIBRARY_GROUP
                """,
                count = 0,
            ),
            swap(m[6].index + 4, 21, "invoke-static {v7}, $DIVIDER", count = 0),
            swap(m[7].index - 6, 8, "invoke-static {v7}, $DIVIDER", count = 7),
            insert(
                m[8].index + 1,
                "invoke-static {v7}, $MOD_COMPOSE->modRow(Ljava/lang/Object;)V",
            ),
            swap(m[9].index - 2, 9, "invoke-static {v7}, $DIVIDER", count = 0),
        )
    }

    MenuGroupFingerprint.let { it.method.applyEdits(zeroTo(it.instructionMatches[0].index)) }

    BadgeFingerprint.let {
        it.method.applyEdits(
            floatTo(it.instructionMatches[0].index, 0x41400000),
            floatTo(it.instructionMatches[1].index, 0x40000000),
        )
    }

    MenuBadgeItemFingerprint.let {
        val m = it.instructionMatches
        it.method.applyEdits(
            surface(m[0].index),
            floatTo(m[1].index, 0x40000000),
            floatTo(m[2].index, 0x41400000),
        )
    }

    MenuItemFingerprint.let {
        val m = it.instructionMatches
        it.method.applyEdits(
            swap(m[0].index, 5, "const/16 v5, 0x0"),
            surface(m[1].index),
            floatTo(m[2].index, 0x42200000),
            floatTo(m[3].index, 0x42200000),
        )
    }

    MenuTextItemFingerprint.let {
        val m = it.instructionMatches
        it.method.applyEdits(
            floatTo(m[0].index, 0x42200000),
            floatTo(m[1].index, 0x42200000),
            surface(m[2].index),
        )
    }

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

    MenuNavItemFingerprint.let {
        val m = it.instructionMatches
        it.method.applyEdits(
            swap(m[0].index - 1, 41, ""),
            swap(m[1].index, 43, ""),
        )
    }

    MenuFrameFingerprint.let { it.method.applyEdits(zeroTo(it.instructionMatches[0].index)) }

    AccountMenuItemFingerprint.let {
        val m = it.instructionMatches
        it.method.applyEdits(surface(m[0].index), floatTo(m[1].index, 0x42400000))
    }

    AccountSubtitleLambdaFingerprint.let {
        val iget = it.instructionMatches[1].index
        val register = it.method.getInstruction<OneRegisterInstruction>(iget).registerA
        it.method.applyEdits(
            insert(
                iget + 1,
                """
                    invoke-static {v$register}, $OLD_MENU->accountSubtitle(Ljava/lang/String;)Ljava/lang/String;
                    move-result-object v$register
                """,
            ),
        )
    }
}

@Suppress("unused")
val oldMenuPatch = bytecodePatch(
    name = "Old style 3 dot menu",
    description = "Adds \"Mod Settings\" to the 3 dot menu: switch between the stock bottom sheet " +
        "menu and the old style popup menu, and pin extensions to the search bar.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_FIREFOX_NIGHTLY)

    extendWith("extensions/firefox.mpe")

    execute {
        installLambdaInterfaces()
        installComposeMethods()
        installWindowHooks()
        installMenuTweaks()
        installPinHooks()
    }
}
