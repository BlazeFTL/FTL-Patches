package app.ftl.extension.firefox;

import android.os.SystemClock;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings({"unchecked", "rawtypes", "unused"})
public final class TabOrder {

    private static final String TAG = "MorpheFirefox";
    private static final long END_WINDOW_MS = 3000L;
    private static final Map<String, Field> FIELDS = new ConcurrentHashMap<>();

    private static volatile long endUntil;

    private TabOrder() {
    }

    public static void markEnd() {
        endUntil = SystemClock.uptimeMillis() + END_WINDOW_MS;
    }

    public static ArrayList add(Object tab, Collection tabs, String selectedId) {
        ArrayList list = new ArrayList(tabs);
        int at = list.size();
        try {
            boolean end = consumeEnd();
            if (!end && selectedId != null && ModSettings.tabNext()) {
                int selected = indexOfSelected(list, tab, selectedId);
                if (selected >= 0) at = selected + 1;
            }
        } catch (Throwable t) {
            Log.e(TAG, "tab order failed", t);
            at = list.size();
        }
        list.add(at, tab);
        return list;
    }

    private static boolean consumeEnd() {
        long until = endUntil;
        if (until == 0L) return false;
        endUntil = 0L;
        return SystemClock.uptimeMillis() <= until;
    }

    private static int indexOfSelected(ArrayList list, Object tab, String selectedId) throws Exception {
        boolean isPrivate = isPrivate(tab);
        for (int i = 0, n = list.size(); i < n; i++) {
            Object item = list.get(i);
            if (selectedId.equals(call(item, "getId"))) {
                return isPrivate(item) == isPrivate ? i : -1;
            }
        }
        return -1;
    }

    private static boolean isPrivate(Object tab) throws Exception {
        Object content = call(tab, "getContent");
        return Boolean.TRUE.equals(privateFlag(content));
    }

    private static Object call(Object target, String name) throws Exception {
        return target.getClass().getMethod(name).invoke(target);
    }

    private static Object privateFlag(Object content) throws Exception {
        Class<?> type = content.getClass();
        String key = type.getName() + "#private";
        Field field = FIELDS.get(key);
        if (field == null) {
            try {
                field = type.getDeclaredField("private");
            } catch (NoSuchFieldException e) {
                for (Field candidate : type.getDeclaredFields()) {
                    if (candidate.getType() == boolean.class && !Modifier.isStatic(candidate.getModifiers())) {
                        field = candidate;
                        break;
                    }
                }
            }
            if (field == null) throw new NoSuchFieldException("private");
            field.setAccessible(true);
            FIELDS.put(key, field);
        }
        return field.get(content);
    }

    private static Object read(Object target, String name) throws Exception {
        Class<?> type = target.getClass();
        String key = type.getName() + '#' + name;
        Field field = FIELDS.get(key);
        if (field == null) {
            Class<?> current = type;
            while (current != null && field == null) {
                try {
                    field = current.getDeclaredField(name);
                } catch (NoSuchFieldException e) {
                    current = current.getSuperclass();
                }
            }
            if (field == null) throw new NoSuchFieldException(name);
            field.setAccessible(true);
            FIELDS.put(key, field);
        }
        return field.get(target);
    }
}
