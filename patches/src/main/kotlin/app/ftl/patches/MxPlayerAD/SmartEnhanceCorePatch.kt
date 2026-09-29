package app.ftl.patches.mxplayerad

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstructions
import app.morphe.patcher.literal
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableClass
import app.morphe.patcher.util.proxy.mutableTypes.MutableField.Companion.toMutable
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.ImmutableField
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter

internal const val ACTIVITY_SCREEN_CLASS = "Lcom/mxtech/videoplayer/ActivityScreen;"
internal const val ENHANCE_FORCER_CLASS = "Lapp/ftl/extension/mxplayerad/EnhanceForcer;"
internal const val ENHANCE_LEVEL_FIELD = "patch_enhanceLevel"
internal const val ENHANCE_FORCER_FIELD = "patch_enhanceForcer"
internal const val ENHANCE_KICK_METHOD = "patch_enhanceKick"
internal const val ENHANCE_APPLY_METHOD = "patch_applyEnhance"
internal const val ENHANCE_DEFAULT_LEVEL = "0x3e3851ec"

internal fun FieldReference.smali() = "$definingClass->$name:$type"

internal fun MethodReference.smali() =
    "$definingClass->$name(${parameterTypes.joinToString("") { it.toString() }})$returnType"

internal fun MethodReference.sameSignatureAs(other: MethodReference) =
    name == other.name &&
        returnType.toString() == other.returnType.toString() &&
        parameterTypes.map { it.toString() } == other.parameterTypes.map { it.toString() }

internal fun MutableClass.addPatchMethod(
    name: String,
    params: List<String>,
    returnType: String,
    registers: Int,
    smali: String,
    flags: Int = AccessFlags.PUBLIC.value or AccessFlags.FINAL.value,
) {
    val method = ImmutableMethod(
        type,
        name,
        params.map { ImmutableMethodParameter(it, null, null) },
        returnType,
        flags,
        null,
        null,
        MutableMethodImplementation(registers),
    ).toMutable()
    method.addInstructions(0, smali.trimIndent())
    methods.add(method)
}

internal fun MutableClass.addPatchField(name: String, type: String) {
    fields.add(ImmutableField(this.type, name, type, AccessFlags.PUBLIC.value, null, null, null).toMutable())
}

/**
 * Stock reset method (originally "M9"): sput-boolean q=false, iget-object I, E0(-1), Sa().
 * Matched by opcode/literal shape inside ActivityScreen only; nothing obfuscated is pinned.
 * Verified unique in 3.2.5. Refs are read off the match, never named.
 */
internal object SmartEnhanceForceMethodFingerprint : Fingerprint(
    definingClass = ACTIVITY_SCREEN_CLASS,
    returnType = "V",
    parameters = emptyList(),
    filters = listOf(
        opcode(Opcode.CONST_4),
        opcode(Opcode.SPUT_BOOLEAN, location = MatchAfterImmediately()),
        opcode(Opcode.IGET_OBJECT, location = MatchAfterImmediately()),
        literal(-1, location = MatchAfterImmediately()),
        opcode(Opcode.INVOKE_VIRTUAL, location = MatchAfterImmediately()), // E0(I)V
        opcode(Opcode.INVOKE_VIRTUAL, location = MatchAfterImmediately()), // Sa()V
        opcode(Opcode.RETURN_VOID, location = MatchAfterImmediately()),
    ),
)

// SDK interface override - never renamed. Fires on every surface (re)build.
internal object SurfaceCreatedFingerprint : Fingerprint(
    definingClass = ACTIVITY_SCREEN_CLASS,
    name = "surfaceCreated",
    returnType = "V",
    parameters = listOf("Landroid/view/SurfaceHolder;"),
)

/** Values every Smart Enhance patch needs, resolved once by [smartEnhanceCorePatch]. */
internal object EnhanceRefs {
    lateinit var qField: FieldReference
    lateinit var playerField: FieldReference
    lateinit var e0Ref: MethodReference
    lateinit var saRef: MethodReference
    lateinit var m9: MutableMethod
    lateinit var activityScreen: MutableClass
    lateinit var playerType: String
}

/**
 * Shared base: state + retry re-applier that survives the surface/native player rebuild
 * done on lock/unlock. Hidden (no name); both Smart Enhance patches depend on it.
 *
 *  ActivityScreen  += Runnable, level field, forcer field, run()V, patch_enhanceKick()V
 *  player class    += patch_applyEnhance(IF)V  (E0 clone with a caller-chosen value)
 *  surfaceCreated  -> patch_enhanceKick()
 */
internal val smartEnhanceCorePatch = bytecodePatch(
    name = null,
    description = "Shared Smart Enhance re-apply logic.",
) {
    compatibleWith(COMPATIBILITY_MX_PLAYER_AD)
    dependsOn(modSettingsPatch)

    execute {
        val m9 = SmartEnhanceForceMethodFingerprint.method
        val m = SmartEnhanceForceMethodFingerprint.instructionMatches

        val qRef = m[1].getInstruction<ReferenceInstruction>().reference as FieldReference
        val pFieldRef = m[2].getInstruction<ReferenceInstruction>().reference as FieldReference
        val e0Ref = m[4].getInstruction<ReferenceInstruction>().reference as MethodReference
        val saRef = m[5].getInstruction<ReferenceInstruction>().reference as MethodReference

        val activityScreen = mutableClassDefBy(m9.definingClass)
        val pType = pFieldRef.type
        val pClass = mutableClassDefBy(pType)

        val e0 = pClass.methods.firstOrNull {
            it.name == e0Ref.name &&
                it.returnType == "V" &&
                it.parameterTypes.map { t -> t.toString() } == e0Ref.parameterTypes.map { t -> t.toString() }
        } ?: throw PatchException("Could not find the E0(I)V equivalent on $pType")

        val e0Instructions = e0.implementation!!.instructions
        val hFieldRef = e0Instructions.firstOrNull { it.opcode == Opcode.IGET_OBJECT }
            ?.let { (it as ReferenceInstruction).reference as FieldReference }
            ?: throw PatchException("E0: player-core field read not found")
        val wRef = e0Instructions.firstOrNull { it.opcode == Opcode.INVOKE_VIRTUAL }
            ?.let { (it as ReferenceInstruction).reference as MethodReference }
            ?: throw PatchException("E0: FFPlayer getter call not found")
        val ff = wRef.returnType.toString()

        EnhanceRefs.qField = qRef
        EnhanceRefs.playerField = pFieldRef
        EnhanceRefs.e0Ref = e0Ref
        EnhanceRefs.saRef = saRef
        EnhanceRefs.m9 = m9
        EnhanceRefs.activityScreen = activityScreen
        EnhanceRefs.playerType = pType

        val activity = activityScreen.type

        // player class: E0 clone taking the filter value.
        pClass.addPatchMethod(
            ENHANCE_APPLY_METHOD,
            listOf("I", "F"),
            "V",
            5,
            """
                iget-object v0, p0, ${hFieldRef.smali()}
                if-nez v0, :has_core
                return-void
                :has_core
                invoke-virtual {v0}, ${wRef.smali()}
                move-result-object v0
                if-eqz v0, :done
                invoke-virtual {v0, p1}, $ff->setFilter(I)V
                const/4 v1, 0x1
                if-ne p1, v1, :done
                const/4 v1, 0x0
                invoke-virtual {v0, v1}, $ff->setFilterSplitPosition(F)V
                invoke-virtual {v0, p2}, $ff->setFilterValue(F)V
                :done
                return-void
            """,
        )

        // ActivityScreen state.
        activityScreen.addPatchField(ENHANCE_LEVEL_FIELD, "F")
        activityScreen.addPatchField(ENHANCE_FORCER_FIELD, ENHANCE_FORCER_CLASS)
        if ("Ljava/lang/Runnable;" !in activityScreen.interfaces) {
            activityScreen.interfaces.add("Ljava/lang/Runnable;")
        }

        // run(): re-apply current level while Smart Enhance is on. 0 level == stock default.
        activityScreen.addPatchMethod(
            "run",
            emptyList(),
            "V",
            4,
            """
                sget-boolean v0, ${qRef.smali()}
                if-eqz v0, :out
                iget v1, p0, $activity->$ENHANCE_LEVEL_FIELD:F
                const/4 v2, 0x0
                cmpl-float v2, v1, v2
                if-nez v2, :have_level
                const v1, $ENHANCE_DEFAULT_LEVEL
                iput v1, p0, $activity->$ENHANCE_LEVEL_FIELD:F
                :have_level
                iget-object v0, p0, ${pFieldRef.smali()}
                if-eqz v0, :out
                const/4 v2, 0x1
                invoke-virtual {v0, v2, v1}, $pType->$ENHANCE_APPLY_METHOD(IF)V
                :out
                return-void
            """,
        )

        activityScreen.addPatchMethod(
            ENHANCE_KICK_METHOD,
            emptyList(),
            "V",
            2,
            """
                sget-boolean v0, ${qRef.smali()}
                if-eqz v0, :out
                iget-object v0, p0, $activity->$ENHANCE_FORCER_FIELD:$ENHANCE_FORCER_CLASS
                if-nez v0, :have
                new-instance v0, $ENHANCE_FORCER_CLASS
                invoke-direct {v0, p0}, $ENHANCE_FORCER_CLASS-><init>(Ljava/lang/Runnable;)V
                iput-object v0, p0, $activity->$ENHANCE_FORCER_FIELD:$ENHANCE_FORCER_CLASS
                :have
                invoke-virtual {v0}, $ENHANCE_FORCER_CLASS->restart()V
                :out
                return-void
            """,
        )

        // Every surface (re)build restarts the retry window. Replacing return-void
        // (instead of inserting before it) keeps any label bound to it intact.
        val surface = SurfaceCreatedFingerprint.method
        val surfaceInstructions = surface.implementation!!.instructions
        val last = surfaceInstructions.lastIndex
        if (surfaceInstructions[last].opcode != Opcode.RETURN_VOID) {
            throw PatchException("surfaceCreated does not end in return-void")
        }
        surface.replaceInstructions(
            last,
            """
                invoke-virtual {p0}, $activity->$ENHANCE_KICK_METHOD()V
                return-void
            """.trimIndent(),
        )
    }
}
