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
 *  Not part of this version: flat row restyle, Extensions page, extension pinning.
 */
package app.ftl.patches.firefox

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
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

    target.applyEdits(
        insert(
            groupIndex + 1,
            "invoke-static {v$composerRegister}, $MOD_COMPOSE->modRow(Ljava/lang/Object;)V",
        ),
    )

    val interfaces = mapOf(MOD_LAMBDA to function2, MOD_CLICK to types.function0)
    interfaces.forEach { (type, function) ->
        val list = mutableClassDefBy(type).interfaces
        if (function !in list) list.add(function)
    }
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
        installSheetAccessors()
        routeToLegacyMenu()
        installModRow()
    }
}
