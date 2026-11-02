package app.ftl.patches.mobilism

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.booleanOption
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

// Mobilism targets SDK 28, so the patcher's framework has no system_accent names.
// These are the fixed framework ids (API 31): accent1_600, accent1_700, accent3_600.
private const val ACCENT1_600 = "@0x01060025"
private const val ACCENT1_700 = "@0x01060026"
private const val ACCENT3_600 = "@0x0106003f"

private val DYNAMIC_COLORS = mapOf(
    "accent" to ACCENT1_600,
    "apps_accent_color" to ACCENT1_600,
    "apps_color" to ACCENT1_600,
    "darkerBlue" to ACCENT1_700,
    "blue" to ACCENT3_600,
    "colorPrimaryDark" to ACCENT3_600,
    "games_accent_color" to ACCENT3_600,
    "games_color" to ACCENT3_600
)

private val DYNAMIC_COLORS_XML = buildString {
    appendLine("""<?xml version="1.0" encoding="utf-8"?>""")
    appendLine("<resources>")
    DYNAMIC_COLORS.forEach { (name, ref) ->
        appendLine("""    <color name="$name">$ref</color>""")
    }
    appendLine("</resources>")
}

@Suppress("unused")
val materialColorsPatch = resourcePatch(
    name = "Material colors",
    description = "Applies the Material red and purple palette to the Apps and Games tabs."
) {
    compatibleWith(COMPATIBILITY_MOBILISM)

    val dynamicTheme by booleanOption(
        key = "dynamicTheme",
        default = false,
        title = "Dynamic theme",
        description = "Use the wallpaper color palette on Android 12 and newer. " +
            "Older Android versions keep the Material red and purple palette."
    )

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

        if (dynamicTheme == true) {
            get("res/values-v31/colors.xml", copy = false).apply {
                parentFile?.mkdirs()
                writeText(DYNAMIC_COLORS_XML)
            }
        }
    }
}
