package app.ftl.extension.firefox;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.ContextWrapper;
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
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings({"unused", "rawtypes", "unchecked"})
public final class ExtensionPin {

    private static final String TAG = "MorpheFirefox";
    private static final String PREFS = "ftl_pinned_ext";

    private static final int POLL_MS = 1500;

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
    private static boolean polling;
    private static volatile List lastEnds;

    private ExtensionPin() {
    }

    static String[] ids(Context context) {
        if (raw == null) raw = prefs(context).getString("ids", "");
        return raw.isEmpty() ? new String[0] : raw.split("\n");
    }

    static void toggle(Context context, Object item) {
        try {
            String id = (String) prop(item, "id");
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
        Object[] info = PinAccess.extInfo(extensionState);
        return info[PinAccess.E_BROWSER] != null ? info[PinAccess.E_BROWSER] : info[PinAccess.E_PAGE];
    }

    private static Object[] actionInfo(Object extensionState) throws Exception {
        Object action = action(extensionState);
        return action != null ? PinAccess.actionInfo(action) : null;
    }

    private static Object pick(Object[] tabAction, Object[] globalAction, int index) {
        Object value = tabAction != null ? tabAction[index] : null;
        if (value != null) return value;
        return globalAction != null ? globalAction[index] : null;
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
            try {
                Method observe = null;
                for (Method m : browserStore.getClass().getMethods()) {
                    if (m.getName().equals("observeManually") && m.getParameterTypes().length == 1) {
                        observe = m;
                        break;
                    }
                }
                if (observe == null) throw new NoSuchMethodException("observeManually");
                Object subscription = observe.invoke(browserStore, new PinObserver());
                sub = subscription;
                Method resume = null;
                for (Method m : subscription.getClass().getMethods()) {
                    if (m.getName().equals("resume") && m.getParameterTypes().length == 0) {
                        resume = m;
                        break;
                    }
                }
                if (resume == null) throw new NoSuchMethodException("resume");
                resume.setAccessible(true);
                resume.invoke(subscription);
            } catch (Throwable t) {
                Log.e(TAG, "observe failed, polling", t);
                poll();
            }
            kick();
        } catch (Throwable t) {
            Log.e(TAG, "start failed", t);
        }
    }

    static void poll() {
        if (handler == null || polling) return;
        polling = true;
        handler.postDelayed(new PinPoll(), POLL_MS);
    }

    static void tick() {
        try {
            Object browserStore = bstore != null ? bstore.get() : null;
            if (browserStore != null) onState(TabAccess.state(browserStore));
        } catch (Throwable ignored) {
        }
        if (handler != null) handler.postDelayed(new PinPoll(), POLL_MS);
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
        Map extensions = PinAccess.extensions(browserState);
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
        String selected = TabAccess.selected(browserState);
        if (selected == null) return null;
        List tabs = TabAccess.tabs(browserState);
        for (int i = 0; i < tabs.size(); i++) {
            Object tab = tabs.get(i);
            if (selected.equals(TabAccess.id(tab))) return PinAccess.tabExtensions(tab);
        }
        return null;
    }

    static void sync() {
        try {
            Context context = appCtx;
            if (context == null || bstore == null) return;
            Object browserStore = bstore.get();
            if (browserStore == null) return;
            Object browserState = TabAccess.state(browserStore);
            Map extensions = PinAccess.extensions(browserState);
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
            Object[] info = PinAccess.extInfo(extension);
            if (!Boolean.TRUE.equals(info[PinAccess.E_ENABLED])) {
                st.hidden = true;
                return;
            }
            st.hidden = false;

            Object[] tabAction = tabExtensions != null ? actionInfo(tabExtensions.get(id)) : null;
            Object[] globalAction = actionInfo(extension);

            String title = (String) pick(tabAction, globalAction, PinAccess.A_TITLE);
            if (title == null) title = prefs(context).getString("l:" + id, id);
            st.title = title;
            st.enabled = (Boolean) pick(tabAction, globalAction, PinAccess.A_ENABLED);
            st.text = (String) pick(tabAction, globalAction, PinAccess.A_BADGE);
            st.bg = (Integer) pick(tabAction, globalAction, PinAccess.A_BG);
            st.fg = (Integer) pick(tabAction, globalAction, PinAccess.A_FG);

            Object loader = pick(tabAction, globalAction, PinAccess.A_ICON);
            if (loader != null && loader != st.loader) {
                st.loader = loader;
                Object result = PinAccess.invoke2(loader, px(context), new PinCont(st));
                if (result instanceof Bitmap) st.bitmap = (Bitmap) result;
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
        return PinAccess.button(drawable, title, event(id, false));
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
        Object browserState = TabAccess.state(browserStore);
        Map extensions = PinAccess.extensions(browserState);
        Object action = action(extensions.get(id));
        Map tabExtensions = tabExtensionState(browserState);
        Object tabAction = tabExtensions != null ? action(tabExtensions.get(id)) : null;
        if (tabAction != null) action = tabAction;
        if (action != null) invokeFunction0(PinAccess.actionInfo(action)[PinAccess.A_CLICK]);
    }

    static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, 0);
    }

    static boolean isPinned(Context context, String id) {
        for (String pinned : ids(context)) if (pinned.equals(id)) return true;
        return false;
    }

    static void pin(Context context, Object item) throws Exception {
        String id = (String) prop(item, "id");
        if (isPinned(context, id)) return;
        SharedPreferences prefs = prefs(context);
        String current = prefs.getString("ids", "");
        String updated = current.isEmpty() ? id : current + "\n" + id;
        raw = updated;
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("ids", updated);
        editor.putString("l:" + id, (String) prop(item, "label"));
        Bitmap icon = (Bitmap) prop(item, "icon");
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

    public static Object wrap(Object base, Object item) {
        if (!ModSettings.pinEnabled() || appCtx == null) return base;
        return new PinClick(base, new PinLong(appCtx, item));
    }

    public static Object longOf(Object function) {
        return function instanceof PinClick ? ((PinClick) function).onLong : null;
    }

    // Called with the list the toolbar middleware is about to dispatch as BrowserActionsEndUpdated.
    public static void capture(List list) {
        lastEnds = list;
    }

    static void refresh() {
        try {
            Object toolbarStore = store != null ? store.get() : null;
            Object middleware = mw != null ? mw.get() : null;
            List base = lastEnds;
            if (toolbarStore == null || middleware == null || base == null) return;

            ArrayList actions = new ArrayList();
            addPinned(actions, middleware);
            for (Object entry : new ArrayList(base)) {
                if (PinAccess.buttonClick(entry) instanceof PinEvent) continue;
                actions.add(entry);
            }
            lastEnds = actions;
            PinAccess.dispatch(toolbarStore, PinAccess.ends(actions));
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

    private static Activity activityOf(Context context) {
        Context c = context;
        while (c instanceof ContextWrapper) {
            if (c instanceof Activity) return (Activity) c;
            c = ((ContextWrapper) c).getBaseContext();
        }
        return null;
    }

    private static Activity liveActivity(Context fallback) {
        try {
            Object middleware = mw != null ? mw.get() : null;
            if (middleware != null) {
                Activity activity = activityOf((Context) field(middleware, "uiContext"));
                if (activity != null && !activity.isFinishing()) return activity;
            }
        } catch (Throwable ignored) {
        }
        Activity activity = activityOf(fallback);
        return activity != null && !activity.isFinishing() ? activity : null;
    }

    static void showDialog(final Context context, final Object item) {
        try {
            final Activity activity = liveActivity(context);
            if (activity == null) {
                toggle(context, item);
                return;
            }
            String id = (String) prop(item, "id");
            boolean pinned = isPinned(activity, id);
            new AlertDialog.Builder(activity)
                .setTitle((String) prop(item, "label"))
                .setItems(new CharSequence[]{pinned ? "Unpin from top bar" : "Pin to top bar"},
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            toggle(activity, item);
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
            return PinAccess.invoke0(function);
        } catch (Throwable t) {
            Log.e(TAG, "invoke failed", t);
            return null;
        }
    }

    // Fenix WebExtensionMenuItem keeps its getters. Falls back to the field.
    private static Object prop(Object target, String name) throws Exception {
        String getter = "get" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
        try {
            return target.getClass().getMethod(getter).invoke(target);
        } catch (NoSuchMethodException e) {
            return field(target, name);
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
