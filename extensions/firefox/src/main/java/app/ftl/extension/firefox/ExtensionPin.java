package app.ftl.extension.firefox;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings({"unused", "rawtypes", "unchecked"})
public final class ExtensionPin {

    private static final String TAG = "MorpheFirefox";
    private static final String PREFS = "ftl_pinned_ext";

    private static final String BROWSER_STATE = "mozilla.components.browser.state.state.BrowserState";
    private static final String SELECTORS = "mozilla.components.browser.state.selector.SelectorsKt";
    private static final String ACTION_BUTTON = "mozilla.components.compose.browser.toolbar.concept.Action$ActionButton";
    private static final String BUTTON_STATE = ACTION_BUTTON + "$State";
    private static final String INTERACTION = "mozilla.components.compose.browser.toolbar.store.BrowserToolbarInteraction";
    private static final String ENDS_UPDATED =
        "mozilla.components.compose.browser.toolbar.store.BrowserDisplayToolbarAction$BrowserActionsEndUpdated";

    private static Context appCtx;
    private static WeakReference<Object> bstore;
    private static WeakReference<Object> store;
    private static WeakReference<Object> mw;
    private static final ConcurrentHashMap<String, PinState> cache = new ConcurrentHashMap<>();
    private static Handler handler;
    private static int lastHash;
    private static String raw;
    private static boolean started;
    private static Object sub;

    private ExtensionPin() {
    }

    static String[] ids(Context context) {
        if (raw == null) raw = prefs(context).getString("ids", "");
        return raw.isEmpty() ? new String[0] : raw.split("\n");
    }

    static void toggle(Context context, Object item) {
        try {
            String id = (String) field(item, "id");
            if (isPinned(context, id)) {
                unpin(context, id);
                refresh();
                return;
            }
            if (visibleCount(context) >= maxPins()) {
                Toast.makeText(context, "Pin limit: 5. Expanded toolbar layout allows more.", Toast.LENGTH_LONG).show();
                return;
            }
            pin(context, item);
            kick();
        } catch (Throwable t) {
            Log.e(TAG, "toggle failed", t);
        }
    }

    static void kick() {
        if (handler == null) sync();
        else handler.post(new PinRun());
    }

    static PinEvent event(String id, boolean isLong) {
        return new PinEvent(id, isLong);
    }

    static int px(Context context) {
        return (int) (context.getResources().getDisplayMetrics().density * 24f + 0.5f);
    }

    static Bitmap savedIcon(Context context, String id) {
        try {
            String data = prefs(context).getString("i:" + id, null);
            if (data == null) return null;
            byte[] bytes = Base64.decode(data, 0);
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        } catch (Throwable t) {
            return null;
        }
    }

    static PinState state(String id) {
        PinState state = cache.get(id);
        if (state == null) {
            state = new PinState();
            state.id = id;
            cache.put(id, state);
        }
        return state;
    }

    private static Object action(Object extensionState) throws Exception {
        if (extensionState == null) return null;
        Object action = field(extensionState, "browserAction");
        return action != null ? action : field(extensionState, "pageAction");
    }

    private static Object value(Object action, String name) throws Exception {
        return action == null ? null : field(action, name);
    }

    private static Object pick(Object tabAction, Object globalAction, String name) throws Exception {
        Object value = value(tabAction, name);
        return value != null ? value : value(globalAction, name);
    }

    public static void start(Object middleware) {
        if (started) return;
        started = true;
        try {
            Context activity = (Context) field(middleware, "uiContext");
            appCtx = activity.getApplicationContext();
            ids(appCtx);
            Object browserStore = field(middleware, "browserStore");
            bstore = new WeakReference<>(browserStore);
            handler = new Handler(Looper.getMainLooper());
            Class<?> function1 = Class.forName("kotlin.jvm.functions.Function1");
            Method observe = browserStore.getClass().getMethod("observeManually", function1);
            Object subscription = observe.invoke(browserStore, new PinObserver());
            subscription.getClass().getMethod("resume").invoke(subscription);
            sub = subscription;
            kick();
        } catch (Throwable t) {
            Log.e(TAG, "start failed", t);
        }
    }

    public static void remember(Object toolbarStore, Object middleware) {
        try {
            if (ModSettings.pinEnabled()) start(middleware);
            if (store != null && store.get() == toolbarStore) return;
            store = new WeakReference<>(toolbarStore);
            mw = new WeakReference<>(middleware);
        } catch (Throwable t) {
            Log.e(TAG, "remember failed", t);
        }
    }

    static void onState(Object browserState) throws Exception {
        if (raw == null || raw.isEmpty() || appCtx == null) return;
        Map extensions = (Map) field(browserState, "extensions");
        Map tabExtensions = tabExtensionState(browserState);
        String[] ids = ids(appCtx);
        int hash = 17;
        for (String id : ids) {
            hash = hash * 31 + System.identityHashCode(extensions.get(id));
            hash = hash * 31 + System.identityHashCode(tabExtensions != null ? tabExtensions.get(id) : null);
        }
        hash = hash * 31 + ids.length;
        if (hash == lastHash) return;
        lastHash = hash;
        if (handler != null) handler.post(new PinRun());
    }

    private static Map tabExtensionState(Object browserState) throws Exception {
        Class<?> state = Class.forName(BROWSER_STATE);
        Object tab = Class.forName(SELECTORS).getMethod("getSelectedTab", state).invoke(null, browserState);
        if (tab == null) return null;
        return (Map) tab.getClass().getMethod("getExtensionState").invoke(tab);
    }

    static void sync() {
        try {
            Context context = appCtx;
            if (context == null || bstore == null) return;
            Object browserStore = bstore.get();
            if (browserStore == null) return;
            Object browserState = browserStore.getClass().getMethod("getState").invoke(browserStore);
            Map extensions = (Map) field(browserState, "extensions");
            Map tabExtensions = tabExtensionState(browserState);
            for (String id : ids(context)) syncOne(context, id, extensions, tabExtensions);
            refresh();
        } catch (Throwable t) {
            Log.e(TAG, "sync failed", t);
        }
    }

    static void syncOne(Context context, String id, Map extensions, Map tabExtensions) {
        try {
            Object extension = extensions.get(id);
            PinState st = state(id);
            if (extension == null) {
                if (!extensions.isEmpty()) st.hidden = true;
                return;
            }
            if (!(Boolean) field(extension, "enabled")) {
                st.hidden = true;
                return;
            }
            st.hidden = false;

            Object tabAction = tabExtensions != null ? action(tabExtensions.get(id)) : null;
            Object globalAction = action(extension);

            String title = (String) pick(tabAction, globalAction, "title");
            if (title == null) title = (String) field(extension, "name");
            st.title = title;
            st.enabled = (Boolean) pick(tabAction, globalAction, "enabled");
            st.text = (String) pick(tabAction, globalAction, "badgeText");
            st.bg = (Integer) pick(tabAction, globalAction, "badgeBackgroundColor");
            st.fg = (Integer) pick(tabAction, globalAction, "badgeTextColor");

            Object loader = pick(tabAction, globalAction, "loadIcon");
            if (loader != null && loader != st.loader) {
                st.loader = loader;
                Class<?> function2 = Class.forName("kotlin.jvm.functions.Function2");
                Object result = function2.getMethod("invoke", Object.class, Object.class)
                    .invoke(loader, px(context), new PinCont(st));
                Object suspended = Class.forName("kotlin.coroutines.intrinsics.CoroutineSingletons")
                    .getField("COROUTINE_SUSPENDED").get(null);
                if (result != suspended && result instanceof Bitmap) st.bitmap = (Bitmap) result;
            }
        } catch (Throwable t) {
            Log.e(TAG, "syncOne failed", t);
        }
    }

    static void drawBadge(Canvas canvas, Paint paint, int size, String text, Integer bg, Integer fg) {
        float s = size;
        int background = (bg != null ? bg : 0xFF5E5E5E) | 0xFF000000;
        int foreground = (fg != null ? fg : -1) | 0xFF000000;
        paint.setTextSize(0.42f * s);
        paint.setFakeBoldText(true);
        paint.setTextAlign(Paint.Align.CENTER);
        float width = paint.measureText(text) + 0.12f * s * 2f;
        float height = s * 0.5f;
        if (width < height) width = height;
        if (width > s) width = s;
        float left = s - width;
        float top = s - height;
        float radius = height * 0.5f;
        paint.setColor(background);
        canvas.drawRoundRect(left, top, s, s, radius, radius, paint);
        paint.setColor(foreground);
        Paint.FontMetrics fm = paint.getFontMetrics();
        float baseline = height * 0.5f - (fm.ascent + fm.descent) * 0.5f + top;
        canvas.drawText(text, left + width * 0.5f, baseline, paint);
    }

    static Bitmap render(Context context, Bitmap icon, String text, Integer bg, Integer fg, Boolean enabled) {
        int size = px(context);
        Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setFilterBitmap(true);
        if (Boolean.FALSE.equals(enabled)) paint.setAlpha(0x66);
        Rect bounds = new Rect(0, 0, size, size);
        if (icon != null) {
            canvas.drawBitmap(icon, null, bounds, paint);
        } else {
            int id = context.getResources().getIdentifier("mozac_ic_extension_fill_24", "drawable", context.getPackageName());
            if (id == 0) id = context.getResources().getIdentifier("mozac_ic_extension_24", "drawable", context.getPackageName());
            Drawable drawable = context.getDrawable(id);
            if (drawable != null) {
                drawable.setBounds(bounds);
                drawable.draw(canvas);
            }
        }
        if (text != null && !text.isEmpty()) {
            paint.setAlpha(0xFF);
            drawBadge(canvas, paint, size, text, bg, fg);
        }
        return out;
    }

    static Object makeButton(Context context, String id) throws Exception {
        PinState st = cache.get(id);
        Bitmap bitmap = null;
        String title = null;
        String text = null;
        Integer bg = null;
        Integer fg = null;
        Boolean enabled = null;
        if (st != null) {
            if (st.hidden) return null;
            bitmap = st.bitmap;
            title = st.title;
            text = st.text;
            bg = st.bg;
            fg = st.fg;
            enabled = st.enabled;
        }
        if (bitmap == null) bitmap = savedIcon(context, id);
        if (title == null) title = prefs(context).getString("l:" + id, id);

        Drawable drawable = new NoTintDrawable(context.getResources(), render(context, bitmap, text, bg, fg, enabled));
        Class<?> button = Class.forName(ACTION_BUTTON);
        Class<?> buttonState = Class.forName(BUTTON_STATE);
        Class<?> interaction = Class.forName(INTERACTION);
        Constructor<?> constructor = button.getDeclaredConstructor(
            Drawable.class, boolean.class, String.class, buttonState, boolean.class, interaction, interaction, String.class);
        constructor.setAccessible(true);
        return constructor.newInstance(drawable, false, title, buttonState.getField("DEFAULT").get(null), false,
            event(id, false), event(id, true), null);
    }

    public static void addPinned(ArrayList list, Object middleware) {
        try {
            if (!ModSettings.pinEnabled()) return;
            Context context = (Context) field(middleware, "uiContext");
            String[] ids = ids(context);
            int max = maxOf(middleware);
            int added = 0;
            for (int i = 0; i < ids.length && added < max; i++) {
                Object button = makeButton(context, ids[i]);
                if (button != null) {
                    list.add(button);
                    added++;
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "addPinned failed", t);
        }
    }

    static void click(Object middleware, String id) throws Exception {
        Object browserStore = field(middleware, "browserStore");
        Object browserState = browserStore.getClass().getMethod("getState").invoke(browserStore);
        Map extensions = (Map) field(browserState, "extensions");
        Object action = action(extensions.get(id));
        Map tabExtensions = tabExtensionState(browserState);
        Object tabAction = tabExtensions != null ? action(tabExtensions.get(id)) : null;
        if (tabAction != null) action = tabAction;
        if (action != null) invokeFunction0(field(action, "onClick"));
    }

    static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, 0);
    }

    static boolean isPinned(Context context, String id) {
        for (String pinned : ids(context)) if (pinned.equals(id)) return true;
        return false;
    }

    static void pin(Context context, Object item) throws Exception {
        String id = (String) field(item, "id");
        if (isPinned(context, id)) return;
        SharedPreferences prefs = prefs(context);
        String current = prefs.getString("ids", "");
        String updated = current.isEmpty() ? id : current + "\n" + id;
        raw = updated;
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("ids", updated);
        editor.putString("l:" + id, (String) field(item, "label"));
        Bitmap icon = (Bitmap) field(item, "icon");
        if (icon != null) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            icon.compress(Bitmap.CompressFormat.PNG, 100, out);
            editor.putString("i:" + id, Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP));
        }
        editor.apply();
    }

    static void unpin(Context context, String id) {
        StringBuilder builder = new StringBuilder();
        for (String pinned : ids(context)) {
            if (pinned.equals(id)) continue;
            if (builder.length() != 0) builder.append("\n");
            builder.append(pinned);
        }
        raw = builder.toString();
        prefs(context).edit().putString("ids", raw).remove("l:" + id).remove("i:" + id).apply();
    }

    public static Object wrap(Object base, Object item, Context context) {
        if (!ModSettings.pinEnabled()) return base;
        return new PinClick(base, new PinLong(context, item));
    }

    public static Object longOf(Object function) {
        return function instanceof PinClick ? ((PinClick) function).onLong : null;
    }

    static void refresh() {
        try {
            if (store == null || mw == null) return;
            Object toolbarStore = store.get();
            Object middleware = mw.get();
            if (toolbarStore == null || middleware == null) return;

            ArrayList actions = new ArrayList();
            addPinned(actions, middleware);

            Object state = toolbarStore.getClass().getMethod("getState").invoke(toolbarStore);
            Object display = field(state, "displayState");
            List current = (List) field(display, "browserActionsEnd");
            Class<?> buttonClass = Class.forName(ACTION_BUTTON);
            for (Iterator it = current.iterator(); it.hasNext(); ) {
                Object entry = it.next();
                if (buttonClass.isInstance(entry) && field(entry, "onClick") instanceof PinEvent) continue;
                actions.add(entry);
            }

            Object update = Class.forName(ENDS_UPDATED).getConstructor(List.class).newInstance(actions);
            Class<?> action = Class.forName("mozilla.components.lib.state.Action");
            toolbarStore.getClass().getMethod("dispatch", action).invoke(toolbarStore, update);
        } catch (Throwable t) {
            Log.e(TAG, "refresh failed", t);
        }
    }

    static void applySetting() {
        try {
            Object middleware = mw != null ? mw.get() : null;
            if (ModSettings.pinEnabled() && middleware != null) {
                start(middleware);
                kick();
            } else {
                refresh();
            }
        } catch (Throwable t) {
            Log.e(TAG, "applySetting failed", t);
        }
    }

    public static void handle(Object event, Object middleware) {
        try {
            PinEvent pinEvent = (PinEvent) event;
            if (pinEvent.isLong) confirmUnpin((Context) field(middleware, "uiContext"), pinEvent.id);
            else click(middleware, pinEvent.id);
        } catch (Throwable t) {
            Log.e(TAG, "handle failed", t);
        }
    }

    static int maxOf(Object middleware) {
        try {
            Object settings = field(middleware, "settings");
            boolean expanded = (Boolean) settings.getClass().getMethod("getShouldUseExpandedToolbar").invoke(settings);
            return expanded ? Integer.MAX_VALUE : 5;
        } catch (Throwable t) {
            return 5;
        }
    }

    static int maxPins() {
        Object middleware = mw != null ? mw.get() : null;
        return middleware != null ? maxOf(middleware) : 5;
    }

    static int visibleCount(Context context) {
        int count = 0;
        for (String id : ids(context)) {
            PinState st = cache.get(id);
            if (st == null || !st.hidden) count++;
        }
        return count;
    }

    static void confirmUnpin(final Context context, final String id) {
        PinState st = cache.get(id);
        String title = st != null ? st.title : null;
        if (title == null) title = prefs(context).getString("l:" + id, id);
        new AlertDialog.Builder(context)
            .setTitle(title)
            .setPositiveButton("Unpin", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    unpin(context, id);
                    refresh();
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    static void showDialog(final Context context, final Object item) {
        try {
            String id = (String) field(item, "id");
            boolean pinned = isPinned(context, id);
            new AlertDialog.Builder(context)
                .setTitle((String) field(item, "label"))
                .setItems(new CharSequence[]{pinned ? "Unpin from top bar" : "Pin to top bar"},
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            toggle(context, item);
                        }
                    })
                .show();
        } catch (Throwable t) {
            Log.e(TAG, "showDialog failed", t);
        }
    }

    static Object invokeFunction0(Object function) {
        try {
            if (function == null) return null;
            return Class.forName("kotlin.jvm.functions.Function0").getMethod("invoke").invoke(function);
        } catch (Throwable t) {
            Log.e(TAG, "invoke failed", t);
            return null;
        }
    }

    private static Object field(Object target, String name) throws Exception {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException e) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }
}
