package app.ftl.patches.mobilism

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.methodCall
import app.morphe.patcher.newInstance
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.findFreeRegister

private const val EXT = "Lapp/ftl/extension/mobilism"
private const val FILTER = "$EXT/TitleFilter;"
private const val LIST_ADD_ALL = "Ljava/util/List;->addAll(Ljava/util/Collection;)Z"

internal object KeywordFilterHomeMenuCreateFingerprint : Fingerprint(
    definingClass = "Ldemigos/com/mobilism/UI/Main/HomeFragment;",
    name = "onCreateOptionsMenu",
    returnType = "V",
    parameters = listOf("Landroid/view/Menu;", "Landroid/view/MenuInflater;"),
    filters = listOf(
        methodCall(smali = "Landroid/view/MenuInflater;->inflate(ILandroid/view/Menu;)V")
    )
)

internal object KeywordFilterTopMenuCreateFingerprint : Fingerprint(
    definingClass = "Ldemigos/com/mobilism/UI/Main/TopFragment;",
    name = "onCreateOptionsMenu",
    returnType = "V",
    parameters = listOf("Landroid/view/Menu;", "Landroid/view/MenuInflater;"),
    filters = listOf(
        methodCall(smali = "Landroid/view/MenuInflater;->inflate(ILandroid/view/Menu;)V")
    )
)

internal object KeywordFilterHomeMenuSelectedFingerprint : Fingerprint(
    definingClass = "Ldemigos/com/mobilism/UI/Main/HomeFragment;",
    name = "onOptionsItemSelected",
    returnType = "Z",
    parameters = listOf("Landroid/view/MenuItem;")
)

internal object KeywordFilterTopMenuSelectedFingerprint : Fingerprint(
    definingClass = "Ldemigos/com/mobilism/UI/Main/TopFragment;",
    name = "onOptionsItemSelected",
    returnType = "Z",
    parameters = listOf("Landroid/view/MenuItem;")
)

internal object KeywordFilterTopAdapterAddItemsFingerprint : Fingerprint(
    definingClass = "Ldemigos/com/mobilism/UI/Adapter/TopAdapter;",
    name = "addItems",
    returnType = "V",
    parameters = listOf("Ljava/util/List;"),
    filters = listOf(methodCall(smali = LIST_ADD_ALL))
)

internal object KeywordFilterTopAdapterClearFingerprint : Fingerprint(
    definingClass = "Ldemigos/com/mobilism/UI/Adapter/TopAdapter;",
    name = "clearItemsAndLoad",
    returnType = "V",
    parameters = listOf()
)

internal object KeywordFilterTopAdapterBindFingerprint : Fingerprint(
    definingClass = "Ldemigos/com/mobilism/UI/Adapter/TopAdapter\$ViewHolder;",
    name = "setDataOnView",
    returnType = "V",
    parameters = listOf("Ldemigos/com/mobilism/logic/Model/ReleaseModel;", "I"),
    filters = listOf(newInstance("Ldemigos/com/mobilism/UI/Adapter/TopAdapter\$ViewHolder\$1;"))
)

internal object KeywordFilterHomeAdapterAddItemsFingerprint : Fingerprint(
    definingClass = "Ldemigos/com/mobilism/UI/Adapter/HomeScreenAdapter;",
    name = "addItems",
    returnType = "V",
    parameters = listOf("Ljava/util/List;"),
    filters = listOf(methodCall(smali = LIST_ADD_ALL))
)

internal object KeywordFilterTopLoadFingerprint : Fingerprint(
    definingClass = "Ldemigos/com/mobilism/UI/Main/TopFragment;",
    name = "load",
    returnType = "V",
    parameters = listOf("I")
)

internal object KeywordFilterTopPageFingerprint : Fingerprint(
    definingClass = "Ldemigos/com/mobilism/UI/Main/TopFragment\$6;",
    name = "run",
    returnType = "V",
    parameters = listOf()
)

internal object KeywordFilterTopPageFailFingerprint : Fingerprint(
    definingClass = "Ldemigos/com/mobilism/UI/Main/TopFragment\$7;",
    name = "run",
    returnType = "V",
    parameters = listOf()
)

internal object KeywordFilterSearchLoadFingerprint : Fingerprint(
    definingClass = "Ldemigos/com/mobilism/UI/Main/SearchActivity;",
    name = "load",
    returnType = "V",
    parameters = listOf("Ljava/lang/String;", "I")
)

internal object KeywordFilterSearchPageFingerprint : Fingerprint(
    definingClass = "Ldemigos/com/mobilism/UI/Main/SearchActivity\$3;",
    name = "run",
    returnType = "V",
    parameters = listOf(),
    filters = listOf(
        methodCall(smali = "Ldemigos/com/mobilism/UI/Widget/EndlessRecyclerOnScrollListener;->setLoading(Z)V")
    )
)

@Suppress("unused")
val keywordFilterPatch = bytecodePatch(
    name = "Keyword filter",
    description = "Adds a Keyword filter entry to the 3-dot menu to hide posts by title keywords, with long-press hide and copy."
) {
    compatibleWith(COMPATIBILITY_MOBILISM)

    extendWith("extensions/mobilism.mpe")

    execute {
        listOf(KeywordFilterHomeMenuCreateFingerprint, KeywordFilterTopMenuCreateFingerprint).forEach { fingerprint ->
            val inflateIndex = fingerprint.instructionMatches.first().index
            fingerprint.method.addInstructions(
                inflateIndex + 1,
                "invoke-static { p1 }, $FILTER->addMenuItem(Landroid/view/Menu;)V"
            )
        }

        listOf(KeywordFilterHomeMenuSelectedFingerprint, KeywordFilterTopMenuSelectedFingerprint).forEach { fingerprint ->
            val method = fingerprint.method
            val reg = method.findFreeRegister(0)
            method.addInstructions(
                0,
                """
                    invoke-static { p0, p1 }, $FILTER->onOptionsItemSelected(Ljava/lang/Object;Landroid/view/MenuItem;)Z
                    move-result v$reg
                    if-eqz v$reg, :keyword_filter_continue
                    return v$reg
                    :keyword_filter_continue
                    nop
                """
            )
        }

        KeywordFilterTopAdapterAddItemsFingerprint.let { fingerprint ->
            fingerprint.method.addInstructions(
                fingerprint.instructionMatches.first().index,
                "invoke-static { p0, p1 }, $FILTER->onAdd(Ljava/lang/Object;Ljava/util/List;)V"
            )
        }

        KeywordFilterTopAdapterClearFingerprint.method.addInstructions(
            0,
            "invoke-static { p0 }, $FILTER->onClear(Ljava/lang/Object;)V"
        )

        KeywordFilterTopAdapterBindFingerprint.let { fingerprint ->
            fingerprint.method.addInstructions(
                fingerprint.instructionMatches.first().index,
                "invoke-static { p0, p1 }, $FILTER->onBind(Ljava/lang/Object;Ljava/lang/Object;)V"
            )
        }

        KeywordFilterHomeAdapterAddItemsFingerprint.let { fingerprint ->
            fingerprint.method.addInstructions(
                fingerprint.instructionMatches.first().index,
                "invoke-static { p1 }, $FILTER->filterHome(Ljava/util/List;)V"
            )
        }

        KeywordFilterTopLoadFingerprint.method.addInstructions(
            0,
            "invoke-static { p0, p1 }, $FILTER->onTopLoad(Ljava/lang/Object;I)V"
        )

        KeywordFilterTopPageFingerprint.method.let { method ->
            val lastIndex = method.implementation!!.instructions.count() - 1
            method.addInstructions(
                lastIndex,
                "invoke-static { p0 }, $FILTER->onTopPageEnd(Ljava/lang/Object;)V"
            )
            method.addInstructions(
                0,
                "invoke-static { p0 }, $FILTER->onTopPageStart(Ljava/lang/Object;)V"
            )
        }

        KeywordFilterTopPageFailFingerprint.method.let { method ->
            val lastIndex = method.implementation!!.instructions.count() - 1
            method.addInstructions(
                lastIndex,
                "invoke-static { p0 }, $FILTER->onTopPageFail(Ljava/lang/Object;)V"
            )
        }

        KeywordFilterSearchLoadFingerprint.method.addInstructions(
            0,
            "invoke-static { p0, p2 }, $FILTER->onSearchLoad(Ljava/lang/Object;I)V"
        )

        KeywordFilterSearchPageFingerprint.let { fingerprint ->
            val method = fingerprint.method
            val insertIndex = fingerprint.instructionMatches.first().index + 1
            val reg = method.findFreeRegister(insertIndex)
            method.addInstructions(
                insertIndex,
                """
                    invoke-static { p0 }, $FILTER->onSearchPageEnd(Ljava/lang/Object;)Z
                    move-result v$reg
                    if-eqz v$reg, :keyword_filter_search_continue
                    return-void
                    :keyword_filter_search_continue
                    nop
                """
            )
            method.addInstructions(
                0,
                "invoke-static { p0 }, $FILTER->onSearchPageStart(Ljava/lang/Object;)V"
            )
        }
    }
}
