package app.ftl.extension.mobilism;

import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

public final class ModUi {
    public static final int ACCENT = 0xFFFF645E;
    public static final int ACCENT_SOFT = 0xFFFFD9D6;
    public static final int PURPLE = 0xFF9191FF;
    public static final int TEXT = 0xFF1F1B24;
    public static final int SUB = 0xFF6F6879;
    public static final int LINE = 0xFFEFEAF3;
    public static final int SURFACE = 0xFFFFFFFF;
    public static final int SURFACE_ALT = 0xFFF8F5FB;
    public static final int TRACK_OFF = 0xFFDDD8E2;

    private ModUi() {
    }

    public static int dp(Context c, float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics());
    }

    public static GradientDrawable rounded(Context c, int color, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(c, radiusDp));
        return d;
    }

    public static Drawable ripple(Context c, int color, float radiusDp) {
        GradientDrawable content = rounded(c, color, radiusDp);
        if (Build.VERSION.SDK_INT >= 21) {
            GradientDrawable mask = rounded(c, Color.WHITE, radiusDp);
            return new RippleDrawable(ColorStateList.valueOf(0x22FF645E), content, mask);
        }
        return content;
    }

    public static TextView text(Context c, CharSequence s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) {
            t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }
        return t;
    }

    public static TextView sectionLabel(Context c, String s) {
        TextView t = text(c, s.toUpperCase(), 12, ACCENT, true);
        if (Build.VERSION.SDK_INT >= 21) {
            t.setLetterSpacing(0.08f);
        }
        t.setPadding(dp(c, 6), dp(c, 18), dp(c, 6), dp(c, 8));
        return t;
    }

    public static LinearLayout card(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackground(rounded(c, SURFACE_ALT, 18));
        return l;
    }

    public static View divider(Context c) {
        View v = new View(c);
        v.setBackgroundColor(LINE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1);
        lp.leftMargin = dp(c, 16);
        v.setLayoutParams(lp);
        return v;
    }

    public static View switchRow(Context c, CharSequence title, CharSequence desc, boolean checked,
                                 CompoundButton.OnCheckedChangeListener listener) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(c, 56));
        row.setPadding(dp(c, 16), dp(c, 8), dp(c, 12), dp(c, 8));

        LinearLayout labels = new LinearLayout(c);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(text(c, title, 16, TEXT, false));
        if (desc != null && desc.length() > 0) {
            TextView d = text(c, desc, 13, SUB, false);
            d.setPadding(0, dp(c, 2), 0, 0);
            labels.addView(d);
        }
        row.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        final Switch sw = new Switch(c);
        sw.setChecked(checked);
        tint(sw);
        sw.setOnCheckedChangeListener(listener);
        row.addView(sw);

        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sw.toggle();
            }
        });
        return row;
    }

    private static void tint(Switch sw) {
        if (Build.VERSION.SDK_INT < 23) {
            return;
        }
        int[][] states = {{android.R.attr.state_checked}, {}};
        sw.setThumbTintList(new ColorStateList(states, new int[]{ACCENT, 0xFFFFFFFF}));
        sw.setTrackTintList(new ColorStateList(states, new int[]{ACCENT_SOFT, TRACK_OFF}));
    }

    public static TextView textButton(Context c, String label, int color, View.OnClickListener l) {
        TextView b = text(c, label, 14, color, true);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(c, 16), dp(c, 10), dp(c, 16), dp(c, 10));
        b.setBackground(ripple(c, Color.TRANSPARENT, 20));
        b.setOnClickListener(l);
        return b;
    }

    public static ScrollView cappedScroll(Context c, final int maxPx) {
        return new ScrollView(c) {
            @Override
            protected void onMeasure(int widthSpec, int heightSpec) {
                super.onMeasure(widthSpec, View.MeasureSpec.makeMeasureSpec(maxPx, View.MeasureSpec.AT_MOST));
            }
        };
    }

    public static LinearLayout sheet(Context c) {
        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(rounded(c, SURFACE, 28));
        root.setPadding(dp(c, 18), dp(c, 22), dp(c, 18), dp(c, 12));
        return root;
    }

    public static int maxBodyHeight(Context a) {
        return heightFraction(a, 0.58f);
    }

    public static int heightFraction(Context a, float fraction) {
        DisplayMetrics m = a.getResources().getDisplayMetrics();
        return (int) (m.heightPixels * fraction);
    }

    public static android.widget.EditText inputField(Context c, String hint) {
        android.widget.EditText e = new android.widget.EditText(c);
        e.setHint(hint);
        e.setHintTextColor(0xFF9A93A5);
        e.setTextColor(TEXT);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        e.setSingleLine(true);
        e.setBackground(rounded(c, SURFACE_ALT, 14));
        e.setPadding(dp(c, 16), dp(c, 12), dp(c, 16), dp(c, 12));
        return e;
    }

    public static TextView circleButton(Context c, String label, int bg, int fg, int sizeDp, float sp) {
        TextView b = text(c, label, sp, fg, true);
        b.setGravity(Gravity.CENTER);
        b.setBackground(ripple(c, bg, sizeDp / 2f));
        b.setLayoutParams(new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp)));
        return b;
    }

    public static Dialog show(Context a, View content) {
        Dialog d = new Dialog(a);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        d.setContentView(content);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            DisplayMetrics m = a.getResources().getDisplayMetrics();
            int width = Math.min((int) (m.widthPixels * 0.92f), dp(a, 440));
            w.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setDimAmount(0.4f);
            w.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        d.show();
        return d;
    }
}
