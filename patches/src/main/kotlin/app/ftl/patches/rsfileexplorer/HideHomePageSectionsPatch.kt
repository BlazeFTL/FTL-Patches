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

private const val MEDIA_VIEW_HOLDER_CLASS =
    "Lcom/edili/filemanager/ui/homepage/viewholder/MediaViewHolder;"

private val GOTO_OPCODES = setOf(Opcode.GOTO, Opcode.GOTO_16, Opcode.GOTO_32)

/**
 * Matches the constructor that inflates and lays out each home-page section (Recent,
 * Network, Tools, Bookmarks, ...). Class and method name are the app's own real
 * identifiers (not obfuscated), so they're pinned directly; the addView call is a
 * real Android SDK API, called exactly once in this constructor.
 */
private object MediaViewHolderConstructorFingerprint : Fingerprint(
    definingClass = MEDIA_VIEW_HOLDER_CLASS,
    name = "<init>",
    returnType = "V",
    parameters = listOf("Landroid/content/Context;", "Ljava/util/List;"),
    filters = listOf(
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
        val method = MediaViewHolderConstructorFingerprint.method
        val addViewIndex = MediaViewHolderConstructorFingerprint.instructionMatches[0].index

        // The instruction right after addView is the loop's "i++"; jumping there
        // instead skips only that section's addView call, so it's built (and still
        // registered for later population) but never attached to the home page's
        // container view.
        val loopIncrementInstruction = method.implementation!!.instructions[addViewIndex + 1]

        // Each per-type branch ends with `SparseIntArray.append(type, titleResId)`.
        // The 3 hidden types (Network/Tools/Bookmarks) each follow that call with an
        // unconditional `goto` back to the shared addView site; the 4th, kept-visible
        // type has no such goto - it falls straight through instead. Retargeting each
        // of those gotos to jump to the loop increment is what actually intercepts
        // every arrival path; inserting new code physically next to the shared addView
        // site does NOT work, since a `goto` jumps straight past anything inserted
        // there and lands only on its original target.
        //
        // NOTE: this leaves the 4th (fall-through) branch's own resource id untouched
        // as the disambiguator, rather than the type value itself, since it's the
        // branch that gets a goto - not which type number it is - that the compiled
        // if/else chain's layout actually determines.
        val instructions = method.implementation!!.instructions

        fun isSparseIntArrayAppend(index: Int) =
            instructions[index].opcode == Opcode.INVOKE_VIRTUAL &&
                ((instructions[index] as? ReferenceInstruction)?.reference as? MethodReference)?.let { ref ->
                    ref.definingClass == "Landroid/util/SparseIntArray;" && ref.name == "append"
                } == true

        val appendCallIndices = instructions.indices.filter(::isSparseIntArrayAppend)

        // Each branch's goto doesn't necessarily sit right after its append call -
        // e.g. the Bookmarks branch computes an extra bottom-margin value in between -
        // so scan forward for it instead of assuming a fixed offset. Bounded by the
        // next append call (start of the following branch) or addView (the kept-
        // visible branch's fall-through target) in case a branch has no goto at all.
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
