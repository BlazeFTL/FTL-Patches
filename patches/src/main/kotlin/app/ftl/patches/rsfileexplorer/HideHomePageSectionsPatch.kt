package app.ftl.patches.rsfileexplorer

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel

private const val MEDIA_VIEW_HOLDER_CLASS =
    "Lcom/edili/filemanager/ui/homepage/viewholder/MediaViewHolder;"

/**
 * Matches the constructor that inflates and lays out each home-page section (Recent,
 * Network, Tools, Bookmarks, ...). Class and method name are the app's own real
 * identifiers (not obfuscated), so they're pinned directly; the two anchor calls
 * inside are real Android SDK APIs (SparseArray.append, ViewGroup.addView), each
 * called exactly once in this constructor.
 */
private object MediaViewHolderConstructorFingerprint : Fingerprint(
    definingClass = MEDIA_VIEW_HOLDER_CLASS,
    name = "<init>",
    returnType = "V",
    parameters = listOf("Landroid/content/Context;", "Ljava/util/List;"),
    filters = listOf(
        methodCall(
            definingClass = "Landroid/util/SparseArray;",
            name = "append",
            parameters = listOf("I", "Ljava/lang/Object;"),
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
        val method = MediaViewHolderConstructorFingerprint.method
        val matches = MediaViewHolderConstructorFingerprint.instructionMatches
        val appendIndex = matches[0].index
        var addViewIndex = matches[1].index

        // Stash the section's type id in v1: it's dead here (last used as the
        // boolean arg to the two earlier LayoutInflater.inflate() calls, unused
        // for the rest of the constructor), while v3 - which holds the type id at
        // this point - gets reused as scratch inside the per-type branches below.
        method.addInstructions(appendIndex + 1, "move v1, v3")
        addViewIndex += 1

        // The instruction right after addView is the loop's "i++"; jumping there
        // skips only the addView call for that section, so it's built but never
        // attached to the home page's container view.
        val loopIncrementInstruction = method.implementation!!.instructions[addViewIndex + 1]

        // Type ids: 2 = Network, 3 = Tools, 4 = Bookmarks.
        method.addInstructionsWithLabels(
            addViewIndex - 1,
            """
                const/4 v3, 0x2
                if-eq v1, v3, :skip_section
                const/4 v3, 0x3
                if-eq v1, v3, :skip_section
                const/4 v3, 0x4
                if-eq v1, v3, :skip_section
            """.trimIndent(),
            ExternalLabel("skip_section", loopIncrementInstruction),
        )
    }
}
