package app.ftl.extension.snaptube;

import android.util.Log;

import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroup;

import java.lang.reflect.Method;

@SuppressWarnings("unused")
public final class SnaptubeSettingsHider {

    private static final String TAG = "MorpheSnaptube";

    private static final String[] HIDDEN_CATEGORY_PREFIXES = {
        "Download tools",
        "Phone clean",
    };

    private static final String[] HIDDEN_PREFERENCE_KEYS = {
        "recover_deleted_files_settings",
        "whatsapp_status_saver",
        "vault_settings",
        "clean_junk",
        "clean_boost",
        "clean_battery_saver",
        "clean_large_files",
        "clean_trash",
        "clean_whatsapp",
        "photo_clean",
        "clean_app_uninstaller",
    };

    private SnaptubeSettingsHider() {
    }

    public static void hideCategories(PreferenceGroup screen) {
        if (screen == null) return;
        try {
            Method getCount = PreferenceGroup.class.getMethod("J0");
            Method getPref = PreferenceGroup.class.getMethod("I0", int.class);
            Method removePref = PreferenceGroup.class.getMethod("M0", Preference.class);
            Method getTitle = Preference.class.getMethod("C");

            int i = 0;
            while (i < (Integer) getCount.invoke(screen)) {
                Preference pref = (Preference) getPref.invoke(screen, i);
                Object title = getTitle.invoke(pref);
                String titleText = title != null ? title.toString() : null;

                if (titleText != null && startsWithAny(titleText, HIDDEN_CATEGORY_PREFIXES)) {
                    removePref.invoke(screen, pref);
                } else {
                    i++;
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "hideCategories failed, target methods may have been renamed", t);
        }
    }

    public static boolean defaultChannelEnabled(String channelId) {
        return !"Channel_Id_Push".equals(channelId) && !"Channel_Id_Cleaner".equals(channelId);
    }

    public static void hidePreferences(PreferenceFragmentCompat fragment) {
        try {
            Method findPref = PreferenceFragmentCompat.class.getMethod("w1", CharSequence.class);
            Method setVisible = Preference.class.getMethod("x0", boolean.class);

            for (String key : HIDDEN_PREFERENCE_KEYS) {
                Object pref = findPref.invoke(fragment, key);
                if (pref != null) setVisible.invoke(pref, false);
            }
        } catch (Throwable t) {
            Log.e(TAG, "hidePreferences failed, target methods may have been renamed", t);
        }
    }

    private static boolean startsWithAny(String value, String[] prefixes) {
        for (String prefix : prefixes) {
            if (value.startsWith(prefix)) return true;
        }
        return false;
    }
}
