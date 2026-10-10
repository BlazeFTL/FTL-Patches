package app.ftl.patches.xplayer

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.removeInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableClass
import app.morphe.patcher.util.smali.toInstructions
import com.android.tools.smali.dexlib2.builder.BuilderOffsetInstruction
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.HiddenApiRestriction
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.ImmutableAnnotation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter
import com.android.tools.smali.dexlib2.immutable.ImmutableTryBlock
import com.android.tools.smali.dexlib2.immutable.debug.ImmutableDebugItem
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction10x
import java.util.logging.Logger

private val logger = Logger.getLogger("ExtraFileInfo")

internal fun newHelper(host: String, name: String, params: List<String>): MutableMethod =
    ImmutableMethod(
        host,
        name,
        params.map { ImmutableMethodParameter(it, emptySet<ImmutableAnnotation>(), null) },
        "V",
        AccessFlags.PRIVATE.value or AccessFlags.STATIC.value,
        emptySet<ImmutableAnnotation>(),
        emptySet<HiddenApiRestriction>(),
        ImmutableMethodImplementation(
            HELPER_REGISTERS,
            listOf<ImmutableInstruction>(ImmutableInstruction10x(Opcode.RETURN_VOID)),
            emptyList<ImmutableTryBlock>(),
            emptyList<ImmutableDebugItem>()
        )
    ).toMutable()

internal fun MutableMethod.fillWith(smali: String) {
    addInstructionsWithLabels(0, smali.trimIndent())
    removeInstruction(implementation!!.instructions.size - 1)
}

internal fun MutableMethod.addInstructionsAtControlFlowLabel(index: Int, smali: String) {
    val implementation = implementation!!
    val original = implementation.instructions[index]
    if (original is BuilderOffsetInstruction) {
        throw PatchException("Hook point in $definingClass->$name is a branch instruction")
    }
    val inserted = smali.toInstructions(this)
    implementation.addInstruction(index + 1, original)
    implementation.replaceInstruction(index, inserted.first())
    if (inserted.size > 1) implementation.addInstructions(index + 1, inserted.subList(1, inserted.size))
}

internal fun applyVideo(method: MutableMethod, classDef: MutableClass, anchor: Int, refs: VideoRefs) {
    classDef.methods.add(newHelper(refs.host, VIDEO_HELPER, videoHelperParams(refs)).apply { fillWith(videoHelperSmali(refs)) })
    method.addInstructionsAtControlFlowLabel(anchor, callHelper(refs.host, VIDEO_HELPER, videoHelperParams(refs)))
}

internal fun applyFolder(method: MutableMethod, classDef: MutableClass, anchor: Int, refs: FolderRefs) {
    classDef.methods.add(newHelper(refs.host, FOLDER_HELPER, folderHelperParams(refs)).apply { fillWith(folderHelperSmali(refs)) })
    method.addInstructionsAtControlFlowLabel(anchor, callHelper(refs.host, FOLDER_HELPER, folderHelperParams(refs)))
}

@Suppress("unused")
val extraFileInfoPatch = bytecodePatch(
    name = "Extra File Info",
    description = "Shows resolution, date, size and last played on video rows, and video count, size and path on folder rows. Toggle in Mod Settings."
) {
    compatibleWith(XPLAYER_TARGET)

    dependsOn(modSettingsPatch)

    execute {
        val lookup = object : ClassLookup {
            override fun superclassOf(type: String) = classDefBy(type).superclass

            override fun methodBody(type: String, name: String, returnType: String): List<Instruction>? =
                classDefBy(type).methods
                    .firstOrNull { it.name == name && it.parameterTypes.isEmpty() && it.returnType == returnType }
                    ?.implementation?.instructions?.toList()

            override fun instanceFields(type: String): List<FieldReference> =
                classDefBy(type).instanceFields.toList()
        }

        val videoMatch = VideoRowBindFingerprint.matchAll().let {
            it.singleOrNull() ?: throw PatchException("Video row bind: expected 1 match, found ${it.size}")
        }
        val folderMatch = FolderRowBindFingerprint.matchAll().let {
            it.singleOrNull() ?: throw PatchException("Folder row bind: expected 1 match, found ${it.size}")
        }

        val videoMethod = videoMatch.method
        val videoAnchor = videoMatch.instructionMatches.first().index
        val video = resolveVideo(
            videoMethod.implementation!!.instructions,
            videoMethod.definingClass,
            videoMethod.parameterTypes[0].toString(),
            videoAnchor,
            lookup
        )

        val folderMethod = folderMatch.method
        val folderMatches = folderMatch.instructionMatches
        val folderAnchor = folderMatches.first().index
        val folder = resolveFolder(
            folderMethod.implementation!!.instructions,
            folderMethod.definingClass,
            (folderMatches[1].instruction as ReferenceInstruction).reference as FieldReference,
            (folderMatches[2].instruction as ReferenceInstruction).reference as MethodReference,
            lookup,
            video
        )

        (video.log() + folder.log()).forEach { (label, ref) -> logger.info("Extra File Info: $label = $ref") }

        applyVideo(videoMethod, videoMatch.classDef, videoAnchor, video)
        applyFolder(folderMethod, folderMatch.classDef, folderAnchor, folder)
    }
}
