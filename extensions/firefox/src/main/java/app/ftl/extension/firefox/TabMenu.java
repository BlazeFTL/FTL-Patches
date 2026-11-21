package app.ftl.extension.firefox;

import android.util.Log;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings({"unchecked", "rawtypes", "unused"})
public final class TabMenu {

    private static final String TAG = "MorpheFirefox";
    private static final String CLOSE_ALL_TAG = "tabstray.banner.threeDotButton.closeAllTabs";
    private static final String DUPLICATE_TAG = "tabstray.banner.threeDotButton.closeDuplicateTabs";
    private static final String DUPLICATE_LABEL = "Close duplicate tabs";
    private static final int ITEM_DEFAULTS = 0x34;

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

    // TabsTrayState.getSelectedPage() is a Fenix member, so it keeps its name.
    private static boolean isPrivatePage() throws Exception {
        Object state = trayState;
        if (state == null) return false;
        Method getter = state.getClass().getMethod("getSelectedPage");
        Object page = getter.invoke(state);
        return page instanceof Enum && "PrivateTabs".equals(((Enum) page).name());
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

    // Menu IconItem ctor (Text, int icon, String testTag, Text.String description, Function0 onClick, int defaults).
    // Found by shape, not by name: types are R8-renamed. Text.String is the 4th parameter type.
    // The icon id is read from the close all item, which is also the delete icon.
    private static Object newItem(Object like, boolean isPrivate) throws Exception {
        Constructor<?> target = null;
        for (Constructor<?> constructor : like.getClass().getDeclaredConstructors()) {
            Class<?>[] p = constructor.getParameterTypes();
            if (p.length == 6 && p[1] == int.class && p[2] == String.class && p[5] == int.class
                && !p[0].isPrimitive() && !p[3].isPrimitive() && !p[4].isPrimitive()) {
                target = constructor;
                break;
            }
        }
        if (target == null) throw new NoSuchMethodException("IconItem constructor");

        Class<?> textType = target.getParameterTypes()[3];
        Object text = textType.getConstructor(String.class).newInstance(DUPLICATE_LABEL);
        int icon = iconOf(like);
        Object action = TabCloseDuplicates.of(isPrivate);
        target.setAccessible(true);
        return target.newInstance(text, icon, DUPLICATE_TAG, null, action, ITEM_DEFAULTS);
    }

    private static int iconOf(Object item) throws Exception {
        Class<?> type = item.getClass();
        while (type != null && type != Object.class) {
            for (Field field : type.getDeclaredFields()) {
                if (field.getType() != int.class) continue;
                field.setAccessible(true);
                return field.getInt(item);
            }
            type = type.getSuperclass();
        }
        throw new NoSuchFieldException("icon");
    }
}
