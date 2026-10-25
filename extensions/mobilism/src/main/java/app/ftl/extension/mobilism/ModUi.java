package app.ftl.extension.mobilism;

import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public final class ModUi {
    public static final int ACCENT = 0xFFDB4A44;
    public static final int ACCENT_SOFT = 0xFFFFDAD6;
    public static final int ON_TEXT = 0xFFB3261E;
    public static final int PURPLE = 0xFF5B5BD6;
    public static final int TEXT = 0xFF201A1A;
    public static final int SUB = 0xFF6F6365;
    public static final int LINE = 0xFFE8DEDF;
    public static final int SURFACE = 0xFFFFFBFA;
    public static final int SURFACE_ALT = 0xFFF6EEEE;
    public static final int OUTLINE = 0xFF857375;
    public static final int TRACK_OFF = 0xFFEADFE0;

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

    private static GradientDrawable corners(Context c, int color, float top, float bottom) {
        float t = dp(c, top);
        float b = dp(c, bottom);
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadii(new float[]{t, t, t, t, b, b, b, b});
        return d;
    }

    public static Drawable ripple(Context c, int color, float radiusDp) {
        return ripple(c, color, radiusDp, 0x22DB4A44);
    }

    public static Drawable ripple(Context c, int color, float radiusDp, int rippleColor) {
        GradientDrawable content = rounded(c, color, radiusDp);
        if (Build.VERSION.SDK_INT >= 21) {
            GradientDrawable mask = rounded(c, Color.WHITE, radiusDp);
            return new RippleDrawable(ColorStateList.valueOf(rippleColor), content, mask);
        }
        return content;
    }

    public static Drawable segment(Context c, int index, int total, boolean clickable) {
        float top = index == 0 ? 22 : 5;
        float bottom = index == total - 1 ? 22 : 5;
        GradientDrawable content = corners(c, SURFACE_ALT, top, bottom);
        if (clickable && Build.VERSION.SDK_INT >= 21) {
            return new RippleDrawable(ColorStateList.valueOf(0x22DB4A44), content, corners(c, Color.WHITE, top, bottom));
        }
        return content;
    }

    public static LinearLayout.LayoutParams segmentParams(Context c, int index, int total) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (index < total - 1) {
            lp.bottomMargin = dp(c, 2);
        }
        return lp;
    }

    public static TextView text(Context c, CharSequence s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) {
            t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        }
        return t;
    }

    public static TextView sectionLabel(Context c, String s) {
        TextView t = text(c, s.toUpperCase(), 12, ON_TEXT, true);
        if (Build.VERSION.SDK_INT >= 21) {
            t.setLetterSpacing(0.1f);
        }
        t.setPadding(dp(c, 6), dp(c, 20), dp(c, 6), dp(c, 8));
        return t;
    }

    public static LinearLayout card(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
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

    public static View switchRow(Context c, CharSequence title, final CharSequence onText, final CharSequence offText,
                                 boolean checked, int index, int total, final PillSwitch.Listener listener) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(c, 68));
        row.setPadding(dp(c, 20), dp(c, 10), dp(c, 18), dp(c, 10));
        row.setBackground(segment(c, index, total, true));
        row.setLayoutParams(segmentParams(c, index, total));

        LinearLayout labels = new LinearLayout(c);
        labels.setOrientation(LinearLayout.VERTICAL);
        final TextView t = text(c, title, 16, checked ? TEXT : SUB, false);
        labels.addView(t);
        final TextView status;
        if (onText != null && offText != null) {
            status = text(c, checked ? onText : offText, 13, checked ? ON_TEXT : SUB, false);
            status.setPadding(0, dp(c, 2), 0, 0);
            labels.addView(status);
        } else {
            status = null;
        }
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        labelLp.rightMargin = dp(c, 14);
        row.addView(labels, labelLp);

        final PillSwitch sw = new PillSwitch(c);
        sw.setChecked(checked);
        sw.setListener(new PillSwitch.Listener() {
            @Override
            public void onChanged(boolean on) {
                t.setTextColor(on ? TEXT : SUB);
                if (status != null) {
                    status.setText(on ? onText : offText);
                    status.setTextColor(on ? ON_TEXT : SUB);
                }
                if (listener != null) {
                    listener.onChanged(on);
                }
            }
        });
        row.addView(sw);

        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sw.toggle();
            }
        });
        return row;
    }

    public static TextView textButton(Context c, String label, int color, View.OnClickListener l) {
        TextView b = text(c, label, 15, color, true);
        b.setGravity(Gravity.CENTER);
        b.setMinHeight(dp(c, 48));
        b.setPadding(dp(c, 20), dp(c, 10), dp(c, 20), dp(c, 10));
        b.setBackground(ripple(c, Color.TRANSPARENT, 28));
        b.setOnClickListener(l);
        return b;
    }

    public static TextView filledButton(Context c, String label, View.OnClickListener l) {
        TextView b = text(c, label, 15, Color.WHITE, true);
        b.setGravity(Gravity.CENTER);
        b.setMinHeight(dp(c, 48));
        b.setMinWidth(dp(c, 104));
        b.setPadding(dp(c, 28), dp(c, 12), dp(c, 28), dp(c, 12));
        b.setBackground(ripple(c, ACCENT, 28, 0x33FFFFFF));
        b.setOnClickListener(l);
        return b;
    }

    public static final class CappedScroll extends ScrollView {
        private int maxPx;

        public CappedScroll(Context c, int maxPx) {
            super(c);
            this.maxPx = maxPx;
        }

        public void setMaxHeightPx(int px) {
            maxPx = px;
            requestLayout();
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            super.onMeasure(widthSpec, View.MeasureSpec.makeMeasureSpec(maxPx, View.MeasureSpec.AT_MOST));
        }
    }

    public static CappedScroll cappedScroll(Context c, int maxPx) {
        return new CappedScroll(c, maxPx);
    }

    public static final class Icon extends View {
        public static final int SEARCH = 0;
        public static final int BACK = 1;
        public static final int CLOSE = 2;
        public static final int PLUS = 3;

        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int type;

        public Icon(Context c, int type, int color) {
            super(c);
            this.type = type;
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
            p.setStrokeWidth(dp(c, 2.2f));
            p.setColor(color);
        }

        @Override
        protected void onDraw(Canvas cv) {
            float u = Math.min(getWidth(), getHeight()) / 40f;
            float ox = (getWidth() - 40f * u) / 2f;
            float oy = (getHeight() - 40f * u) / 2f;
            cv.save();
            cv.translate(ox, oy);
            cv.scale(u, u);
            float sw = p.getStrokeWidth();
            p.setStrokeWidth(sw / u);
            if (type == SEARCH) {
                cv.drawCircle(18f, 18f, 7.5f, p);
                cv.drawLine(23.5f, 23.5f, 29f, 29f, p);
            } else if (type == BACK) {
                cv.drawLine(28f, 20f, 12f, 20f, p);
                cv.drawLine(12f, 20f, 19f, 13f, p);
                cv.drawLine(12f, 20f, 19f, 27f, p);
            } else if (type == PLUS) {
                cv.drawLine(20f, 12f, 20f, 28f, p);
                cv.drawLine(12f, 20f, 28f, 20f, p);
            } else {
                cv.drawLine(14f, 14f, 26f, 26f, p);
                cv.drawLine(26f, 14f, 14f, 26f, p);
            }
            p.setStrokeWidth(sw);
            cv.restore();
        }
    }

    public static View iconButton(Context c, int type, int color, int bg, int sizeDp, View.OnClickListener l) {
        Icon i = new Icon(c, type, color);
        i.setBackground(ripple(c, bg, sizeDp / 2f));
        i.setLayoutParams(new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp)));
        i.setOnClickListener(l);
        return i;
    }

    public static LinearLayout sheet(Context c) {
        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(rounded(c, SURFACE, 28));
        root.setPadding(dp(c, 20), dp(c, 24), dp(c, 20), dp(c, 14));
        return root;
    }

    public static void clearSheet(LinearLayout root) {
        root.removeAllViews();
    }

    public static int maxBodyHeight(Context a) {
        return heightFraction(a, 0.5f);
    }

    public static int heightFraction(Context a, float fraction) {
        DisplayMetrics m = a.getResources().getDisplayMetrics();
        return (int) (m.heightPixels * fraction);
    }

    public static EditText inputField(Context c, String hint) {
        EditText e = new EditText(c);
        e.setHint(hint);
        e.setHintTextColor(0xFF9A8E90);
        e.setTextColor(TEXT);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        e.setSingleLine(true);
        StateListDrawable bg = new StateListDrawable();
        GradientDrawable focused = rounded(c, SURFACE_ALT, 28);
        focused.setStroke(dp(c, 2), ACCENT);
        bg.addState(new int[]{android.R.attr.state_focused}, focused);
        bg.addState(new int[]{}, rounded(c, SURFACE_ALT, 28));
        e.setBackground(bg);
        e.setMinHeight(dp(c, 52));
        e.setPadding(dp(c, 20), dp(c, 14), dp(c, 20), dp(c, 14));
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
            w.setDimAmount(0.5f);
            w.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        d.show();
        return d;
    }
}
