package app.ftl.extension.dpi

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.util.Log

object DensityPatch {
    private const val TAG = "MorpheDpi"
    private const val DEFAULT_PERCENT = 100
    private const val MIN_PERCENT = 25
    private const val MAX_PERCENT = 300
    private const val MIN_DPI = 96
    private const val MAX_DPI = 640

    @Volatile
    private var percent = DEFAULT_PERCENT

    @JvmStatic
    fun setPercent(value: Int) {
        percent = value
    }

    @JvmStatic
    fun wrap(base: Context): Context {
        val value = percent
        if (value !in MIN_PERCENT..MAX_PERCENT || value == DEFAULT_PERCENT) return base
        return try {
            val realDpi = Resources.getSystem().displayMetrics.densityDpi
            val target = Math.round(realDpi * value / 100f).coerceIn(MIN_DPI, MAX_DPI)
            val currentDpi = base.resources.configuration.densityDpi
            if (target == realDpi || currentDpi == target) {
                base
            } else {
                val config = Configuration()
                config.densityDpi = target
                base.createConfigurationContext(config)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "wrap failed", t)
            base
        }
    }
}
