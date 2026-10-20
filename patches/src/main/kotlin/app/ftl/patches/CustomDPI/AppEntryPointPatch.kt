package app.ftl.patches.customdpi

import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Document
import org.w3c.dom.Element

internal object AppEntryPoint {
    var applicationClassName: String? = null
        internal set
    var activityClassNames: Set<String> = emptySet()
        internal set
}

private fun resolveClassName(name: String, packageName: String): String? = when {
    name.isEmpty() -> null
    name.startsWith(".") -> packageName + name
    !name.contains(".") -> "$packageName.$name"
    else -> name
}

private fun Document.findActivityClassNames(packageName: String): Set<String> {
    val result = LinkedHashSet<String>()

    for (tag in listOf("activity", "activity-alias")) {
        val elements = getElementsByTagName(tag)

        for (i in 0 until elements.length) {
            val element = elements.item(i) as Element

            if (element.getAttribute("android:enabled") == "false") continue

            val nameAttr = if (tag == "activity-alias") "android:targetActivity" else "android:name"
            resolveClassName(element.getAttribute(nameAttr), packageName)?.let { result.add(it) }
        }
    }

    return result
}

internal val findAppEntryPointPatch = resourcePatch(
    description = "Resolves the app's Application class and every declared activity class.",
    default = false,
) {
    execute {
        val packageName = packageMetadata.packageName

        document("AndroidManifest.xml").use { document ->
            val applicationElement = document.getElementsByTagName("application").item(0) as Element

            AppEntryPoint.applicationClassName =
                resolveClassName(applicationElement.getAttribute("android:name"), packageName)
            AppEntryPoint.activityClassNames = document.findActivityClassNames(packageName)
        }
    }
}
