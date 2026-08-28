package app.ftl.patches.ampere

import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

// No targets → compatible with any version.
private val COMPATIBILITY_AMPERE = Compatibility(
    packageName = "com.gombosdev.ampere",
    name = "Ampere",
)

// name -> new value, taken from the colors.xml diff (old -> new palette).
private val UPDATED_COLORS = mapOf(
    "Accent" to "#ff448aff",
    "AccentCharging" to "#ff9191ff",
    "AccentChargingDark" to "#ff8400ff",
    "AccentChargingLight" to "#ff9191ff",
    "AccentDark" to "#ff910fff",
    "AccentDischarging" to "#fff14747",
    "AccentDischargingDark" to "#fff14747",
    "AccentDischargingLight" to "#ffff645e",
    "AccentLight" to "#ff448aff",
    "CardBgDark" to "#ff000004",
    "Primary" to "#ff000000",
)

val materialColorsUpgradePatch = resourcePatch(
    name = "Material Colors Upgrade Peach And Purple",
    description = "Updates Accent/Primary/CardBgDark to the newer Material color palette.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_AMPERE)

    execute {
        document("res/values/colors.xml").use { document ->
            val colors = document.getElementsByTagName("color")
            for (i in 0 until colors.length) {
                val element = colors.item(i) as? Element ?: continue
                UPDATED_COLORS[element.getAttribute("name")]?.let { newValue ->
                    element.textContent = newValue
                }
            }
        }
    }
}
