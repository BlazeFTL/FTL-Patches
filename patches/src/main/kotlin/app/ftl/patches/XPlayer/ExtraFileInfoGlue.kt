package app.ftl.patches.xplayer

internal const val EXTENSION_CLASS = "Lapp/ftl/extension/xplayer/ExtraFileInfo;"
internal const val VIDEO_HELPER = "ftl\$bindVideoRow"
internal const val FOLDER_HELPER = "ftl\$bindFolderRow"
internal const val HELPER_REGISTERS = 16

internal const val BIND_VIDEO_DESC =
    "Landroid/widget/TextView;Landroid/widget/TextView;Ljava/lang/String;JJLjava/lang/String;Ljava/lang/Object;"
internal const val BIND_FOLDER_DESC =
    "Landroid/widget/TextView;Landroid/widget/TextView;Landroid/view/View;Landroid/view/View;" +
        "Ljava/lang/String;Ljava/lang/String;ILjava/lang/String;Z"

internal fun videoHelperParams(r: VideoRefs) = listOf(r.host, r.holder, MEDIA_FILE_INFO)

internal fun folderHelperParams(r: FolderRefs) = listOf(r.host, r.holder, r.bean)

internal fun callHelper(host: String, helper: String, params: List<String>) =
    "invoke-static/range {p0 .. p2}, $host->$helper(${params.joinToString("")})V"

internal fun videoHelperSmali(r: VideoRefs) = """
    iget-object v9, p0, ${r.owner.desc()}
    iget v9, v9, ${r.mode.desc()}
    const/4 v10, 0x1
    if-eq v9, v10, :skip
    iget-object v0, p1, ${r.primary.desc()}
    iget-object v1, p1, ${r.secondary.desc()}
    invoke-virtual {p2}, ${r.path.desc()}
    move-result-object v2
    invoke-virtual {p2}, ${r.date.desc()}
    move-result-wide v3
    iget-wide v5, p2, ${r.size.desc()}
    invoke-static {v5, v6}, ${r.formatter.desc()}
    move-result-object v7
    invoke-virtual {p2}, ${r.played.desc()}
    move-result-object v8
    invoke-static/range {v0 .. v8}, $EXTENSION_CLASS->bindVideoRow($BIND_VIDEO_DESC)V
    :skip
    return-void
"""

internal fun folderHelperSmali(r: FolderRefs) = """
    iget-object v0, p2, ${r.list.desc()}
    const-wide/16 v1, 0x0
    if-eqz v0, :sized
    invoke-interface {v0}, Ljava/util/List;->iterator()Ljava/util/Iterator;
    move-result-object v3
    :loop
    invoke-interface {v3}, Ljava/util/Iterator;->hasNext()Z
    move-result v4
    if-eqz v4, :sized
    invoke-interface {v3}, Ljava/util/Iterator;->next()Ljava/lang/Object;
    move-result-object v4
    check-cast v4, $MEDIA_FILE_INFO
    iget-wide v5, v4, ${r.sizeField.desc()}
    add-long/2addr v1, v5
    goto :loop
    :sized
    invoke-static {v1, v2}, ${r.formatter.desc()}
    move-result-object v7
    iget-wide v9, p0, ${r.now.desc()}
    invoke-virtual {p2}, ${r.modified.desc()}
    move-result-wide v11
    sub-long/2addr v9, v11
    const-wide/32 v11, 0x5265c00
    cmp-long v8, v9, v11
    if-gtz v8, :old
    const/4 v8, 0x1
    goto :flag
    :old
    const/4 v8, 0x0
    :flag
    iget-object v0, p1, ${r.title.desc()}
    iget-object v1, p1, ${r.countView.desc()}
    iget-object v2, p1, ${r.row.desc()}
    iget-object v3, p1, ${r.itemView.desc()}
    iget-object v4, p2, ${r.name.desc()}
    iget-object v5, p2, ${r.path.desc()}
    invoke-virtual {p2}, ${r.countGetter.desc()}
    move-result v6
    invoke-static/range {v0 .. v8}, $EXTENSION_CLASS->bindFolderRow($BIND_FOLDER_DESC)V
    return-void
"""
