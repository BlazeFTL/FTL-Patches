package app.ftl.extension.firefox;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.util.Log;
import android.widget.Toast;

@SuppressWarnings("unused")
public final class ModSettings {

    private static final String TAG = "MorpheFirefox";
    private static final String PREFS = "ftl_mod_settings";
    private static final String KEY_OLD_MENU = "old_menu";

    private static final boolean DEFAULT_OLD_MENU = true;
    private static final String KEY_PIN = "pin_ext";
    private static final boolean DEFAULT_PIN = true;
    private static final String KEY_SCROLL = "scroll_memory";
    private static final boolean DEFAULT_SCROLL = true;
    private static final String KEY_TAB_NEXT = "tab_next_to_current";
    private static final boolean DEFAULT_TAB_NEXT = true;
    private static final String KEY_TAB_END = "tab_end_button";
    private static final boolean DEFAULT_TAB_END = true;
    private static final String KEY_CLOSE_DUPLICATES = "close_duplicates";
    private static final boolean DEFAULT_CLOSE_DUPLICATES = true;
    private static final String KEY_TAB_SCROLL = "tab_scroll_button";
    private static final boolean DEFAULT_TAB_SCROLL = true;
    private static final String KEY_HIDE_FULLSCREEN_TOAST = "hide_fullscreen_toast";
    private static final boolean DEFAULT_HIDE_FULLSCREEN_TOAST = true;
    private static final String KEY_RANGE_SELECT = "range_select";
    private static final boolean DEFAULT_RANGE_SELECT = true;

    private static volatile int latched;
    private static volatile int pinLatched;
    private static volatile int scrollLatched;
    private static volatile boolean fallbackFailed;
    private static volatile Boolean tabNextCache;
    private static volatile Boolean tabEndCache;
    private static volatile Boolean closeDuplicatesCache;
    private static volatile Boolean tabScrollCache;
    private static volatile Boolean hideFullscreenToastCache;
    private static volatile Boolean rangeSelectCache;

    private ModSettings() {
    }

    public static void latch(Context context) {
        try {
            latched = isOldMenu(context) ? 2 : 1;
        } catch (Throwable t) {
            Log.e(TAG, "latch failed", t);
        }
    }

    public static boolean oldMenu() {
        if (latched == 0 && !fallbackFailed) {
            try {
                Application app = currentApplication();
                if (app != null) latch(app);
                else fallbackFailed = true;
            } catch (Throwable t) {
                fallbackFailed = true;
                Log.e(TAG, "fallback latch failed", t);
            }
        }
        return latched == 0 ? DEFAULT_OLD_MENU : latched == 2;
    }

    public static boolean pinEnabled() {
        if (pinLatched == 0 && !fallbackFailed) {
            try {
                Application app = currentApplication();
                if (app != null) pinLatched = isPinSaved(app) ? 2 : 1;
            } catch (Throwable t) {
                Log.e(TAG, "pin state failed", t);
            }
        }
        return pinLatched == 0 ? DEFAULT_PIN : pinLatched == 2;
    }

    public static boolean scrollMemory() {
        if (scrollLatched == 0) {
            try {
                Application app = currentApplication();
                if (app != null) scrollLatched = isScrollSaved(app) ? 2 : 1;
            } catch (Throwable t) {
                Log.e(TAG, "scroll state failed", t);
            }
        }
        return scrollLatched == 0 ? DEFAULT_SCROLL : scrollLatched == 2;
    }

    public static boolean tabNext() {
        Boolean value = tabNextCache;
        if (value == null) {
            try {
                Application app = currentApplication();
                if (app != null) {
                    value = isTabNextSaved(app);
                    tabNextCache = value;
                }
            } catch (Throwable t) {
                Log.e(TAG, "tab next state failed", t);
            }
        }
        return value != null ? value : DEFAULT_TAB_NEXT;
    }

    public static boolean tabEnd() {
        Boolean value = tabEndCache;
        if (value == null) {
            try {
                Application app = currentApplication();
                if (app != null) {
                    value = isTabEndSaved(app);
                    tabEndCache = value;
                }
            } catch (Throwable t) {
                Log.e(TAG, "tab end state failed", t);
            }
        }
        return value != null ? value : DEFAULT_TAB_END;
    }

    public static boolean tabScroll() {
        Boolean value = tabScrollCache;
        if (value == null) {
            try {
                Application app = currentApplication();
                if (app != null) {
                    value = isTabScrollSaved(app);
                    tabScrollCache = value;
                }
            } catch (Throwable t) {
                Log.e(TAG, "tab scroll state failed", t);
            }
        }
        return value != null ? value : DEFAULT_TAB_SCROLL;
    }

    static boolean isTabScrollSaved(Context context) {
        return prefs(context).getBoolean(KEY_TAB_SCROLL, DEFAULT_TAB_SCROLL);
    }

    static void saveTabScroll(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_TAB_SCROLL, enabled).apply();
        tabScrollCache = enabled;
    }

    public static boolean hideFullscreenToast() {
        Boolean value = hideFullscreenToastCache;
        if (value == null) {
            try {
                Application app = currentApplication();
                if (app != null) {
                    value = isHideFullscreenToastSaved(app);
                    hideFullscreenToastCache = value;
                }
            } catch (Throwable t) {
                Log.e(TAG, "fullscreen toast state failed", t);
            }
        }
        return value != null ? value : DEFAULT_HIDE_FULLSCREEN_TOAST;
    }

    static boolean isHideFullscreenToastSaved(Context context) {
        return prefs(context).getBoolean(KEY_HIDE_FULLSCREEN_TOAST, DEFAULT_HIDE_FULLSCREEN_TOAST);
    }

    static void saveHideFullscreenToast(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_HIDE_FULLSCREEN_TOAST, enabled).apply();
        hideFullscreenToastCache = enabled;
    }

    public static boolean rangeSelect() {
        Boolean value = rangeSelectCache;
        if (value == null) {
            try {
                Application app = currentApplication();
                if (app != null) {
                    value = isRangeSelectSaved(app);
                    rangeSelectCache = value;
                }
            } catch (Throwable t) {
                Log.e(TAG, "range select state failed", t);
            }
        }
        return value != null ? value : DEFAULT_RANGE_SELECT;
    }

    static boolean isRangeSelectSaved(Context context) {
        return prefs(context).getBoolean(KEY_RANGE_SELECT, DEFAULT_RANGE_SELECT);
    }

    static void saveRangeSelect(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_RANGE_SELECT, enabled).apply();
        rangeSelectCache = enabled;
    }

    public static boolean closeDuplicates() {
        Boolean value = closeDuplicatesCache;
        if (value == null) {
            try {
                Application app = currentApplication();
                if (app != null) {
                    value = isCloseDuplicatesSaved(app);
                    closeDuplicatesCache = value;
                }
            } catch (Throwable t) {
                Log.e(TAG, "close duplicates state failed", t);
            }
        }
        return value != null ? value : DEFAULT_CLOSE_DUPLICATES;
    }

    static boolean isCloseDuplicatesSaved(Context context) {
        return prefs(context).getBoolean(KEY_CLOSE_DUPLICATES, DEFAULT_CLOSE_DUPLICATES);
    }

    static void saveCloseDuplicates(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_CLOSE_DUPLICATES, enabled).apply();
        closeDuplicatesCache = enabled;
    }

    static boolean isTabNextSaved(Context context) {
        return prefs(context).getBoolean(KEY_TAB_NEXT, DEFAULT_TAB_NEXT);
    }

    static void saveTabNext(Context context, boolean enabled) {
        SharedPreferences.Editor editor = prefs(context).edit().putBoolean(KEY_TAB_NEXT, enabled);
        if (!enabled) {
            editor.putBoolean(KEY_TAB_END, false);
            tabEndCache = false;
        }
        editor.apply();
        tabNextCache = enabled;
    }

    static boolean isTabEndSaved(Context context) {
        return prefs(context).getBoolean(KEY_TAB_END, DEFAULT_TAB_END);
    }

    static void saveTabEnd(Context context, boolean enabled) {
        SharedPreferences.Editor editor = prefs(context).edit().putBoolean(KEY_TAB_END, enabled);
        if (enabled) {
            editor.putBoolean(KEY_TAB_NEXT, true);
            tabNextCache = true;
        }
        editor.apply();
        tabEndCache = enabled;
    }

    static boolean isScrollSaved(Context context) {
        return prefs(context).getBoolean(KEY_SCROLL, DEFAULT_SCROLL);
    }

    static void saveScroll(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_SCROLL, enabled).apply();
        scrollLatched = enabled ? 2 : 1;
    }

    static boolean isPinSaved(Context context) {
        return prefs(context).getBoolean(KEY_PIN, DEFAULT_PIN);
    }

    static void savePin(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_PIN, enabled).apply();
        pinLatched = enabled ? 2 : 1;
    }

    public static void open(Context context) {
        try {
            final Context app = context.getApplicationContext() != null
                ? context.getApplicationContext() : context;
            Activity activity = findActivity(context);
            if (activity == null || activity.isFinishing()) {
                save(app, !isOldMenu(app));
                toast(app);
                return;
            }
            ModSettingsDialog.show(activity);
        } catch (Throwable t) {
            Log.e(TAG, "open failed", t);
        }
    }

    static boolean isOldMenu(Context context) {
        return prefs(context).getBoolean(KEY_OLD_MENU, DEFAULT_OLD_MENU);
    }

    static void save(Context context, boolean oldMenu) {
        prefs(context).edit().putBoolean(KEY_OLD_MENU, oldMenu).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static void toast(Context context) {
        Toast.makeText(context, "Saved. Close and reopen the menu to apply.", Toast.LENGTH_SHORT).show();
    }

    private static Activity findActivity(Context context) {
        Context c = context;
        while (c instanceof ContextWrapper) {
            if (c instanceof Activity) return (Activity) c;
            c = ((ContextWrapper) c).getBaseContext();
        }
        return null;
    }

    private static Application currentApplication() throws Exception {
        Class<?> activityThread = Class.forName("android.app.ActivityThread");
        return (Application) activityThread.getMethod("currentApplication").invoke(null);
    }
}
