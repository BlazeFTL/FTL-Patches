package app.ftl.patches.apkcleanup

import app.morphe.patcher.patch.ResourcePatchContext
import java.io.File

private val PACKAGE_NAME = Regex("\"package_name\"\\s*:\\s*\"([^\"]+)\"")

internal fun ResourcePatchContext.resourcePackageDirs(): Map<String, File> {
    val root = get("package.json", false).parentFile?.parentFile ?: return emptyMap()
    val result = linkedMapOf<String, File>()
    root.listFiles { f -> f.isDirectory }.orEmpty().sortedBy { it.name }.forEach { dir ->
        val json = dir.resolve("package.json").takeIf { it.isFile } ?: return@forEach
        val name = PACKAGE_NAME.find(json.readText())?.groupValues?.get(1) ?: return@forEach
        result.putIfAbsent(name, dir)
    }
    return result
}

internal fun ResourcePatchContext.resDirectories(packageName: String): MutableMap<String, MutableList<String>> {
    val byDir = linkedMapOf<String, MutableList<String>>()
    listDecodableFiles("res/**", packageName).forEach { path ->
        val parts = path.split("/")
        if (parts.size != 3 || parts[0] != "res") return@forEach
        byDir.getOrPut(parts[1]) { mutableListOf() }.add(parts[2])
    }
    return byDir
}
