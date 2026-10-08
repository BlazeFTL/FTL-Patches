package app.ftl.patches.xplayer

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

private const val AD_REMOVED_KEY = "adRemoved"
private const val MAX_GAP = 3

private val XPLAYER_COMPATIBILITY = Compatibility(
    packageName = "video.player.videoplayer",
    name = "XPlayer",
    targets = listOf(AppTarget(version = "2.9.2"))
)

private fun constTrue(register: Int) =
    if (register < 16) "const/4 v$register, 0x1" else "const/16 v$register, 0x1"

private fun Instruction.isAdRemovedString() =
    (opcode == Opcode.CONST_STRING || opcode == Opcode.CONST_STRING_JUMBO) &&
        ((this as ReferenceInstruction).reference as? StringReference)?.string == AD_REMOVED_KEY

private fun Instruction.staticCallOrNull() =
    if (opcode == Opcode.INVOKE_STATIC) (this as? ReferenceInstruction)?.reference as? MethodReference else null

private fun MethodReference.paramTypes() = parameterTypes.map { it.toString() }

private fun MutableMethod.forceAdRemovedReadsTrue() {
    val instructions = implementation!!.instructions
    for (i in instructions.indices) {
        if (!instructions[i].isAdRemovedString()) continue
        for (j in i + 1..minOf(i + 1 + MAX_GAP, instructions.size - 2)) {
            val call = instructions[j].staticCallOrNull() ?: continue
            if (call.returnType != "Z" || call.paramTypes() != listOf("Ljava/lang/String;", "Z")) continue
            val moveResult = instructions[j + 1]
            if (moveResult.opcode == Opcode.MOVE_RESULT) {
                replaceInstruction(j + 1, constTrue((moveResult as OneRegisterInstruction).registerA))
            }
            break
        }
    }
}

val unlockProPatch = bytecodePatch(
    name = "Unlock Pro",
    description = "Unlocks all pro features."
) {
    compatibleWith(XPLAYER_COMPATIBILITY)

    execute {
        val adRemovedReads = AdRemovedReadFingerprint.matchAllOrNull()
            ?: throw PatchException("adRemoved reads not found")
        val purchaseCheck = PurchasedProductsCheckFingerprint.match()
        val ownerClass = purchaseCheck.originalClassDef

        val statePrefix = ownerClass.type.removeSuffix(";") + '$'
        val stateType = ownerClass.fields.firstOrNull { it.type.startsWith(statePrefix) }?.type
            ?: throw PatchException("state class not found")

        val stateSetterRef = ownerClass.methods.firstNotNullOfOrNull { method ->
            val instructions = method.implementation?.instructions?.toList()
                ?: return@firstNotNullOfOrNull null
            instructions.indices.asSequence()
                .filter { instructions[it].isAdRemovedString() }
                .flatMap { i ->
                    instructions.subList(i + 1, minOf(i + 2 + MAX_GAP, instructions.size)).asSequence()
                }
                .mapNotNull { it.staticCallOrNull() }
                .firstOrNull {
                    it.definingClass == stateType &&
                        it.returnType == "Z" &&
                        it.paramTypes() == listOf(stateType, "Z")
                }
        } ?: throw PatchException("state setter not found")

        val stateClass = mutableClassDefBy(stateType)

        val setter = stateClass.methods.first {
            it.name == stateSetterRef.name && it.paramTypes() == stateSetterRef.paramTypes()
        }
        val setterInstructions = setter.implementation!!.instructions
        val setterFirst = setterInstructions.first()
        if (setterFirst.opcode != Opcode.IPUT_BOOLEAN) throw PatchException("unexpected state setter")
        val flagName = ((setterFirst as ReferenceInstruction).reference as FieldReference).name

        val getter = stateClass.methods.first { m ->
            !AccessFlags.STATIC.isSet(m.accessFlags) &&
                m.returnType == "Z" &&
                m.parameterTypes.isEmpty() &&
                m.implementation?.instructions?.firstOrNull()?.let {
                    it.opcode == Opcode.IGET_BOOLEAN &&
                        ((it as ReferenceInstruction).reference as FieldReference).name == flagName
                } == true
        }

        val getterRegister = (getter.implementation!!.instructions.first() as OneRegisterInstruction).registerA
        val setterLast = setterInstructions.last()
        if (setterLast.opcode != Opcode.RETURN) throw PatchException("unexpected state setter")
        val setterReturnRegister = (setterLast as OneRegisterInstruction).registerA

        getter.addInstruction(1, constTrue(getterRegister))
        setter.addInstruction(setterInstructions.size - 1, constTrue(setterReturnRegister))

        purchaseCheck.method.apply {
            val first = implementation!!.instructions.first()
            if (first.opcode != Opcode.CONST_4 || (first as NarrowLiteralInstruction).narrowLiteral != 0) {
                throw PatchException("unexpected purchase check")
            }
            replaceInstruction(0, constTrue((first as OneRegisterInstruction).registerA))
        }

        adRemovedReads.forEach { it.method.forceAdRemovedReadsTrue() }
    }
}
