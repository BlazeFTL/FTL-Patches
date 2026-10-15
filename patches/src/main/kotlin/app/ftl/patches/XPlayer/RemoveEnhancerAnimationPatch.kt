package app.ftl.patches.xplayer

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.HiddenApiRestriction
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction21t
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
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

private val logger = Logger.getLogger("RemoveEnhancerAnimation")

private const val PREFS = "Lapp/ftl/extension/xplayer/ModPrefs;"
private const val XVIDEO = "Lcom/inshot/inplayer/widget/XVideoView;"
private const val STRING_TYPE = "Ljava/lang/String;"
private const val MAP_TYPE = "Ljava/util/Map;"

private const val APPLY_HELPER = "fxApply"
private const val LIVE_HELPER = "fxLive"
private const val LOAD_HELPER = "fxLoad"

internal object EnhancerApplyFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("I"),
    filters = listOf(
        string("ModeStart"),
        fieldAccess(
            definingClass = "this",
            type = "I",
            opcode = Opcode.IPUT,
            location = InstructionLocation.MatchAfterWithin(12)
        ),
        string("f3o8RJ4xc", location = InstructionLocation.MatchAfterImmediately())
    )
)

internal object EnhancerReloadFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("Z"),
    filters = listOf(
        methodCall(
            definingClass = XVIDEO,
            name = "setOnM3u8LoadListener",
            opcode = Opcode.INVOKE_VIRTUAL
        ),
        methodCall(
            definingClass = XVIDEO,
            parameters = listOf(STRING_TYPE, MAP_TYPE, "I"),
            returnType = "V",
            opcode = Opcode.INVOKE_VIRTUAL,
            location = InstructionLocation.MatchAfterWithin(12)
        )
    )
)

private class ReloadRefs(
    val enhancerOff: FieldReference,
    val mode: FieldReference,
    val customMode: FieldReference,
    val view: FieldReference,
    val forced: FieldReference,
    val seek: FieldReference,
    val staticMode: FieldReference,
    val setMode: MethodReference,
    val load: MethodReference
) {
    fun log() = listOf(
        "enhancer-off flag" to enhancerOff.desc(),
        "enhancer mode" to mode.desc(),
        "custom mode" to customMode.desc(),
        "video view" to view.desc(),
        "forced-mode flag" to forced.desc(),
        "seek position" to seek.desc(),
        "default mode (static)" to staticMode.desc(),
        "mode setter" to setMode.desc(),
        "video loader" to load.desc()
    )
}

private fun Instruction.fieldOf() = (this as? ReferenceInstruction)?.reference as? FieldReference

private fun Instruction.methodOf() = (this as? ReferenceInstruction)?.reference as? MethodReference

private fun MethodReference.paramList() = parameterTypes.map { it.toString() }

private fun newInstanceMethod(host: String, name: String, params: List<String>, registers: Int): MutableMethod =
    ImmutableMethod(
        host,
        name,
        params.map { ImmutableMethodParameter(it, emptySet<ImmutableAnnotation>(), null) },
        "V",
        AccessFlags.PRIVATE.value,
        emptySet<ImmutableAnnotation>(),
        emptySet<HiddenApiRestriction>(),
        ImmutableMethodImplementation(
            registers,
            listOf<ImmutableInstruction>(ImmutableInstruction10x(Opcode.RETURN_VOID)),
            emptyList<ImmutableTryBlock>(),
            emptyList<ImmutableDebugItem>()
        )
    ).toMutable()

private fun resolveReload(method: MutableMethod, listenerIndex: Int, loadIndex: Int): ReloadRefs {
    val host = method.definingClass
    val instructions = method.implementation!!.instructions

    val setModeIndex = (listenerIndex - 1 downTo 0).firstOrNull { i ->
        val ref = instructions[i].methodOf()
        instructions[i].opcode == Opcode.INVOKE_VIRTUAL &&
            ref != null &&
            ref.definingClass == XVIDEO &&
            ref.returnType == "V" &&
            ref.paramList() == listOf("I", "I")
    } ?: throw PatchException("Enhancer reload: mode setter call not found before listener")

    val setMode = instructions[setModeIndex].methodOf()!!
    val load = instructions[loadIndex].methodOf()
        ?: throw PatchException("Enhancer reload: loader call is not a method reference")

    fun fieldsBefore(end: Int, start: Int, opcode: Opcode, accept: (FieldReference) -> Boolean) =
        (start until end).mapNotNull { i ->
            instructions[i].takeIf { it.opcode == opcode }?.fieldOf()?.takeIf(accept)
        }

    val booleans = fieldsBefore(setModeIndex, 0, Opcode.IGET_BOOLEAN) { it.definingClass == host && it.type == "Z" }
    val ints = fieldsBefore(setModeIndex, 0, Opcode.IGET) { it.definingClass == host && it.type == "I" }
    val views = fieldsBefore(setModeIndex, 0, Opcode.IGET_OBJECT) { it.definingClass == host && it.type == XVIDEO }
    val statics = fieldsBefore(setModeIndex, 0, Opcode.SGET) { it.definingClass == XVIDEO && it.type == "I" }
    val seeks = fieldsBefore(loadIndex, setModeIndex, Opcode.IGET) { it.definingClass == host && it.type == "I" }

    if (booleans.size < 3) throw PatchException("Enhancer reload: expected 3+ boolean reads, found ${booleans.size}")
    if (ints.size < 3) throw PatchException("Enhancer reload: expected 3+ int reads, found ${ints.size}")
    if (views.isEmpty()) throw PatchException("Enhancer reload: video view field not found")
    if (statics.isEmpty()) throw PatchException("Enhancer reload: default mode field not found")
    if (seeks.isEmpty()) throw PatchException("Enhancer reload: seek position field not found")

    val refs = ReloadRefs(
        enhancerOff = booleans[booleans.size - 2],
        mode = ints[ints.size - 2],
        customMode = ints.last(),
        view = views.last(),
        forced = booleans.last(),
        seek = seeks.last(),
        staticMode = statics.last(),
        setMode = setMode,
        load = load
    )

    val readIndex = (0 until setModeIndex).firstOrNull {
        instructions[it].opcode == Opcode.IGET_BOOLEAN && instructions[it].fieldOf()?.desc() == refs.enhancerOff.desc()
    } ?: throw PatchException("Enhancer reload: enhancer-off read not found")

    fun at(offset: Int) = instructions.getOrNull(readIndex + offset)
        ?: throw PatchException("Enhancer reload: mode block is truncated")

    val gotoOpcodes = setOf(Opcode.GOTO, Opcode.GOTO_16, Opcode.GOTO_32)
    val shapeOk = at(1).opcode == Opcode.CONST_4 &&
        at(2).opcode == Opcode.IF_EQZ &&
        at(3).opcode == Opcode.MOVE &&
        at(4).opcode in gotoOpcodes &&
        at(5).opcode == Opcode.IGET && at(5).fieldOf()?.desc() == refs.mode.desc() &&
        at(6).opcode == Opcode.CONST_4 &&
        at(7).opcode == Opcode.IF_NEZ &&
        at(8).opcode == Opcode.IGET && at(8).fieldOf()?.desc() == refs.customMode.desc() &&
        at(9).opcode == Opcode.IF_LEZ
    if (!shapeOk) throw PatchException("Enhancer reload: mode selection block has unexpected shape")

    return refs
}

private fun liveSmali(refs: ReloadRefs) = """
    iget-boolean v0, p0, ${refs.enhancerOff.desc()}
    const/4 v1, 0x0
    if-eqz v0, :enabled
    move v0, v1
    goto :modeKnown
    :enabled
    iget v0, p0, ${refs.mode.desc()}
    :modeKnown
    if-nez v0, :resolved
    iget v2, p0, ${refs.customMode.desc()}
    if-lez v2, :resolved
    const/4 v3, 0x1
    if-ne v2, v3, :notFirst
    const/4 v0, 0x7
    goto :resolved
    :notFirst
    const/4 v3, 0x2
    if-ne v2, v3, :notSecond
    const/16 v0, 0x8
    goto :resolved
    :notSecond
    const/4 v3, 0x3
    if-ne v2, v3, :resolved
    const/16 v0, 0x9
    :resolved
    iget-object v2, p0, ${refs.view.desc()}
    iget-boolean v3, p0, ${refs.forced.desc()}
    if-eqz v3, :defaultMode
    const/4 v3, 0x3
    goto :modeSet
    :defaultMode
    sget v3, ${refs.staticMode.desc()}
    :modeSet
    iget v1, p0, ${refs.seek.desc()}
    invoke-virtual {v2, v1}, $XVIDEO->setSeekWhenPrepared(I)V
    invoke-virtual {v2, v3, v0}, ${refs.setMode.desc()}
    return-void
"""

private fun applySmali(host: String, refs: ReloadRefs, stateSetter: MethodReference) = """
    invoke-static {}, $PREFS->enhancerFast()Z
    move-result v0
    if-eqz v0, :stock
    iget-object v0, p0, ${refs.view.desc()}
    invoke-virtual {v0}, $XVIDEO->getCurrentPosition()I
    move-result v0
    iput v0, p0, ${refs.seek.desc()}
    iget-object v0, p0, ${refs.view.desc()}
    invoke-virtual {v0}, $XVIDEO->isPlaying()Z
    move-result v0
    if-eqz v0, :paused
    invoke-direct {p0}, $host->$LIVE_HELPER()V
    return-void
    :paused
    const/4 v0, 0x1
    invoke-static {v0}, $PREFS->setEnhancerPending(Z)V
    iget-object v0, p0, ${refs.view.desc()}
    invoke-virtual {v0}, $XVIDEO->getCurrentPosition()I
    move-result v0
    iput v0, p0, ${refs.seek.desc()}
    :stock
    invoke-direct {p0, p1}, ${stateSetter.desc()}
    return-void
"""

private fun loadSmali(refs: ReloadRefs) = """
    invoke-static {}, $PREFS->enhancerPending()Z
    move-result v0
    if-nez v0, :seekOnly
    invoke-virtual {p1, p2, p3, p4}, ${refs.load.desc()}
    goto :done
    :seekOnly
    iget v0, p0, ${refs.seek.desc()}
    invoke-virtual {p1, v0}, $XVIDEO->setSeekWhenPrepared(I)V
    :done
    const/4 v0, 0x0
    invoke-static {v0}, $PREFS->setEnhancerPending(Z)V
    return-void
"""

@Suppress("unused")
val removeEnhancerAnimationPatch = bytecodePatch(
    name = "Remove Video Enhancer Animation",
    description = "Skips the Visual Enhancer enable animation and applies effects instantly without reloading the video. Toggle in Mod Settings."
) {
    compatibleWith(XPLAYER_TARGET)

    dependsOn(modSettingsPatch)

    execute {
        val applyMatch = EnhancerApplyFingerprint.matchAll().let {
            it.singleOrNull() ?: throw PatchException("Enhancer apply: expected 1 match, found ${it.size}")
        }
        val reloadMatch = EnhancerReloadFingerprint.matchAll().let {
            it.singleOrNull() ?: throw PatchException("Enhancer reload: expected 1 match, found ${it.size}")
        }

        val applyMethod = applyMatch.method
        val reloadMethod = reloadMatch.method
        val host = applyMethod.definingClass
        if (reloadMethod.definingClass != host) {
            throw PatchException("Enhancer apply and reload live in different classes")
        }

        val refs = resolveReload(
            reloadMethod,
            reloadMatch.instructionMatches[0].index,
            reloadMatch.instructionMatches[1].index
        )
        refs.log().forEach { (label, ref) -> logger.info("Remove Enhancer Animation: $label = $ref") }

        val applyInstructions = applyMethod.implementation!!.instructions
        val gateIndex = applyMatch.instructionMatches[2].index

        val branchIndex = (gateIndex until applyInstructions.size - 1).firstOrNull { i ->
            val first = applyInstructions[i]
            val second = applyInstructions[i + 1]
            first.opcode == Opcode.IF_EQZ && second.opcode == Opcode.IF_EQZ &&
                (first as BuilderInstruction21t).target.location.index ==
                (second as BuilderInstruction21t).target.location.index
        } ?: throw PatchException("Enhancer apply: animation branch not found")

        val wasOffRegister = (applyInstructions[branchIndex] as OneRegisterInstruction).registerA
        val paramRegister = (applyInstructions[branchIndex + 1] as OneRegisterInstruction).registerA
        if (paramRegister != applyMethod.implementation!!.registerCount - 1) {
            throw PatchException("Enhancer apply: animation branch does not test the mode parameter")
        }

        val instantIndex = (applyInstructions[branchIndex] as BuilderInstruction21t).target.location.index
        val instantCall = applyInstructions[instantIndex]
        val instantFive = instantCall as? FiveRegisterInstruction
        val stateSetter = instantCall.methodOf()
        if (instantCall.opcode != Opcode.INVOKE_DIRECT ||
            instantFive == null ||
            stateSetter == null ||
            stateSetter.definingClass != host ||
            stateSetter.returnType != "V" ||
            stateSetter.paramList() != listOf("Z") ||
            instantFive.registerCount != 2
        ) {
            throw PatchException("Enhancer apply: instant path does not start with the state setter call")
        }
        val nextCall = applyInstructions[instantIndex + 1].methodOf()
        if (applyInstructions[instantIndex + 1].opcode != Opcode.INVOKE_DIRECT ||
            nextCall == null ||
            nextCall.definingClass != host ||
            nextCall.returnType != "V" ||
            nextCall.paramList().isNotEmpty()
        ) {
            throw PatchException("Enhancer apply: instant path is missing the refresh call")
        }
        val flagRegister = instantFive.registerD

        val classDef = applyMatch.classDef
        classDef.methods.add(newInstanceMethod(host, LIVE_HELPER, emptyList(), 5).apply { fillWith(liveSmali(refs)) })
        classDef.methods.add(
            newInstanceMethod(host, APPLY_HELPER, listOf("Z"), 3).apply { fillWith(applySmali(host, refs, stateSetter)) }
        )
        classDef.methods.add(
            newInstanceMethod(
                host,
                LOAD_HELPER,
                listOf(XVIDEO, STRING_TYPE, MAP_TYPE, "I"),
                6
            ).apply { fillWith(loadSmali(refs)) }
        )

        applyMethod.replaceInstruction(
            instantIndex,
            "invoke-direct {p0, v$flagRegister}, $host->$APPLY_HELPER(Z)V"
        )
        applyMethod.addInstructions(
            gateIndex,
            """
                invoke-static/range {v$wasOffRegister .. v$wasOffRegister}, $PREFS->enhancerAnim(I)I
                move-result v$wasOffRegister
            """
        )

        val reloadInstructions = reloadMethod.implementation!!.instructions
        val loadIndex = reloadMatch.instructionMatches[1].index
        val loadCall = reloadInstructions[loadIndex] as? FiveRegisterInstruction
            ?: throw PatchException("Enhancer reload: loader call is not a five-register invoke")
        if (loadCall.registerCount != 4) {
            throw PatchException("Enhancer reload: unexpected loader call arity ${loadCall.registerCount}")
        }
        reloadMethod.replaceInstruction(
            loadIndex,
            "invoke-direct {p0, v${loadCall.registerC}, v${loadCall.registerD}, v${loadCall.registerE}, v${loadCall.registerF}}, " +
                "$host->$LOAD_HELPER($XVIDEO$STRING_TYPE$MAP_TYPE" + "I)V"
        )
    }
}
