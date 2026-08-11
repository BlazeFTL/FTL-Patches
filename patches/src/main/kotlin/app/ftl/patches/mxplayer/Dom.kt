package app.ftl.patches.mxplayer

import org.w3c.dom.Element

internal fun Element.findById(id: String): Element? {
    val nodes = getElementsByTagName("*")
    for (i in 0 until nodes.length) {
        val element = nodes.item(i) as? Element ?: continue
        val value = element.getAttribute("android:id")
        if (value == "@id/$id" || value == "@+id/$id") return element
    }
    return null
}

internal fun Element.collapse(vararg extraAttributes: String) {
    setAttribute("android:layout_width", "0.0dip")
    setAttribute("android:layout_height", "0.0dip")
    extraAttributes.forEach { setAttribute(it, "0.0dip") }
    
}
