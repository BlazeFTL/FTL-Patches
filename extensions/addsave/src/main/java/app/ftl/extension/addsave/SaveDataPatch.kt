package app.ftl.extension.addsave

import android.content.Context
import android.content.res.AssetManager
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Restores bundled save-data archives.
 *
 * Looks for up to three top-level asset zips, each optional:
 * - "res0" -> unzipped into /data/data/<package>
 * - "res1" -> unzipped into /sdcard/Android/data/<package>
 * - "res2" -> unzipped into the app's OBB directory
 *
 * Runs on every launch, not just the first: any target file that already exists is
 * skipped rather than overwritten, so repeat calls are a cheap no-op and only files
 * the user (or the OS) removed get re-extracted. The OBB zip additionally gets a
 * full size/count integrity check first; if everything already matches, extraction
 * is skipped entirely, and if it doesn't match and stale top-level files are
 * present, those are deleted before re-extracting.
 */
object SaveDataPatch {
    private const val TAG = "MorpheAddSave"

    private const val INTERNAL_ASSET = "res0"
    private const val EXTERNAL_ASSET = "res1"
    private const val OBB_ASSET = "res2"

    @JvmStatic
    fun restore(context: Context) {
        try {
            val assets = context.assets
            val names = assets.list("")?.toSet() ?: emptySet()

            if (INTERNAL_ASSET in names) {
                restoreZip(assets, INTERNAL_ASSET, File("/data/data/${context.packageName}"))
            }

            if (EXTERNAL_ASSET in names) {
                val target = File(Environment.getExternalStorageDirectory(), "Android/data/${context.packageName}")
                restoreZip(assets, EXTERNAL_ASSET, target)
            }

            if (OBB_ASSET in names) {
                context.obbDir?.let { restoreObb(assets, it) }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "restore failed", t)
        }
    }

    private fun restoreZip(assets: AssetManager, assetName: String, targetDir: File) {
        try {
            targetDir.mkdirs()
            assets.open(assetName).use { input -> unzip(input, targetDir) }
        } catch (t: Throwable) {
            Log.e(TAG, "restoreZip failed for $assetName", t)
        }
    }

    private fun restoreObb(assets: AssetManager, targetDir: File) {
        try {
            if (isObbAlreadyRestored(assets, targetDir)) return

            if (targetDir.exists()) {
                targetDir.listFiles()?.forEach { if (it.isFile) it.delete() }
            }

            targetDir.mkdirs()
            assets.open(OBB_ASSET).use { input -> unzip(input, targetDir) }
        } catch (t: Throwable) {
            Log.e(TAG, "restoreObb failed", t)
        }
    }

    /** True if every entry in the obb zip already exists at the target with a matching size. */
    private fun isObbAlreadyRestored(assets: AssetManager, targetDir: File): Boolean {
        return try {
            assets.open(OBB_ASSET).use { input ->
                ZipInputStream(input).use { zip ->
                    var total = 0
                    var matched = 0
                    var entry = zip.nextEntry

                    while (entry != null) {
                        total++
                        val outFile = File(targetDir, entry.name)
                        if (outFile.exists() && outFile.length() == entry.size) matched++
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }

                    total > 0 && total == matched
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "isObbAlreadyRestored check failed", t)
            false
        }
    }

    private fun unzip(input: InputStream, targetDir: File) {
        val buffer = ByteArray(8192)

        ZipInputStream(input).use { zip ->
            var entry = zip.nextEntry

            while (entry != null) {
                val outFile = File(targetDir, entry.name)

                if (!outFile.exists()) {
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { out ->
                            var count = zip.read(buffer)
                            while (count > 0) {
                                out.write(buffer, 0, count)
                                count = zip.read(buffer)
                            }
                        }
                    }
                }

                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }
}
