package app.ftl.patches.resources

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.rawResourcePatch
import java.io.File
import java.util.logging.Logger

private val logger = Logger.getLogger("PngOptimizerPatch")

// Bundled at patches/src/main/resources/bin/{name}. ARM64 Android ELF binaries —
// only runnable when the patcher itself runs on an ARM64 Android device.
private fun extractTool(name: String): File {
    val classLoader = object {}.javaClass.classLoader
    val stream = classLoader.getResourceAsStream("bin/$name")
        ?: throw PatchException("Bundled tool \"$name\" not found on classpath")

    val out = File.createTempFile(name, null)
    out.deleteOnExit()
    stream.use { input -> out.outputStream().use { input.copyTo(it) } }
    out.setExecutable(true)
    return out
}

private fun runTool(binary: File, vararg args: String) {
    val process = ProcessBuilder(listOf(binary.absolutePath) + args)
        .redirectErrorStream(true)
        .start()
    process.inputStream.readBytes()
    process.waitFor()
}

private fun File.directorySize(): Long =
    walkTopDown().filter { it.isFile }.sumOf { it.length() }

val pngOptimizerPatch = rawResourcePatch(
    name = "Png optimizer",
    description = "Compresses png resources with pngquant (color quantization) and optipng (structure optimization, skipped for .9.png to preserve stretch regions).",
) {
    execute {
        val roots = listOf("res", "assets")
            .map { get(it, false) }
            .filter { it.isDirectory }
        if (roots.isEmpty()) return@execute

        val pngquant = extractTool("pngquant")
        val optipng = extractTool("optipng")

        val sizeBefore = roots.sumOf { it.directorySize() }
        var pngCount = 0
        var ninePatchCount = 0

        roots.forEach { root ->
            root.walkTopDown()
                .filter { it.isFile && it.extension.equals("png", ignoreCase = true) }
                .forEach { file ->
                    val is9Patch = file.name.endsWith(".9.png", ignoreCase = true)

                    runTool(pngquant, "64", "--force", "--strip", "--skip-if-larger", "--ext", ".png", file.absolutePath)

                    if (is9Patch) {
                        ninePatchCount++
                    } else {
                        runTool(optipng, "-o7", "-preserve", "-silent", "-clobber", file.absolutePath)
                        pngCount++
                    }
                }
        }

        val freedKb = (sizeBefore - roots.sumOf { it.directorySize() }) / 1024
        logger.info("Png optimizer: png=$pngCount, 9.png=$ninePatchCount, freed=${freedKb}Kb")
    }
}
