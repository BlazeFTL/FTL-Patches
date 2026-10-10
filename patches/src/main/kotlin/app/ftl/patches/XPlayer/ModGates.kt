package app.ftl.patches.xplayer

import app.morphe.patcher.patch.PatchException
import org.w3c.dom.Document
import org.w3c.dom.Element

private const val GATE_CLASS = "app.ftl.extension.xplayer.ModGate"

private val SINGLE_CHILD_LAYOUTS = setOf("SwipeRefreshLayout", "TextInputLayout", "TabLayout")

internal object ModKeys {
    const val HIDE_CAST = "hide_cast"
    const val CLEAN_SHEETS = "clean_sheets"
    const val CLEAN_PLAYER_MENU = "clean_player_menu"
    const val HIDE_BOTTOM_BAR = "hide_bottom_bar"
    const val HIDE_PLAYER_BUTTONS = "hide_player_buttons"
    const val VOLUME_BOOST = "volume_boost"
    const val EXTRA_INFO = "extra_info"
}

internal object GateMode {
    const val ZERO = "zero"
    const val TEXT = "text"
    const val GONE = "gone"
    const val COLLAPSE = "collapse"
    const val BADGE = "badge"
    const val WIDTH = "width"
}

private fun Element.isMultiChildContainer(): Boolean {
    if (tagName == "merge") return true
    val simple = tagName.substringAfterLast('.')
    return simple.endsWith("Layout") && simple !in SINGLE_CHILD_LAYOUTS
}

private fun commonContainer(targets: List<Element>): Element {
    val chains = targets.map { target ->
        generateSequence(target.parentNode) { it.parentNode }
            .filterIsInstance<Element>()
            .toList()
            .asReversed()
    }
    var common: Element? = null
    var depth = 0
    while (chains.all { depth < it.size } && chains.all { it[depth] === chains[0][depth] }) {
        common = chains[0][depth]
        depth++
    }
    return common ?: throw PatchException("Gate targets share no container")
}

internal fun Document.addGate(path: String, key: String, mode: String, ids: List<String>) {
    val index = indexById()
    val targets = ids.map { index.byId(path, it) }

    var container = commonContainer(targets)
    while (!container.isMultiChildContainer()) {
        container = container.parentNode as? Element
            ?: throw PatchException("No multi-child container for gate in $path")
    }

    val gate = createElement(GATE_CLASS)
    gate.setAttributes(
        "android:layout_width" to "0.0dip",
        "android:layout_height" to "0.0dip",
        "android:visibility" to "gone",
        "android:tag" to "$key:$mode:${ids.joinToString(",")}"
    )
    container.appendChild(gate)
}
