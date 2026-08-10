package app.ftl.extension.toast

import android.content.Context
import android.util.Log
import android.widget.Toast

object ToastPatch {
    private const val TAG = "MorpheToast"
    private const val PREFS = "morphe_toast_prefs"
    private const val KEY_SHOWN = "shown"

    @JvmStatic
    fun show(context: Context, message: String, showOnce: Boolean) {
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
