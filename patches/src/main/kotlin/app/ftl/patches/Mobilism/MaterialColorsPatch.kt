package app.ftl.patches.mobilism

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.booleanOption
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.patch.stringOption
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
// These are the fixed framework ids (API 31): tone 600 and 700 of accent1, accent2 and accent3.
private val DYNAMIC_FAMILIES = mapOf(
    "Primary" to Pair("@0x01060025", "@0x01060026"),
    "Secondary" to Pair("@0x01060032", "@0x01060033"),
    "Tertiary" to Pair("@0x0106003f", "@0x01060040")
)

private fun dynamicColorsXml(family: String): String {
    val (main, dark) = DYNAMIC_FAMILIES[family] ?: DYNAMIC_FAMILIES.getValue("Primary")
    val colors = mapOf(
        "accent" to main,
        "apps_accent_color" to main,
        "apps_color" to main,
        "darkerBlue" to dark,
        "blue" to main,
        "colorPrimaryDark" to main,
        "games_accent_color" to main,
        "games_color" to main
    )
    return buildString {
        appendLine("""<?xml version="1.0" encoding="utf-8"?>""")
        appendLine("<resources>")
        colors.forEach { (name, ref) ->
            appendLine("""    <color name="$name">$ref</color>""")
        }
        appendLine("</resources>")
    }
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

    val dynamicFamily by stringOption(
        key = "dynamicColorFamily",
        default = "Primary",
        values = DYNAMIC_FAMILIES.keys.associateWith { it },
        title = "Dynamic color family",
        description = "Which system color family tints the app when Dynamic theme is on. " +
            "Change it if the default looks grey or off on your device."
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
                writeText(dynamicColorsXml(dynamicFamily ?: "Primary"))
            }
        }
    }
}
