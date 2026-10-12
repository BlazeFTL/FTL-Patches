package app.ftl.patches.xplayer

import app.morphe.patcher.patch.PatchException
import org.w3c.dom.Document
import org.w3c.dom.Element

internal fun Document.indexById(): Map<String, Element> {
    val all = getElementsByTagName("*")
    val result = LinkedHashMap<String, Element>()
    for (i in 0 until all.length) {
        val element = all.item(i) as Element
        val id = element.getAttribute("android:id")
        if (id.isNotEmpty()) result[id] = element
    }
    return result
}

internal fun Map<String, Element>.byId(path: String, name: String): Element =
    this["@id/$name"] ?: throw PatchException("@id/$name not found in $path")

internal fun Element.setAttributes(vararg attributes: Pair<String, String>) {
    attributes.forEach { (name, value) -> setAttribute(name, value) }
}
