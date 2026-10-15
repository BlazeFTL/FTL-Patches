package app.ftl.extension.snaptube;

import android.util.Log;

import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroup;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("unused")
public final class SnaptubeSettingsHider {

    private static final String TAG = "MorpheSnaptube";

    private static final int MAX_CHILDREN = 512;

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

    public static void hideCategories(PreferenceGroup screen, String setVisibleName) {
        if (screen == null) return;
        try {
            Method getChild = findChildGetter();
            Method setVisible = findSetVisible(setVisibleName);
            List<Method> textGetters = findTextGetters();

            for (int i = 0; i < MAX_CHILDREN; i++) {
                Preference child;
                try {
                    child = (Preference) getChild.invoke(screen, i);
                } catch (InvocationTargetException e) {
                    if (e.getCause() instanceof IndexOutOfBoundsException) break;
                    throw e;
                }
                if (child instanceof PreferenceGroup && hasHiddenCategoryText(child, textGetters)) {
                    setVisible.invoke(child, false);
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "hideCategories failed; target methods may have changed", t);
        }
    }

    public static void hidePreferences(PreferenceFragmentCompat fragment, String setVisibleName) {
        if (fragment == null) return;
        try {
            Method findPref = findPreferenceMethod();
            Method setVisible = findSetVisible(setVisibleName);

            for (String key : HIDDEN_PREFERENCE_KEYS) {
                Object pref = findPref.invoke(fragment, key);
                if (pref != null) setVisible.invoke(pref, false);
            }
        } catch (Throwable t) {
            Log.e(TAG, "hidePreferences failed; target methods may have changed", t);
        }
    }

    public static boolean defaultChannelEnabled(String channelId) {
        return !"Channel_Id_Push".equals(channelId)
            && !"Channel_Id_Cleaner".equals(channelId);
    }

    private static Method findPreferenceMethod() throws NoSuchMethodException {
        for (Method method : PreferenceFragmentCompat.class.getMethods()) {
            Class<?>[] params = method.getParameterTypes();
            if (params.length == 1
                && params[0] == CharSequence.class
                && Preference.class.isAssignableFrom(method.getReturnType())) {
                return method;
            }
        }
        throw new NoSuchMethodException("findPreference");
    }

    private static Method findChildGetter() throws NoSuchMethodException {
        for (Method method : PreferenceGroup.class.getMethods()) {
            Class<?>[] params = method.getParameterTypes();
            if (params.length == 1
                && params[0] == int.class
                && Preference.class.isAssignableFrom(method.getReturnType())) {
                return method;
            }
        }
        throw new NoSuchMethodException("getPreference");
    }

    private static Method findSetVisible(String resolvedName) throws NoSuchMethodException {
        if (resolvedName != null && !resolvedName.isEmpty()) {
            try {
                return Preference.class.getMethod(resolvedName, boolean.class);
            } catch (NoSuchMethodException ignored) {
            }
        }
        return Preference.class.getMethod("setVisible", boolean.class);
    }

    private static List<Method> findTextGetters() {
        List<Method> getters = new ArrayList<>();
        for (Method method : Preference.class.getMethods()) {
            if (method.getParameterTypes().length == 0 && method.getReturnType() == CharSequence.class) {
                getters.add(method);
            }
        }
        return getters;
    }

    private static boolean hasHiddenCategoryText(Preference pref, List<Method> textGetters) {
        for (Method getter : textGetters) {
            try {
                Object value = getter.invoke(pref);
                if (value != null && startsWithAny(value.toString(), HIDDEN_CATEGORY_PREFIXES)) {
                    return true;
                }
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    private static boolean startsWithAny(String value, String[] prefixes) {
        for (String prefix : prefixes) {
            if (value.startsWith(prefix)) return true;
        }
        return false;
    }
}
