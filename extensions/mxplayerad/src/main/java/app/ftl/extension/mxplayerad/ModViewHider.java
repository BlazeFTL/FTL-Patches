package app.ftl.extension.mxplayerad;

import android.content.Context;
import android.content.res.Resources;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;

public class ModViewHider extends View {
    private static final String FALLBACK_PACKAGE = "com.mxtech.videoplayer.ad";
    private static final List<WeakReference<ModViewHider>> LIVE = new ArrayList<WeakReference<ModViewHider>>();

    private final String[][] rules;
    private final WeakHashMap<View, int[]> saved = new WeakHashMap<View, int[]>();

    public ModViewHider(Context context, AttributeSet attrs) {
        super(context, attrs);
        Object tag = getTag();
        String[] specs = tag instanceof CharSequence ? tag.toString().split(";") : new String[0];
        rules = new String[specs.length][];
        for (int i = 0; i < specs.length; i++) {
            rules[i] = specs[i].split("\\|");
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        LIVE.add(new WeakReference<ModViewHider>(this));
        apply();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        for (int i = LIVE.size() - 1; i >= 0; i--) {
            ModViewHider hider = LIVE.get(i).get();
            if (hider == null || hider == this) LIVE.remove(i);
        }
    }

    static void refreshAll() {
        for (WeakReference<ModViewHider> reference : new ArrayList<WeakReference<ModViewHider>>(LIVE)) {
            ModViewHider hider = reference.get();
            if (hider != null) hider.apply();
        }
    }

    private void apply() {
        ViewParent parent = getParent();
        if (!(parent instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) parent;

        for (String[] rule : rules) {
            if (rule.length < 3) continue;

            int id = idOf(rule[1]);
            if (id == 0) continue;
            View target = group.findViewById(id);
            if (target == null) continue;

            boolean showInstead = rule[2].equals("show");
            boolean changeVisibility = !rule[2].equals("collapse");
            boolean changeSize = !rule[2].equals("gone") && !showInstead;
            int[] state = saved.get(target);

            if (ModSettings.get(rule[0])) {
                ViewGroup.LayoutParams params = target.getLayoutParams();
                if (state == null) {
                    state = new int[] {
                        target.getVisibility(),
                        params == null ? 0 : params.width,
                        params == null ? 0 : params.height,
                    };
                    saved.put(target, state);
                }
                if (changeVisibility) target.setVisibility(showInstead ? View.VISIBLE : View.GONE);
                if (changeSize && params != null) {
                    params.width = 0;
                    params.height = 0;
                    target.setLayoutParams(params);
                }
            } else if (state != null) {
                ViewGroup.LayoutParams params = target.getLayoutParams();
                if (changeSize && params != null) {
                    params.width = state[1];
                    params.height = state[2];
                    target.setLayoutParams(params);
                }
                if (changeVisibility) target.setVisibility(state[0]);
                saved.remove(target);
            }
        }
    }

    private int idOf(String name) {
        Resources resources = getResources();
        int id = resources.getIdentifier(name, "id", getContext().getPackageName());
        if (id == 0) id = resources.getIdentifier(name, "id", FALLBACK_PACKAGE);
        return id;
    }
}
