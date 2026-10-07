package app.ftl.extension.firefox;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.view.WindowInsets;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AlphaAnimation;
import android.view.animation.AnimationSet;
import android.view.animation.ScaleAnimation;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

@SuppressWarnings("unused")
public final class OldMenu {

    private static final String TAG = "MorpheFirefox";
    private static final String BEHAVIOR = "com.google.android.material.bottomsheet.BottomSheetBehavior";
    private static final int FLAG_DIM_BEHIND = 2;

    public static boolean extensionsActive;

    private static boolean bottomToolbar;

    private static Context appContext;

    private static java.util.Set<String> longBadges;

    private static WeakReference<Dialog> dialogRef = new WeakReference<>(null);
    private static WeakReference<View> sheetRef = new WeakReference<>(null);
    private static WeakReference<Object> behaviorRef = new WeakReference<>(null);
    private static WeakReference<View> enterPlayedFor = new WeakReference<>(null);

    private OldMenu() {
    }

    private static final ViewOutlineProvider OUTLINE = new ViewOutlineProvider() {
        @Override
        public void getOutline(View view, Outline outline) {
            outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(view.getContext(), 12));
        }
    };

    private static final View.OnLayoutChangeListener LAYOUT = new View.OnLayoutChangeListener() {
        @Override
        public void onLayoutChange(View v, int left, int top, int right, int bottom,
                                   int oldLeft, int oldTop, int oldRight, int oldBottom) {
            if (isBottom(v.getContext())) {
                v.setTranslationY(-dp(v.getContext(), 8));
                return;
            }
            WindowInsets insets = v.getRootWindowInsets();
            int statusBar = insets != null ? insets.getSystemWindowInsetTop() : 0;
            int target = statusBar + (int) dp(v.getContext(), 8);
            int height = bottom - top;
            int parentHeight = v.getParent() instanceof View ? ((View) v.getParent()).getHeight() : 0;
            int slack = Math.max(parentHeight - height, 0);
            v.setTranslationY(target - slack);
        }
    };

    public static void onCreateDialog(Dialog dialog) {
        try {
            ModSettings.latch(dialog.getContext());
            extensionsActive = false;
            bottomToolbar = isBottom(dialog.getContext());
            appContext = dialog.getContext().getApplicationContext();
            if (!ModSettings.oldMenu()) return;
            Window window = dialog.getWindow();
            if (window == null) return;
            window.clearFlags(FLAG_DIM_BEHIND);
            window.setDimAmount(0f);
            window.setWindowAnimations(animStyle(dialog.getContext()));
        } catch (Throwable t) {
            Log.e(TAG, "onCreateDialog failed", t);
        }
    }

    public static void onShowStart(DialogInterface dialog) {
        if (dialog instanceof Dialog) dialogRef = new WeakReference<>((Dialog) dialog);
    }

    public static void afterShow() {
        if (!ModSettings.oldMenu()) return;
        apply(dialogRef.get());
    }

    public static void onViewCreated(Dialog dialog) {
        if (!ModSettings.oldMenu() || dialog == null) return;
        dialogRef = new WeakReference<>(dialog);
        apply(dialog);
        View sheet = findSheet(dialog);
        if (sheet != null) playEnter(sheet);
    }

    public static int menuWidth(Resources resources, int dp) {
        return (int) (resources.getDisplayMetrics().density * dp);
    }

    public static void setExtensionsActive(Object accessPoint, boolean expanded) {
        boolean browser = accessPoint instanceof Enum && "Browser".equals(((Enum<?>) accessPoint).name());
        extensionsActive = expanded && browser;
        applyWidth(extensionsActive ? 314 : 240);
    }

    public static float navPadding() {
        return bottomToolbar ? 9f : 12f;
    }

    public static float bottomPadding(float stock, boolean expanded) {
        if (stock == 16f || expanded) return bottomToolbar ? 0f : 6f;
        return 48f;
    }

    public static boolean hideBadge(String text) {
        try {
            if (text == null || !ModSettings.oldMenu() || appContext == null) return false;
            if (longBadges == null) {
                java.util.Set<String> set = new java.util.HashSet<>();
                Resources res = appContext.getResources();
                String[] names = {
                    "ip_protection_menu_try_vpn_cta",
                    "ip_protection_menu_error",
                    "ip_protection_menu_paused",
                    "ip_protection_menu_connecting",
                };
                for (String name : names) {
                    int id = res.getIdentifier(name, "string", appContext.getPackageName());
                    if (id != 0) set.add(res.getString(id));
                }
                longBadges = set;
            }
            return longBadges.contains(text);
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean showBottomDivider() {
        return bottomToolbar && ModSettings.oldMenu();
    }

    private static int modIcon;

    public static int modIcon(Context context) {
        if (modIcon != 0) return modIcon;
        Resources res = context.getResources();
        String pkg = context.getPackageName();
        String[] names = {
            "ftl_ic_mod_settings",
            "mozac_ic_customize_24",
            "mozac_ic_sparkle_24",
            "mozac_ic_experiment_24",
            "mozac_ic_settings_24",
        };
        for (String name : names) {
            int id = res.getIdentifier(name, "drawable", pkg);
            if (id != 0) {
                modIcon = id;
                break;
            }
        }
        return modIcon;
    }

    public static Object trailingIcon(Object stock) {
        return ModSettings.oldMenu() ? null : stock;
    }

    public static String accountSubtitle(String stock) {
        return ModSettings.oldMenu() ? null : stock;
    }

    private static void apply(Dialog dialog) {
        if (dialog == null) return;
        try {
            Window window = dialog.getWindow();
            if (window != null) {
                window.clearFlags(FLAG_DIM_BEHIND);
                window.setWindowAnimations(animStyle(window.getContext()));
                Drawable bg = window.getDecorView().getBackground();
                if (bg instanceof ColorDrawable && ((ColorDrawable) bg).getAlpha() == 100) {
                    window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                }
            }

            View sheet = findSheet(dialog);
            if (sheet == null) return;

            if (sheetRef.get() != sheet) {
                sheetRef = new WeakReference<>(sheet);
                sheet.addOnLayoutChangeListener(LAYOUT);
                sheet.setOutlineProvider(OUTLINE);
                sheet.setClipToOutline(true);
            }

            Object behavior = behaviorOf(sheet);
            behaviorRef = new WeakReference<>(behavior);
            if (behavior != null) {
                setHideable(behavior, false);
                setBoolean(behavior, "draggable", false);
            }

            float density = sheet.getContext().getResources().getDisplayMetrics().density;
            Drawable bg = sheet.getBackground();
            if (bg instanceof GradientDrawable) ((GradientDrawable) bg).setCornerRadius(12 * density);
            sheet.setElevation(8 * density);

            ViewGroup.LayoutParams lp = sheet.getLayoutParams();
            if (lp != null && lp.getClass().getName().equals("androidx.coordinatorlayout.widget.CoordinatorLayout$LayoutParams")) {
                lp.getClass().getField("gravity").setInt(lp, 0x800035);
                ((ViewGroup.MarginLayoutParams) lp).setMarginEnd((int) (8 * density));
                sheet.setLayoutParams(lp);
            }
        } catch (Throwable t) {
            Log.e(TAG, "apply failed", t);
        }
    }

    private static void applyWidth(int dp) {
        try {
            View sheet = sheetRef.get();
            Object behavior = behaviorRef.get();
            if (sheet == null || behavior == null) return;
            Resources res = sheet.getContext().getResources();
            int width = Math.min(menuWidth(res, dp), res.getDisplayMetrics().widthPixels);
            Field f = behavior.getClass().getDeclaredField("maxWidth");
            f.setAccessible(true);
            if (f.getInt(behavior) != width) {
                f.setInt(behavior, width);
                sheet.requestLayout();
            }
        } catch (Throwable t) {
            Log.e(TAG, "applyWidth failed", t);
        }
    }

    private static View findSheet(Dialog dialog) {
        Context c = dialog.getContext();
        int id = c.getResources().getIdentifier("design_bottom_sheet", "id", c.getPackageName());
        return id == 0 ? null : dialog.findViewById(id);
    }

    private static Object behaviorOf(View sheet) {
        try {
            Method from = Class.forName(BEHAVIOR).getMethod("from", View.class);
            return from.invoke(null, sheet);
        } catch (Throwable t) {
            return null;
        }
    }

    private static void setHideable(Object behavior, boolean value) {
        try {
            behavior.getClass().getMethod("setHideable", boolean.class).invoke(behavior, value);
        } catch (Throwable ignored) {
        }
        setBoolean(behavior, "hideable", value);
    }

    private static void setBoolean(Object target, String field, boolean value) {
        try {
            Field f = Class.forName(BEHAVIOR).getDeclaredField(field);
            f.setAccessible(true);
            f.setBoolean(target, value);
        } catch (Throwable ignored) {
        }
    }

    private static void playEnter(View sheet) {
        if (enterPlayedFor.get() == sheet || !isBottom(sheet.getContext())) return;
        enterPlayedFor = new WeakReference<>(sheet);

        AnimationSet set = new AnimationSet(true);
        set.addAnimation(new ScaleAnimation(0f, 1f, 0f, 1f,
            ScaleAnimation.RELATIVE_TO_SELF, 0.95f, ScaleAnimation.RELATIVE_TO_SELF, 0.95f));
        set.addAnimation(new AlphaAnimation(0f, 1f));
        set.setInterpolator(new AccelerateDecelerateInterpolator());
        set.setDuration(200);
        sheet.startAnimation(set);
    }

    private static int animStyle(Context context) {
        if (isBottom(context)) return 0;
        return context.getResources().getIdentifier(
            "Mozac_Browser_Menu_Animation_OverflowMenuTop", "style", context.getPackageName());
    }

    private static boolean isBottom(Context context) {
        try {
            Object settings = settings(context);
            return (Boolean) settings.getClass().getMethod("getShouldUseBottomToolbar").invoke(settings);
        } catch (Throwable ignored) {
        }
        try {
            Object settings = settings(context);
            Object position = settings.getClass().getMethod("getToolbarPosition").invoke(settings);
            return "BOTTOM".equals(((Enum<?>) position).name());
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Object settings(Context context) throws Exception {
        Class<?> contextKt = Class.forName("org.mozilla.fenix.ext.ContextKt");
        Object app = contextKt.getMethod("getApplication", Context.class).invoke(null, context);
        Object components = app.getClass().getMethod("getComponents").invoke(app);
        return components.getClass().getMethod("getSettings").invoke(components);
    }

    private static float dp(Context context, float value) {
        return value * context.getResources().getDisplayMetrics().density;
    }
}
