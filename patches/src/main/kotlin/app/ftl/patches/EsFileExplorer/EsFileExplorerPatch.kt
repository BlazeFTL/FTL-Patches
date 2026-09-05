package app.ftl.patches.esfileexplorer

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.opcode
import app.morphe.patcher.methodCall
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.checkCast
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.removeInstruction
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

private val COMPATIBILITY_ES_FILE_EXPLORER = Compatibility(
    packageName = "com.estrongs.android.pop",
    name = "ES File Explorer",
    targets = listOf(
        AppTarget(version = "4.4.3.7", versionCode = 10353),
    ),
)

// HomeAdapter keeps its real name and overridden method names (getItemCount,
// onBindViewHolder, onCreateViewHolder are RecyclerView.Adapter overrides,
// which ProGuard/R8 can't rename without breaking virtual dispatch).
// Internal, non-override methods on the same class (like "k" below) are NOT
// protected by that and do get renamed - confirmed by diffing stock builds.

private object HomeAdapterPresenceBranchFingerprint : Fingerprint(
    definingClass = "Lcom/estrongs/android/ui/homepage/HomeAdapter;",
    name = "getItemCount",
    returnType = "I",
    filters = listOf(
        fieldAccess(
            definingClass = "this",
            type = "Landroidx/recyclerview/widget/RecyclerView\$Adapter;",
            opcode = Opcode.IGET_OBJECT,
        ),
        opcode(Opcode.IF_EQZ, location = MatchAfterImmediately()),
    ),
)

// Method name "k" is ProGuard-obfuscated and renames between builds (same
// class of break as HideToolTilesPatch's Lo/mg -> Lo/sg). Anchored instead on
// the real java.util.List calls that make this the only HomeAdapter method
// which both checks the list size and appends to it - the sibling method "j"
// only delegates to this one, it doesn't call List.add itself.
// getItemViewType is a real RecyclerView.Adapter override (same guarantee as
// getItemCount) and happens to access the exact same ct2.a field this patch
// needs - used purely to capture that field's current obfuscated name.
private object HomeItemViewTypeFingerprint : Fingerprint(
    definingClass = "Lcom/estrongs/android/ui/homepage/HomeAdapter;",
    name = "getItemViewType",
    returnType = "I",
    parameters = listOf("I"),
    filters = listOf(
        methodCall(smali = "Ljava/util/List;->get(I)Ljava/lang/Object;"),
        opcode(Opcode.MOVE_RESULT_OBJECT, location = MatchAfterImmediately()),
        opcode(Opcode.CHECK_CAST, location = MatchAfterImmediately()),
        opcode(Opcode.IGET, location = MatchAfterImmediately()),
    ),
)

private object HomeSkipLogHeaderFingerprint : Fingerprint(
    definingClass = "Lcom/estrongs/android/ui/homepage/HomeAdapter;",
    returnType = "V",
    parameters = listOf("L", "I"), // 1st param (originally Les/ct2;) is obfuscated.
    filters = listOf(
        methodCall(smali = "Ljava/util/List;->size()I"),
        opcode(Opcode.IF_LE),
        methodCall(smali = "Ljava/util/List;->add(Ljava/lang/Object;)Z"),
    ),
)

private object HomeBindMediaFingerprint : Fingerprint(
    definingClass = "Lcom/estrongs/android/ui/homepage/HomeAdapter;",
    name = "onBindViewHolder",
    returnType = "V",
    parameters = listOf("Landroidx/recyclerview/widget/RecyclerView\$ViewHolder;", "I"),
    filters = listOf(
        // MediaViewHolder is a real, stable class name; the bind method name
        // itself ("e") is obfuscated, so it's left unspecified here - the
        // (Object)V signature on this specific receiver type is unique
        // enough within onBindViewHolder on its own.
        methodCall(
            definingClass = "Lcom/estrongs/android/ui/homepage/viewholder/MediaViewHolder;",
            parameters = listOf("Ljava/lang/Object;"),
            returnType = "V",
        ),
    ),
)

private object HomeCreateMediaFingerprint : Fingerprint(
    definingClass = "Lcom/estrongs/android/ui/homepage/HomeAdapter;",
    name = "onCreateViewHolder",
    returnType = "Landroidx/recyclerview/widget/RecyclerView\$ViewHolder;",
    parameters = listOf("Landroid/view/ViewGroup;", "I"),
    filters = listOf(
        // Constructor name <init> can't be obfuscated, only real identifiers here.
        methodCall(smali = "Lcom/estrongs/android/ui/homepage/viewholder/MediaViewHolder;-><init>(Landroid/view/ViewGroup;Ljava/util/List;)V"),
    ),
)

private object HomeCreateFavoriteFingerprint : Fingerprint(
    definingClass = "Lcom/estrongs/android/ui/homepage/HomeAdapter;",
    name = "onCreateViewHolder",
    returnType = "Landroidx/recyclerview/widget/RecyclerView\$ViewHolder;",
    parameters = listOf("Landroid/view/ViewGroup;", "I"),
    filters = listOf(
        methodCall(smali = "Lcom/estrongs/android/ui/homepage/viewholder/FavoriteHolder;-><init>(Landroid/view/ViewGroup;)V"),
    ),
)

// Les/b06;, Les/f00;, Les/f33;, Les/nb4;, Les/wg1; below are ALL synthetic,
// ProGuard-repackaged names (the "es" bucket) - same category as KW/I1 in
// HideToolTilesPatch and just as unstable, so none of them are pinned as
// definingClass anywhere below. Every fingerprint in this section is anchored
// purely on real Android SDK / java.util calls and matches app-wide.

// Originally Les/b06;->b(Ljava/util/List;)V. Anchored on the real
// SparseArray/List calls used to rebuild the cached row views - this is the
// only method in the app building a SparseArray-backed list cache of exactly
// this shape.
private object MenuListFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("Ljava/util/List;"),
    filters = listOf(
        methodCall(smali = "Ljava/util/List;->size()I"),
        opcode(Opcode.IF_EQ),
        methodCall(
            definingClass = "Landroid/util/SparseArray;",
            name = "get",
            parameters = listOf("I"),
            returnType = "Ljava/lang/Object;",
        ),
        methodCall(smali = "Ljava/util/List;->get(I)Ljava/lang/Object;"),
    ),
)

// Originally Les/f00;->v(Les/f00$f;Les/fe1;I)V. fe1 implements a real
// android.view.MenuItem-shaped interface, so isEnabled()/getIcon()/getTitle()
// keep their real names even though fe1 itself is obfuscated - only those are
// used as anchors, chained with the real Drawable/TextView calls around them.
private object MenuBindFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("L", "L", "I"), // Les/f00$f; and Les/fe1; are obfuscated.
    filters = listOf(
        methodCall(name = "isEnabled", returnType = "Z"),
        methodCall(name = "getIcon", returnType = "Landroid/graphics/drawable/Drawable;"),
        methodCall(definingClass = "Landroid/graphics/drawable/Drawable;", name = "mutate"),
        methodCall(definingClass = "Landroid/widget/TextView;", name = "setCompoundDrawables"),
        methodCall(name = "getTitle", returnType = "Ljava/lang/CharSequence;"),
        // fe1's fallback id method (originally "m") is obfuscated and has no
        // stable name of its own, so it isn't matched by name at all - just by
        // sitting immediately after getTitle()'s null check, which is enough
        // to identify it uniquely. Captured dynamically in execute() below.
        opcode(Opcode.MOVE_RESULT_OBJECT, location = MatchAfterImmediately()),
        opcode(Opcode.IF_NEZ, location = MatchAfterImmediately()),
        methodCall(returnType = "I", opcode = Opcode.INVOKE_VIRTUAL, location = MatchAfterImmediately()),
    ),
)

// Originally Les/f33;->d(Z)V. Anchored on the lazy-init/clear of a
// CopyOnWriteArrayList + ConcurrentHashMap pair at the top of the method -
// the only method in the app doing exactly that. The 7 media-handler
// registrations that follow (originally sq6/fr/go1/x53/q33/mc4/dd6) are all
// obfuscated types with no stable anchor of their own, so the ones to keep
// vs. remove are found by counting NEW_INSTANCE opcodes in execute() below,
// not by name (see MediaHandlerEndFingerprint's removed x53 pin, which broke
// the same way KW/I1 did).
private object MediaHandlerFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("Z"),
    filters = listOf(
        methodCall(smali = "Ljava/util/concurrent/CopyOnWriteArrayList;-><init>()V"),
        methodCall(smali = "Ljava/util/List;->clear()V"),
        methodCall(smali = "Ljava/util/concurrent/ConcurrentHashMap;-><init>()V"),
        methodCall(smali = "Ljava/util/Map;->clear()V"),
    ),
)

// Originally Les/nb4;->o()V. Anchored on NaviListView (a real, descriptive
// app class - not synthetic like the others) plus the real ListView/View/
// AbsListView calls configuring it, ending on the flag check that gates
// whether a header view gets added.
private object NavigationHeaderFingerprint : Fingerprint(
    returnType = "V",
    filters = listOf(
        checkCast("Lcom/estrongs/android/ui/view/NaviListView;"),
        methodCall(smali = "Landroid/widget/ListView;->setDividerHeight(I)V"),
        methodCall(smali = "Landroid/view/View;->setOverScrollMode(I)V"),
        methodCall(smali = "Landroid/view/View;->setVerticalScrollBarEnabled(Z)V"),
        methodCall(smali = "Landroid/widget/AbsListView;->setFastScrollEnabled(Z)V"),
        opcode(Opcode.SGET_BOOLEAN),
    ),
)

// Originally Les/wg1;->u(Les/de1;)[Ljava/lang/String;. Anchored on the real
// ArrayList/array calls: build an ArrayList, walk an array with aget-object,
// filter into the ArrayList, then toArray() it back into a String[]. The
// (obfuscated param) -> String[] shape plus this call sequence is unique.
private object WebSearchFingerprint : Fingerprint(
    returnType = "[Ljava/lang/String;",
    parameters = listOf("L"), // Les/de1; is obfuscated.
    filters = listOf(
        methodCall(smali = "Ljava/util/ArrayList;-><init>()V"),
        opcode(Opcode.AGET_OBJECT),
        methodCall(smali = "Ljava/util/ArrayList;->add(Ljava/lang/Object;)Z"),
        methodCall(smali = "Ljava/util/ArrayList;->toArray([Ljava/lang/Object;)[Ljava/lang/Object;"),
    ),
)

private fun menuFilter(fe1Type: String, idMethodCall: String) = """
    invoke-interface {p1}, Ljava/util/List;->iterator()Ljava/util/Iterator;
    move-result-object v2
    :ftl_menu_loop
    invoke-interface {v2}, Ljava/util/Iterator;->hasNext()Z
    move-result v3
    if-eqz v3, :ftl_menu_done
    invoke-interface {v2}, Ljava/util/Iterator;->next()Ljava/lang/Object;
    move-result-object v3
    check-cast v3, $fe1Type
    move-object v0, v3
    invoke-virtual {v0}, $idMethodCall
    move-result v4
    const v3, 0x7f1302c4
    if-eq v4, v3, :ftl_menu_remove
    const v3, 0x7f1302c7
    if-eq v4, v3, :ftl_menu_remove
    const v3, 0x7f1308b2
    if-eq v4, v3, :ftl_menu_remove
    const v3, 0x7f13005f
    if-eq v4, v3, :ftl_menu_remove
    const v3, 0x7f13027a
    if-eq v4, v3, :ftl_menu_remove
    const v3, 0x7f130040
    if-eq v4, v3, :ftl_menu_remove
    const v3, 0x7f1301d8
    if-eq v4, v3, :ftl_menu_remove
    const v3, 0x7f130dc0
    if-eq v4, v3, :ftl_menu_remove
    invoke-virtual {v0}, $fe1Type->getTitle()Ljava/lang/CharSequence;
    move-result-object v3
    if-eqz v3, :ftl_menu_loop
    invoke-virtual {v3}, Ljava/lang/Object;->toString()Ljava/lang/String;
    move-result-object v3
    const-string v4, "Send"
    invoke-virtual {v3, v4}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
    move-result v4
    if-nez v4, :ftl_menu_remove
    const-string v4, "Hide"
    invoke-virtual {v3, v4}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
    move-result v4
    if-nez v4, :ftl_menu_remove
    const-string v4, "Add to desktop"
    invoke-virtual {v3, v4}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
    move-result v4
    if-nez v4, :ftl_menu_remove
    const-string v4, "Remote Play"
    invoke-virtual {v3, v4}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
    move-result v4
    if-nez v4, :ftl_menu_remove
    const-string v4, "Play To"
    invoke-virtual {v3, v4}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
    move-result v4
    if-nez v4, :ftl_menu_remove
    const-string v4, "Add to favorite"
    invoke-virtual {v3, v4}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
    move-result v4
    if-nez v4, :ftl_menu_remove
    const-string v4, "Backup to PCS"
    invoke-virtual {v3, v4}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
    move-result v4
    if-nez v4, :ftl_menu_remove
    const-string v4, "Encrypt"
    invoke-virtual {v3, v4}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
    move-result v4
    if-nez v4, :ftl_menu_remove
    const-string v4, "Web Search"
    invoke-virtual {v3, v4}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
    move-result v4
    if-nez v4, :ftl_menu_remove
    const-string v4, "Copy to"
    invoke-virtual {v3, v4}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
    move-result v4
    if-nez v4, :ftl_menu_remove
    const-string v4, "Move to"
    invoke-virtual {v3, v4}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
    move-result v4
    if-nez v4, :ftl_menu_remove
    const-string v4, "Chromecast"
    invoke-virtual {v3, v4}, Ljava/lang/String;->equalsIgnoreCase(Ljava/lang/String;)Z
    move-result v4
    if-nez v4, :ftl_menu_remove
    const-string v4, "Add to Playing"
    invoke-virtual {v3, v4}, Ljava/lang/String;->equalsIgnoreCase(Ljava/lang/String;)Z
    move-result v4
    if-nez v4, :ftl_menu_remove
    const-string v4, "Play"
    invoke-virtual {v3, v4}, Ljava/lang/String;->equalsIgnoreCase(Ljava/lang/String;)Z
    move-result v4
    if-nez v4, :ftl_menu_remove
    const-string v4, "Decrypt"
    invoke-virtual {v3, v4}, Ljava/lang/String;->equalsIgnoreCase(Ljava/lang/String;)Z
    move-result v4
    if-nez v4, :ftl_menu_remove
    const-string v4, "Auto Backup"
    invoke-virtual {v3, v4}, Ljava/lang/String;->equalsIgnoreCase(Ljava/lang/String;)Z
    move-result v4
    if-eqz v4, :ftl_menu_loop
    :ftl_menu_remove
    invoke-interface {v2}, Ljava/util/Iterator;->remove()V
    goto :ftl_menu_loop
    :ftl_menu_done
""".trimIndent()

val esFileExplorerPatch = bytecodePatch(
    name = "ES File Explorer Ui Cleanup",
    description = "Removes BookMark, New Files, Cleaner Row In HomePage, Cleans More menu actions",
    default = false,
) {
    compatibleWith(COMPATIBILITY_ES_FILE_EXPLORER)

    execute {
        HomeAdapterPresenceBranchFingerprint.methodOrNull?.let { method ->
            HomeAdapterPresenceBranchFingerprint.instructionMatches.getOrNull(1)?.let { match ->
                // Invert only the first adapter-presence branch, matching the hand-modified APK.
                method.addInstructions(
                    match.index,
                    "instance-of v0, v0, Ljava/lang/Object;\nxor-int/lit8 v0, v0, 0x1",
                )
            }
        }

        // ct2.a is now captured off getItemViewType's own instructions instead
        // of being hardcoded - the two build in lockstep since it's the same
        // field, on the same class, in the same dex.
        val ct2FieldRef = HomeItemViewTypeFingerprint.instructionMatchesOrNull?.last()
            ?.let { (it.instruction as ReferenceInstruction).reference as FieldReference }

        if (ct2FieldRef != null) {
            val ct2Field = "${ct2FieldRef.definingClass}->${ct2FieldRef.name}:${ct2FieldRef.type}"

            HomeSkipLogHeaderFingerprint.methodOrNull?.addInstructions(
                0,
                """
                iget v0, p1, $ct2Field
                const/4 v1, 0x3
                if-ne v0, v1, :ftl_home_continue
                return-void
                :ftl_home_continue
                """.trimIndent(),
            )
        }

        HomeBindMediaFingerprint.methodOrNull?.let { method ->
            HomeBindMediaFingerprint.instructionMatches.firstOrNull()?.let { match ->
                method.addInstructions(
                    match.index + 1,
                """
                iget-object p2, p1, Landroidx/recyclerview/widget/RecyclerView${'$'}ViewHolder;->itemView:Landroid/view/View;
                const/16 v3, 0x8
                invoke-virtual {p2, v3}, Landroid/view/View;->setVisibility(I)V
                """.trimIndent(),
                )
            }
        }

        HomeCreateMediaFingerprint.methodOrNull?.let { method ->
            HomeCreateMediaFingerprint.instructionMatches.firstOrNull()?.let { match ->
                method.addInstructions(
                    match.index + 1,
                """
                iget-object v0, p2, Landroidx/recyclerview/widget/RecyclerView${'$'}ViewHolder;->itemView:Landroid/view/View;
                const/16 v1, 0x8
                invoke-virtual {v0, v1}, Landroid/view/View;->setVisibility(I)V
                """.trimIndent(),
                )
            }
        }

        HomeCreateFavoriteFingerprint.methodOrNull?.let { method ->
            HomeCreateFavoriteFingerprint.instructionMatches.firstOrNull()?.let { match ->
                method.addInstructions(
                    match.index + 1,
                """
                iget-object v0, p2, Landroidx/recyclerview/widget/RecyclerView${'$'}ViewHolder;->itemView:Landroid/view/View;
                const/16 v1, 0x8
                invoke-virtual {v0, v1}, Landroid/view/View;->setVisibility(I)V
                """.trimIndent(),
                )
            }
        }

        // fe1's fallback-id method (originally "m") is captured here once, off
        // MenuBindFingerprint's own match, and reused for both patches below
        // since it's the same class/method in both places.
        val fe1IdMethodRef = MenuBindFingerprint.instructionMatchesOrNull?.getOrNull(7)
            ?.let { (it.instruction as ReferenceInstruction).reference as MethodReference }

        if (fe1IdMethodRef != null) {
            val fe1Type = fe1IdMethodRef.definingClass
            val idMethodCall = "$fe1Type->${fe1IdMethodRef.name}()${fe1IdMethodRef.returnType}"

            MenuListFingerprint.methodOrNull?.addInstructions(0, menuFilter(fe1Type, idMethodCall))

            MenuBindFingerprint.methodOrNull?.let { method ->
                // f00$f's view-holder toggle methods (originally "a"/"c") are
                // captured the same way, scoped to f00's own (dynamically
                // resolved) class via the two structurally distinct call sites
                // that reach them - a SparseArray.get()+cast+call for "a", and
                // a SparseArray.put()+goto+call for "c" (the only put() in the
                // class).
                val f00Type = MenuBindFingerprint.originalClassDefOrNull?.type
                val toggleARef = f00Type?.let { type ->
                    object : Fingerprint(
                        definingClass = type,
                        filters = listOf(
                            methodCall(smali = "Landroid/util/SparseArray;->get(I)Ljava/lang/Object;"),
                            opcode(Opcode.MOVE_RESULT_OBJECT, location = MatchAfterImmediately()),
                            opcode(Opcode.CHECK_CAST, location = MatchAfterImmediately()),
                            methodCall(
                                parameters = emptyList(),
                                returnType = "V",
                                opcode = Opcode.INVOKE_VIRTUAL,
                                location = MatchAfterImmediately(),
                            ),
                        ),
                    ) {}.instructionMatchesOrNull?.last()
                        ?.let { (it.instruction as ReferenceInstruction).reference as MethodReference }
                }
                val toggleCRef = f00Type?.let { type ->
                    object : Fingerprint(
                        definingClass = type,
                        filters = listOf(
                            methodCall(smali = "Landroid/util/SparseArray;->put(ILjava/lang/Object;)V"),
                            opcode(Opcode.GOTO, location = MatchAfterImmediately()),
                            methodCall(
                                parameters = emptyList(),
                                returnType = "V",
                                opcode = Opcode.INVOKE_VIRTUAL,
                                location = MatchAfterImmediately(),
                            ),
                        ),
                    ) {}.instructionMatchesOrNull?.last()
                        ?.let { (it.instruction as ReferenceInstruction).reference as MethodReference }
                }

                if (toggleARef != null && toggleCRef != null) {
                    val toggleACall = "${toggleARef.definingClass}->${toggleARef.name}()${toggleARef.returnType}"
                    val toggleCCall = "${toggleCRef.definingClass}->${toggleCRef.name}()${toggleCRef.returnType}"

                    method.addInstructions(
                        0,
                        """
                        invoke-virtual {p2}, $idMethodCall
                        move-result v0
                        const v1, 0x7f130808
                        if-ne v0, v1, :ftl_menu_bind_continue
                        invoke-virtual {p1}, $toggleACall
                        return-void
                        :ftl_menu_bind_continue
                        invoke-virtual {p1}, $toggleCCall
                        """.trimIndent(),
                    )
                }
            }
        }

        MediaHandlerFingerprint.methodOrNull?.let { method ->
            val instructions = method.implementation!!.instructions.toList()
            val afterMapClear = MediaHandlerFingerprint.instructionMatches.last().index

            // First handler-registration branch (originally gated on Les/fi4;->w0:Z).
            // Its own class/field are obfuscated, so it's found by opcode shape:
            // SGET_BOOLEAN -> IF_NEZ -> NEW_INSTANCE with no gaps. Earlier
            // SGET_BOOLEAN candidates in this method (originally Les/wb1;->e/a)
            // don't have that exact immediate chain, so this is unambiguous.
            var startIndex = -1
            for (i in afterMapClear until instructions.size - 2) {
                if (instructions[i].opcode == Opcode.SGET_BOOLEAN &&
                    instructions[i + 1].opcode == Opcode.IF_NEZ &&
                    instructions[i + 2].opcode == Opcode.NEW_INSTANCE
                ) {
                    startIndex = i + 1 // The IF_NEZ itself; removed along with the rest below.
                    break
                }
            }
            if (startIndex == -1) error("ES File Explorer media-handler start anchor not found")

            // 2 handler registrations get removed (originally "fr" and "go1"), each
            // built from exactly 2 NEW_INSTANCE opcodes (the handler + its shared
            // callback object). The 3rd handler (originally "x53") is kept, so its
            // NEW_INSTANCE - the 5th one after the branch - marks where removal stops.
            var newInstanceCount = 0
            var keepStartIndex = -1
            for (i in startIndex until instructions.size) {
                if (instructions[i].opcode == Opcode.NEW_INSTANCE) {
                    newInstanceCount++
                    if (newInstanceCount == 5) {
                        keepStartIndex = i
                        break
                    }
                }
            }
            if (keepStartIndex == -1) error("ES File Explorer media-handler end anchor not found")

            // Remove the conditional media-handler block through the stable
            // 3rd-handler block. This reproduces the reference mod without
            // injecting a goto to an enclosing label.
            for (index in keepStartIndex - 1 downTo startIndex) {
                method.removeInstruction(index)
            }
        }

        NavigationHeaderFingerprint.methodOrNull?.let { method ->
            NavigationHeaderFingerprint.instructionMatches.last().let { match ->
                // Force the existing `if-nez v0, :cond_2` branch to skip the header setup.
                // This preserves the method's own label table and avoids unresolved labels.
                method.addInstructions(match.index + 1, "const/4 v0, 0x1")
            }
        }

        WebSearchFingerprint.methodOrNull?.let { method ->
            WebSearchFingerprint.instructionMatches[1].let { match ->
                method.addInstructions(
                    match.index + 1,
                """
                const-string v6, "web_search"
                const-string v3, ""
                invoke-virtual {v5, v6, v3}, Ljava/lang/String;->replace(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Ljava/lang/String;
                move-result-object v5
                const/4 v3, 0x0
                """.trimIndent(),
                )
            }
        }
    }
}
