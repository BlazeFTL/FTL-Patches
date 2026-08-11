package app.ftl.extension.toast

import android.content.Context
import android.util.Log
import android.widget.Toast

object ToastPatch {
    private const val TAG = "MorpheToast"
    private const val PREFS = "morphe_toast_prefs"
    private const val KEY_SHOWN = "shown"

    @Volatile private var message: String = "Mod By BlazeFTL"
    @Volatile private var showOnce: Boolean = true

    @JvmStatic
    fun setMessage(value: String) { message = value }

    @JvmStatic
    fun setShowOnce(value: Boolean) { showOnce = value }

    @JvmStatic
    fun show(context: Context) {
        try {
            if (showOnce) {
                val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                if (prefs.getBoolean(KEY_SHOWN, false)) return
                prefs.edit().putBoolean(KEY_SHOWN, true).apply()
            }
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        } catch (t: Throwable) {
            Log.e(TAG, "show failed", t)
        }
    }
}
