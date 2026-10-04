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

    private static volatile int latched;
    private static volatile boolean fallbackFailed;

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
