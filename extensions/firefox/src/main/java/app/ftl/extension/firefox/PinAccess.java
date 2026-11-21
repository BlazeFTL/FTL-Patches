package app.ftl.extension.firefox;

import android.graphics.drawable.Drawable;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Typed accessors for obfuscated Android-Components extension and toolbar types. The ftl* methods do not
 * exist in this source: the patch generates them into this class (see ExtensionPinPatch.kt) and they are
 * called by reflection, so R8 cannot inline or rename them.
 */
@SuppressWarnings({"unchecked", "rawtypes", "unused"})
public final class PinAccess {

    // Index of each value in the array returned by actionInfo().
    static final int A_TITLE = 0;
    static final int A_ENABLED = 1;
    static final int A_ICON = 2;
    static final int A_BADGE = 3;
    static final int A_FG = 4;
    static final int A_BG = 5;
    static final int A_CLICK = 6;

    // Index of each value in the array returned by extInfo().
    static final int E_ENABLED = 0;
    static final int E_BROWSER = 1;
    static final int E_PAGE = 2;

    private static final Map<String, Method> METHODS = new ConcurrentHashMap<>();

    private PinAccess() {
    }

    private static Object call(String name, Class<?>[] types, Object... args) throws Exception {
        Method method = METHODS.get(name);
        if (method == null) {
            method = PinAccess.class.getMethod(name, types);
            METHODS.put(name, method);
        }
        return method.invoke(null, args);
    }

    static Map extensions(Object browserState) throws Exception {
        return (Map) call("ftlExtensions", new Class<?>[]{Object.class}, browserState);
    }

    static Map tabExtensions(Object tab) throws Exception {
        return (Map) call("ftlTabExtensions", new Class<?>[]{Object.class}, tab);
    }

    // {enabled (Boolean), browserAction, pageAction}
    static Object[] extInfo(Object extensionState) throws Exception {
        return (Object[]) call("ftlExtInfo", new Class<?>[]{Object.class}, extensionState);
    }

    // {title, enabled, loadIcon, badgeText, badgeTextColor, badgeBackgroundColor, onClick}
    static Object[] actionInfo(Object action) throws Exception {
        return (Object[]) call("ftlActionInfo", new Class<?>[]{Object.class}, action);
    }

    static Object invoke0(Object function) throws Exception {
        return call("ftlInvoke0", new Class<?>[]{Object.class}, function);
    }

    static Object invoke2(Object function, Object first, Object second) throws Exception {
        return call("ftlInvoke2", new Class<?>[]{Object.class, Object.class, Object.class}, function, first, second);
    }

    static Object button(Drawable drawable, String title, Object event) throws Exception {
        return call("ftlButton", new Class<?>[]{Drawable.class, String.class, Object.class}, drawable, title, event);
    }

    static Object buttonClick(Object entry) {
        try {
            return call("ftlButtonClick", new Class<?>[]{Object.class}, entry);
        } catch (Throwable t) {
            return null;
        }
    }

    static Object ends(List actions) throws Exception {
        return call("ftlEnds", new Class<?>[]{List.class}, actions);
    }

    static void dispatch(Object store, Object action) throws Exception {
        call("ftlDispatch", new Class<?>[]{Object.class, Object.class}, store, action);
    }
}
