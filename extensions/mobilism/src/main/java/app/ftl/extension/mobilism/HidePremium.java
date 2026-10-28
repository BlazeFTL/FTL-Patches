package app.ftl.extension.mobilism;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

public final class HidePremium {
    private static final String GO_PREMIUM = "Go premium";
    private static final String RIPPLE = "RippleView";

    private HidePremium() {
    }

    public static void onNavDrawer(Object activity) {
        if (activity instanceof Activity) {
            applyHeader((Activity) activity);
        }
    }

    public static void onPremiumDownload(Object view) {
        if (view instanceof View && ModFeatures.isOn(ModFeatures.HIDE_PREMIUM_DOWNLOAD)) {
            ((View) view).setVisibility(View.GONE);
        }
    }

    public static void applyHeader(Activity activity) {
        try {
            View nav = ModDrawer.findNav(activity.getWindow().getDecorView());
            if (nav == null) {
                return;
            }
            View header = (View) nav.getClass().getMethod("getHeaderView", int.class).invoke(nav, 0);
            if (header != null) {
                apply(header, ModFeatures.isOn(ModFeatures.HIDE_PREMIUM_HEADER));
            }
        } catch (Throwable ignored) {
        }
    }

    private static void apply(View v, boolean hide) {
        if (v instanceof TextView) {
            CharSequence t = ((TextView) v).getText();
            if (t != null && GO_PREMIUM.equalsIgnoreCase(t.toString().trim())) {
                int state = hide ? View.GONE : View.VISIBLE;
                v.setVisibility(state);
                View p = v;
                while (p.getParent() instanceof View) {
                    p = (View) p.getParent();
                    if (p.getClass().getName().endsWith(RIPPLE)) {
                        p.setVisibility(state);
                        break;
                    }
                }
            }
            return;
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                apply(g.getChildAt(i), hide);
            }
        }
    }
}
