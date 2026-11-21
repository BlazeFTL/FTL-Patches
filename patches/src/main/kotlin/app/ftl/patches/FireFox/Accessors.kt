/*
 * Obfuscation-proof access to Android-Components tab state.
 *
 * Nightly now ships R8-renamed Compose/Kotlin/Android-Components classes (Composer, Function0, Unit,
 * BrowserState, TabSessionState, ContentState, Store...). Only Fenix classes keep names.
 * Nothing here pins an obfuscated name. Everything is resolved from stable anchors:
 *   - Kotlin data class toString() literals:
 *       "BrowserState(tabs=" / ", selectedTabId="
 *       "TabSessionState(id=" / ", content="
 *       "ContentState(url="
 *       "RemoveTabsAction(tabIds="
 *   - Fenix class Core.getStore() return type, walked up its superclasses for getState/dispatch.
 *   - TabManagerFloatingToolbarKt static helper returning Kotlin Unit (Unit.INSTANCE field).
 * Typed accessor methods are generated into TabAccess (extension) and called reflectively from Java.
 *
 * HOW TO FIND AGAIN IN A NEW BUILD (if a fingerprint here fails):
 *   BrowserState / TabSessionState / ContentState : search the dump for the toString literals above.
 *   Store class                                   : Core.getStore() return type; getState/dispatch are on its superclass.
 *   Kotlin Unit                                   : any static method returning it ends with sget-object <Unit>;->a:<Unit>; return-object.
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
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter

internal const val TAB_ACCESS = "Lapp/ftl/extension/firefox/TabAccess;"

private const val ACC_OBJ = "Ljava/lang/Object;"
private const val ACC_STRING = "Ljava/lang/String;"
private const val ACC_LIST = "Ljava/util/List;"
private const val ACC_SET = "Ljava/util/Set;"
private const val ACC_CORE = "Lorg/mozilla/fenix/components/Core;"
private const val ACC_TOOLBAR_KT = "Lorg/mozilla/fenix/tabstray/ui/fab/TabManagerFloatingToolbarKt;"
private const val ACC_TAB_LAYOUT_KT = "Lorg/mozilla/fenix/tabstray/ui/tabpage/TabLayoutKt;"
private const val ACC_LIST_REORDER = "Lorg/mozilla/fenix/tabstray/browser/compose/ListReorderState;"
private const val ACC_GRID_REORDER = "Lorg/mozilla/fenix/tabstray/browser/compose/legacy/GridReorderState;"
private const val ACC_LIST_STATE_KT = "Lorg/mozilla/fenix/compose/ext/LazyListStateKt;"

internal fun fieldString(reference: FieldReference) =
    "${reference.definingClass}->${reference.name}:${reference.type}"

internal fun BytecodePatchContext.addStaticMethod(
    owner: String,
    name: String,
    parameters: List<String>,
    returnType: String,
    registers: Int,
    smali: String,
) {
    val holder = mutableClassDefBy(owner)
    holder.methods.removeAll { it.name == name }
    val method = ImmutableMethod(
        owner,
        name,
        parameters.map { ImmutableMethodParameter(it, null, null) },
        returnType,
        AccessFlags.PUBLIC.value or AccessFlags.STATIC.value,
        null,
        null,
        MutableMethodImplementation(registers),
    ).toMutable()
    method.addInstructionsWithLabels(0, smali.trimIndent())
    holder.methods.add(method)
}

private object BrowserStateStringFingerprint : Fingerprint(
    name = "toString",
    returnType = ACC_STRING,
    parameters = listOf(),
    filters = listOf(string("BrowserState(tabs="), string(", selectedTabId=")),
)

private object TabStateStringFingerprint : Fingerprint(
    name = "toString",
    returnType = ACC_STRING,
    parameters = listOf(),
    filters = listOf(string("TabSessionState(id="), string(", content=")),
)

private object ContentStateStringFingerprint : Fingerprint(
    name = "toString",
    returnType = ACC_STRING,
    parameters = listOf(),
    filters = listOf(string("ContentState(url=")),
)

private object RemoveTabsStringFingerprint : Fingerprint(
    name = "toString",
    returnType = ACC_STRING,
    parameters = listOf(),
    filters = listOf(string("RemoveTabsAction(tabIds=")),
)

internal class TabModel(
    val state: String,
    val tabs: String,
    val selected: String,
    val tab: String,
    val tabId: String,
    val tabContent: String,
    val contentUrl: String,
    val contentPrivate: String,
)

private fun BytecodePatchContext.fieldAfter(fingerprint: Fingerprint, match: Int, opcode: Opcode): FieldReference {
    val method = fingerprint.method
    val instructions = method.implementation!!.instructions.toList()
    val start = fingerprint.instructionMatches[match].index
    for (i in start until instructions.size) {
        val instruction = instructions[i]
        if (instruction.opcode == opcode) {
            return (instruction as ReferenceInstruction).reference as FieldReference
        }
    }
    throw PatchException("No $opcode after toString literal in ${method.definingClass}")
}

internal fun BytecodePatchContext.resolveTabModel(): TabModel {
    val browser = BrowserStateStringFingerprint
    val tabs = fieldAfter(browser, 0, Opcode.IGET_OBJECT)
    val selected = fieldAfter(browser, 1, Opcode.IGET_OBJECT)
    val tabState = TabStateStringFingerprint
    val tabId = fieldAfter(tabState, 0, Opcode.IGET_OBJECT)
    val tabContent = fieldAfter(tabState, 1, Opcode.IGET_OBJECT)
    val contentState = ContentStateStringFingerprint
    val url = fieldAfter(contentState, 0, Opcode.IGET_OBJECT)
    val isPrivate = fieldAfter(contentState, 0, Opcode.IGET_BOOLEAN)

    if (tabs.type != ACC_LIST || selected.type != ACC_STRING || tabId.type != ACC_STRING ||
        url.type != ACC_STRING || isPrivate.type != "Z"
    ) {
        throw PatchException("Unexpected tab model field types: $tabs $selected $tabId $url $isPrivate")
    }
    if (tabContent.type != url.definingClass || tabContent.definingClass != tabId.definingClass) {
        throw PatchException("Tab content field does not link TabSessionState to ContentState")
    }
    return TabModel(
        state = browser.method.definingClass,
        tabs = fieldString(tabs),
        selected = fieldString(selected),
        tab = tabState.method.definingClass,
        tabId = fieldString(tabId),
        tabContent = fieldString(tabContent),
        contentUrl = fieldString(url),
        contentPrivate = fieldString(isPrivate),
    )
}

internal fun BytecodePatchContext.resolveUnit(): String {
    for (method in mutableClassDefBy(ACC_TOOLBAR_KT).methods) {
        val returnType = method.returnType
        if (!returnType.startsWith("L") || returnType.startsWith("Ljava/")) continue
        val instructions = method.implementation?.instructions ?: continue
        for (instruction in instructions) {
            val reference = (instruction as? ReferenceInstruction)?.reference as? FieldReference ?: continue
            if (instruction.opcode == Opcode.SGET_OBJECT &&
                reference.definingClass == returnType && reference.type == returnType
            ) {
                return fieldString(reference)
            }
        }
    }
    throw PatchException("Kotlin Unit.INSTANCE not found in TabManagerFloatingToolbarKt")
}

private fun MutableMethod.isStoreGetter(): Boolean =
    parameterTypes.isEmpty() && returnType.startsWith("L") && !returnType.startsWith("Ljava/")

private fun BytecodePatchContext.resolveStore(): Triple<String, String, String> {
    val store = mutableClassDefBy(ACC_CORE).methods
        .firstOrNull { it.name == "getStore" && it.parameterTypes.isEmpty() }?.returnType
        ?: throw PatchException("Core.getStore() not found")

    var state: String? = null
    var dispatch: String? = null
    var type: String? = store
    while (type != null && type != ACC_OBJ && (state == null || dispatch == null)) {
        val definition = mutableClassDefBy(type)
        if (state == null) {
            definition.methods.firstOrNull { it.name == "getState" && it.isStoreGetter() }?.let {
                state = "${it.definingClass}->getState()${it.returnType}"
            }
        }
        if (dispatch == null) {
            definition.methods.firstOrNull {
                it.name == "dispatch" && it.parameterTypes.size == 1 && it.returnType == "V"
            }?.let { dispatch = "${it.definingClass}->dispatch(${it.parameterTypes[0]})V" }
        }
        type = definition.superclass
    }
    return Triple(store, state ?: throw PatchException("Store.getState not found on $store"),
        dispatch ?: throw PatchException("Store.dispatch not found on $store"))
}

internal fun BytecodePatchContext.installTabAccessors() {
    val model = resolveTabModel()
    val unit = resolveUnit()
    val (store, getState, dispatch) = resolveStore()

    val removeType = RemoveTabsStringFingerprint.method.definingClass
    val hasConstructor = mutableClassDefBy(removeType).methods.any {
        it.name == "<init>" && it.parameterTypes.map { type -> type.toString() } == listOf(ACC_LIST, ACC_SET)
    }
    if (!hasConstructor) throw PatchException("RemoveTabsAction(List, Set) constructor not found in $removeType")

    addStaticMethod(TAB_ACCESS, "ftlTabs", listOf(ACC_OBJ), ACC_LIST, 2, """
        check-cast p0, ${model.state}
        iget-object v0, p0, ${model.tabs}
        return-object v0
    """)
    addStaticMethod(TAB_ACCESS, "ftlSelected", listOf(ACC_OBJ), ACC_STRING, 2, """
        check-cast p0, ${model.state}
        iget-object v0, p0, ${model.selected}
        return-object v0
    """)
    addStaticMethod(TAB_ACCESS, "ftlTabId", listOf(ACC_OBJ), ACC_STRING, 2, """
        check-cast p0, ${model.tab}
        iget-object v0, p0, ${model.tabId}
        return-object v0
    """)
    addStaticMethod(TAB_ACCESS, "ftlTabUrl", listOf(ACC_OBJ), ACC_STRING, 2, """
        check-cast p0, ${model.tab}
        iget-object v0, p0, ${model.tabContent}
        iget-object v0, v0, ${model.contentUrl}
        return-object v0
    """)
    addStaticMethod(TAB_ACCESS, "ftlTabPrivate", listOf(ACC_OBJ), "Z", 2, """
        check-cast p0, ${model.tab}
        iget-object v0, p0, ${model.tabContent}
        iget-boolean v0, v0, ${model.contentPrivate}
        return v0
    """)
    addStaticMethod(TAB_ACCESS, "ftlState", listOf(ACC_OBJ), ACC_OBJ, 2, """
        check-cast p0, $store
        invoke-virtual {p0}, $getState
        move-result-object v0
        return-object v0
    """)
    addStaticMethod(TAB_ACCESS, "ftlRemove", listOf(ACC_OBJ, ACC_LIST, ACC_SET), "V", 4, """
        check-cast p0, $store
        new-instance v0, $removeType
        invoke-direct {v0, p1, p2}, $removeType-><init>(Ljava/util/List;Ljava/util/Set;)V
        invoke-virtual {p0, v0}, $dispatch
        return-void
    """)
    addStaticMethod(TAB_ACCESS, "ftlUnit", listOf(), ACC_OBJ, 1, """
        sget-object v0, $unit
        return-object v0
    """)
}


private object FabSignatureFingerprint : Fingerprint(
    definingClass = ACC_TOOLBAR_KT,
    name = "FloatingToolbarFAB",
)

internal class ComposeTypes(val composer: String, val function0: String)

// FloatingToolbarFAB(TabsTrayState, F0, F0, F0, F0, Composer, I) is a Fenix method, so its signature names
// the obfuscated Composer (param 5) and Function0 (param 1) types.
internal fun BytecodePatchContext.resolveComposeTypes(): ComposeTypes {
    val parameters = FabSignatureFingerprint.method.parameterTypes.map { it.toString() }
    if (parameters.size != 7) throw PatchException("Unexpected FloatingToolbarFAB signature: $parameters")
    return ComposeTypes(composer = parameters[5], function0 = parameters[1])
}

private fun BytecodePatchContext.constructorParameter(owner: String, index: Int): String =
    mutableClassDefBy(owner).methods.firstOrNull { it.name == "<init>" }
        ?.parameterTypes?.getOrNull(index)?.toString()
        ?: throw PatchException("Constructor parameter $index not found in $owner")

private fun isObject(type: String) = type.startsWith("L") && !type.startsWith("Ljava/")

private fun BytecodePatchContext.layoutGetter(state: String): Pair<String, String> {
    val getter = mutableClassDefBy(state).methods.firstOrNull {
        !AccessFlags.STATIC.isSet(it.accessFlags) && it.parameterTypes.isEmpty() &&
            it.name != "<init>" && isObject(it.returnType)
    } ?: throw PatchException("layoutInfo getter not found in $state")
    return "$state->${getter.name}()${getter.returnType}" to getter.returnType
}

private fun BytecodePatchContext.noArg(owner: String, what: String, match: (MutableMethod) -> Boolean): String {
    val method = mutableClassDefBy(owner).methods.firstOrNull { it.parameterTypes.isEmpty() && match(it) }
        ?: throw PatchException("$what not found in $owner")
    return "$owner->${method.name}()${method.returnType}"
}

private fun BytecodePatchContext.isEnum(type: String): Boolean =
    isObject(type) && runCatching { AccessFlags.ENUM.isSet(mutableClassDefBy(type).accessFlags) }.getOrDefault(false)

// Lazy list/grid state, layout info and item info are R8-renamed. They are found from Fenix classes that
// name them: ListReorderState/GridReorderState constructors (state type), LazyListStateKt.isItemPartiallyVisible
// (item info type), TabLayoutScrollHelper (ScrollableState type) and TabManagerFloatingToolbarKt (Composer).
// Members are picked by shape: layoutInfo = only no-arg object getter of the state, items = List getter of the
// layout info, orientation = enum getter, request = instance (II)V, dispatchRawDelta = (F)F of ScrollableState,
// item size = the (I) getter that is not getIndex/getOffset, composite key = no-arg J of Composer.
internal fun BytecodePatchContext.installScrollAccessors() {
    val types = resolveComposeTypes()
    val list = constructorParameter(ACC_LIST_REORDER, 0)
    val grid = constructorParameter(ACC_GRID_REORDER, 0)
    val item = mutableClassDefBy(ACC_LIST_STATE_KT).methods
        .firstOrNull { it.name == "isItemPartiallyVisible" }?.parameterTypes?.getOrNull(1)?.toString()
        ?: throw PatchException("LazyListItemInfo type not found")

    val (listLayoutCall, listLayout) = layoutGetter(list)
    val (gridLayoutCall, gridLayout) = layoutGetter(grid)
    val listItems = noArg(listLayout, "visibleItemsInfo") { it.returnType == "Ljava/util/List;" }
    val gridItems = noArg(gridLayout, "visibleItemsInfo") { it.returnType == "Ljava/util/List;" }
    val orientation = noArg(listLayout, "orientation") { isEnum(it.returnType) }
    val itemSize = noArg(item, "item size") {
        it.returnType == "I" && it.name != "getIndex" && it.name != "getOffset"
    }

    val request = mutableClassDefBy(list).methods.firstOrNull {
        !AccessFlags.STATIC.isSet(it.accessFlags) && it.name != "<init>" && it.returnType == "V" &&
            it.parameterTypes.map { type -> type.toString() } == listOf("I", "I")
    } ?: throw PatchException("requestScrollToItem(II)V not found in $list")

    val helper = mutableClassDefBy(ACC_TAB_LAYOUT_KT).methods
        .firstOrNull { it.name.startsWith("TabLayoutScrollHelper") && it.parameterTypes.size == 7 }
        ?: throw PatchException("TabLayoutScrollHelper not found")
    val scrollable = helper.parameterTypes.map { it.toString() }
        .firstOrNull { isObject(it) && it != types.composer }
        ?: throw PatchException("ScrollableState parameter not found")
    val raw = mutableClassDefBy(scrollable).methods.firstOrNull {
        it.returnType == "F" && it.parameterTypes.map { type -> type.toString() } == listOf("F")
    } ?: throw PatchException("dispatchRawDelta(F)F not found in $scrollable")

    val compositeKey = noArg(types.composer, "getCompositeKeyHashCode") { it.returnType == "J" }

    addStaticMethod(TAB_ACCESS, "ftlScrollItems", listOf(ACC_OBJ), ACC_LIST, 2, """
        instance-of v0, p0, $list
        if-eqz v0, :ftl_grid
        check-cast p0, $list
        invoke-virtual {p0}, $listLayoutCall
        move-result-object v0
        invoke-interface {v0}, $listItems
        move-result-object v0
        return-object v0
        :ftl_grid
        instance-of v0, p0, $grid
        if-eqz v0, :ftl_none
        check-cast p0, $grid
        invoke-virtual {p0}, $gridLayoutCall
        move-result-object v0
        invoke-interface {v0}, $gridItems
        move-result-object v0
        return-object v0
        :ftl_none
        const/4 v0, 0x0
        return-object v0
    """)
    addStaticMethod(TAB_ACCESS, "ftlScrollOrientation", listOf(ACC_OBJ), "I", 2, """
        instance-of v0, p0, $list
        if-eqz v0, :ftl_none
        check-cast p0, $list
        invoke-virtual {p0}, $listLayoutCall
        move-result-object v0
        invoke-interface {v0}, $orientation
        move-result-object v0
        invoke-virtual {v0}, Ljava/lang/Enum;->ordinal()I
        move-result v0
        return v0
        :ftl_none
        const/4 v0, -0x1
        return v0
    """)
    addStaticMethod(TAB_ACCESS, "ftlScrollRequest", listOf(ACC_OBJ, "I"), "Z", 3, """
        instance-of v0, p0, $list
        if-eqz v0, :ftl_no
        check-cast p0, $list
        const/4 v0, 0x0
        invoke-virtual {p0, p1, v0}, $list->${request.name}(II)V
        const/4 v0, 0x1
        return v0
        :ftl_no
        const/4 v0, 0x0
        return v0
    """)
    addStaticMethod(TAB_ACCESS, "ftlScrollRaw", listOf(ACC_OBJ, "F"), "F", 3, """
        check-cast p0, $scrollable
        invoke-interface {p0, p1}, $scrollable->${raw.name}(F)F
        move-result v0
        return v0
    """)
    addStaticMethod(TAB_ACCESS, "ftlItemSize", listOf(ACC_OBJ), "I", 2, """
        check-cast p0, $item
        invoke-interface {p0}, $itemSize
        move-result v0
        return v0
    """)
    addStaticMethod(TAB_ACCESS, "ftlComposerKey", listOf(ACC_OBJ), "J", 3, """
        check-cast p0, ${types.composer}
        invoke-interface {p0}, $compositeKey
        move-result-wide v0
        return-wide v0
    """)
}
