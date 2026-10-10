package app.ftl.extension.xplayer;

import android.content.Context;
import android.content.SharedPreferences;

@SuppressWarnings("unused")
public final class ModPrefs {
    static final String FILE = "ftl_mod_settings";
    static final String KEY_HIDE_CAST = "hide_cast";
    static final String KEY_CLEAN_SHEETS = "clean_sheets";
    static final String KEY_CLEAN_PLAYER_MENU = "clean_player_menu";
    static final String KEY_HIDE_BOTTOM_BAR = "hide_bottom_bar";
    static final String KEY_HIDE_PLAYER_BUTTONS = "hide_player_buttons";
    static final String KEY_VOLUME_BOOST = "volume_boost";

    private static volatile SharedPreferences prefs;

    private ModPrefs() {
    }

    private static Context currentApplication() {
        try {
            Object app = Class.forName("android.app.ActivityThread")
                    .getMethod("currentApplication")
                    .invoke(null);
            return app instanceof Context ? (Context) app : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static SharedPreferences prefs(Context context) {
        SharedPreferences cached = prefs;
        if (cached != null) {
            return cached;
        }
        Context base = context != null ? context.getApplicationContext() : null;
        if (base == null) {
            base = currentApplication();
        }
        if (base == null) {
            base = context;
        }
        if (base == null) {
            return null;
        }
        cached = base.getSharedPreferences(FILE, Context.MODE_PRIVATE);
        prefs = cached;
        return cached;
    }

    static boolean get(Context context, String key, boolean fallback) {
        try {
            SharedPreferences p = prefs(context);
            return p == null ? fallback : p.getBoolean(key, fallback);
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    static void put(Context context, String key, boolean value) {
        try {
            SharedPreferences p = prefs(context);
            if (p != null) {
                p.edit().putBoolean(key, value).apply();
            }
        } catch (Throwable ignored) {
        }
    }

    public static boolean hideCast(Context context) {
        try {
            return context != null && get(context, KEY_HIDE_CAST, true);
        } catch (Throwable ignored) {
            return true;
        }
    }

    private static boolean boostOn() {
        return get(null, KEY_VOLUME_BOOST, true);
    }

    public static int boostMul(int value) {
        return boostOn() ? value * 3 : value * 2;
    }

    public static int boostExtra(int over) {
        return boostOn() ? over * 2 : 0;
    }

    public static int boostScale(int value) {
        return boostOn() ? value * 2 : value;
    }

    public static int boostUnscale(int value) {
        return boostOn() ? value >> 1 : value;
    }
}
