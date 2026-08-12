package app.ftl.patches.esfileexplorer

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.opcode
import app.morphe.patcher.methodCall
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode

private val COMPATIBILITY_ES_FILE_EXPLORER = Compatibility(
    packageName = "com.estrongs.android.pop",
    name = "ES File Explorer",
    targets = listOf(
        AppTarget(version = "4.4.3.7", versionCode = 10353),
    ),
)

private object HomeGetItemCountAdapterFingerprint : Fingerprint(
    definingClass = "Lcom/estrongs/android/ui/homepage/HomeAdapter;",
    name = "getItemCount",
    returnType = "I",
    filters = listOf(
        methodCall(smali = "Les/fx4;->Y2()Z"),
        opcode(Opcode.MOVE_RESULT, location = MatchAfterImmediately()),
    ),
)

private object HomeSkipLogHeaderFingerprint : Fingerprint(
    definingClass = "Lcom/estrongs/android/ui/homepage/HomeAdapter;",
    name = "k",
    returnType = "V",
    parameters = listOf("Les/ct2;", "I"),
)

private object HomeBindMediaFingerprint : Fingerprint(
    definingClass = "Lcom/estrongs/android/ui/homepage/HomeAdapter;",
    name = "onBindViewHolder",
    returnType = "V",
    parameters = listOf("Landroidx/recyclerview/widget/RecyclerView\$ViewHolder;", "I"),
    filters = listOf(
        methodCall(smali = "Lcom/estrongs/android/ui/homepage/viewholder/MediaViewHolder;->e(Ljava/lang/Object;)V"),
    ),
)

private object HomeCreateMediaFingerprint : Fingerprint(
    definingClass = "Lcom/estrongs/android/ui/homepage/HomeAdapter;",
    name = "onCreateViewHolder",
    returnType = "Landroidx/recyclerview/widget/RecyclerView\$ViewHolder;",
    parameters = listOf("Landroid/view/ViewGroup;", "I"),
    filters = listOf(
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

private object MenuListFingerprint : Fingerprint(
    definingClass = "Les/b06;",
    name = "b",
    returnType = "V",
    parameters = listOf("Ljava/util/List;"),
)

private object MenuBindFingerprint : Fingerprint(
    definingClass = "Les/f00;",
    name = "v",
    returnType = "V",
    parameters = listOf("Les/f00\$f;", "Les/fe1;", "I"),
)

private object MediaHandlerFingerprint : Fingerprint(
    definingClass = "Les/f33;",
    name = "d",
    returnType = "V",
    parameters = listOf("Z"),
    filters = listOf(
        opcode(Opcode.SGET_BOOLEAN),
        opcode(Opcode.IF_NEZ, location = MatchAfterImmediately()),
        opcode(Opcode.NEW_INSTANCE, location = MatchAfterImmediately()),
    ),
)

private object NavigationHeaderFingerprint : Fingerprint(
    definingClass = "Les/nb4;",
    name = "o",
    returnType = "V",
    filters = listOf(opcode(Opcode.SGET_BOOLEAN)),
)

private object WebSearchFingerprint : Fingerprint(
    definingClass = "Les/wg1;",
    name = "u",
    returnType = "[Ljava/lang/String;",
    parameters = listOf("Les/de1;"),
    filters = listOf(opcode(Opcode.AGET_OBJECT)),
)

private const val MENU_FILTER = """
    invoke-interface {p1}, Ljava/util/List;->iterator()Ljava/util/Iterator;
    move-result-object v2
    :ftl_menu_loop
    invoke-interface {v2}, Ljava/util/Iterator;->hasNext()Z
    move-result v3
    if-eqz v3, :ftl_menu_done
    invoke-interface {v2}, Ljava/util/Iterator;->next()Ljava/lang/Object;
    move-result-object v3
    check-cast v3, Les/fe1;
    invoke-virtual {v3}, Les/fe1;->getTitle()Ljava/lang/CharSequence;
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
    if-eqz v4, :ftl_menu_loop
    :ftl_menu_remove
    invoke-interface {v2}, Ljava/util/Iterator;->remove()V
    goto :ftl_menu_loop
    :ftl_menu_done
""".trimIndent()

val esFileExplorerPatch = bytecodePatch(
    name = "ES File Explorer Cleanup",
    description = "Removes selected ES File Explorer home tiles, menu actions, navigation header, media handler, and web-search entry.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_ES_FILE_EXPLORER)

    execute {
        HomeGetItemCountAdapterFingerprint.instructionMatches.getOrNull(1)?.let {
            it.method.addInstructions(
                it.index + 1,
                "xor-int/lit8 v0, v0, 0x1",
            )
        }

        HomeSkipLogHeaderFingerprint.methodOrNull?.addInstructions(
            0,
            """
            iget v0, p1, Les/ct2;->a:I
            const/4 v1, 0x3
            if-ne v0, v1, :ftl_home_continue
            return-void
            :ftl_home_continue
            """.trimIndent(),
        )

        HomeBindMediaFingerprint.instructionMatches.firstOrNull()?.let {
            it.method.addInstructions(
                it.index + 1,
                """
                iget-object p2, p1, Landroidx/recyclerview/widget/RecyclerView${'$'}ViewHolder;->itemView:Landroid/view/View;
                const/16 v3, 0x8
                invoke-virtual {p2, v3}, Landroid/view/View;->setVisibility(I)V
                """.trimIndent(),
            )
        }

        HomeCreateMediaFingerprint.instructionMatches.firstOrNull()?.let {
            it.method.addInstructions(
                it.index + 1,
                """
                iget-object v0, p2, Landroidx/recyclerview/widget/RecyclerView${'$'}ViewHolder;->itemView:Landroid/view/View;
                const/16 v1, 0x8
                invoke-virtual {v0, v1}, Landroid/view/View;->setVisibility(I)V
                """.trimIndent(),
            )
        }

        HomeCreateFavoriteFingerprint.instructionMatches.firstOrNull()?.let {
            it.method.addInstructions(
                it.index + 1,
                """
                iget-object v0, p2, Landroidx/recyclerview/widget/RecyclerView${'$'}ViewHolder;->itemView:Landroid/view/View;
                const/16 v1, 0x8
                invoke-virtual {v0, v1}, Landroid/view/View;->setVisibility(I)V
                """.trimIndent(),
            )
        }

        MenuListFingerprint.methodOrNull?.addInstructions(0, MENU_FILTER)

        MenuBindFingerprint.methodOrNull?.addInstructions(
            0,
            """
            invoke-virtual {p2}, Les/fe1;->m()I
            move-result v0
            const v1, 0x7f130808
            if-ne v0, v1, :ftl_menu_bind_continue
            invoke-virtual {p1}, Les/f00${'$'}f;->a()V
            return-void
            :ftl_menu_bind_continue
            invoke-virtual {p1}, Les/f00${'$'}f;->c()V
            """.trimIndent(),
        )

        MediaHandlerFingerprint.instructionMatches.firstOrNull()?.let {
            it.method.addInstructions(it.index + 1, "goto :goto_2")
        }

        NavigationHeaderFingerprint.instructionMatches.firstOrNull()?.let {
            it.method.addInstructions(it.index + 1, "goto :goto_0")
        }

        WebSearchFingerprint.instructionMatches.firstOrNull()?.let {
            it.method.addInstructions(
                it.index + 1,
                """
                const-string v6, "web_search"
                invoke-virtual {v6, v5}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
                move-result v6
                if-nez v6, :cond_1f
                """.trimIndent(),
            )
        }
    }
}
