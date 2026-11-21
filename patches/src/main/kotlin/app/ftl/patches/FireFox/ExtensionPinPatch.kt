/*
 * Extension pinning (pin an extension from the 3 dot menu to the top bar).
 *
 * 161.0a1 R8-renames every Android-Components class this feature touches. Nothing here pins an obfuscated
 * name. How each piece is found (use this to find it again in a new build; obfuscated names seen in the
 * build this was written for are in brackets and are only examples):
 *
 *  BrowserToolbarMiddleware (Fenix, named). Fields uiContext / browserStore / settings are read by reflection.
 *      buildEndBrowserActions   first ArrayList.<init>() is the end actions list. addPinned() is called right after it.
 *      invoke(Object, Object, Object)  the middleware lambda (store, next, action). The prologue is the run of
 *          move-object / check-cast / getClass() calls at the start. remember() and the PinEvent branch go
 *          right after it. The branch calls next.invoke(action) and jumps to the final Unit return.
 *      caller of buildEndBrowserActions that then does new X(List) + dispatch: X = BrowserActionsEndUpdated
 *          [t62$a]. capture(list) is called right before new X. refresh() re-dispatches X with the pins swapped.
 *
 *  Toolbar button [tb$a]
 *      toString literal "ActionButton(drawable=". Main constructor = (Drawable, Z, String, State, Interaction,
 *      LongClick) [zb2, zb2$c]. The synthetic default constructor (Drawable, Z, String, Event, I) gives the
 *      Event interface [zb2$a] (marker interface, no methods) and the default State (first sget-object of the
 *      State type). PinEvent implements Event. onClick field = the only field typed Interaction.
 *      Long click needs a CombinedEventAndMenu [zb2$c] (event + menu [zb2$b]). It is passed as null, so a long
 *      press on a pinned toolbar button does nothing. Unpin from the 3 dot menu (long press on the extension).
 *
 *  State model (all through generated accessors in PinAccess)
 *      WebExtensionState [j6k]      toString literal "WebExtensionState(id=". R8 hoists its const-strings, so
 *                                   fields are taken from the order of the iget instructions in toString:
 *                                   first Z = enabled, first two fields typed Action = browserAction, pageAction.
 *      Action [ub]                  toString literal "Action(title=". iget order = title, enabled, loadIcon,
 *                                   badgeText, badgeTextColor, badgeBackgroundColor, onClick (types checked).
 *      BrowserState [oa2]           toString literal "BrowserState(tabs=". extensions = the Map field whose generic
 *                                   Signature names WebExtensionState. Fallback: first iget after ", extensions=".
 *      TabSessionState [j8i]        toString literal "TabSessionState(id=". extensionState = same Map rule.
 *      Store [heh]                  Core.getStore() return type, walked up to the class that declares dispatch(1)
 *                                   and observeManually(1). dispatch param = lib.state.Action [vb]. Function1 =
 *                                   observeManually param.
 *      Function0 [qo7] / Function2 [fp7]  same sources as OldMenuPatch (FloatingToolbarFAB, ExpandableMenuItemAnimation).
 *      Continuation [c84]           last parameter of buildEndBrowserActions (suspend). getContext() returns
 *                                   CoroutineContext [sc4]; the instance is EmptyCoroutineContext.INSTANCE, found by
 *                                   toString literal "EmptyCoroutineContext". If any of this is missing the icon
 *                                   loader is not wired (saved icon is used) and the patch still applies.
 *
 *  Menu hooks (Fenix, named)
 *      ExtensionsMenuItemKt.WebExtensionMenuItems  the only constructor call taking (MenuAccessPoint, Function0,
 *          WebExtensionMenuItem). The Function0 it creates is wrapped by ExtensionPin.wrap(click, item).
 *      ListItemKt.IconListItem*  its single call to ListItem-*. The two adjacent Function0 arguments are onClick and
 *          onLongClick. onLongClick = ExtensionPin.longOf(onClick).
 */
package app.ftl.patches.firefox

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.string
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference
import com.android.tools.smali.dexlib2.iface.value.ArrayEncodedValue
import com.android.tools.smali.dexlib2.iface.value.StringEncodedValue
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod

private const val PIN = "Lapp/ftl/extension/firefox/ExtensionPin;"
private const val PIN_ACCESS = "Lapp/ftl/extension/firefox/PinAccess;"
private const val PIN_EVENT = "Lapp/ftl/extension/firefox/PinEvent;"
private const val PIN_CLICK = "Lapp/ftl/extension/firefox/PinClick;"
private const val PIN_LONG = "Lapp/ftl/extension/firefox/PinLong;"
private const val PIN_OBSERVER = "Lapp/ftl/extension/firefox/PinObserver;"
private const val PIN_CONT = "Lapp/ftl/extension/firefox/PinCont;"

private const val PIN_MIDDLEWARE = "Lorg/mozilla/fenix/components/toolbar/BrowserToolbarMiddleware;"
private const val PIN_LIST_ITEM_KT = "Lorg/mozilla/fenix/compose/list/ListItemKt;"
private const val PIN_MENU_ITEM = "Lorg/mozilla/fenix/components/menu/store/WebExtensionMenuItem;"
private const val PIN_ACCESS_POINT = "Lorg/mozilla/fenix/components/menu/MenuAccessPoint;"
private const val PIN_CORE = "Lorg/mozilla/fenix/components/Core;"

private const val PIN_OBJECT = "Ljava/lang/Object;"
private const val PIN_STRING = "Ljava/lang/String;"
private const val PIN_MAP = "Ljava/util/Map;"
private const val PIN_LIST = "Ljava/util/List;"
private const val PIN_DRAWABLE = "Landroid/graphics/drawable/Drawable;"

private object PinActionFingerprint : Fingerprint(
    name = "toString",
    returnType = PIN_STRING,
    parameters = listOf(),
    filters = listOf(string("Action(title="), string(", loadIcon="), string(", onClick=")),
)

private object PinExtensionStateFingerprint : Fingerprint(
    name = "toString",
    returnType = PIN_STRING,
    parameters = listOf(),
    filters = listOf(string("WebExtensionState(id=")),
)

private object PinBrowserStateFingerprint : Fingerprint(
    name = "toString",
    returnType = PIN_STRING,
    parameters = listOf(),
    filters = listOf(string("BrowserState(tabs=")),
)

private object PinTabStateFingerprint : Fingerprint(
    name = "toString",
    returnType = PIN_STRING,
    parameters = listOf(),
    filters = listOf(string("TabSessionState(id=")),
)

private object PinButtonFingerprint : Fingerprint(
    name = "toString",
    returnType = PIN_STRING,
    parameters = listOf(),
    filters = listOf(string("ActionButton(drawable=")),
)

private object PinEmptyContextFingerprint : Fingerprint(
    name = "toString",
    returnType = PIN_STRING,
    parameters = listOf(),
    filters = listOf(string("EmptyCoroutineContext")),
)

private fun MutableMethod.pinInstructions() = implementation!!.instructions.toList()

private fun Instruction.pinMethod() = (this as? ReferenceInstruction)?.reference as? MethodReference

private fun Instruction.pinFieldRef() = (this as? ReferenceInstruction)?.reference as? FieldReference

private fun Instruction.pinTypeRef() = ((this as? ReferenceInstruction)?.reference as? TypeReference)?.type

private fun MethodReference.pinParams() = parameterTypes.map { it.toString() }

// Instance fields read by a data class toString, in read order, without duplicates.
private fun Method.pinReadFields(owner: String): List<FieldReference> {
    val seen = LinkedHashMap<String, FieldReference>()
    for (instruction in implementation?.instructions ?: return emptyList()) {
        if (!instruction.opcode.name.startsWith("iget")) continue
        val field = instruction.pinFieldRef() ?: continue
        if (field.definingClass == owner) seen.putIfAbsent(field.name, field)
    }
    return seen.values.toList()
}

private fun Method.pinFieldAfterLiteral(literalIndex: Int, opcode: Opcode, owner: String): FieldReference? {
    val instructions = implementation?.instructions?.toList() ?: return null
    for (i in literalIndex until instructions.size) {
        val field = instructions[i].pinFieldRef() ?: continue
        if (instructions[i].opcode == opcode && field.definingClass == owner) return field
    }
    return null
}

private fun pinIsObject(type: String) = type.startsWith("L") && !type.startsWith("Ljava/")

private class PinModel(
    val state: String,
    val extensions: FieldReference,
    val tab: String,
    val tabExtensions: FieldReference,
    val extension: String,
    val enabled: FieldReference,
    val browserAction: FieldReference,
    val pageAction: FieldReference,
    val action: String,
    val actionFields: List<FieldReference>,
)

// Map<String, WebExtensionState> field of a state class. The generic Signature annotation names the value type.
// Falls back to the first object read after the toString literal if R8 dropped the signature.
private fun BytecodePatchContext.pinExtensionMap(
    owner: String,
    extensionType: String,
    toString: Method,
    literal: String,
): FieldReference {
    val bySignature = classDefBy(owner).instanceFields.filter { field ->
        field.type == PIN_MAP && field.annotations.any { note ->
            note.type == "Ldalvik/annotation/Signature;" && note.elements.any { element ->
                (element.value as? ArrayEncodedValue)?.value?.any {
                    (it as? StringEncodedValue)?.value == extensionType
                } == true
            }
        }
    }
    if (bySignature.size == 1) return bySignature[0]
    if (bySignature.size > 1) throw PatchException("More than one Map<String, WebExtensionState> field in $owner")
    val literalAt = toString.implementation!!.instructions.toList().indexOfFirst { instruction ->
        ((instruction as? ReferenceInstruction)?.reference as? StringReference)?.string == literal
    }
    val fallback = if (literalAt >= 0) toString.pinFieldAfterLiteral(literalAt, Opcode.IGET_OBJECT, owner) else null
    if (fallback == null || fallback.type != PIN_MAP) {
        throw PatchException("Extension map field not found in $owner (no Signature and no toString literal)")
    }
    return fallback
}

private fun BytecodePatchContext.resolvePinModel(): PinModel {
    val actionMethod = PinActionFingerprint.method
    val action = actionMethod.definingClass
    val actionFields = actionMethod.pinReadFields(action)
    if (actionFields.size < 7) throw PatchException("Unexpected field count in $action.toString: ${actionFields.size}")
    val picked = actionFields.take(7)
    val expected = listOf(PIN_STRING, "Ljava/lang/Boolean;", null, PIN_STRING, "Ljava/lang/Integer;", "Ljava/lang/Integer;", null)
    picked.forEachIndexed { i, field ->
        val type = expected[i]
        if (type != null && field.type != type || type == null && !pinIsObject(field.type)) {
            throw PatchException("Unexpected Action field $i in $action: ${fieldString(field)}")
        }
    }

    val extensionMethod = PinExtensionStateFingerprint.method
    val extension = extensionMethod.definingClass
    val extensionFields = extensionMethod.pinReadFields(extension)
    val enabled = extensionFields.firstOrNull { it.type == "Z" }
        ?: throw PatchException("WebExtensionState.enabled not found in $extension")
    val actions = extensionFields.filter { it.type == action }
    if (actions.size < 2) throw PatchException("WebExtensionState browserAction/pageAction not found in $extension")

    val stateMethod = PinBrowserStateFingerprint.method
    val state = stateMethod.definingClass
    val tabMethod = PinTabStateFingerprint.method
    val tab = tabMethod.definingClass
    return PinModel(
        state = state,
        extensions = pinExtensionMap(state, extension, stateMethod, ", extensions="),
        tab = tab,
        tabExtensions = pinExtensionMap(tab, extension, tabMethod, ", extensionState="),
        extension = extension,
        enabled = enabled,
        browserAction = actions[0],
        pageAction = actions[1],
        action = action,
        actionFields = picked,
    )
}

private class PinStore(val base: String, val dispatchParameter: String, val function1: String)

private fun BytecodePatchContext.resolvePinStore(): PinStore {
    val coreStore = classDefBy(PIN_CORE).methods
        .firstOrNull { it.name == "getStore" && it.parameterTypes.isEmpty() }?.returnType
        ?: throw PatchException("Core.getStore() not found")
    var type: String? = coreStore
    while (type != null && type != PIN_OBJECT) {
        val definition = classDefBy(type)
        val dispatch = definition.methods.firstOrNull {
            it.name == "dispatch" && it.parameterTypes.size == 1 && it.returnType == "V"
        }
        val observe = definition.methods.firstOrNull { it.name == "observeManually" && it.parameterTypes.size == 1 }
        if (dispatch != null && observe != null) {
            return PinStore(type, dispatch.parameterTypes[0].toString(), observe.parameterTypes[0].toString())
        }
        type = definition.superclass
    }
    throw PatchException("Store class with dispatch/observeManually not found above $coreStore")
}

private class PinButton(
    val type: String,
    val state: String,
    val stateDefault: FieldReference,
    val interaction: String,
    val longClick: String,
    val event: String,
    val clickField: FieldReference,
)

private fun BytecodePatchContext.resolvePinButton(): PinButton {
    val type = PinButtonFingerprint.method.definingClass
    val definition = classDefBy(type)
    val main = definition.methods.firstOrNull {
        val p = it.pinParams()
        it.name == "<init>" && p.size == 6 && p[0] == PIN_DRAWABLE && p[1] == "Z" && p[2] == PIN_STRING
    } ?: throw PatchException("ActionButton(Drawable, Z, String, State, Interaction, LongClick) not found in $type")
    val parameters = main.pinParams()
    val synthetic = definition.methods.firstOrNull {
        val p = it.pinParams()
        it.name == "<init>" && p.size == 5 && p[0] == PIN_DRAWABLE && p[4] == "I"
    } ?: throw PatchException("ActionButton default constructor not found in $type")
    val event = synthetic.pinParams()[3]
    if (parameters[4] !in classDefBy(event).interfaces) {
        throw PatchException("$event is not an Interaction of $type (${parameters[4]})")
    }
    val stateDefault = synthetic.implementation!!.instructions.firstNotNullOfOrNull { instruction ->
        instruction.pinFieldRef()?.takeIf { instruction.opcode == Opcode.SGET_OBJECT && it.type == parameters[3] }
    } ?: throw PatchException("ActionButton default State not found in $type")
    val clickFields = definition.instanceFields.filter { it.type == parameters[4] }
    if (clickFields.size != 1) throw PatchException("ActionButton onClick field not unique in $type")
    return PinButton(
        type = type,
        state = parameters[3],
        stateDefault = stateDefault,
        interaction = parameters[4],
        longClick = parameters[5],
        event = event,
        clickField = clickFields[0],
    )
}

private class PinContinuation(val type: String, val contextType: String, val contextInstance: FieldReference)

// Optional. Without it the extension still works but icons come from the saved bitmap.
private fun BytecodePatchContext.resolvePinContinuation(): PinContinuation? {
    val type = classDefBy(PIN_MIDDLEWARE).methods.firstOrNull { it.name == "buildEndBrowserActions" }
        ?.parameterTypes?.lastOrNull()?.toString() ?: return null
    val definition = classDefByOrNull(type) ?: return null
    val context = definition.methods.firstOrNull { it.name == "getContext" && it.parameterTypes.isEmpty() } ?: return null
    if (definition.methods.none { it.name == "resumeWith" && it.pinParams() == listOf(PIN_OBJECT) }) return null
    val empty = PinEmptyContextFingerprint.methodOrNull?.definingClass ?: return null
    if (context.returnType !in classDefBy(empty).interfaces) return null
    val instance = classDefBy(empty).staticFields.firstOrNull { it.type == empty } ?: return null
    return PinContinuation(type, context.returnType, instance)
}

private fun BytecodePatchContext.pinFunction2(): String =
    classDefBy(MENU_ITEM_KT).methods.firstOrNull { it.name == "ExpandableMenuItemAnimation" }
        ?.parameterTypes?.getOrNull(1)?.toString() ?: throw PatchException("Function2 type not found")

private fun BytecodePatchContext.addPinMethod(
    owner: String,
    name: String,
    returnType: String,
    registers: Int,
    smali: String,
) {
    val holder = mutableClassDefBy(owner)
    holder.methods.removeAll { it.name == name }
    val method = ImmutableMethod(
        owner,
        name,
        listOf(),
        returnType,
        AccessFlags.PUBLIC.value or AccessFlags.FINAL.value,
        null,
        null,
        MutableMethodImplementation(registers),
    ).toMutable()
    method.addInstructionsWithLabels(0, smali.trimIndent())
    holder.methods.add(method)
}

private fun BytecodePatchContext.pinImplements(owner: String, type: String) {
    val list = mutableClassDefBy(owner).interfaces
    if (type !in list) list.add(type)
}

private fun BytecodePatchContext.pinMiddleware() = mutableClassDefBy(PIN_MIDDLEWARE)

// addPinned: right after the first ArrayList.<init>() of buildEndBrowserActions (the end actions list).
private fun BytecodePatchContext.hookAddPinned() {
    val method = pinMiddleware().methods.singleOrNull { it.name == "buildEndBrowserActions" }
        ?: throw PatchException("BrowserToolbarMiddleware.buildEndBrowserActions not found or not unique")
    val list = method.pinInstructions()
    val init = list.indexOfFirst {
        val reference = it.pinMethod()
        it.opcode == Opcode.INVOKE_DIRECT && reference != null && reference.definingClass == "Ljava/util/ArrayList;" &&
            reference.name == "<init>" && reference.parameterTypes.isEmpty()
    }
    if (init < 0) throw PatchException("ArrayList() not found in buildEndBrowserActions")
    val register = (list[init] as FiveRegisterInstruction).registerC
    if (method.parameterBase() > 15) throw PatchException("p0 is out of 4 bit range in buildEndBrowserActions")
    method.applyEdits(
        insert(init + 1, "invoke-static {v$register, p0}, $PIN->addPinned(Ljava/util/ArrayList;Ljava/lang/Object;)V"),
    )
}

// capture: right before `new BrowserActionsEndUpdated(list)` in the method that awaits buildEndBrowserActions.
// Returns the BrowserActionsEndUpdated type.
private fun BytecodePatchContext.hookCapture(): String {
    class Site(val method: MutableMethod, val created: Int, val list: Int, val type: String)

    val sites = ArrayList<Site>()
    for (method in pinMiddleware().methods) {
        if (method.name == "buildEndBrowserActions") continue
        val list = method.implementation?.instructions?.toList() ?: continue
        val call = list.indexOfFirst { it.pinMethod()?.name == "buildEndBrowserActions" }
        if (call < 0) continue
        for (i in call + 1 until list.size) {
            val reference = list[i].pinMethod() ?: continue
            if (list[i].opcode != Opcode.INVOKE_DIRECT || reference.name != "<init>" ||
                reference.pinParams() != listOf(PIN_LIST)
            ) continue
            val created = (i - 1 downTo call).firstOrNull {
                list[it].opcode == Opcode.NEW_INSTANCE && list[it].pinTypeRef() == reference.definingClass
            } ?: continue
            sites += Site(method, created, (list[i] as FiveRegisterInstruction).registerD, reference.definingClass)
        }
    }
    val site = sites.singleOrNull()
        ?: throw PatchException("BrowserActionsEndUpdated creation site not unique (${sites.size})")
    site.method.applyEdits(
        insert(site.created, site.method.staticCall(site.created, "$PIN->capture(Ljava/util/List;)V", listOf(site.list))),
    )
    return site.type
}

// remember + PinEvent branch: at the end of the prologue of the middleware lambda invoke(store, next, action).
private fun BytecodePatchContext.hookDispatcher() {
    val method = pinMiddleware().methods.singleOrNull {
        it.name == "invoke" && it.returnType == PIN_OBJECT && it.pinParams() == listOf(PIN_OBJECT, PIN_OBJECT, PIN_OBJECT)
    } ?: throw PatchException("BrowserToolbarMiddleware.invoke(Object, Object, Object) not found or not unique")
    val list = method.pinInstructions()

    // Prologue copies the parameters to low registers. Use the parameter itself when there is no copy.
    fun copyOf(source: Int): Int = list.firstNotNullOfOrNull { instruction ->
        if (!instruction.opcode.name.startsWith("move-object")) return@firstNotNullOfOrNull null
        (instruction as TwoRegisterInstruction).takeIf { it.registerB == source }?.registerA
    } ?: source.takeIf { it <= 15 } ?: throw PatchException("Parameter v$source not reachable in ${method.name}")

    val self = copyOf(method.parameterBase())
    val store = copyOf(parameterRegister(method, 0))
    val next = copyOf(parameterRegister(method, 1))
    val action = copyOf(parameterRegister(method, 2))
    if (maxOf(self, store, next, action) > 15) throw PatchException("Middleware registers are out of 4 bit range")

    var end = 0
    while (end < list.size) {
        val instruction = list[end]
        val prologue = instruction.opcode.name.startsWith("move-object") || instruction.opcode == Opcode.CHECK_CAST ||
            instruction.opcode == Opcode.INVOKE_VIRTUAL && instruction.pinMethod()?.name == "getClass"
        if (!prologue) break
        end++
    }
    val at = end
    val function1 = list.take(at).firstOrNull {
        it.opcode == Opcode.CHECK_CAST && (it as OneRegisterInstruction).registerA == next
    }?.pinTypeRef() ?: throw PatchException("Function1 cast of the next lambda not found in ${method.name}")

    val tail = list.size
    if (list[tail - 1].opcode != Opcode.RETURN_OBJECT || list[tail - 2].opcode != Opcode.SGET_OBJECT) {
        throw PatchException("Unexpected end of ${method.name}")
    }
    val scratch = Liveness(method).takeLow(1, at)[0]
    method.applyEdits(
        insert(
            at,
            """
                invoke-static {v$store, v$self}, $PIN->remember(Ljava/lang/Object;Ljava/lang/Object;)V
                instance-of v$scratch, v$action, $PIN_EVENT
                if-eqz v$scratch, :ftl_pin_skip
                invoke-static {v$action, v$self}, $PIN->handle(Ljava/lang/Object;Ljava/lang/Object;)V
                invoke-interface {v$next, v$action}, $function1->invoke(Ljava/lang/Object;)Ljava/lang/Object;
                goto :ftl_pin_end
            """,
            mapOf<String, Target>(
                "ftl_pin_skip" to { at },
                "ftl_pin_end" to { implementation!!.instructions.size - 2 },
            ),
        ),
    )
}

// wrap: the Function0 created in WebExtensionMenuItems for each extension row.
private fun BytecodePatchContext.hookExtensionClick(function0: String) {
    val method = mutableClassDefBy(EXTENSIONS_MENU_ITEM_KT).methods.singleOrNull { it.name == "WebExtensionMenuItems" }
        ?: throw PatchException("ExtensionsMenuItemKt.WebExtensionMenuItems not found or not unique")
    val list = method.pinInstructions()
    val creations = list.indices.filter { i ->
        val reference = list[i].pinMethod()
        list[i].opcode == Opcode.INVOKE_DIRECT && reference != null && reference.name == "<init>" &&
            reference.pinParams().let { PIN_ACCESS_POINT in it && PIN_MENU_ITEM in it && function0 in it }
    }
    val creation = creations.singleOrNull()
        ?: throw PatchException("Extension click lambda creation not unique (${creations.size})")
    val register = (list[creation] as? FiveRegisterInstruction)?.registerC
        ?: throw PatchException("Extension click lambda constructor is not a 5 register invoke")
    val item = method.callArgument(creation, PIN_MENU_ITEM)
        ?: throw PatchException("WebExtensionMenuItem argument not found at $creation")
    val cast = (creation + 1 until minOf(list.size, creation + 40)).firstOrNull {
        list[it].opcode == Opcode.CHECK_CAST && (list[it] as OneRegisterInstruction).registerA == register &&
            list[it].pinTypeRef() == function0
    } ?: throw PatchException("Function0 cast of the extension click lambda not found")
    if (item == register || Liveness(method).redefinedBetween(item, creation + 1, cast + 1)) {
        throw PatchException("WebExtensionMenuItem register v$item is reused before the cast")
    }
    val call = method.staticCall(
        cast + 1,
        "$PIN->wrap(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
        listOf(register, item),
    )
    method.applyEdits(
        insert(
            cast + 1,
            """
                $call
                move-result-object v$register
                check-cast v$register, $function0
            """,
        ),
    )
}

// longOf: IconListItem passes null as onLongClick to the inner ListItem. Replace it with longOf(onClick).
private fun BytecodePatchContext.hookLongClick(function0: String) {
    val method = mutableClassDefBy(PIN_LIST_ITEM_KT).methods
        .singleOrNull { Regex("IconListItem(-.*)?").matches(it.name) }
        ?: throw PatchException("ListItemKt.IconListItem not found or not unique")
    val list = method.pinInstructions()
    val calls = list.indices.filter { i ->
        val reference = list[i].pinMethod()
        (list[i].opcode == Opcode.INVOKE_STATIC || list[i].opcode == Opcode.INVOKE_STATIC_RANGE) &&
            reference != null && reference.definingClass == PIN_LIST_ITEM_KT && reference.name.startsWith("ListItem-")
    }
    val call = calls.singleOrNull() ?: throw PatchException("ListItem call in IconListItem not unique (${calls.size})")
    val parameters = method.callParameters(call)
    val pairs = (0 until parameters.size - 1).filter {
        parameters[it].first == function0 && parameters[it + 1].first == function0
    }
    val pair = pairs.singleOrNull() ?: throw PatchException("onClick/onLongClick pair not unique (${pairs.size})")
    val click = parameters[pair].second
    val long = parameters[pair + 1].second
    val invoke = method.staticCall(call, "$PIN->longOf(Ljava/lang/Object;)Ljava/lang/Object;", listOf(click))
    method.applyEdits(
        insert(
            call,
            """
                $invoke
                move-result-object v$long
                check-cast v$long, $function0
            """,
        ),
    )
}

private fun BytecodePatchContext.installPinAccessors(
    model: PinModel,
    store: PinStore,
    button: PinButton,
    ends: String,
    function0: String,
    function2: String,
) {
    addStaticMethod(PIN_ACCESS, "ftlExtensions", listOf(PIN_OBJECT), PIN_MAP, 2, """
        check-cast p0, ${model.state}
        iget-object v0, p0, ${fieldString(model.extensions)}
        return-object v0
    """)
    addStaticMethod(PIN_ACCESS, "ftlTabExtensions", listOf(PIN_OBJECT), PIN_MAP, 2, """
        check-cast p0, ${model.tab}
        iget-object v0, p0, ${fieldString(model.tabExtensions)}
        return-object v0
    """)
    addStaticMethod(PIN_ACCESS, "ftlExtInfo", listOf(PIN_OBJECT), "[Ljava/lang/Object;", 4, """
        check-cast p0, ${model.extension}
        const/4 v0, 0x3
        new-array v0, v0, [Ljava/lang/Object;
        iget-boolean v1, p0, ${fieldString(model.enabled)}
        invoke-static {v1}, Ljava/lang/Boolean;->valueOf(Z)Ljava/lang/Boolean;
        move-result-object v1
        const/4 v2, 0x0
        aput-object v1, v0, v2
        iget-object v1, p0, ${fieldString(model.browserAction)}
        const/4 v2, 0x1
        aput-object v1, v0, v2
        iget-object v1, p0, ${fieldString(model.pageAction)}
        const/4 v2, 0x2
        aput-object v1, v0, v2
        return-object v0
    """)
    val actionSmali = buildString {
        appendLine("check-cast p0, ${model.action}")
        appendLine("const/4 v0, 0x${model.actionFields.size}")
        appendLine("new-array v0, v0, [Ljava/lang/Object;")
        model.actionFields.forEachIndexed { i, field ->
            appendLine("iget-object v1, p0, ${fieldString(field)}")
            appendLine("const/4 v2, 0x$i")
            appendLine("aput-object v1, v0, v2")
        }
        appendLine("return-object v0")
    }
    addStaticMethod(PIN_ACCESS, "ftlActionInfo", listOf(PIN_OBJECT), "[Ljava/lang/Object;", 4, actionSmali)
    addStaticMethod(PIN_ACCESS, "ftlInvoke0", listOf(PIN_OBJECT), PIN_OBJECT, 2, """
        check-cast p0, $function0
        invoke-interface {p0}, $function0->invoke()Ljava/lang/Object;
        move-result-object v0
        return-object v0
    """)
    addStaticMethod(PIN_ACCESS, "ftlInvoke2", listOf(PIN_OBJECT, PIN_OBJECT, PIN_OBJECT), PIN_OBJECT, 4, """
        check-cast p0, $function2
        invoke-interface {p0, p1, p2}, $function2->invoke(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;
        move-result-object v0
        return-object v0
    """)
    val buttonConstructor =
        "${button.type}-><init>(${PIN_DRAWABLE}Z$PIN_STRING${button.state}${button.interaction}${button.longClick})V"
    addStaticMethod(PIN_ACCESS, "ftlButton", listOf(PIN_DRAWABLE, PIN_STRING, PIN_OBJECT), PIN_OBJECT, 10, """
        new-instance v0, ${button.type}
        move-object v1, p0
        const/4 v2, 0x0
        move-object v3, p1
        sget-object v4, ${fieldString(button.stateDefault)}
        check-cast p2, ${button.interaction}
        move-object v5, p2
        const/4 v6, 0x0
        invoke-direct/range {v0 .. v6}, $buttonConstructor
        return-object v0
    """)
    addStaticMethod(PIN_ACCESS, "ftlButtonClick", listOf(PIN_OBJECT), PIN_OBJECT, 2, """
        instance-of v0, p0, ${button.type}
        if-eqz v0, :ftl_none
        check-cast p0, ${button.type}
        iget-object v0, p0, ${fieldString(button.clickField)}
        return-object v0
        :ftl_none
        const/4 v0, 0x0
        return-object v0
    """)
    addStaticMethod(PIN_ACCESS, "ftlEnds", listOf(PIN_LIST), PIN_OBJECT, 2, """
        new-instance v0, $ends
        invoke-direct {v0, p0}, $ends-><init>(Ljava/util/List;)V
        return-object v0
    """)
    addStaticMethod(PIN_ACCESS, "ftlDispatch", listOf(PIN_OBJECT, PIN_OBJECT), "V", 2, """
        check-cast p0, ${store.base}
        check-cast p1, ${store.dispatchParameter}
        invoke-virtual {p0, p1}, ${store.base}->dispatch(${store.dispatchParameter})V
        return-void
    """)
}

internal fun BytecodePatchContext.installExtensionPin() {
    val function0 = resolveComposeTypes().function0
    val function2 = pinFunction2()
    val model = resolvePinModel()
    val store = resolvePinStore()
    val button = resolvePinButton()

    hookAddPinned()
    val ends = hookCapture()
    hookDispatcher()
    hookExtensionClick(function0)
    hookLongClick(function0)

    installPinAccessors(model, store, button, ends, function0, function2)

    pinImplements(PIN_CLICK, function0)
    pinImplements(PIN_LONG, function0)
    pinImplements(PIN_OBSERVER, store.function1)
    pinImplements(PIN_EVENT, button.event)

    val continuation = resolvePinContinuation()
    if (continuation != null) {
        pinImplements(PIN_CONT, continuation.type)
        addPinMethod(PIN_CONT, "getContext", continuation.contextType, 2, """
            sget-object v0, ${fieldString(continuation.contextInstance)}
            return-object v0
        """)
    }
}
