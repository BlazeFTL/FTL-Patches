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

    private val activeActivities =
        java.util.Collections.newSetFromMap(java.util.WeakHashMap<Activity, Boolean>())

    private val recreateAttempted =
        java.util.Collections.newSetFromMap(java.util.WeakHashMap<Activity, Boolean>())

    @JvmStatic
    fun setPercent(value: Int) { percent = value }

    @JvmStatic
    fun init(application: Application) {
        Log.i(TAG, "init(application) called, alreadyInitialized=$initialized")
        if (initialized) return
        try {
            register(application, percent)
        } catch (t: Throwable) {
            Log.e(TAG, "init failed", t)
        }
    }

    /**
     * Used when the patch injects into an Activity.onCreate() instead of
     * Application.onCreate() (e.g. no usable custom Application subclass was found).
     * This ensures the activity that's actually running gets patched immediately,
     * since [register]'s ActivityLifecycleCallbacks only cover activities created
     * AFTER registration and would otherwise miss this one.
     */
    @JvmStatic
    fun init(activity: Activity) {
        Log.i(TAG, "init(activity) path used, activity=${activity.javaClass.name}")
        init(activity.application)
        try {
            forceDensity(activity)
            activeActivities.add(activity)
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
                Log.i(TAG, "preCreated ${activity.javaClass.name} before=${activity.resources.displayMetrics.densityDpi}")
                forceDensity(activity)
                Log.i(TAG, "preCreated ${activity.javaClass.name} after=${activity.resources.displayMetrics.densityDpi}")
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                forceDensity(activity)
                activeActivities.add(activity)
                Log.i(TAG, "created ${activity.javaClass.name} dpi=${activity.resources.displayMetrics.densityDpi}")
            }

            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {
                val current = activity.resources.displayMetrics.densityDpi
                Log.i(TAG, "resumed ${activity.javaClass.name} dpi=$current target=$targetDpi")

                if (current == targetDpi) return
                if (!recreateAttempted.add(activity)) return

                Log.i(TAG, "density reset detected on ${activity.javaClass.name}, recreating once")
                forceDensity(activity)
                activity.recreate()
            }
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {
                activeActivities.remove(activity)
            }
        })

        application.registerComponentCallbacks(object : ComponentCallbacks2 {
            override fun onConfigurationChanged(newConfig: Configuration) {
                applyTo(application.resources)
                activeActivities.toList().forEach { forceDensity(it) }
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
