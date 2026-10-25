package app.ftl.patches.mobilism

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

private val MATERIAL_COLORS = mapOf(
    "accent" to "#ffff645e",
    "apps_accent_color" to "#ffff645e",
    "apps_color" to "#ffff645e",
    "blue" to "#ff9191ff",
    "colorPrimaryDark" to "#ff9191ff",
    "darkerBlue" to "#ffff645e",
    "games_accent_color" to "#ff9191ff",
    "games_color" to "#ff9191ff"
)

@Suppress("unused")
val materialColorsPatch = resourcePatch(
    name = "Material colors",
    description = "Applies the Material red and purple palette to the Apps and Games tabs."
) {
    compatibleWith(COMPATIBILITY_MOBILISM)

    execute {
        document("res/values/colors.xml").use { document ->
            val remaining = MATERIAL_COLORS.keys.toMutableSet()
            val colors = document.getElementsByTagName("color")
            for (i in 0 until colors.length) {
                val color = colors.item(i) as Element
                val name = color.getAttribute("name")
                val value = MATERIAL_COLORS[name] ?: continue
                color.textContent = value
                remaining.remove(name)
            }
            if (remaining.isNotEmpty()) {
                throw PatchException("Colors not found: ${remaining.joinToString()}")
            }
        }
    }
}
