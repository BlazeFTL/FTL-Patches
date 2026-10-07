package app.ftl.patches.videodownloader

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.filePathOption
import app.morphe.patcher.patch.resourcePatch

val useCustomHostsPatch = resourcePatch(
    name = "Use Your Own Host File For Stronger AdBlock",
    description = "Replaces res/raw/hosts.txt with a text/host file you select.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_VIDEO_DOWNLOADER)

    val hostsFileOption = filePathOption(
        key = "hostsFile",
        title = "Host file",
        description = "Text/host file that replaces the built-in res/raw/hosts.txt.",
        required = true,
    )

    execute {
        val source = hostsFileOption.file
            ?: throw PatchException("No host file selected")
        if (!source.isFile) throw PatchException("Host file not found: ${source.path}")
        if (source.length() == 0L) throw PatchException("Host file is empty: ${source.path}")

        val resourcesRoot = get("res", false).parentFile.parentFile
        val targets = resourcesRoot.listFiles { file -> file.isDirectory }
            .orEmpty()
            .map { it.resolve("res/raw/hosts.txt") }
            .filter { it.isFile }

        if (targets.isEmpty()) throw PatchException("res/raw/hosts.txt not found in this APK")

        targets.forEach { source.copyTo(it, overwrite = true) }
    }
}
