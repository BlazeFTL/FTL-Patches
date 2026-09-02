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
 * Restores bundled save-data archives on first launch.
 *
 * Looks for up to three top-level asset zips, each optional:
 * - "res0" -> unzipped into /data/data/<package>
 * - "res1" -> unzipped into /sdcard/Android/data/<package>
 * - "res2" -> unzipped into the app's OBB directory
 *
 * Only runs once per install, tracked via a SharedPreferences flag.
 */
object SaveDataPatch {
    private const val TAG = "MorpheAddSave"
    private const val PREFS = "morphe_savedata_prefs"
    private const val KEY_RESTORED = "restored"

    private const val INTERNAL_ASSET = "res0"
    private const val EXTERNAL_ASSET = "res1"
    private const val OBB_ASSET = "res2"

    @JvmStatic
    fun restore(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (prefs.getBoolean(KEY_RESTORED, false)) return
            prefs.edit().putBoolean(KEY_RESTORED, true).apply()

            val assets = context.assets
            val names = assets.list("")?.toSet() ?: emptySet()

            if (INTERNAL_ASSET in names) {
                restoreZip(assets, INTERNAL_ASSET, File("/data/data/${context.packageName}"))
            }

            if (OBB_ASSET in names) {
                context.obbDir?.let { restoreZip(assets, OBB_ASSET, it) }
            }

            if (EXTERNAL_ASSET in names) {
                val target = File(Environment.getExternalStorageDirectory(), "Android/data/${context.packageName}")
                restoreZip(assets, EXTERNAL_ASSET, target)
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

    private fun unzip(input: InputStream, targetDir: File) {
        val buffer = ByteArray(8192)

        ZipInputStream(input).use { zip ->
            var entry = zip.nextEntry

            while (entry != null) {
                val outFile = File(targetDir, entry.name)

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

                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }
}
