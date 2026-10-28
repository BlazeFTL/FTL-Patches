package app.ftl.extension.mobilism;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

public final class ModPrefs {
    private static final String FILE = "mobilism_mod_settings";

    private static volatile Context appContext;

    private ModPrefs() {
    }

    public static Context context() {
        Context c = appContext;
        if (c != null) {
            return c;
        }
        try {
            Class<?> thread = Class.forName("android.app.ActivityThread");
            Object app = thread.getMethod("currentApplication").invoke(null);
            if (app instanceof Application) {
                appContext = (Application) app;
                return appContext;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static SharedPreferences prefs() {
        Context c = context();
        return c == null ? null : c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static boolean getBoolean(String key, boolean def) {
        SharedPreferences p = prefs();
        return p == null ? def : p.getBoolean(key, def);
    }

    public static void setBoolean(String key, boolean value) {
        SharedPreferences p = prefs();
        if (p != null) {
            p.edit().putBoolean(key, value).apply();
        }
    }

    public static void clear() {
        SharedPreferences p = prefs();
        if (p == null) {
            return;
        }
        SharedPreferences.Editor e = p.edit();
        for (String key : p.getAll().keySet()) {
            if (!key.startsWith("present_")) {
                e.remove(key);
            }
        }
        e.apply();
    }
}
