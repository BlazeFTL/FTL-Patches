package app.ftl.extension.dpi

import android.app.Activity
import android.app.Application
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Bundle
import android.util.Log
import kotlin.math.roundToInt

object DensityPatch {
    private const val TAG = "MorpheDpi"
    private const val DEFAULT_PERCENT = 100
    private const val MIN_PERCENT = 25
    private const val MAX_PERCENT = 300
    private const val MIN_DPI = 96
    private const val MAX_DPI = 640
    private const val BASELINE_DPI = 160f

    @Volatile
    private var targetDpi = 0

    @Volatile private var percent = DEFAULT_PERCENT

    @Volatile
    private var initialized = false

    @JvmStatic
    fun setPercent(value: Int) { percent = value }

    // DensityPatch.kt — add alongside existing init(application: Application)
    @JvmStatic
    fun init(activity: Activity) {
    init(activity.application)
    try {
        forceDensity(activity)
    } catch (t: Throwable) {
        Log.e(TAG, "init(activity) failed", t)
    }
}

    private fun register(application: Application, percent: Int) {
        initialized = true

        val clampedPercent = if (percent in MIN_PERCENT..MAX_PERCENT) percent else DEFAULT_PERCENT
        val originalDpi = application.resources.displayMetrics.densityDpi
        val scaled = (originalDpi * clampedPercent / 100f).roundToInt()
        targetDpi = scaled.coerceIn(MIN_DPI, MAX_DPI)

        Log.i(TAG, "init originalDpi=$originalDpi percent=$clampedPercent targetDpi=$targetDpi")

        if (targetDpi == originalDpi) return

        applyTo(application.resources)

        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityPreCreated(activity: Activity, savedInstanceState: Bundle?) {
                forceDensity(activity)
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                forceDensity(activity)
            }

            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })

        application.registerComponentCallbacks(object : ComponentCallbacks2 {
            override fun onConfigurationChanged(newConfig: Configuration) {
                applyTo(application.resources)
            }

            override fun onLowMemory() {}
            override fun onTrimMemory(level: Int) {}
        })
    }

    private fun forceDensity(activity: Activity) {
        applyTo(activity.resources)
        val base: Context? = activity.baseContext
        if (base != null && base.resources !== activity.resources) {
            applyTo(base.resources)
        }
    }

    private fun applyTo(resources: Resources) {
        val metrics = resources.displayMetrics
        if (metrics.densityDpi == targetDpi) return

        val scale = targetDpi / BASELINE_DPI
        metrics.densityDpi = targetDpi
        metrics.density = scale
        @Suppress("DEPRECATION")
        run { metrics.scaledDensity = scale }

        val configuration = resources.configuration
        configuration.densityDpi = targetDpi

        @Suppress("DEPRECATION")
        resources.updateConfiguration(configuration, metrics)
    }
}
