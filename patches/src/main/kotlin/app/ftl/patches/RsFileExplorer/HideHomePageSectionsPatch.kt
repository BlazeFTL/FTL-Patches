package app.ftl.patches.rsfileexplorer

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.removeInstruction
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private val GOTO_OPCODES = setOf(Opcode.GOTO, Opcode.GOTO_16, Opcode.GOTO_32)

/**
 * v2.3.1.1 removed the standalone MediaViewHolder(Context, List) constructor and
 * inlined its section-building loop directly into the adapter's
 * onCreateViewHolder(ViewGroup, int). Both the enclosing adapter class and the
 * ViewHolder return type are obfuscated and rotate every build (androidx itself
 * is minified in this app), so neither is pinned.
 *
 * onCreateViewHolder is a real Adapter override name and is stable. The
 * SparseIntArray.append(II) and ViewGroup.addView(View, LayoutParams) calls are
 * real, unobfuscated Android SDK calls used as the structural anchor - same as
 * the pre-2.3.1.1 fingerprint, just relocated.
 *
 * NOTE: verify uniqueness before shipping - count how many onCreateViewHolder(ViewGroup, int)
 * methods in the app also contain 2+ SparseIntArray.append(II) calls plus this addView
 * overload. If >1, add another filter (e.g. a resource-id literal known stable across builds).
 */
private object HomeSectionsFingerprint : Fingerprint(
    name = "onCreateViewHolder",
    returnType = "L",
    parameters = listOf("Landroid/view/ViewGroup;", "I"),
    filters = listOf(
        methodCall(
            definingClass = "Landroid/util/SparseIntArray;",
            name = "append",
            parameters = listOf("I", "I"),
            returnType = "V",
        ),
        methodCall(
            definingClass = "Landroid/util/SparseIntArray;",
            name = "append",
            parameters = listOf("I", "I"),
            returnType = "V",
        ),
        methodCall(
            definingClass = "Landroid/view/ViewGroup;",
            name = "addView",
            parameters = listOf("Landroid/view/View;", "Landroid/view/ViewGroup\$LayoutParams;"),
            returnType = "V",
        ),
    ),
)

val hideHomePageSectionsPatch = bytecodePatch(
    name = "Hide network, tools and bookmarks on home page",
    description = "Hides the Network, Tools and Bookmarks sections from the home page section list.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_RS_FILE_EXPLORER)

    execute {
        val method = HomeSectionsFingerprint.method
        val addViewIndex = HomeSectionsFingerprint.instructionMatches.last().index
        val instructions = method.implementation!!.instructions

        // Loop increment sits right after the shared addView call; jumping there
        // instead skips only that section's addView, so it's still built and
        // registered in the SparseIntArray title map, just never attached to the
        // home page's container view.
        val loopIncrementInstruction = instructions[addViewIndex + 1]

        fun isSparseIntArrayAppend(index: Int) =
            instructions[index].opcode == Opcode.INVOKE_VIRTUAL &&
                ((instructions[index] as? ReferenceInstruction)?.reference as? MethodReference)?.let { ref ->
                    ref.definingClass == "Landroid/util/SparseIntArray;" && ref.name == "append"
                } == true

        val appendCallIndices = instructions.indices.filter(::isSparseIntArrayAppend)

        // Each of the 3 hidden-type branches (Network/Tools/Bookmarks) ends its
        // append call with an unconditional goto back to the shared addView site;
        // the 4th, kept-visible branch has no such goto and falls straight
        // through instead - same shape as the pre-2.3.1.1 build, just with the
        // whole block relocated into onCreateViewHolder.
        val gotosToRedirect = appendCallIndices.mapNotNull { appendIndex ->
            var i = appendIndex + 1
            while (i < addViewIndex) {
                if (instructions[i].opcode in GOTO_OPCODES) return@mapNotNull i
                if (isSparseIntArrayAppend(i)) return@mapNotNull null
                i++
            }
            null
        }

        gotosToRedirect.forEach { gotoIndex ->
            method.addInstructionsWithLabels(
                gotoIndex,
                "goto :skip_section",
                ExternalLabel("skip_section", loopIncrementInstruction),
            )
            method.removeInstruction(gotoIndex + 1)
        }
    }
}
