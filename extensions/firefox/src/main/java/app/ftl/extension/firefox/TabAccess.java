package app.ftl.extension.firefox;

import android.util.Log;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Typed accessors for obfuscated Android-Components tab state. The ftl* methods do not exist in this
 * source: the patch generates them into this class (see Accessors.kt) and they are called by
 * reflection, so R8 cannot inline or rename them.
 */
@SuppressWarnings({"unchecked", "rawtypes", "unused"})
public final class TabAccess {

    private static final String TAG = "MorpheFirefox";
    private static final Map<String, Method> METHODS = new ConcurrentHashMap<>();

    private TabAccess() {
    }

    private static Object call(String name, Class<?>[] types, Object... args) throws Exception {
        Method method = METHODS.get(name);
        if (method == null) {
            method = TabAccess.class.getMethod(name, types);
            METHODS.put(name, method);
        }
        return method.invoke(null, args);
    }

    public static List tabs(Object state) throws Exception {
        return (List) call("ftlTabs", new Class<?>[]{Object.class}, state);
    }

    public static String selected(Object state) throws Exception {
        return (String) call("ftlSelected", new Class<?>[]{Object.class}, state);
    }

    public static String id(Object tab) throws Exception {
        return (String) call("ftlTabId", new Class<?>[]{Object.class}, tab);
    }

    public static String url(Object tab) throws Exception {
        return (String) call("ftlTabUrl", new Class<?>[]{Object.class}, tab);
    }

    public static boolean isPrivate(Object tab) throws Exception {
        return (Boolean) call("ftlTabPrivate", new Class<?>[]{Object.class}, tab);
    }

    public static Object state(Object store) throws Exception {
        return call("ftlState", new Class<?>[]{Object.class}, store);
    }

    public static void remove(Object store, List ids, Set excluded) throws Exception {
        call("ftlRemove", new Class<?>[]{Object.class, List.class, Set.class}, store, ids, excluded);
    }

    public static Object unit() {
        try {
            return call("ftlUnit", new Class<?>[0]);
        } catch (Throwable t) {
            Log.e(TAG, "unit lookup failed", t);
            return null;
        }
    }

    public static Object invoke0(Object function) {
        try {
            if (function == null) return null;
            Method method = function.getClass().getMethod("invoke");
            method.setAccessible(true);
            return method.invoke(function);
        } catch (Throwable t) {
            Log.e(TAG, "invoke failed", t);
            return null;
        }
    }
}
