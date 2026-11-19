/*
 * Old style 3 dot menu (popup) + Mod Settings entry.
 *
 * 162.0a1 renames Compose/Kotlin/AC/material classes (Composer, Function0, BottomSheetBehavior members...) and
 * folds lambdas into static methods. Nothing here pins an obfuscated name. How each piece is found
 * (use this to find it again in a new build):
 *
 *  onCreateDialog / onViewCreated / calculateMenuSheetWidth
 *      Fenix MenuDialogFragment and MenuFragment methods (named).
 *  onShow
 *      onCreateDialog creates an OnShowListener; its onShow calls a static method of the fragment. That static
 *      method (obfuscated name) is the real show code. The window insets listener it creates is the only class it
 *      instantiates that has an onApplyWindowInsets method.
 *  Fragment / DialogFragment
 *      superclass of the fragment that declares getResources()/getDialog().
 *  BottomSheetBehavior accessors (generated into OldMenu)
 *      class name is kept. from = static (View)BottomSheetBehavior. setHideable = (Z)V that calls setState; setState =
 *      the method containing "Cannot set state: ". draggable = first iget-boolean after View.isShown() in
 *      onInterceptTouchEvent. maxWidth = the int field written right after calculateMenuSheetWidth() in the show code.
 *  CoordinatorLayout.LayoutParams.gravity
 *      type = parameter of BottomSheetBehavior.onAttachedToLayoutParams; gravity = field assigned from the first
 *      TypedArray.getInteger in LayoutParams(Context, AttributeSet).
 *  Legacy menu routing
 *      every Settings.isMenuCustomizationEnabled() call site in the 4 Fenix classes that read it.
 *  Mod Settings row
 *      MainMenuKt method that calls QuitMenuGroup. The MenuGroup call right before it is followed by ModCompose.modRow.
 *      Compose references come from existing Fenix code: MenuItemKt.MenuItem / MenuGroupKt.MenuGroup / ExpandableMenuItemAnimation
 *      signatures (Fenix, named), rememberComposableLambda (static call (I, X, Composer) returning the MenuGroup content type)
 *      and painterResource (static call (I, I, Composer) returning the MenuItem painter type, found in MainMenuKt).
 *
 *  Sheet handle
 *      MenuDialogBottomSheetKt.MenuBottomSheetHandle (Fenix, named; replaced mozilla BottomSheetHandleKt, which no longer
 *      exists). Returns at index 0 when the old menu is on, before the composer group starts.
 *
 *  Flat rows
 *      restyleRows() below (rules documented there; every anchor is structural).
 *
 *  Dividers and flat library list
 *      installModRow(): HorizontalDivider is found in MenuScaffoldKt (mask 7 = all defaults). Dividers go after every
 *      MenuGroup / ToolsAndActionsMenuGroup / LibraryMenuGroup call in the QuitMenuGroup method. LibraryMenuGroup
 *      returns early into ModCompose.libraryGroupFlagged (MenuItem rows). Params: p2 bookmarks, p3 history,
 *      p4 downloads, p5 passwords, p6 show passwords, p7 downloads flag.
 *
 *  Extensions page
 *      Main menu column method (the one with the QuitMenuGroup call). When old menu is on, the access point is
 *      Browser and p10 (extensions expanded) is true, the column is replaced by ModCompose.extensionsPage(p23 list,
 *      p19 toggle): the list, a divider and a back row. OldMenu.setExtensionsActive(p1, p10) runs at index 0 for
 *      the wider popup. Params checked against ToolsAndActionsMenuGroup: p23 is its extension list lambda,
 *      p19 its extensions click.
 *  Library order
 *      An extra libraryGroupFlagged block runs at the join after the VPN group (library first, like the old
 *      build). The original LibraryMenuGroup call is left untouched in the column method (no edit next to the
 *      new-instance/invoke-direct that follows it, that is what caused the VerifyError "Expected initialization on
 *      uninitialized reference" in the column method). Instead LibraryMenuGroup itself, when old menu is on,
 *      only draws the divider and returns.
 *  Extensions row
 *      NumberedChevronBadge count forced to 0 (chevron only) and the MenuItem description (param 5) set to null.
 *
 *  Not part of this version: extension pinning.
 */
package app.ftl.patches.firefox

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
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

private const val OM_DIALOG_FRAGMENT = "${MENU_PACKAGE}MenuDialogFragment;"
private const val OM_MENU_FRAGMENT = "${MENU_PACKAGE}MenuFragment;"
private const val OM_BSB = "Lcom/google/android/material/bottomsheet/BottomSheetBehavior;"
private const val OM_ON_SHOW = "Landroid/content/DialogInterface\$OnShowListener;"
private const val OM_DIALOG = "Landroid/app/Dialog;"
private const val OM_BUNDLE = "Landroid/os/Bundle;"
private const val OM_VIEW = "Landroid/view/View;"
private const val OM_SETTINGS = "Lorg/mozilla/fenix/utils/Settings;"
private const val OM_OBJECT = "Ljava/lang/Object;"
private const val OM_EXPANDABLE_ANIMATION = "ExpandableMenuItemAnimation"

private fun MethodReference.params() = parameterTypes.map { it.toString() }

private fun MethodReference.smali() = "$definingClass->$name(${params().joinToString("")})$returnType"

private fun MutableMethod.params() = parameterTypes.map { it.toString() }

private fun MutableMethod.smali() = "$definingClass->$name(${params().joinToString("")})$returnType"

private fun MutableMethod.instructionList() = implementation!!.instructions.toList()

private fun MutableMethod.lastReturnObject() =
    instructionList().indexOfLast { it.opcode == Opcode.RETURN_OBJECT }

private fun MutableMethod.lastReturnVoid() =
    instructionList().indexOfLast { it.opcode == Opcode.RETURN_VOID }

private fun BytecodePatchContext.classOrNull(type: String) =
    runCatching { mutableClassDefBy(type) }.getOrNull()

private fun BytecodePatchContext.methodNamed(owner: String, name: String, parameters: List<String>? = null) =
    mutableClassDefBy(owner).methods.firstOrNull {
        it.name == name && (parameters == null || it.params() == parameters)
    } ?: throw PatchException("$owner->$name not found")

// Walks up the superclasses until a class declares a method that matches.
private fun BytecodePatchContext.declaringClass(start: String, what: String, matches: (MutableMethod) -> Boolean): String {
    var type: String? = start
    while (type != null && type != OM_OBJECT) {
        val definition = classOrNull(type) ?: break
        if (definition.methods.any(matches)) return type
        type = definition.superclass
    }
    throw PatchException("$what not found above $start")
}

private class FragmentTypes(val fragment: String, val dialogFragment: String)

private fun BytecodePatchContext.fragmentTypes(owner: String) = FragmentTypes(
    fragment = declaringClass(owner, "Fragment") {
        it.name == "getResources" && it.params().isEmpty() && it.returnType == "Landroid/content/res/Resources;"
    },
    dialogFragment = declaringClass(owner, "DialogFragment") {
        it.name == "getDialog" && it.params().isEmpty() && it.returnType == OM_DIALOG
    },
)

private class ShowParts(val show: MutableMethod, val insets: MutableMethod)

private fun BytecodePatchContext.showParts(owner: String): ShowParts {
    val create = methodNamed(owner, "onCreateDialog", listOf(OM_BUNDLE))
    val listenerType = create.instructionList().firstNotNullOfOrNull { instruction ->
        if (instruction.opcode != Opcode.NEW_INSTANCE) return@firstNotNullOfOrNull null
        val type = ((instruction as ReferenceInstruction).reference as TypeReference).type
        type.takeIf { classOrNull(it)?.interfaces?.contains(OM_ON_SHOW) == true }
    } ?: throw PatchException("OnShowListener not created in $owner.onCreateDialog")

    val onShow = methodNamed(listenerType, "onShow")
    val target = onShow.instructionList().firstNotNullOfOrNull { instruction ->
        val reference = (instruction as? ReferenceInstruction)?.reference as? MethodReference
        reference?.takeIf { instruction.opcode == Opcode.INVOKE_STATIC && it.definingClass == owner }
    } ?: throw PatchException("Show code call not found in $listenerType.onShow")
    val show = methodNamed(owner, target.name, target.params())

    val insetsType = show.instructionList().firstNotNullOfOrNull { instruction ->
        if (instruction.opcode != Opcode.NEW_INSTANCE) return@firstNotNullOfOrNull null
        val type = ((instruction as ReferenceInstruction).reference as TypeReference).type
        type.takeIf { classOrNull(it)?.methods?.any { method -> method.name == "onApplyWindowInsets" } == true }
    } ?: throw PatchException("Window insets listener not created in ${show.name}")
    return ShowParts(show, methodNamed(insetsType, "onApplyWindowInsets"))
}

private fun BytecodePatchContext.hookFragment(owner: String, widthDp: Int, viewCreated: Boolean) {
    val types = fragmentTypes(owner)

    val create = methodNamed(owner, "onCreateDialog", listOf(OM_BUNDLE))
    val at = create.lastReturnObject()
    val dialog = create.getInstruction<OneRegisterInstruction>(at).registerA
    create.addInstructions(
        at,
        "invoke-static/range {v$dialog .. v$dialog}, $OLD_MENU->onCreateDialog(Landroid/app/Dialog;)V",
    )

    if (viewCreated) {
        val method = methodNamed(owner, "onViewCreated", listOf(OM_VIEW, OM_BUNDLE))
        val classCall = method.instructionList().indexOfFirst { instruction ->
            val reference = (instruction as? ReferenceInstruction)?.reference as? MethodReference
            reference != null && reference.definingClass == OM_OBJECT && reference.name == "getClass"
        }
        if (classCall < 0) throw PatchException("getClass call not found in $owner.onViewCreated")
        val scratch = Liveness(method).takeLow(1, classCall + 1)[0]
        method.addInstructions(
            classCall + 1,
            """
                invoke-virtual {p0}, ${types.dialogFragment}->getDialog()$OM_DIALOG
                move-result-object v$scratch
                invoke-static {v$scratch}, $OLD_MENU->onViewCreated(Landroid/app/Dialog;)V
            """,
        )
    }

    methodNamed(owner, "calculateMenuSheetWidth", listOf()).applyEdits(
        returnWhenOld(
            0,
            0,
            """
                invoke-virtual {p0}, ${types.fragment}->getResources()Landroid/content/res/Resources;
                move-result-object v0
                const/16 v1, $widthDp
                invoke-static {v0, v1}, $OLD_MENU->menuWidth(Landroid/content/res/Resources;I)I
                move-result v0
                return v0
            """,
        ),
    )

    val parts = showParts(owner)
    parts.show.addInstructions(parts.show.lastReturnVoid(), "invoke-static {}, $OLD_MENU->afterShow()V")
    parts.show.addInstructions(0, "invoke-static {p1}, $OLD_MENU->onShowStart(Landroid/content/DialogInterface;)V")
    parts.insets.applyEdits(
        returnWhenOld(
            0,
            0,
            """
                const/4 v0, 0x0
                invoke-virtual {p1, v0, v0, v0, v0}, $OM_VIEW->setPadding(IIII)V
                return-object p2
            """,
        ),
    )
}

private fun BytecodePatchContext.installWindowHooks() {
    hookFragment(OM_DIALOG_FRAGMENT, 240, viewCreated = true)
    hookFragment(OM_MENU_FRAGMENT, 300, viewCreated = false)
}

private fun BytecodePatchContext.hideSheetHandle() {
    val handle = mutableClassDefBy("${COMPOSE_PACKAGE}MenuDialogBottomSheetKt;").methods.firstOrNull {
        it.name == "MenuBottomSheetHandle" && it.returnType == "V"
    } ?: throw PatchException("MenuDialogBottomSheetKt.MenuBottomSheetHandle not found")
    handle.applyEdits(returnWhenOld(0, 0, "return-void"))
}

private val LEGACY_ROUTING_CLASSES = listOf(
    "Lorg/mozilla/fenix/components/toolbar/BrowserToolbarMiddleware;",
    "Lorg/mozilla/fenix/components/toolbar/CustomTabBrowserToolbarMiddleware;",
    "Lorg/mozilla/fenix/HomeActivity;",
    "Lorg/mozilla/fenix/home/toolbar/BrowserToolbarMiddleware;",
)

private fun BytecodePatchContext.routeToLegacyMenu() {
    for (owner in LEGACY_ROUTING_CLASSES) {
        var hooked = 0
        for (method in mutableClassDefBy(owner).methods) {
            val instructions = method.implementation?.instructions?.toList() ?: continue
            for (i in instructions.indices.reversed()) {
                val reference = (instructions[i] as? ReferenceInstruction)?.reference as? MethodReference ?: continue
                if (reference.definingClass != OM_SETTINGS || reference.name != "isMenuCustomizationEnabled" ||
                    reference.returnType != "Z"
                ) continue
                if (instructions.getOrNull(i + 1)?.opcode != Opcode.MOVE_RESULT) continue
                method.applyEdits(filterFieldRead(i + 1))
                hooked++
            }
        }
        if (hooked == 0) throw PatchException("isMenuCustomizationEnabled() read not found in $owner")
    }
}

private fun BytecodePatchContext.installSheetAccessors() {
    val behavior = mutableClassDefBy(OM_BSB)

    val from = behavior.methods.firstOrNull {
        it.name != "<init>" && com.android.tools.smali.dexlib2.AccessFlags.STATIC.isSet(it.accessFlags) &&
            it.params() == listOf(OM_VIEW) && it.returnType == OM_BSB
    } ?: throw PatchException("BottomSheetBehavior.from(View) not found")

    val setState = behavior.methods.firstOrNull { method ->
        method.params() == listOf("I") && method.returnType == "V" && method.implementation?.instructions?.any {
            ((it as? ReferenceInstruction)?.reference as? com.android.tools.smali.dexlib2.iface.reference.StringReference)
                ?.string == "Cannot set state: "
        } == true
    } ?: throw PatchException("BottomSheetBehavior.setState(I) not found")

    val hideable = behavior.methods.firstOrNull { method ->
        method.params() == listOf("Z") && method.returnType == "V" && method.implementation?.instructions?.any {
            val reference = (it as? ReferenceInstruction)?.reference as? MethodReference
            reference != null && reference.name == setState.name && reference.definingClass == OM_BSB
        } == true
    } ?: throw PatchException("BottomSheetBehavior.setHideable(Z) not found")

    val intercept = behavior.methods.firstOrNull { it.name == "onInterceptTouchEvent" }
        ?: throw PatchException("BottomSheetBehavior.onInterceptTouchEvent not found")
    val interceptInstructions = intercept.instructionList()
    val shown = interceptInstructions.indexOfFirst {
        ((it as? ReferenceInstruction)?.reference as? MethodReference)?.name == "isShown"
    }
    val draggableInstruction = interceptInstructions.drop(shown + 1).firstOrNull { it.opcode == Opcode.IGET_BOOLEAN }
    if (shown < 0 || draggableInstruction == null) throw PatchException("BottomSheetBehavior.draggable not found")
    val draggable = (draggableInstruction as ReferenceInstruction).reference as FieldReference

    val width = methodNamed(OM_DIALOG_FRAGMENT, "calculateMenuSheetWidth", listOf())
    val show = showParts(OM_DIALOG_FRAGMENT).show
    val showInstructions = show.instructionList()
    val widthCall = showInstructions.indexOfFirst {
        ((it as? ReferenceInstruction)?.reference as? MethodReference)?.name == width.name
    }
    val maxWidth = showInstructions.drop(widthCall + 1).firstOrNull {
        it.opcode == Opcode.IPUT && ((it as ReferenceInstruction).reference as FieldReference).definingClass == OM_BSB
    }
    if (widthCall < 0 || maxWidth == null) throw PatchException("BottomSheetBehavior.maxWidth not found")
    val maxWidthField = (maxWidth as ReferenceInstruction).reference as FieldReference

    val layoutParams = behavior.methods.firstOrNull { it.name == "onAttachedToLayoutParams" }
        ?.params()?.singleOrNull() ?: throw PatchException("CoordinatorLayout.LayoutParams type not found")
    val lpConstructor = mutableClassDefBy(layoutParams).methods.firstOrNull {
        it.name == "<init>" && it.params() == listOf("Landroid/content/Context;", "Landroid/util/AttributeSet;")
    } ?: throw PatchException("LayoutParams(Context, AttributeSet) not found")
    val lpInstructions = lpConstructor.instructionList()
    val integer = lpInstructions.indexOfFirst {
        ((it as? ReferenceInstruction)?.reference as? MethodReference)?.name == "getInteger"
    }
    val gravityPut = lpInstructions.drop(integer + 1).firstOrNull {
        it.opcode == Opcode.IPUT && ((it as ReferenceInstruction).reference as FieldReference).definingClass == layoutParams
    }
    if (integer < 0 || gravityPut == null) throw PatchException("LayoutParams.gravity not found")
    val gravity = (gravityPut as ReferenceInstruction).reference as FieldReference

    addStaticMethod(OLD_MENU, "ftlSheetBehavior", listOf(OM_VIEW), OM_OBJECT, 2, """
        invoke-static {p0}, ${from.smali()}
        move-result-object v0
        return-object v0
    """)
    addStaticMethod(OLD_MENU, "ftlSheetHideable", listOf(OM_OBJECT, "Z"), "V", 3, """
        check-cast p0, $OM_BSB
        invoke-virtual {p0, p1}, ${hideable.smali()}
        return-void
    """)
    addStaticMethod(OLD_MENU, "ftlSheetDraggable", listOf(OM_OBJECT, "Z"), "V", 3, """
        check-cast p0, $OM_BSB
        iput-boolean p1, p0, ${fieldString(draggable)}
        return-void
    """)
    addStaticMethod(OLD_MENU, "ftlSheetMaxWidth", listOf(OM_OBJECT), "I", 2, """
        check-cast p0, $OM_BSB
        iget v0, p0, ${fieldString(maxWidthField)}
        return v0
    """)
    addStaticMethod(OLD_MENU, "ftlSheetSetMaxWidth", listOf(OM_OBJECT, "I"), "V", 3, """
        check-cast p0, $OM_BSB
        iput p1, p0, ${fieldString(maxWidthField)}
        return-void
    """)
    addStaticMethod(OLD_MENU, "ftlGravity", listOf(OM_OBJECT), "I", 2, """
        instance-of v0, p0, $layoutParams
        if-eqz v0, :ftl_none
        check-cast p0, $layoutParams
        iget v0, p0, ${fieldString(gravity)}
        return v0
        :ftl_none
        const/4 v0, -0x1
        return v0
    """)
    addStaticMethod(OLD_MENU, "ftlSetGravity", listOf(OM_OBJECT, "I"), "V", 3, """
        instance-of v0, p0, $layoutParams
        if-eqz v0, :ftl_done
        check-cast p0, $layoutParams
        iput p1, p0, ${fieldString(gravity)}
        :ftl_done
        return-void
    """)
}

private fun BytecodePatchContext.installModRow() {
    val types = resolveComposeTypes()
    val composer = types.composer

    val menuItem = mutableClassDefBy(MENU_ITEM_KT).methods.firstOrNull { it.name == "MenuItem" }
        ?: throw PatchException("MenuItemKt.MenuItem not found")
    val itemParams = menuItem.params()
    if (itemParams.size != 21 || itemParams[10] != types.function0 || itemParams[17] != composer) {
        throw PatchException("Unexpected MenuItem signature: ${menuItem.smali()}")
    }
    val painterType = itemParams[1]
    val contentType = itemParams[16]

    val menuGroup = mutableClassDefBy(MENU_GROUP_KT).methods.firstOrNull { it.name == "MenuGroup" }
        ?: throw PatchException("MenuGroupKt.MenuGroup not found")
    if (menuGroup.params() != listOf("I", contentType, composer)) {
        throw PatchException("Unexpected MenuGroup signature: ${menuGroup.smali()}")
    }

    val function2 = mutableClassDefBy(MENU_ITEM_KT).methods
        .firstOrNull { it.name == OM_EXPANDABLE_ANIMATION }?.params()?.getOrNull(1)
        ?: throw PatchException("Function2 type not found")

    val mainMenu = mutableClassDefBy(MAIN_MENU_KT)
    var remember: MethodReference? = null
    var painter: MethodReference? = null
    var string: MethodReference? = null
    var quitMethod: MutableMethod? = null
    var quitIndex = -1
    var groupAt = -1
    for (method in mainMenu.methods) {
        val instructions = method.implementation?.instructions?.toList() ?: continue
        instructions.forEachIndexed { _, instruction ->
            val reference = (instruction as? ReferenceInstruction)?.reference as? MethodReference
                ?: return@forEachIndexed
            if (instruction.opcode != Opcode.INVOKE_STATIC) return@forEachIndexed
            val parameters = reference.params()
            if (remember == null && parameters.size == 3 && parameters[0] == "I" && parameters[2] == composer &&
                reference.returnType == contentType
            ) remember = reference
            if (painter == null && parameters == listOf("I", "I", composer) && reference.returnType == painterType) {
                painter = reference
            }
            if (string == null && parameters == listOf("I", composer) && reference.returnType == "Ljava/lang/String;") {
                string = reference
            }
        }
        val quit = instructions.indexOfFirst {
            val reference = (it as? ReferenceInstruction)?.reference as? MethodReference
            it.opcode == Opcode.INVOKE_STATIC && reference != null &&
                reference.definingClass == MAIN_MENU_KT && reference.name == "QuitMenuGroup"
        }
        if (quit < 0) continue
        // The recomposition wrapper also calls QuitMenuGroup. The real call site has a MenuGroup call before it.
        val group = (quit - 1 downTo 0).firstOrNull { i ->
            val reference = (instructions[i] as? ReferenceInstruction)?.reference as? MethodReference
            instructions[i].opcode == Opcode.INVOKE_STATIC && reference != null &&
                reference.definingClass == MENU_GROUP_KT && reference.name == "MenuGroup"
        } ?: continue
        if (quitMethod != null) throw PatchException("More than one QuitMenuGroup call site found")
        quitMethod = method
        quitIndex = quit
        groupAt = group
    }
    val rememberCall = remember ?: throw PatchException("rememberComposableLambda not found")
    val painterCall = painter ?: throw PatchException("painterResource not found")
    val stringCall = string ?: throw PatchException("stringResource not found")
    val target = quitMethod ?: throw PatchException("QuitMenuGroup call site not found")

    val instructions = target.instructionList()
    val groupIndex = groupAt
    val groupCall = instructions[groupIndex] as? FiveRegisterInstruction
        ?: throw PatchException("MenuGroup call is not a 5 register invoke")
    val composerRegister = groupCall.registerE

    val itemSmali = menuItem.smali()
    val rememberSmali = rememberCall.smali()
    val groupSmali = menuGroup.smali()
    val painterSmali = painterCall.smali()
    val stringSmali = stringCall.smali()

    val modifierType = itemParams[2]
    val divider = mutableClassDefBy(MENU_SCAFFOLD_KT).methods.firstNotNullOfOrNull { method ->
        method.implementation?.instructions?.firstNotNullOfOrNull { instruction ->
            val reference = (instruction as? ReferenceInstruction)?.reference as? MethodReference
            if ((instruction.opcode == Opcode.INVOKE_STATIC || instruction.opcode == Opcode.INVOKE_STATIC_RANGE) &&
                reference != null && reference.returnType == "V" &&
                reference.params() == listOf(modifierType, "F", "J", composer, "I", "I")
            ) reference else null
        }
    } ?: throw PatchException("HorizontalDivider call not found in MenuScaffoldKt")
    val dividerSmali = divider.smali()

    val libraryMenuGroup = methodsOf(MAIN_MENU_KT, "LibraryMenuGroup").single()
    val libraryParams = libraryMenuGroup.params()
    val clickType = types.function0
    if (libraryParams != listOf("I", composer, clickType, clickType, clickType, clickType, "Z", "Z")) {
        throw PatchException("Unexpected LibraryMenuGroup signature: ${libraryMenuGroup.smali()}")
    }
    val libraryFields = HashMap<String, FieldReference>()
    libraryMenuGroup.instructionList().forEach { instruction ->
        val reference = (instruction as? ReferenceInstruction)?.reference as? FieldReference
        if (instruction.opcode == Opcode.SGET && reference != null) libraryFields[reference.name] = reference
    }
    fun libraryField(name: String) = libraryFields[name]?.let { fieldString(it) }
        ?: throw PatchException("LibraryMenuGroup resource $name not found")
    val historyText = libraryField("library_history")
    val bookmarksText = libraryField("library_bookmarks")
    val downloadsText = libraryField("library_downloads")
    val passwordsText = libraryField("browser_menu_passwords")
    val historyIcon = libraryField("mozac_ic_history_24")
    val bookmarksIcon = libraryField("mozac_ic_bookmark_tray_fill_24")
    val downloadsIcon = libraryField("mozac_ic_download_24")
    val passwordsIcon = libraryField("mozac_ic_login_24")

    addStaticMethod(MOD_COMPOSE, "modRow", listOf(OM_OBJECT), "V", 3, """
        check-cast p0, $composer
        invoke-static {}, $MOD_LAMBDA->modRow()$MOD_LAMBDA
        move-result-object v0
        const v1, 0x7a11b0a1
        invoke-static {v1, v0, p0}, $rememberSmali
        move-result-object v0
        const/4 v1, 0x6
        invoke-static {v1, v0, p0}, $groupSmali
        return-void
    """)
    addStaticMethod(MOD_COMPOSE, "modRowContent", listOf(OM_OBJECT), "V", 27, """
        move-object/from16 v17, p0
        check-cast v17, $composer
        invoke-static {}, $OLD_MENU->modIcon()I
        move-result v1
        const/4 v5, 0x0
        move-object/from16 v4, v17
        invoke-static {v1, v5, v4}, $painterSmali
        move-result-object v22
        invoke-static {}, $MOD_CLICK->open()$MOD_CLICK
        move-result-object v10
        check-cast v10, ${types.function0}
        const-string v21, "Mod Settings"
        move-object/from16 v0, v21
        move-object/from16 v1, v22
        const/4 v2, 0x0
        const/4 v3, 0x0
        const/4 v4, 0x0
        const/4 v5, 0x0
        const/4 v6, 0x0
        const/4 v7, 0x0
        const/4 v8, 0x0
        const/4 v9, 0x0
        const/4 v11, 0x0
        const/4 v12, 0x0
        const/4 v13, 0x0
        const/4 v14, 0x0
        const/4 v15, 0x0
        const/16 v16, 0x0
        const/16 v18, 0x40
        const/16 v19, 0x0
        const v20, 0x3f7fc
        invoke-static/range {v0 .. v20}, $itemSmali
        return-void
    """)

    addStaticMethod(MOD_COMPOSE, "divider", listOf(OM_OBJECT), "V", 8, """
        check-cast p0, $composer
        const/4 v0, 0x0
        const/4 v1, 0x0
        const-wide/16 v2, 0x0
        const/4 v5, 0x0
        const/4 v6, 0x7
        move-object v4, p0
        invoke-static/range {v0 .. v6}, $dividerSmali
        return-void
    """)
    addStaticMethod(MOD_COMPOSE, "libraryItem", listOf(OM_OBJECT, "I", "I", OM_OBJECT), "V", 27, """
        move-object/from16 v17, p0
        check-cast v17, $composer
        move-object/from16 v0, v17
        move/from16 v1, p1
        move/from16 v2, p2
        move-object/from16 v10, p3
        check-cast v10, ${types.function0}
        invoke-static {v1, v0}, $stringSmali
        move-result-object v21
        const/4 v5, 0x0
        invoke-static {v2, v5, v0}, $painterSmali
        move-result-object v22
        move-object/from16 v0, v21
        move-object/from16 v1, v22
        const/4 v2, 0x0
        const/4 v3, 0x0
        const/4 v4, 0x0
        const/4 v5, 0x0
        const/4 v6, 0x0
        const/4 v7, 0x0
        const/4 v8, 0x0
        const/4 v9, 0x0
        const/4 v11, 0x0
        const/4 v12, 0x0
        const/4 v13, 0x0
        const/4 v14, 0x0
        const/4 v15, 0x0
        const/16 v16, 0x0
        const/16 v18, 0x40
        const/16 v19, 0x0
        const v20, 0x3f7fc
        invoke-static/range {v0 .. v20}, $itemSmali
        return-void
    """)
    addStaticMethod(MOD_COMPOSE, "libraryList", listOf(OM_OBJECT, OM_OBJECT, OM_OBJECT, OM_OBJECT, OM_OBJECT), "V", 12, """
        move-object v0, p4
        move-object v1, p0
        move-object v2, p1
        move-object v3, p2
        move-object v4, p3
        sget v5, $historyText
        sget v6, $historyIcon
        invoke-static {v0, v5, v6, v1}, $MOD_COMPOSE->libraryItem(Ljava/lang/Object;IILjava/lang/Object;)V
        sget v5, $bookmarksText
        sget v6, $bookmarksIcon
        invoke-static {v0, v5, v6, v2}, $MOD_COMPOSE->libraryItem(Ljava/lang/Object;IILjava/lang/Object;)V
        sget v5, $downloadsText
        sget v6, $downloadsIcon
        invoke-static {v0, v5, v6, v3}, $MOD_COMPOSE->libraryItem(Ljava/lang/Object;IILjava/lang/Object;)V
        if-eqz v4, :ftl_no_passwords
        sget v5, $passwordsText
        sget v6, $passwordsIcon
        invoke-static {v0, v5, v6, v4}, $MOD_COMPOSE->libraryItem(Ljava/lang/Object;IILjava/lang/Object;)V
        :ftl_no_passwords
        return-void
    """)
    addStaticMethod(MOD_COMPOSE, "libraryGroup", listOf(OM_OBJECT, OM_OBJECT, OM_OBJECT, OM_OBJECT, OM_OBJECT), "V", 9, """
        check-cast p4, $composer
        invoke-static {p1, p0, p2, p3}, $MOD_LAMBDA->libraryList(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)$MOD_LAMBDA
        move-result-object v0
        const v1, 0x41d486a7
        invoke-static {v1, v0, p4}, $rememberSmali
        move-result-object v0
        const/4 v1, 0x6
        invoke-static {v1, v0, p4}, $groupSmali
        return-void
    """)
    val expandableSmali = mutableClassDefBy(MENU_ITEM_KT).methods.first { it.name == OM_EXPANDABLE_ANIMATION }.smali()
    fun fieldIn(owner: String, name: String): String {
        for (method in mutableClassDefBy(owner).methods) {
            for (instruction in method.implementation?.instructions ?: continue) {
                val reference = (instruction as? ReferenceInstruction)?.reference as? FieldReference
                if (instruction.opcode == Opcode.SGET && reference != null && reference.name == name) {
                    return fieldString(reference)
                }
            }
        }
        throw PatchException("$owner resource $name not found")
    }
    val extensionsText = fieldIn(EXTENSIONS_MENU_ITEM_KT, "browser_menu_extensions")
    val backIcon = fieldIn(MENU_NAVIGATION_KT, "mozac_ic_back_24")
    addStaticMethod(MOD_COMPOSE, "extensionsPage", listOf(OM_OBJECT, OM_OBJECT, OM_OBJECT), "V", 27, """
        move-object/from16 v17, p2
        check-cast v17, $composer
        move-object/from16 v4, v17
        move-object/from16 v1, p0
        check-cast v1, $function2
        const/4 v2, 0x1
        const/4 v3, 0x6
        invoke-static {v2, v1, v4, v3}, $expandableSmali
        invoke-static {v4}, $MOD_COMPOSE->divider(Ljava/lang/Object;)V
        sget v1, $extensionsText
        invoke-static {v1, v4}, $stringSmali
        move-result-object v21
        sget v1, $backIcon
        const/4 v5, 0x0
        invoke-static {v1, v5, v4}, $painterSmali
        move-result-object v22
        move-object/from16 v10, p1
        check-cast v10, ${types.function0}
        move-object/from16 v0, v21
        move-object/from16 v1, v22
        const/4 v2, 0x0
        const/4 v3, 0x0
        const/4 v4, 0x0
        const/4 v5, 0x0
        const/4 v6, 0x0
        const/4 v7, 0x0
        const/4 v8, 0x0
        const/4 v9, 0x0
        const/4 v11, 0x0
        const/4 v12, 0x0
        const/4 v13, 0x0
        const/4 v14, 0x0
        const/4 v15, 0x0
        const/16 v16, 0x0
        const/16 v18, 0x40
        const/16 v19, 0x0
        const v20, 0x3f7fc
        invoke-static/range {v0 .. v20}, $itemSmali
        return-void
    """)
    addStaticMethod(MOD_COMPOSE, "libraryGroupFlagged", listOf("Z", "Z", OM_OBJECT, OM_OBJECT, OM_OBJECT, OM_OBJECT, OM_OBJECT), "V", 7, """
        if-nez p0, :ftl_passwords
        const/4 p5, 0x0
        :ftl_passwords
        invoke-static {p2, p3, p4, p5, p6}, $MOD_COMPOSE->libraryGroup(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V
        return-void
    """)

    val liveness = Liveness(target)
    val edits = ArrayList<Edit>()
    val targetParams = target.params()
    val accessPointType = "${MENU_PACKAGE}MenuAccessPoint;"
    val toolsGroup = methodsOf(MAIN_MENU_KT, "ToolsAndActionsMenuGroup").single()
    val extensionContentType = toolsGroup.params().getOrNull(2)
    if (targetParams.getOrNull(1) != accessPointType || targetParams.getOrNull(10) != "Z" ||
        targetParams.getOrNull(19) != types.function0 || targetParams.getOrNull(23) != extensionContentType ||
        targetParams.getOrNull(43) != composer || targetParams.getOrNull(25) != "Z" || targetParams.getOrNull(26) != "Z" ||
        (27..30).any { targetParams.getOrNull(it) != types.function0 }
    ) throw PatchException("Unexpected main menu column signature: ${target.smali()}")

    fun dividerEdit(index: Int, register: Int): Edit {
        val scratch = liveness.takeAny(1, index)[0]
        return insert(
            index,
        """
            invoke-static {}, $MOD_SETTINGS->oldMenu()Z
            move-result v${scratch}
            if-eqz v${scratch}, :ftl_no_divider
            invoke-static/range {v$register .. v$register}, $MOD_COMPOSE->divider(Ljava/lang/Object;)V
        """,
        mapOf("ftl_no_divider" to { index }),
        )
    }

    val composerCall = instructions.indexOfFirst {
        val reference = it.methodRef()
        it.opcode == Opcode.INVOKE_INTERFACE && reference != null && reference.definingClass == composer &&
            reference.name == "q" && reference.params() == listOf("I", "Z")
    }
    if (composerCall < 0) throw PatchException("Composer.startRestartGroup check not found")
    val composerReg = (instructions[composerCall] as FiveRegisterInstruction).registerC
    val startGroup = instructions.firstNotNullOfOrNull {
        val reference = it.methodRef()
        if (it.opcode == Opcode.INVOKE_INTERFACE && reference != null && reference.definingClass == composer &&
            reference.params() == listOf("I") && reference.returnType == "V"
        ) reference else null
    }?.smali() ?: throw PatchException("Composer.startReplaceGroup not found")
    val endGroup = instructions.firstNotNullOfOrNull {
        val reference = it.methodRef()
        if (it.opcode == Opcode.INVOKE_INTERFACE && reference != null && reference.definingClass == composer &&
            reference.params().isEmpty() && reference.returnType == "V"
        ) reference else null
    }?.smali() ?: throw PatchException("Composer.endReplaceGroup not found")

    val widthScratch = liveness.takeLow(2, 0)
    edits += insert(
        0,
        """
            move-object/from16 v${widthScratch[0]}, p1
            move/from16 v${widthScratch[1]}, p10
            invoke-static {v${widthScratch[0]}, v${widthScratch[1]}}, $OLD_MENU->setExtensionsActive(Ljava/lang/Object;Z)V
        """,
    )

    val pageIndex = (composerCall + 1 until instructions.size).first { instructions[it].opcode == Opcode.IF_EQZ } + 1
    val page = liveness.takeLow(4, pageIndex)
    val pageEndInstruction = instructions[instructions.indexOfLast { it.opcode == Opcode.RETURN_OBJECT } - 1]
    edits += insert(
        pageIndex,
        """
            invoke-static {}, $MOD_SETTINGS->oldMenu()Z
            move-result v${page[0]}
            if-eqz v${page[0]}, :ftl_page_stock
            move/from16 v${page[0]}, p10
            if-eqz v${page[0]}, :ftl_page_stock
            move-object/from16 v${page[1]}, p1
            sget-object v${page[2]}, ${accessPointType}->Browser:$accessPointType
            if-ne v${page[1]}, v${page[2]}, :ftl_page_stock
            move-object/from16 v${page[3]}, p43
            const v${page[0]}, 0x7a1c3e55
            invoke-interface {v${page[3]}, v${page[0]}}, $startGroup
            move-object/from16 v${page[1]}, p23
            move-object/from16 v${page[2]}, p19
            invoke-static {v${page[1]}, v${page[2]}}, $MOD_LAMBDA->extensionsPage(Ljava/lang/Object;Ljava/lang/Object;)$MOD_LAMBDA
            move-result-object v${page[1]}
            const v${page[0]}, 0x41d486a8
            invoke-static {v${page[0]}, v${page[1]}, v${page[3]}}, $rememberSmali
            move-result-object v${page[1]}
            const/4 v${page[0]}, 0x6
            invoke-static {v${page[0]}, v${page[1]}, v${page[3]}}, $groupSmali
            invoke-interface {v${page[3]}}, $endGroup
            goto/16 :ftl_page_done
        """,
        mapOf(
            "ftl_page_stock" to { pageIndex },
            "ftl_page_done" to { target.implementation!!.instructions.indexOfFirst { it === pageEndInstruction } },
        ),
    )

    val ipState = "${MENU_PACKAGE}store/IPProtectionMenuState;"
    val ipIndex = instructions.indexOfFirst { it.methodRef()?.params()?.contains(ipState) == true && it.opcode == Opcode.INVOKE_DIRECT }
    if (ipIndex < 0) throw PatchException("IPProtectionMenuState group constructor not found")
    val vpnGroup = (ipIndex until instructions.size).first { i ->
        val reference = instructions[i].methodRef()
        instructions[i].opcode == Opcode.INVOKE_STATIC && reference != null &&
            reference.definingClass == MENU_GROUP_KT && reference.name == "MenuGroup"
    }
    val ends = (vpnGroup + 1 until instructions.size).filter { i ->
        val reference = instructions[i].methodRef()
        instructions[i].opcode == Opcode.INVOKE_INTERFACE && reference != null && reference.definingClass == composer &&
            reference.params().isEmpty() && reference.returnType == "V"
    }
    val join = (ends.getOrNull(1) ?: throw PatchException("VPN group end not found")) + 1
    val run = liveness.takeRun(7, join)
    edits += insert(
        join,
        """
            invoke-static {}, $MOD_SETTINGS->oldMenu()Z
            move-result v$run
            if-eqz v$run, :ftl_no_library
            move-object/from16 v${run + 6}, v$composerReg
            invoke-static/range {v${run + 6} .. v${run + 6}}, $MOD_COMPOSE->divider(Ljava/lang/Object;)V
            move/from16 v$run, p25
            move/from16 v${run + 1}, p26
            move-object/from16 v${run + 2}, p27
            move-object/from16 v${run + 3}, p28
            move-object/from16 v${run + 4}, p29
            move-object/from16 v${run + 5}, p30
            invoke-static/range {v$run .. v${run + 6}}, $MOD_COMPOSE->libraryGroupFlagged(ZZLjava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V
        """,
        mapOf("ftl_no_library" to { join }),
    )

    val dividerScratch = liveness.takeLow(1, groupIndex + 1)[0]
    edits += insert(
        groupIndex + 1,
        """
            invoke-static {}, $MOD_SETTINGS->oldMenu()Z
            move-result v$dividerScratch
            if-eqz v$dividerScratch, :ftl_no_divider
            invoke-static/range {v$composerRegister .. v$composerRegister}, $MOD_COMPOSE->divider(Ljava/lang/Object;)V
            :ftl_no_divider
            invoke-static {v$composerRegister}, $MOD_COMPOSE->modRow(Ljava/lang/Object;)V
        """,
    )

    instructions.forEachIndexed { i, instruction ->
        if (instruction.opcode != Opcode.INVOKE_STATIC && instruction.opcode != Opcode.INVOKE_STATIC_RANGE) {
            return@forEachIndexed
        }
        val reference = instruction.methodRef() ?: return@forEachIndexed
        if (reference.definingClass != MAIN_MENU_KT) return@forEachIndexed
        when (reference.name) {
            "ToolsAndActionsMenuGroup" -> {
                val register = (instruction as RegisterRangeInstruction).startRegister + reference.params().indexOf(composer)
                edits += dividerEdit(i, register)
            }
            "QuitMenuGroup" -> edits += dividerEdit(i, composerReg)
        }
    }
    target.applyEdits(*edits.toTypedArray())

    val libraryScratch = Liveness(libraryMenuGroup).takeLow(1, 0)[0]
    libraryMenuGroup.applyEdits(
        insert(
            0,
            """
                invoke-static {}, $MOD_SETTINGS->oldMenu()Z
                move-result v$libraryScratch
                if-eqz v$libraryScratch, :ftl_library_stock
                invoke-static/range {p1 .. p1}, $MOD_COMPOSE->divider(Ljava/lang/Object;)V
                return-void
            """,
            mapOf("ftl_library_stock" to { 0 }),
        ),
    )

    val interfaces = mapOf(MOD_LAMBDA to function2, MOD_CLICK to types.function0)
    interfaces.forEach { (type, function) ->
        val list = mutableClassDefBy(type).interfaces
        if (function !in list) list.add(function)
    }
}

private object ColorSchemeStringFingerprint : Fingerprint(
    name = "toString",
    returnType = "Ljava/lang/String;",
    parameters = listOf(),
    filters = listOf(string("ColorScheme(primary="), string("surface="), string("surfaceBright="), string("surfaceContainerHigh=")),
)

private fun BytecodePatchContext.colorField(match: Int): FieldReference {
    val method = ColorSchemeStringFingerprint.method
    val instructions = method.implementation!!.instructions.toList()
    val start = ColorSchemeStringFingerprint.instructionMatches[match].index
    val found = (start until instructions.size).firstOrNull { instructions[it].opcode == Opcode.IGET_WIDE }
        ?: throw PatchException("ColorScheme field after toString literal not found")
    return (instructions[found] as ReferenceInstruction).reference as FieldReference
}

private fun Instruction.highConst(bits: Int) =
    opcode == Opcode.CONST_HIGH16 && (this as NarrowLiteralInstruction).narrowLiteral == bits

private fun Instruction.methodRef() = (this as? ReferenceInstruction)?.reference as? MethodReference

private fun Liveness.takeAny(count: Int, index: Int): List<Int> {
    val picked = free(index).filter { it < 256 }.take(count)
    if (picked.size < count) throw PatchException("not enough free registers at $index")
    return picked
}

private fun surfaceTo(index: Int, field: FieldReference) = Edit(index) {
    val instruction = getInstruction<TwoRegisterInstruction>(index)
    val a = instruction.registerA
    val b = instruction.registerB
    swapAt(index, if (a == b) a + 1 else a, "iget-wide v$a, v$b, ${fieldString(field)}")
}

private fun skipWhenOld(index: Int, scratch: Int) = Edit(index) { swapAt(index, scratch, "", count = 1) }

private fun navPaddingTo(index: Int) = Edit(index) {
    val register = getInstruction<OneRegisterInstruction>(index).registerA
    swapAt(index, register, "invoke-static {}, $OLD_MENU->navPadding()F\nmove-result v$register")
}

private fun BytecodePatchContext.methodsOf(owner: String, name: String) =
    mutableClassDefBy(owner).methods.filter { it.name == name }.ifEmpty { throw PatchException("$owner->$name not found") }

private fun BytecodePatchContext.recolor(owner: String, from: FieldReference, to: FieldReference): Int {
    var count = 0
    for (method in mutableClassDefBy(owner).methods) {
        val list = method.instructionList()
        val edits = list.mapIndexedNotNull { i, instruction ->
            val reference = (instruction as? ReferenceInstruction)?.reference as? FieldReference
            if (instruction.opcode == Opcode.IGET_WIDE && reference != null &&
                reference.definingClass == from.definingClass && reference.name == from.name
            ) surfaceTo(i, to) else null
        }
        if (edits.isNotEmpty()) {
            method.applyEdits(*edits.toTypedArray())
            count += edits.size
        }
    }
    return count
}

private fun BytecodePatchContext.hideAccountSubtitle() {
    for (method in mutableClassDefBy(ACCOUNT_KT).methods) {
        val list = method.instructionList()
        val calls = list.indices.filter { j ->
            val reference = list[j].methodRef()
            list[j].opcode == Opcode.INVOKE_STATIC_RANGE && reference != null &&
                reference.params().firstOrNull() == "Ljava/lang/String;" && reference.params().size >= 20
        }
        if (calls.size < 2) continue
        val at = calls.last()
        val start = (list[at] as RegisterRangeInstruction).startRegister
        method.applyEdits(
            insert(
                at,
                """
                    invoke-static/range {v$start .. v$start}, $OLD_MENU->accountSubtitle(Ljava/lang/String;)Ljava/lang/String;
                    move-result-object v$start
                    if-eqz v$start, :ftl_no_subtitle
                """,
                mapOf("ftl_no_subtitle" to { at + 1 }),
            ),
        )
        return
    }
    throw PatchException("Account subtitle Text call not found")
}

private fun BytecodePatchContext.hideExtensionExtras() {
    val badge = methodsOf(EXTENSIONS_MENU_ITEM_KT, "NumberedChevronBadge").single()
    if (badge.params().firstOrNull() != "I") throw PatchException("Unexpected NumberedChevronBadge signature")
    val badgeScratch = Liveness(badge).takeLow(1, 0)[0]
    badge.applyEdits(
        insert(
            0,
            """
                invoke-static {}, $MOD_SETTINGS->oldMenu()Z
                move-result v$badgeScratch
                if-eqz v$badgeScratch, :ftl_count
                const/16 p0, 0x0
            """,
            mapOf("ftl_count" to { 0 }),
        ),
    )

    for (method in mutableClassDefBy(EXTENSIONS_MENU_ITEM_KT).methods) {
        val list = method.instructionList()
        val at = list.indices.firstOrNull { i ->
            val reference = list[i].methodRef()
            list[i].opcode == Opcode.INVOKE_STATIC_RANGE && reference != null &&
                reference.definingClass == MENU_ITEM_KT && reference.name == "MenuItem"
        } ?: continue
        val description = (list[at] as RegisterRangeInstruction).startRegister + 5
        val scratch = Liveness(method).takeLow(1, at)[0]
        method.applyEdits(
            insert(
                at,
                """
                    invoke-static {}, $MOD_SETTINGS->oldMenu()Z
                    move-result v$scratch
                    if-eqz v$scratch, :ftl_description
                    const/16 v$description, 0x0
                """,
                mapOf("ftl_description" to { at }),
            ),
        )
        return
    }
    throw PatchException("ExtensionsMenuItem MenuItem call not found")
}

/*
 * Flat rows. Every rule is found by structure (float literal pairs, ColorScheme toString labels, named Fenix methods).
 * Rules (old 161 build -> same rule on the obfuscated build):
 *  surfaceBright -> surface        iget-wide of ColorScheme.surfaceBright in MenuItemKt and MainMenuKt
 *  row padding 16/8dp -> 12/2dp    two adjacent const/high16 (16.0f, 8.0f) in MenuItemKt
 *  row height 56/52dp -> 40dp      const/high16 0x42600000 / 0x42500000 in MenuItemKt
 *  group spacing 2dp -> 0          only const/high16 0x40000000 in MenuGroupKt.MenuGroup
 *  frame spacing 12dp -> 0         first const/high16 0x41400000 followed by <init>(FZ..) in MenuScaffoldKt.MenuFrame
 *  nav bar padding                 const/high16 12.0f + 4.0f pair in MenuNavigationKt.MenuNavigation -> OldMenu.navPadding()
 *  nav labels hidden               MenuNavItem: the void call after the 4.0f literal (spacer) and the Text call (first param String, 20+ params)
 *  long badges hidden              MenuItemKt.Badge(String, ...) returns early when OldMenu.hideBadge(text)
 *  outer padding                   MainMenu: const/high16 8.0f right after sget MenuAccessPoint.Home -> 0;
 *                                  bottom padding of the 4 float padding call that has the 84.0f literal -> OldMenu.bottomPadding
 */
private fun BytecodePatchContext.restyleRows() {
    val surface = colorField(1)
    val bright = colorField(2)
    val high = colorField(3)
    if (surface.definingClass != bright.definingClass || surface.type != "J" || bright.type != "J") {
        throw PatchException("Unexpected ColorScheme fields: $surface $bright")
    }

    var recolored = 0
    for (owner in listOf(MENU_ITEM_KT, MAIN_MENU_KT, IP_PROTECTION_KT, ACCOUNT_KT, "${COMPOSE_PACKAGE}AddonMenuItemKt;")) {
        recolored += recolor(owner, bright, surface)
    }
    recolor(MENU_NAVIGATION_KT, high, surface)
    if (recolored == 0) throw PatchException("ColorScheme.surfaceBright reads not found in menu classes")

    var rows = 0
    for (method in mutableClassDefBy(MENU_ITEM_KT).methods) {
        val list = method.instructionList()
        val edits = ArrayList<Edit>()
        for (i in list.indices) {
            val instruction = list[i]
            if (instruction.highConst(0x42600000) || instruction.highConst(0x42500000)) {
                edits += floatTo(i, 0x42200000)
                rows++
            }
            val next = list.getOrNull(i + 1) ?: continue
            if (instruction.highConst(0x41800000) && next.highConst(0x41000000)) {
                edits += floatTo(i, 0x41400000)
                edits += floatTo(i + 1, 0x40000000)
                rows++
            } else if (instruction.highConst(0x41000000) && next.highConst(0x41800000)) {
                edits += floatTo(i, 0x40000000)
                edits += floatTo(i + 1, 0x41400000)
                rows++
            }
        }
        if (edits.isNotEmpty()) method.applyEdits(*edits.toTypedArray())
    }
    if (rows == 0) throw PatchException("Menu row size literals not found in MenuItemKt")

    var groups = 0
    for (method in methodsOf(MENU_GROUP_KT, "MenuGroup")) {
        val list = method.instructionList()
        val edits = list.indices.filter { list[it].highConst(0x40000000) }.map { zeroTo(it) }
        if (edits.isNotEmpty()) {
            method.applyEdits(*edits.toTypedArray())
            groups += edits.size
        }
    }
    if (groups == 0) throw PatchException("MenuGroup spacing literal not found")

    var frames = 0
    for (method in methodsOf(MENU_SCAFFOLD_KT, "MenuFrame")) {
        val list = method.instructionList()
        val at = list.indices.firstOrNull { i ->
            list[i].highConst(0x41400000) && (i + 1..i + 3).any { j ->
                val reference = list.getOrNull(j)?.methodRef()
                list.getOrNull(j)?.opcode == Opcode.INVOKE_DIRECT && reference != null && reference.name == "<init>" &&
                    reference.params().take(2) == listOf("F", "Z")
            }
        }
        if (at != null) {
            method.applyEdits(zeroTo(at))
            frames++
        }
    }
    if (frames == 0) throw PatchException("MenuFrame spacing literal not found")

    for (method in methodsOf(MENU_NAVIGATION_KT, "MenuNavigation")) {
        val list = method.instructionList()
        val at = list.indices.firstOrNull { i ->
            list[i].highConst(0x41400000) && list.getOrNull(i + 1)?.highConst(0x40800000) == true
        } ?: throw PatchException("Navigation bar padding literal not found")
        method.applyEdits(navPaddingTo(at))
    }

    for (method in methodsOf(MENU_NAVIGATION_KT, "MenuNavItem")) {
        val list = method.instructionList()
        val liveness = Liveness(method)
        val literal = list.indices.firstOrNull { list[it].highConst(0x40800000) }
            ?: throw PatchException("Navigation label spacer literal not found")
        val spacer = (literal + 1..literal + 4).firstOrNull { j ->
            val reference = list.getOrNull(j)?.methodRef()
            list.getOrNull(j)?.opcode == Opcode.INVOKE_STATIC && reference != null && reference.returnType == "V" &&
                reference.params().size == 4 && reference.params()[2] == "F"
        } ?: throw PatchException("Navigation label spacer call not found")
        val text = list.indices.firstOrNull { j ->
            val reference = list[j].methodRef()
            list[j].opcode == Opcode.INVOKE_STATIC_RANGE && reference != null &&
                reference.params().firstOrNull() == "Ljava/lang/String;" && reference.params().size >= 20
        } ?: throw PatchException("Navigation label text call not found")
        method.applyEdits(
            skipWhenOld(spacer, liveness.takeLow(1, spacer)[0]),
            skipWhenOld(text, liveness.takeLow(1, text)[0]),
        )
    }

    for (method in mutableClassDefBy(IP_PROTECTION_KT).methods) {
        val list = method.instructionList()
        val edits = list.indices.filter { list[it].highConst(0x42500000) }.map { floatTo(it, 0x42400000) }
        if (edits.isNotEmpty()) method.applyEdits(*edits.toTypedArray())
    }

    for (method in mutableClassDefBy(MAIN_MENU_KT).methods) {
        val list = method.instructionList()
        val at = list.indices.firstOrNull { i ->
            list[i].highConst(0x40000000) && (i + 1..i + 2).any { j ->
                val reference = list.getOrNull(j)?.methodRef()
                list.getOrNull(j)?.opcode == Opcode.INVOKE_DIRECT && reference != null && reference.name == "<init>" &&
                    reference.params().take(2) == listOf("F", "Z")
            }
        } ?: continue
        method.applyEdits(zeroTo(at))
        break
    }

    hideAccountSubtitle()
    hideExtensionExtras()

    val badge = mutableClassDefBy(MENU_ITEM_KT).methods.firstOrNull {
        it.name == "Badge" && it.params().firstOrNull() == "Ljava/lang/String;"
    } ?: throw PatchException("MenuItemKt.Badge not found")
    val badgeScratch = Liveness(badge).takeLow(1, 0)[0]
    badge.applyEdits(
        insert(
            0,
            """
                invoke-static/range {p0 .. p0}, $OLD_MENU->hideBadge(Ljava/lang/String;)Z
                move-result v$badgeScratch
                if-eqz v$badgeScratch, :ftl_show
                return-void
            """,
            mapOf("ftl_show" to { 0 }),
        ),
    )

    val mainMenu = methodsOf(MAIN_MENU_KT, "MainMenu").single()
    val list = mainMenu.instructionList()
    val edits = ArrayList<Edit>()
    val home = list.indices.firstOrNull { i ->
        val reference = (list[i] as? ReferenceInstruction)?.reference as? FieldReference
        list[i].opcode == Opcode.SGET_OBJECT && reference != null && reference.name == "Home" &&
            reference.definingClass == "${MENU_PACKAGE}MenuAccessPoint;" && list.getOrNull(i + 1)?.highConst(0x41000000) == true
    } ?: throw PatchException("MainMenu outer padding literal not found")
    edits += zeroTo(home + 1)

    val padding = list.indices.firstOrNull { i ->
        val reference = list[i].methodRef()
        list[i].opcode == Opcode.INVOKE_STATIC && reference != null && reference.params().size == 5 &&
            reference.params().drop(1).all { it == "F" } && reference.returnType != "V" &&
            (maxOf(0, i - 25) until i).any { list[it].highConst(0x42a80000) }
    } ?: throw PatchException("MainMenu bottom padding call not found")
    val bottom = (list[padding] as FiveRegisterInstruction).registerG
    if (bottom > 15) throw PatchException("MainMenu bottom padding register v$bottom is out of reach")
    val paddingScratch = Liveness(mainMenu).takeLow(1, padding)[0]
    edits += insert(
        padding,
        """
            invoke-static {}, $MOD_SETTINGS->oldMenu()Z
            move-result v$paddingScratch
            if-eqz v$paddingScratch, :ftl_keep
            const/4 v$paddingScratch, 0x0
            invoke-static {v$bottom, v$paddingScratch}, $OLD_MENU->bottomPadding(FZ)F
            move-result v$bottom
        """,
        mapOf("ftl_keep" to { padding }),
    )
    mainMenu.applyEdits(*edits.toTypedArray())
}

@Suppress("unused")
val oldMenuPatch = bytecodePatch(
    name = "Old style 3 dot menu And Extension Pinning",
    description = "Adds \"Mod Settings\" to the 3 dot menu and switches between the stock bottom sheet " +
        "menu and the old style popup menu.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_FIREFOX_NIGHTLY)

    dependsOn(modIconPatch)

    extendWith("extensions/firefox.mpe")

    execute {
        installTabAccessors()
        installWindowHooks()
        hideSheetHandle()
        restyleRows()
        installSheetAccessors()
        routeToLegacyMenu()
        installModRow()
    }
}
