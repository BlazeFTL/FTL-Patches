package app.ftl.patches.dpi

import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Document
import org.w3c.dom.Element

internal object AppEntryPoint {
    var applicationClassName: String? = null
        internal set
    var launcherActivityClassName: String? = null
        internal set
}

private fun resolveClassName(name: String, packageName: String): String? = when {
    name.isEmpty() -> null
    name.startsWith(".") -> packageName + name
    !name.contains(".") -> "$packageName.$name"
    else -> name
}

private fun Element.childElements(tag: String) = getElementsByTagName(tag).let { list ->
    (0 until list.length).map { list.item(it) as Element }
}

private fun Document.findLauncherActivity(packageName: String): String? {
    for (tag in listOf("activity", "activity-alias")) {
        val elements = getElementsByTagName(tag)

        for (i in 0 until elements.length) {
            val element = elements.item(i) as Element

            if (element.getAttribute("android:enabled") == "false") continue
            if (element.getAttribute("android:exported") == "false") continue

            val isLauncher = element.childElements("intent-filter").any { filter ->
                val hasMain = filter.childElements("action")
                    .any { it.getAttribute("android:name") == "android.intent.action.MAIN" }
                val hasLauncher = filter.childElements("category")
                    .any { it.getAttribute("android:name") == "android.intent.category.LAUNCHER" }
                hasMain && hasLauncher
            }

            if (isLauncher) {
                val nameAttr = if (tag == "activity-alias") "android:targetActivity" else "android:name"
                return resolveClassName(element.getAttribute(nameAttr), packageName)
            }
        }
    }

    return null
}
// Universal patch (no compatibleWith), so `default` must be false.
// Unnamed: this only ever runs as a dependency of universalDpiPatch, never selected on its own.
internal val findAppEntryPointPatch = resourcePatch(
    description = "Resolves the app's Application class, or its launcher activity as a fallback.",
    default = false,
) {
    execute {
        val packageName = packageMetadata.packageName

        document("AndroidManifest.xml").use { document ->
            val applicationElement = document.getElementsByTagName("application").item(0) as Element

            AppEntryPoint.applicationClassName =
                resolveClassName(applicationElement.getAttribute("android:name"), packageName)
            AppEntryPoint.launcherActivityClassName = document.findLauncherActivity(packageName)
        }
    }
}
