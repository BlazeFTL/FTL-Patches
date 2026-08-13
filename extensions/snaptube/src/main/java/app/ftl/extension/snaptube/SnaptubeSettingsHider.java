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
            Method getCount = findMethod(
                PreferenceGroup.class,
                new String[]{"J0", "getPreferenceCount"}
            );
            Method getPref = findMethod(
                PreferenceGroup.class,
                new String[]{"I0", "getPreference"},
                int.class
            );
            Method removePref = findMethod(
                PreferenceGroup.class,
                new String[]{"M0", "removePreference"},
                Preference.class
            );
            Method getTitle = findMethod(
                Preference.class,
                new String[]{"C", "getTitle"}
            );

            int i = 0;
            while (i < ((Number) getCount.invoke(screen)).intValue()) {
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
            Log.e(TAG, "hideCategories failed; target methods may have changed", t);
        }
    }

    public static boolean defaultChannelEnabled(String channelId) {
        return !"Channel_Id_Push".equals(channelId)
            && !"Channel_Id_Cleaner".equals(channelId);
    }

    public static void hidePreferences(PreferenceFragmentCompat fragment) {
        if (fragment == null) return;
        try {
            // Newer SnapTube builds use x1; older builds used w1.
            Method findPref = findMethod(
                PreferenceFragmentCompat.class,
                new String[]{"x1", "w1", "findPreference"},
                CharSequence.class
            );
            Method setVisible = findMethod(
                Preference.class,
                new String[]{"x0", "setVisible"},
                boolean.class
            );

            for (String key : HIDDEN_PREFERENCE_KEYS) {
                Object pref = findPref.invoke(fragment, key);
                if (pref != null) setVisible.invoke(pref, false);
            }
        } catch (Throwable t) {
            Log.e(TAG, "hidePreferences failed; target methods may have changed", t);
        }
    }

    private static Method findMethod(Class<?> owner, String[] names, Class<?>... parameterTypes)
        throws NoSuchMethodException {
        for (String name : names) {
            try {
                return owner.getMethod(name, parameterTypes);
            } catch (NoSuchMethodException ignored) {
                // Try the next mapping used by another SnapTube/AndroidX build.
            }
        }
        throw new NoSuchMethodException(owner.getName());
    }

    private static boolean startsWithAny(String value, String[] prefixes) {
        for (String prefix : prefixes) {
            if (value.startsWith(prefix)) return true;
        }
        return false;
    }
}
