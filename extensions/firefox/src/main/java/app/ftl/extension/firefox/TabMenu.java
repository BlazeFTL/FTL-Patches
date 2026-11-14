package app.ftl.extension.firefox;

import android.app.Application;
import android.content.res.Resources;
import android.util.Log;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings({"unchecked", "rawtypes", "unused"})
public final class TabMenu {

    private static final String TAG = "MorpheFirefox";
    private static final String CLOSE_ALL_TAG = "tabstray.banner.threeDotButton.closeAllTabs";
    private static final String DUPLICATE_TAG = "tabstray.banner.threeDotButton.closeDuplicateTabs";
    private static final String DUPLICATE_LABEL = "Close duplicate tabs";

    private static volatile Object trayState;

    private TabMenu() {
    }

    public static void remember(Object state) {
        trayState = state;
    }

    public static List withDuplicates(Object menu) {
        List items = (List) menu;
        if (!ModSettings.closeDuplicates()) return items;
        try {
            int at = indexOfCloseAll(items);
            if (at < 0) return items;
            ArrayList result = new ArrayList(items);
            result.add(at, newItem(items.get(at), isPrivatePage()));
            return result;
        } catch (Throwable t) {
            Log.e(TAG, "close duplicates item failed", t);
            return items;
        }
    }

    private static boolean isPrivatePage() throws Exception {
        Object state = trayState;
        if (state == null) return false;
        Class<?> type = state.getClass();
        while (type != null && type != Object.class) {
            for (Field field : type.getDeclaredFields()) {
                if (!field.getType().isEnum()) continue;
                field.setAccessible(true);
                Object value = field.get(state);
                if (!(value instanceof Enum)) continue;
                String name = ((Enum) value).name();
                if ("PrivateTabs".equals(name)) return true;
                if ("NormalTabs".equals(name) || "SyncedTabs".equals(name) || "TabGroups".equals(name)) return false;
            }
            type = type.getSuperclass();
        }
        return false;
    }

    private static int indexOfCloseAll(List items) throws Exception {
        for (int i = 0, n = items.size(); i < n; i++) {
            if (hasString(items.get(i), CLOSE_ALL_TAG)) return i;
        }
        return -1;
    }

    private static boolean hasString(Object item, String value) throws Exception {
        if (item == null) return false;
        Class<?> type = item.getClass();
        while (type != null && type != Object.class) {
            for (Field field : type.getDeclaredFields()) {
                if (field.getType() != String.class) continue;
                field.setAccessible(true);
                if (value.equals(field.get(item))) return true;
            }
            type = type.getSuperclass();
        }
        return false;
    }

    private static Object newItem(Object like, boolean isPrivate) throws Exception {
        for (Constructor<?> constructor : like.getClass().getDeclaredConstructors()) {
            Class<?>[] p = constructor.getParameterTypes();
            if (p.length == 6 && p[1] == int.class && p[2] == String.class && p[5] == int.class) {
                constructor.setAccessible(true);
                return constructor.newInstance(
                    newText(p[3]), icon(), DUPLICATE_TAG, null, TabCloseDuplicates.of(isPrivate), 0x34);
            }
        }
        throw new NoSuchMethodException("IconItem constructor");
    }

    private static Object newText(Class<?> type) throws Exception {
        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            Class<?>[] p = constructor.getParameterTypes();
            if (p.length == 1 && p[0] == String.class) {
                constructor.setAccessible(true);
                return constructor.newInstance(DUPLICATE_LABEL);
            }
        }
        throw new NoSuchMethodException("Text constructor");
    }

    private static int icon() throws Exception {
        Application app = (Application) Class.forName("android.app.ActivityThread")
            .getMethod("currentApplication").invoke(null);
        int id = app.getResources().getIdentifier("mozac_ic_delete_24", "drawable", app.getPackageName());
        if (id == 0) throw new Resources.NotFoundException("mozac_ic_delete_24");
        return id;
    }
}
