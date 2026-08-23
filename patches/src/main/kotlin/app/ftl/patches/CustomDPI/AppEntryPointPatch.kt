package app.ftl.patches.dpi

import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Document
import org.w3c.dom.Element

internal object AppEntryPoint {
    var applicationClassName: String? = null
        internal set
    var launcherActivityClassName: String? = null
        internal set

    // One resolvable activity class name per distinct android:process declared in the
    // manifest. Used as a fallback when there's no usable Application.onCreate(): apps
    // that isolate a splash/ad screen (or any component) into its own process need
    // DensityPatch initialized separately in each process, since it's a static object
    // whose state doesn't cross process boundaries. Injecting into only the launcher
    // activity would leave every other process unpatched.
    var processEntryActivities: Map<String, String> = emptyMap()
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

private fun resolveProcessName(process: String, packageName: String, defaultProcess: String): String = when {
    process.isEmpty() -> defaultProcess
    process.startsWith(":") -> packageName + process
    else -> process
}

private fun Document.findProcessEntryActivities(
    packageName: String,
    defaultProcess: String,
): Map<String, String> {
    val result = LinkedHashMap<String, String>()

    for (tag in listOf("activity", "activity-alias")) {
        val elements = getElementsByTagName(tag)

        for (i in 0 until elements.length) {
            val element = elements.item(i) as Element

            if (element.getAttribute("android:enabled") == "false") continue

            val nameAttr = if (tag == "activity-alias") "android:targetActivity" else "android:name"
            val className = resolveClassName(element.getAttribute(nameAttr), packageName) ?: continue
            val process = resolveProcessName(element.getAttribute("android:process"), packageName, defaultProcess)

            // First resolvable activity found per process wins; we only need one entry
            // point per process, not an exhaustive list.
            result.putIfAbsent(process, className)
        }
    }

    return result
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

            val defaultProcess =
                resolveProcessName(applicationElement.getAttribute("android:process"), packageName, packageName)
            AppEntryPoint.processEntryActivities = document.findProcessEntryActivities(packageName, defaultProcess)
        }
    }
}
