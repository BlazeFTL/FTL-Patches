package app.ftl.patches.xplayer

import app.morphe.patcher.patch.PatchException
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.WideLiteralInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

internal const val TEXT_VIEW = "Landroid/widget/TextView;"
internal const val VIEW = "Landroid/view/View;"
internal const val STRING = "Ljava/lang/String;"
internal const val OBJECT = "Ljava/lang/Object;"
internal const val CHAR_SEQUENCE = "Ljava/lang/CharSequence;"
internal const val LIST = "Ljava/util/List;"
internal const val SET = "Ljava/util/Set;"
internal const val DB_BEAN = "Lcom/inshot/xplayer/content/RecentMediaStorage\$DBBean;"
private const val CLICK_LISTENER = "Landroid/view/View\$OnClickListener;"

internal fun FieldReference.desc() = "$definingClass->$name:$type"

internal fun MethodReference.desc() = "$definingClass->$name(${parameterTypes.joinToString("")})$returnType"

internal interface ClassLookup {
    fun superclassOf(type: String): String?
    fun methodBody(type: String, name: String, returnType: String): List<Instruction>?
    fun instanceFields(type: String): List<FieldReference>
}

internal class VideoRefs(
    val host: String,
    val holder: String,
    val owner: FieldReference,
    val mode: FieldReference,
    val primary: FieldReference,
    val secondary: FieldReference,
    val path: MethodReference,
    val date: MethodReference,
    val size: FieldReference,
    val formatter: MethodReference,
    val played: MethodReference,
    val itemView: FieldReference
) {
    fun log() = listOf(
        "video host" to host,
        "video holder" to holder,
        "adapter -> outer" to owner.desc(),
        "view mode" to mode.desc(),
        "info text F" to primary.desc(),
        "info text G" to secondary.desc(),
        "path getter" to path.desc(),
        "date getter" to date.desc(),
        "size field" to size.desc(),
        "size formatter" to formatter.desc(),
        "played getter" to played.desc(),
        "itemView" to itemView.desc()
    )
}

internal class FolderRefs(
    val host: String,
    val holder: String,
    val bean: String,
    val title: FieldReference,
    val name: FieldReference,
    val countView: FieldReference,
    val countGetter: MethodReference,
    val path: FieldReference,
    val row: FieldReference,
    val list: FieldReference,
    val modified: MethodReference,
    val now: FieldReference,
    val itemView: FieldReference,
    val sizeField: FieldReference,
    val formatter: MethodReference
) {
    fun log() = listOf(
        "folder host" to host,
        "folder holder" to holder,
        "folder bean" to bean,
        "name text" to title.desc(),
        "name field" to name.desc(),
        "count text" to countView.desc(),
        "count getter" to countGetter.desc(),
        "path field" to path.desc(),
        "row root" to row.desc(),
        "videos list" to list.desc(),
        "date getter" to modified.desc(),
        "now field" to now.desc(),
        "itemView" to itemView.desc(),
        "size field (video)" to sizeField.desc(),
        "size formatter (video)" to formatter.desc()
    )
}

private fun Instruction.fieldRef() = (this as? ReferenceInstruction)?.reference as? FieldReference

private fun Instruction.methodRef() = (this as? ReferenceInstruction)?.reference as? MethodReference

private fun Instruction.isCall(
    opcode: Opcode? = null,
    definingClass: String? = null,
    name: String? = null,
    returnType: String? = null,
    params: List<String>? = null
): Boolean {
    val ref = methodRef() ?: return false
    return (opcode == null || this.opcode == opcode) &&
        (definingClass == null || ref.definingClass == definingClass) &&
        (name == null || ref.name == name) &&
        (returnType == null || ref.returnType == returnType) &&
        (params == null || ref.parameterTypes.map { it.toString() } == params)
}

private fun Instruction.registerA() = (this as OneRegisterInstruction).registerA

private fun Instruction.registerB() = (this as TwoRegisterInstruction).registerB

private fun Instruction.callArg(index: Int): Int {
    val five = this as FiveRegisterInstruction
    return when (index) {
        0 -> five.registerC
        1 -> five.registerD
        else -> throw IllegalArgumentException("arg $index")
    }
}

private fun List<Instruction>.windows(size: Int, test: (List<Instruction>) -> Boolean): List<Pair<Int, List<Instruction>>> =
    (0..this.size - size).map { it to subList(it, it + size) }.filter { test(it.second) }

private fun <T> List<T>.only(what: String): T =
    singleOrNull() ?: throw PatchException("$what: expected exactly 1 match, found $size")

private fun <T> List<T>.firstOrFail(what: String): T =
    firstOrNull() ?: throw PatchException("$what: landmark not found")

private fun requireLandmark(instructions: List<Instruction>, what: String, test: (Instruction) -> Boolean) {
    if (instructions.none(test)) throw PatchException("Landmark missing: $what")
}

internal fun checkVideoLandmarks(instructions: List<Instruction>) {
    requireLandmark(instructions, "ProgressBar.setMax") {
        it.isCall(definingClass = "Landroid/widget/ProgressBar;", name = "setMax")
    }
    requireLandmark(instructions, "ProgressBar.setProgress") {
        it.isCall(definingClass = "Landroid/widget/ProgressBar;", name = "setProgress")
    }
    requireLandmark(instructions, "const-wide/16 0x64") {
        it.opcode == Opcode.CONST_WIDE_16 && (it as? WideLiteralInstruction)?.wideLiteral == 0x64L
    }
}

internal fun checkFolderLandmarks(instructions: List<Instruction>) {
    requireLandmark(instructions, "String.format(Locale,String,Object[])") {
        it.isCall(
            definingClass = STRING,
            name = "format",
            params = listOf("Ljava/util/Locale;", STRING, "[Ljava/lang/Object;")
        )
    }
    requireLandmark(instructions, "Locale.ENGLISH") { it.fieldRef()?.let { f -> f.definingClass == "Ljava/util/Locale;" && f.name == "ENGLISH" } == true }
    requireLandmark(instructions, "String.equalsIgnoreCase") { it.isCall(definingClass = STRING, name = "equalsIgnoreCase") }
    requireLandmark(instructions, "ImageView.setImageResource") { it.isCall(definingClass = "Landroid/widget/ImageView;", name = "setImageResource") }
    requireLandmark(instructions, "CompoundButton.setChecked") { it.isCall(definingClass = "Landroid/widget/CompoundButton;", name = "setChecked") }
}

private fun resolveItemView(
    instructions: List<Instruction>,
    holder: String,
    lookup: ClassLookup,
    known: FieldReference? = null
): FieldReference {
    val parent = lookup.superclassOf(holder) ?: throw PatchException("itemView: no superclass for $holder")
    if (known != null && known.definingClass == parent) return known
    instructions.mapNotNull { it.fieldRef() }
        .firstOrNull { it.definingClass == parent && it.type == VIEW }
        ?.let { return it }
    val candidates = lookup.instanceFields(parent).filter { it.type == VIEW }
    return candidates.only("itemView field of $parent")
}

internal fun resolveVideo(
    instructions: List<Instruction>,
    host: String,
    holder: String,
    anchorIndex: Int,
    lookup: ClassLookup
): VideoRefs {
    checkVideoLandmarks(instructions)

    val ownerWindow = instructions.windows(2) { w ->
        val a = w[0].fieldRef()
        val b = w[1].fieldRef()
        w[0].opcode == Opcode.IGET_OBJECT && w[1].opcode == Opcode.IGET &&
            a != null && b != null &&
            a.definingClass == host && b.type == "I" && b.definingClass == a.type
    }.firstOrFail("adapter outer / view mode field")
    val modeIndex = ownerWindow.first + 1
    val modeReg = instructions[modeIndex].registerA()
    val constOneReg = instructions.subList(modeIndex + 1, minOf(modeIndex + 8, instructions.size))
        .filter { it.opcode == Opcode.CONST_4 && (it as NarrowLiteralInstruction).narrowLiteral == 1 }
        .map { it.registerA() }
    val comparesToOne = instructions.subList(modeIndex + 1, minOf(modeIndex + 8, instructions.size)).any {
        (it.opcode == Opcode.IF_NE || it.opcode == Opcode.IF_EQ) &&
            it.registerA() == modeReg && it.registerB() in constOneReg
    }
    if (!comparesToOne) throw PatchException("view mode field is not compared with const 1")

    val selection = instructions.windows(8) { w ->
        val size = w[0].fieldRef()
        val f = w[3].fieldRef()
        val g = w[5].fieldRef()
        w[0].opcode == Opcode.IGET_WIDE && size != null && size.definingClass == MEDIA_FILE_INFO && size.type == "J" &&
            w[1].opcode == Opcode.INVOKE_STATIC && w[1].isCall(returnType = STRING, params = listOf("J")) &&
            w[1].callArg(0) == w[0].registerA() &&
            w[2].opcode == Opcode.MOVE_RESULT_OBJECT &&
            w[3].opcode == Opcode.IGET_OBJECT && f?.type == TEXT_VIEW &&
            w[4].isCall(Opcode.INVOKE_VIRTUAL, TEXT_VIEW, "setText", params = listOf(CHAR_SEQUENCE)) &&
            w[4].callArg(0) == w[3].registerA() && w[4].callArg(1) == w[2].registerA() &&
            w[5].opcode == Opcode.IGET_OBJECT && g?.type == TEXT_VIEW && g.definingClass == f.definingClass &&
            w[5].registerB() == w[3].registerB() &&
            w[6].opcode == Opcode.IF_EQZ && w[6].registerA() == w[5].registerA() &&
            w[7].isCall(Opcode.INVOKE_VIRTUAL, TEXT_VIEW, "setText", params = listOf(CHAR_SEQUENCE)) &&
            w[7].callArg(0) == w[5].registerA() && w[7].callArg(1) == w[2].registerA()
    }.map { it.second }
    val selectionWindow = selection.firstOrFail("selection-mode size/info text block")
    val sizeField = selection.map { it[0].fieldRef()!! }.distinctBy { it.desc() }.only("size field")
    val formatter = selection.map { it[1].methodRef()!! }.distinctBy { it.desc() }.only("size formatter")
    val primary = selectionWindow[3].fieldRef()!!
    val secondary = selectionWindow[5].fieldRef()!!

    val visibilityPair = instructions.windows(5) { w ->
        val g = w[0].fieldRef()
        val f = w[3].fieldRef()
        w[0].opcode == Opcode.IGET_OBJECT && g?.type == TEXT_VIEW &&
            w[1].opcode == Opcode.IF_EQZ &&
            w[2].isCall(definingClass = VIEW, name = "setVisibility", params = listOf("I")) &&
            w[3].opcode == Opcode.IGET_OBJECT && f?.type == TEXT_VIEW && f.definingClass == g.definingClass &&
            w[4].isCall(definingClass = VIEW, name = "setVisibility", params = listOf("I"))
    }.only("secondary info text visibility block").second
    if (visibilityPair[0].fieldRef()!!.name != secondary.name || visibilityPair[3].fieldRef()!!.name != primary.name) {
        throw PatchException("info text views disagree between selection block and visibility block")
    }
    val beforeAnchor = instructions[anchorIndex - 2]
    if (beforeAnchor.opcode != Opcode.IGET_OBJECT || beforeAnchor.fieldRef()!!.name != primary.name) {
        throw PatchException("info text view not found right before hook point")
    }

    val path = instructions.windows(3) { w ->
        w[0].isCall(Opcode.INVOKE_VIRTUAL, MEDIA_FILE_INFO, returnType = STRING, params = listOf()) &&
            w[1].opcode == Opcode.MOVE_RESULT_OBJECT &&
            w[2].isCall(definingClass = VIEW, name = "setTag", params = listOf(OBJECT))
    }.only("path getter").second[0].methodRef()!!
    requireLandmark(instructions, "path getter feeding Set.contains") { instruction ->
        instruction.isCall(definingClass = SET, name = "contains")
    }

    val date = instructions.windows(3) { w ->
        w[0].isCall(Opcode.INVOKE_VIRTUAL, MEDIA_FILE_INFO, returnType = "J", params = listOf()) &&
            w[1].opcode == Opcode.MOVE_RESULT_WIDE &&
            w[2].isCall(Opcode.INVOKE_VIRTUAL, returnType = STRING, params = listOf("J"))
    }.only("date getter").second[0].methodRef()!!

    val played = instructions
        .filter { it.isCall(Opcode.INVOKE_VIRTUAL, MEDIA_FILE_INFO, returnType = DB_BEAN, params = listOf()) }
        .map { it.methodRef()!! }
        .distinctBy { it.name }
        .only("played-state getter")

    return VideoRefs(
        host = host,
        holder = holder,
        owner = instructions[ownerWindow.first].fieldRef()!!,
        mode = instructions[modeIndex].fieldRef()!!,
        primary = primary,
        secondary = secondary,
        path = path,
        date = date,
        size = sizeField,
        formatter = formatter,
        played = played,
        itemView = resolveItemView(instructions, holder, lookup)
    )
}

internal fun resolveFolder(
    instructions: List<Instruction>,
    host: String,
    nowField: FieldReference,
    modifiedGetter: MethodReference,
    lookup: ClassLookup,
    video: VideoRefs
): FolderRefs {
    checkFolderLandmarks(instructions)
    val bean = modifiedGetter.definingClass

    val title = instructions.windows(3) { w ->
        val name = w[1].fieldRef()
        w[0].opcode == Opcode.IGET_OBJECT && w[0].fieldRef()?.type == TEXT_VIEW &&
            w[1].opcode == Opcode.IGET_OBJECT && name?.type == STRING && name.definingClass == bean &&
            w[2].isCall(Opcode.INVOKE_VIRTUAL, TEXT_VIEW, "setText", params = listOf(CHAR_SEQUENCE)) &&
            w[2].callArg(0) == w[0].registerA() && w[2].callArg(1) == w[1].registerA()
    }.only("folder name text").second

    val count = instructions.windows(6) { w ->
        w[0].isCall(Opcode.INVOKE_VIRTUAL, bean, returnType = "I", params = listOf()) &&
            w[1].opcode == Opcode.MOVE_RESULT &&
            w[2].opcode == Opcode.IGET_OBJECT && w[2].fieldRef()?.type == TEXT_VIEW &&
            w[3].isCall(Opcode.INVOKE_STATIC, STRING, "valueOf", params = listOf("I")) &&
            w[4].opcode == Opcode.MOVE_RESULT_OBJECT &&
            w[5].isCall(Opcode.INVOKE_VIRTUAL, TEXT_VIEW, "setText", params = listOf(CHAR_SEQUENCE))
    }.only("folder count text").second

    val path = instructions.windows(3) { w ->
        val onBean = w[0].fieldRef()
        val onHost = w[1].fieldRef()
        w[0].opcode == Opcode.IGET_OBJECT && onBean?.type == STRING && onBean.definingClass == bean &&
            w[1].opcode == Opcode.IGET_OBJECT && onHost?.type == STRING && onHost.definingClass == host &&
            w[2].isCall(Opcode.INVOKE_VIRTUAL, STRING, "equalsIgnoreCase", params = listOf(STRING))
    }.only("folder path field").second[0].fieldRef()!!
    val pathTagged = instructions.windows(2) { w ->
        w[0].opcode == Opcode.IGET_OBJECT && w[0].fieldRef()?.desc() == path.desc() &&
            w[1].isCall(definingClass = VIEW, name = "setTag", params = listOf(OBJECT))
    }
    if (pathTagged.isEmpty()) throw PatchException("folder path field is never passed to View.setTag")

    val row = instructions.windows(3) { w ->
        val first = w[0].fieldRef()
        val last = w[2].fieldRef()
        w[0].opcode == Opcode.IGET_OBJECT && first?.type == VIEW &&
            w[1].isCall(definingClass = VIEW, name = "setOnClickListener", params = listOf(CLICK_LISTENER)) &&
            w[2].opcode == Opcode.IGET_OBJECT && last?.name == first.name && last.definingClass == first.definingClass
    }.only("folder row root").second[0].fieldRef()!!

    val countGetter = count[0].methodRef()!!
    val fromGetter = lookup.methodBody(bean, countGetter.name, "I")
        ?.mapNotNull { it.fieldRef() }
        ?.firstOrNull { it.definingClass == bean && it.type == LIST }
    val list = fromGetter ?: lookup.instanceFields(bean).filter { it.type == LIST }.only("folder videos list field")

    val holder = title[0].fieldRef()!!.definingClass
    return FolderRefs(
        host = host,
        holder = holder,
        bean = bean,
        title = title[0].fieldRef()!!,
        name = title[1].fieldRef()!!,
        countView = count[2].fieldRef()!!,
        countGetter = countGetter,
        path = path,
        row = row,
        list = list,
        modified = modifiedGetter,
        now = nowField,
        itemView = resolveItemView(instructions, holder, lookup, video.itemView),
        sizeField = video.size,
        formatter = video.formatter
    )
}
