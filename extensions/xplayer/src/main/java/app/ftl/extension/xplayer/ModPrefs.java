package app.ftl.extension.xplayer;

import android.content.Context;

@SuppressWarnings("unused")
public final class ModPrefs {
    static final String FILE = "ftl_mod_settings";
    static final String KEY_HIDE_CAST = "hide_cast";

    private ModPrefs() {
    }

    static boolean get(Context context, String key, boolean fallback) {
        return context.getSharedPreferences(FILE, 0).getBoolean(key, fallback);
    }

    static void put(Context context, String key, boolean value) {
        context.getSharedPreferences(FILE, 0).edit().putBoolean(key, value).apply();
    }

    public static boolean hideCast(Context context) {
        try {
            return context != null && get(context, KEY_HIDE_CAST, true);
        } catch (Throwable ignored) {
            return true;
        }
    }
}
