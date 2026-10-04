package app.ftl.extension.videodownloader;

import android.content.Context;

final class PopupStore {
    static final String REDIRECT = "popup_block";
    static final String WINDOW = "popup_window_block2";
    static final int BLOCK = 1;
    static final int ALLOW = 2;

    private PopupStore() {
    }

    static int get(Context ctx, String file, String key) {
        return ctx.getSharedPreferences(file, 0).getInt(key, 0);
    }

    static void put(Context ctx, String file, String key, int value) {
        ctx.getSharedPreferences(file, 0).edit().putInt(key, value).apply();
    }

    static void remove(Context ctx, String file, String key) {
        ctx.getSharedPreferences(file, 0).edit().remove(key).apply();
    }
}
