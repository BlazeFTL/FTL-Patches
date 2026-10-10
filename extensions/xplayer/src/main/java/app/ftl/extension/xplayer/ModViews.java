package app.ftl.extension.xplayer;

import android.content.Context;
import android.content.res.Resources;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;

final class ModViews {
    private static volatile String resourcePackage;
    private static final ConcurrentHashMap<String, int[]> RESOLVED = new ConcurrentHashMap<>();

    private ModViews() {
    }

    static void apply(String spec, ViewGroup scope) {
        String[] parts = spec.split(":", 3);
        if (parts.length != 3) {
            return;
        }
        Context context = scope.getContext();
        if (!ModPrefs.get(context, parts[0], true)) {
            return;
        }
        if ("self".equals(parts[1])) {
            transform(scope, "zero");
            return;
        }

        int[] ids = RESOLVED.get(spec);
        if (ids == null) {
            Resources resources = scope.getResources();
            String pkg = packageOf(scope);
            String[] names = parts[2].split(",");
            int[] found = new int[names.length];
            int count = 0;
            for (String name : names) {
                int id = resources.getIdentifier(name, "id", pkg);
                if (id != 0) {
                    found[count++] = id;
                }
            }
            ids = Arrays.copyOf(found, count);
            RESOLVED.put(spec, ids);
        }
        if (ids.length > 0) {
            walk(scope, ids, parts[1]);
        }
    }

    static String packageOf(ViewGroup scope) {
        String cached = resourcePackage;
        if (cached != null) {
            return cached;
        }
        try {
            int id = firstAppId(scope);
            if (id != 0) {
                cached = scope.getResources().getResourcePackageName(id);
                resourcePackage = cached;
                return cached;
            }
        } catch (Throwable ignored) {
        }
        return scope.getContext().getPackageName();
    }

    private static int firstAppId(View view) {
        int id = view.getId();
        if (id != View.NO_ID && (id >>> 24) == 0x7f) {
            return id;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                int found = firstAppId(group.getChildAt(i));
                if (found != 0) {
                    return found;
                }
            }
        }
        return 0;
    }

    private static boolean matches(int[] ids, int id) {
        if (id == View.NO_ID) {
            return false;
        }
        for (int candidate : ids) {
            if (candidate == id) {
                return true;
            }
        }
        return false;
    }

    private static void walk(View view, int[] ids, String mode) {
        if (matches(ids, view.getId())) {
            transform(view, mode);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                walk(group.getChildAt(i), ids, mode);
            }
        }
    }

    private static void resize(View view, int width, int height) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (params == null) {
            return;
        }
        if (width >= 0) {
            params.width = width;
        }
        if (height >= 0) {
            params.height = height;
        }
        view.setLayoutParams(params);
    }

    private static void transform(View view, String mode) {
        switch (mode) {
            case "zero": {
                view.setPadding(0, 0, 0, 0);
                ViewGroup.LayoutParams params = view.getLayoutParams();
                if (params instanceof ViewGroup.MarginLayoutParams) {
                    ((ViewGroup.MarginLayoutParams) params).setMargins(0, 0, 0, 0);
                }
                resize(view, 0, 0);
                break;
            }
            case "text":
                if (view instanceof TextView) {
                    ((TextView) view).setHeight(0);
                    ((TextView) view).setWidth(0);
                } else {
                    view.setVisibility(View.GONE);
                }
                break;
            case "gone":
                view.setAlpha(0f);
                view.setClickable(false);
                view.setFocusable(false);
                view.setVisibility(View.GONE);
                break;
            case "collapse":
                view.setMinimumWidth(0);
                if (view instanceof TextView) {
                    ((TextView) view).setMinWidth(0);
                }
                resize(view, -1, 0);
                break;
            case "badge":
                if (view instanceof ViewGroup) {
                    ViewGroup group = (ViewGroup) view;
                    for (int i = 0; i < group.getChildCount(); i++) {
                        View child = group.getChildAt(i);
                        if (child instanceof ViewGroup
                                && child.getClass().getName().endsWith("ConstraintLayout")) {
                            resize(child, 0, 0);
                            break;
                        }
                    }
                }
                break;
            case "width":
                resize(view, 0, -1);
                break;
            default:
                break;
        }
    }
}
