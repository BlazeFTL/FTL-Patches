package app.ftl.extension.dpi

import android.app.Activity
import android.app.Application
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Bundle
import android.util.Log
import android.view.ViewTreeObserver
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

    // DIAGNOSTIC: tracks which activities already have the per-frame watchdog attached,
    // so onActivityResumed (which can fire many times for the same instance) doesn't
    // stack duplicate listeners.
    private val watchedActivities =
        java.util.WeakHashMap<Activity, ViewTreeObserver.OnGlobalLayoutListener>()

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

        applyTo(application.resources, "register")

        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityPreCreated(activity: Activity, savedInstanceState: Bundle?) {
                Log.i(TAG, "preCreated ${activity.javaClass.name} before=${activity.resources.displayMetrics.densityDpi} ${configSnapshot(activity)}")
                forceDensity(activity)
                Log.i(TAG, "preCreated ${activity.javaClass.name} after=${activity.resources.displayMetrics.densityDpi}")
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                forceDensity(activity)
                activeActivities.add(activity)
                Log.i(TAG, "created ${activity.javaClass.name} dpi=${activity.resources.displayMetrics.densityDpi}")
            }

            override fun onActivityStarted(activity: Activity) {}

            // Added in API 29. Fire before the activity's own onStart()/onResume() run,
            // to beat whatever re-reads real display metrics inside them (observed:
            // FileExplorerActivity resets to the true device dpi on every resume, not
            // just once, so this has to run ahead of it every time, not just react after).
            override fun onActivityPreStarted(activity: Activity) {
                forceDensity(activity)
            }

            override fun onActivityPreResumed(activity: Activity) {
                forceDensity(activity)
            }

            override fun onActivityResumed(activity: Activity) {
                Log.i(
                    TAG,
                    "resumed ${activity.javaClass.name} raw=" +
                        "${activity.resources.displayMetrics.densityDpi} target=$targetDpi " +
                        "${windowStateSnapshot(activity)} ${configSnapshot(activity)}",
                )
                forceDensity(activity)
                attachWatchdog(activity)
            }
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {
                activeActivities.remove(activity)
                detachWatchdog(activity)
            }
        })

        application.registerComponentCallbacks(object : ComponentCallbacks2 {
            override fun onConfigurationChanged(newConfig: Configuration) {
                Log.i(TAG, "app onConfigurationChanged density=${newConfig.densityDpi} ${newConfig}")
                applyTo(application.resources, "appConfigChanged")
                activeActivities.toList().forEach { forceDensity(it) }
            }

            override fun onLowMemory() {}
            override fun onTrimMemory(level: Int) {}
        })
    }

    // DIAGNOSTIC: per-frame watchdog. Lifecycle callbacks (resume/configChanged) only
    // fire on discrete transitions; if something is re-asserting a different density on
    // every relayout (e.g. system-driven letterbox/size-compat rescaling), this catches
    // it between those transitions instead of only after the fact.
    private fun attachWatchdog(activity: Activity) {
        if (watchedActivities.containsKey(activity)) return
        val decorView = try { activity.window?.decorView } catch (t: Throwable) { null } ?: return
        val listener = ViewTreeObserver.OnGlobalLayoutListener {
            try {
                val current = activity.resources.displayMetrics.densityDpi
                if (current != targetDpi) {
                    Log.w(
                        TAG,
                        "DRIFT ${activity.javaClass.name} was=$current target=$targetDpi " +
                            "${windowStateSnapshot(activity)}",
                    )
                    forceDensity(activity)
                    Log.w(TAG, "DRIFT ${activity.javaClass.name} corrected to=${activity.resources.displayMetrics.densityDpi}")
                }
            } catch (t: Throwable) {
                Log.e(TAG, "watchdog failed", t)
            }
        }
        try {
            decorView.viewTreeObserver.addOnGlobalLayoutListener(listener)
            watchedActivities[activity] = listener
        } catch (t: Throwable) {
            Log.e(TAG, "attachWatchdog failed", t)
        }
    }

    private fun detachWatchdog(activity: Activity) {
        val listener = watchedActivities.remove(activity) ?: return
        try {
            activity.window?.decorView?.viewTreeObserver?.removeOnGlobalLayoutListener(listener)
        } catch (t: Throwable) {
            Log.e(TAG, "detachWatchdog failed", t)
        }
    }

    private fun windowStateSnapshot(activity: Activity): String = try {
        val multiWindow = activity.isInMultiWindowMode
        val pip = activity.isInPictureInPictureMode
        "multiWindow=$multiWindow pip=$pip"
    } catch (t: Throwable) {
        "windowState=unavailable(${t.javaClass.simpleName})"
    }

    private fun configSnapshot(activity: Activity): String = try {
        val c = activity.resources.configuration
        "screenWDp=${c.screenWidthDp} screenHDp=${c.screenHeightDp} smallestWDp=${c.smallestScreenWidthDp} cfg=$c"
    } catch (t: Throwable) {
        "config=unavailable(${t.javaClass.simpleName})"
    }

    private fun forceDensity(activity: Activity) {
        applyTo(activity.resources, "activity:${activity.javaClass.simpleName}")
        val base: Context? = activity.baseContext
        if (base != null && base.resources !== activity.resources) {
            applyTo(base.resources, "baseContext:${activity.javaClass.simpleName}")
        }
    }

    private fun applyTo(resources: Resources, caller: String) {
        val metrics = resources.displayMetrics
        if (metrics.densityDpi == targetDpi) return
        Log.i(TAG, "applyTo[$caller] ${metrics.densityDpi} -> $targetDpi")

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
