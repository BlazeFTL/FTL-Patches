package app.ftl.patches.`1dm`

import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element
import org.w3c.dom.Node

private val BRAND_REGEX = Regex("1dm", RegexOption.IGNORE_CASE)
private val STRING_TAGS = setOf("string", "item")

private fun Element.replaceBrand() {
    val text = textContent ?: return
    if (text.contains("://") || !BRAND_REGEX.containsMatchIn(text)) return

    for (i in 0 until childNodes.length) {
        val child = childNodes.item(i)
        if (child.nodeType == Node.TEXT_NODE || child.nodeType == Node.CDATA_SECTION_NODE) {
            child.nodeValue = BRAND_REGEX.replace(child.nodeValue, "IDM")
        }
    }
}

@Suppress("unused")
val renameBrandPatch = resourcePatch(
    name = "Rename 1DM to IDM",
    description = "Replaces 1DM+ with IDM+ and 1DM with IDM in app strings.",
    default = true
) {
    compatibleWith(
        Compatibility(
            packageName = "idm.internet.download.manager.plus",
            name = "1DM+"
        )
    )

    execute {
        decode("res/values*/strings.xml").forEach { path ->
            document(path).use { document ->
                val nodes = document.getElementsByTagName("*")
                for (i in 0 until nodes.length) {
                    val element = nodes.item(i) as Element
                    if (element.tagName in STRING_TAGS) element.replaceBrand()
                }
            }
        }
    }
}
