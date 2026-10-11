package app.ftl.patches.removeads

import app.morphe.patcher.patch.resourcePatch

private val TAG_SPAN = Regex("<[^>]+>")
private val TAG_NAME = Regex("^<\\s*([A-Za-z0-9_.:\\-]+)")
private val ID_ATTR = Regex("android:id=\"@\\+?(?:android:)?id/([A-Za-z0-9_]+)\"")
private val INCLUDE_LAYOUT_ATTR = Regex("layout=\"@layout/([A-Za-z0-9_]+)\"")
private val ADVIEW_TAG = Regex("^<com\\.google\\.android\\.gms\\.ads\\.AdView\\b")
private val WIDTH_ATTR = Regex("android:layout_width=\"[^\"]*\"")
private val HEIGHT_ATTR = Regex("android:layout_height=\"[^\"]*\"")
private val HEIGHT_WRAP = Regex("android:layout_height=\"wrap_content\"")
private val VISIBILITY_ATTR = Regex("android:visibility=\"[^\"]*\"")
private val VISIBILITY_GONE = Regex("android:visibility=\"gone\"")
private val CONTENT_ATTR = Regex("android:text=|android:src=|app:srcCompat=")
private val TAIL = Regex("\\s*/?>$")

private val CONTAINER_TAGS = setOf(
    "FrameLayout", "LinearLayout", "RelativeLayout", "ConstraintLayout", "CoordinatorLayout",
    "CardView", "MaterialCardView", "ShimmerFrameLayout", "LinearLayoutCompat",
)
private val SPACER_TAGS = setOf("View", "Space")

private class LayoutNode(
    val name: String,
    val tag: String,
    val start: Int,
    val end: Int,
    val parent: LayoutNode?,
) {
    val children = mutableListOf<LayoutNode>()
    val simpleName: String get() = name.substringAfterLast('.')
    val isContainer: Boolean get() = simpleName in CONTAINER_TAGS
    var adSelf = false
    var hasAd = false
    var clean = false
}

private fun parseLayout(xml: String): List<LayoutNode> {
    val roots = mutableListOf<LayoutNode>()
    val stack = mutableListOf<LayoutNode>()
    for (match in TAG_SPAN.findAll(xml)) {
        val tag = match.value
        if (tag.startsWith("<?") || tag.startsWith("<!")) continue
        if (tag.startsWith("</")) {
            if (stack.isNotEmpty()) stack.removeAt(stack.size - 1)
            continue
        }
        val name = TAG_NAME.find(tag)?.groupValues?.get(1) ?: continue
        val parent = stack.lastOrNull()
        val node = LayoutNode(name, tag, match.range.first, match.range.last + 1, parent)
        if (parent != null) parent.children.add(node) else roots.add(node)
        if (!tag.endsWith("/>")) stack.add(node)
    }
    return roots
}

private fun isAdElement(node: LayoutNode): Boolean {
    val id = ID_ATTR.find(node.tag)?.groupValues?.get(1)
    if (id != null) {
        if (idLooksAdRelated(id)) return true
    }
    if (ADVIEW_TAG.containsMatchIn(node.tag)) return true
    if (node.name == "include") {
        val layoutName = INCLUDE_LAYOUT_ATTR.find(node.tag)?.groupValues?.get(1)
        if (layoutName != null && idLooksAdRelated(layoutName)) return true
    }
    return false
}

private fun classify(node: LayoutNode) {
    node.children.forEach { classify(it) }
    node.adSelf = isAdElement(node)
    when {
        node.adSelf -> {
            node.hasAd = true
            node.clean = true
        }
        node.children.isEmpty() -> {
            node.hasAd = false
            node.clean = node.simpleName in SPACER_TAGS ||
                (node.isContainer && HEIGHT_WRAP.containsMatchIn(node.tag)) ||
                (node.simpleName == "TextView" && VISIBILITY_GONE.containsMatchIn(node.tag) &&
                    !CONTENT_ATTR.containsMatchIn(node.tag))
        }
        else -> {
            node.hasAd = node.children.any { it.hasAd }
            node.clean = node.isContainer && node.children.all { it.clean }
        }
    }
}

private fun shouldHide(node: LayoutNode): Boolean =
    node.adSelf || (node.parent != null && node.hasAd && node.clean)

private fun zeroTag(tag: String): String? {
    if (!WIDTH_ATTR.containsMatchIn(tag) || !HEIGHT_ATTR.containsMatchIn(tag)) return null
    var patched = tag
    patched = WIDTH_ATTR.replace(patched, "android:layout_width=\"0.0dip\"")
    patched = HEIGHT_ATTR.replace(patched, "android:layout_height=\"0.0dip\"")
    patched = if (VISIBILITY_ATTR.containsMatchIn(patched)) {
        VISIBILITY_ATTR.replace(patched, "android:visibility=\"gone\"")
    } else {
        patched.replaceFirst(TAIL, " android:visibility=\"gone\"${if (patched.trimEnd().endsWith("/>")) " />" else ">"}")
    }
    return patched
}

private fun hideAdElements(xml: String): String {
    val roots = parseLayout(xml)
    if (roots.isEmpty()) return xml
    roots.forEach { classify(it) }

    val ordered = mutableListOf<LayoutNode>()
    fun collect(node: LayoutNode) {
        ordered.add(node)
        node.children.forEach { collect(it) }
    }
    roots.forEach { collect(it) }

    val out = StringBuilder(xml.length + 256)
    var cursor = 0
    for (node in ordered) {
        if (!shouldHide(node)) continue
        val patched = zeroTag(node.tag) ?: continue
        out.append(xml, cursor, node.start)
        out.append(patched)
        cursor = node.end
    }
    out.append(xml, cursor, xml.length)
    return out.toString()
}

// name = null keeps this out of PatchLoader's top-level list (removeAdsPatch
// pulls it in via dependsOn), so it doesn't show as its own toggle in the UI.
val hideAdLayoutsPatch = resourcePatch(
    name = null,
    description = "Zeroes size and hides visibility of ad-related view containers in layout XML.",
) {
    execute {
        val layoutDir = get("res/layout", false)
        layoutDir.walkTopDown()
            .filter { it.isFile && it.extension.equals("xml", ignoreCase = true) }
            .forEach { file ->
                val original = file.readText()
                val patched = hideAdElements(original)
                if (patched != original) file.writeText(patched)
            }
    }
}
