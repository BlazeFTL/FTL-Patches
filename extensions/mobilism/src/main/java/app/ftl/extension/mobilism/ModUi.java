package app.ftl.extension.mobilism;

import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
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
    public static final int TEXT = 0xFF1F1B1C;
    public static final int SUB = 0xFF6F6365;
    public static final int LINE = 0xFFEBE4E5;
    public static final int BORDER = 0xFFD8CFD0;
    public static final int SURFACE = 0xFFFFFFFF;
    public static final int OUTLINE = 0xFF857375;
    public static final int TRACK_OFF = 0xFFFFFFFF;

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

    public static GradientDrawable bordered(Context c, int fill, int stroke, float radiusDp) {
        GradientDrawable d = rounded(c, fill, radiusDp);
        d.setStroke(Math.max(1, dp(c, 1)), stroke);
        return d;
    }

    public static Drawable ripple(Context c, int color, float radiusDp) {
        return ripple(c, color, radiusDp, 0x22000000 | (ACCENT & 0x00FFFFFF));
    }

    public static Drawable ripple(Context c, int color, float radiusDp, int rippleColor) {
        GradientDrawable content = rounded(c, color, radiusDp);
        if (Build.VERSION.SDK_INT >= 21) {
            GradientDrawable mask = rounded(c, Color.WHITE, radiusDp);
            return new RippleDrawable(ColorStateList.valueOf(rippleColor), content, mask);
        }
        return content;
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
        TextView t = text(c, s.toUpperCase(), 11, ON_TEXT, true);
        if (Build.VERSION.SDK_INT >= 21) {
            t.setLetterSpacing(0.08f);
        }
        t.setPadding(dp(c, 4), dp(c, 14), dp(c, 4), dp(c, 6));
        return t;
    }

    public static LinearLayout card(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackground(bordered(c, SURFACE, LINE, 16));
        if (Build.VERSION.SDK_INT >= 21) {
            l.setClipToOutline(true);
        }
        return l;
    }

    public static void addRow(Context c, LinearLayout card, View row) {
        if (card.getChildCount() > 0) {
            View v = new View(c);
            v.setBackgroundColor(LINE);
            card.addView(v, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
        }
        card.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    public static View switchRow(Context c, int icon, CharSequence title, final CharSequence onText, final CharSequence offText,
                                 boolean checked, final PillSwitch.Listener listener) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(c, 52));
        row.setPadding(dp(c, 14), dp(c, 8), dp(c, 14), dp(c, 8));
        row.setBackground(ripple(c, Color.TRANSPARENT, 0));

        if (icon >= 0) {
            Icon ic = new Icon(c, icon, SUB);
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(c, 22), dp(c, 22));
            ilp.rightMargin = dp(c, 12);
            row.addView(ic, ilp);
        }

        LinearLayout labels = new LinearLayout(c);
        labels.setOrientation(LinearLayout.VERTICAL);
        final TextView t = text(c, title, 15, checked ? TEXT : SUB, false);
        labels.addView(t);
        final TextView status;
        if (onText != null && offText != null) {
            status = text(c, checked ? onText : offText, 12, checked ? ON_TEXT : SUB, false);
            status.setPadding(0, dp(c, 1), 0, 0);
            labels.addView(status);
        } else {
            status = null;
        }
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        labelLp.rightMargin = dp(c, 10);
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
        TextView b = text(c, label, 14, color, true);
        b.setGravity(Gravity.CENTER);
        b.setMinHeight(dp(c, 40));
        b.setPadding(dp(c, 16), dp(c, 8), dp(c, 16), dp(c, 8));
        b.setBackground(ripple(c, Color.TRANSPARENT, 20));
        b.setOnClickListener(l);
        return b;
    }

    public static TextView filledButton(Context c, String label, View.OnClickListener l) {
        TextView b = text(c, label, 14, Color.WHITE, true);
        b.setGravity(Gravity.CENTER);
        b.setMinHeight(dp(c, 40));
        b.setMinWidth(dp(c, 88));
        b.setPadding(dp(c, 22), dp(c, 8), dp(c, 22), dp(c, 8));
        b.setBackground(ripple(c, ACCENT, 20, 0x33FFFFFF));
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
        public static final int BELL = 4;
        public static final int CHAT = 5;
        public static final int BOOK = 6;
        public static final int STAR = 7;
        public static final int LOGOUT = 8;
        public static final int TROPHY = 9;
        public static final int QR = 10;
        public static final int SMILE = 11;
        public static final int GRID = 12;
        public static final int BULB = 13;
        public static final int DOWNLOAD = 14;
        public static final int CLOCK = 15;
        public static final int COPY = 16;
        public static final int EYEOFF = 17;
        public static final int TUNE = 18;

        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final RectF r = new RectF();
        private final int type;

        public Icon(Context c, int type, int color) {
            super(c);
            this.type = type;
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
            p.setStrokeWidth(1.8f);
            p.setColor(color);
        }

        public void setColor(int color) {
            p.setColor(color);
            invalidate();
        }

        private void line(Canvas cv, float x1, float y1, float x2, float y2) {
            cv.drawLine(x1, y1, x2, y2, p);
        }

        private void dot(Canvas cv, float x, float y) {
            p.setStyle(Paint.Style.FILL);
            cv.drawCircle(x, y, 1.1f, p);
            p.setStyle(Paint.Style.STROKE);
        }

        private void box(Canvas cv, float l, float t, float rr, float b, float rad) {
            path.reset();
            r.set(l, t, rr, b);
            path.addRoundRect(r, rad, rad, Path.Direction.CW);
            cv.drawPath(path, p);
        }

        @Override
        protected void onDraw(Canvas cv) {
            float aw = getWidth() - getPaddingLeft() - getPaddingRight();
            float ah = getHeight() - getPaddingTop() - getPaddingBottom();
            float u = Math.min(aw, ah) / 24f;
            float ox = getPaddingLeft() + (aw - 24f * u) / 2f;
            float oy = getPaddingTop() + (ah - 24f * u) / 2f;
            cv.save();
            cv.translate(ox, oy);
            cv.scale(u, u);
            switch (type) {
                case SEARCH:
                    cv.drawCircle(10.5f, 10.5f, 6f, p);
                    line(cv, 15f, 15f, 20f, 20f);
                    break;
                case BACK:
                    line(cv, 19f, 12f, 5f, 12f);
                    line(cv, 11f, 6f, 5f, 12f);
                    line(cv, 11f, 18f, 5f, 12f);
                    break;
                case CLOSE:
                    line(cv, 6.5f, 6.5f, 17.5f, 17.5f);
                    line(cv, 17.5f, 6.5f, 6.5f, 17.5f);
                    break;
                case PLUS:
                    line(cv, 12f, 5f, 12f, 19f);
                    line(cv, 5f, 12f, 19f, 12f);
                    break;
                case BELL:
                    path.reset();
                    path.moveTo(6f, 16f);
                    path.lineTo(6f, 11f);
                    r.set(6f, 5f, 18f, 17f);
                    path.arcTo(r, 180f, 180f);
                    path.lineTo(18f, 16f);
                    path.lineTo(19.5f, 18f);
                    path.lineTo(4.5f, 18f);
                    path.close();
                    cv.drawPath(path, p);
                    path.reset();
                    path.moveTo(10f, 20.5f);
                    r.set(10f, 18.5f, 14f, 22.5f);
                    path.arcTo(r, 180f, -180f);
                    cv.drawPath(path, p);
                    break;
                case CHAT:
                    box(cv, 4f, 4.5f, 20f, 16f, 4f);
                    path.reset();
                    path.moveTo(8.5f, 16f);
                    path.lineTo(8.5f, 20f);
                    path.lineTo(13f, 16f);
                    cv.drawPath(path, p);
                    break;
                case BOOK:
                    path.reset();
                    path.moveTo(12f, 7f);
                    path.cubicTo(10f, 5.2f, 7f, 4.8f, 4f, 5.4f);
                    path.lineTo(4f, 17.6f);
                    path.cubicTo(7f, 17f, 10f, 17.4f, 12f, 19.2f);
                    path.cubicTo(14f, 17.4f, 17f, 17f, 20f, 17.6f);
                    path.lineTo(20f, 5.4f);
                    path.cubicTo(17f, 4.8f, 14f, 5.2f, 12f, 7f);
                    path.lineTo(12f, 19.2f);
                    cv.drawPath(path, p);
                    break;
                case STAR:
                    path.reset();
                    for (int i = 0; i < 10; i++) {
                        double a = -Math.PI / 2 + i * Math.PI / 5;
                        float rad = i % 2 == 0 ? 8.5f : 3.7f;
                        float x = (float) (12 + rad * Math.cos(a));
                        float y = (float) (12.8 + rad * Math.sin(a));
                        if (i == 0) {
                            path.moveTo(x, y);
                        } else {
                            path.lineTo(x, y);
                        }
                    }
                    path.close();
                    cv.drawPath(path, p);
                    break;
                case LOGOUT:
                    path.reset();
                    path.moveTo(10f, 4.5f);
                    path.lineTo(5f, 4.5f);
                    path.lineTo(5f, 19.5f);
                    path.lineTo(10f, 19.5f);
                    cv.drawPath(path, p);
                    line(cv, 9.5f, 12f, 20f, 12f);
                    line(cv, 16f, 8f, 20f, 12f);
                    line(cv, 16f, 16f, 20f, 12f);
                    break;
                case TROPHY:
                    path.reset();
                    path.moveTo(7f, 4f);
                    path.lineTo(17f, 4f);
                    path.lineTo(17f, 9f);
                    r.set(7f, 4f, 17f, 14f);
                    path.arcTo(r, 0f, 180f);
                    path.close();
                    cv.drawPath(path, p);
                    path.reset();
                    path.moveTo(7f, 6f);
                    path.lineTo(4f, 6f);
                    path.lineTo(4f, 8f);
                    path.lineTo(7f, 10.5f);
                    path.moveTo(17f, 6f);
                    path.lineTo(20f, 6f);
                    path.lineTo(20f, 8f);
                    path.lineTo(17f, 10.5f);
                    cv.drawPath(path, p);
                    line(cv, 12f, 14f, 12f, 18f);
                    line(cv, 8.5f, 20f, 15.5f, 20f);
                    break;
                case QR:
                    box(cv, 4f, 4f, 10f, 10f, 1.5f);
                    box(cv, 14f, 4f, 20f, 10f, 1.5f);
                    box(cv, 4f, 14f, 10f, 20f, 1.5f);
                    line(cv, 14f, 14f, 16.5f, 14f);
                    line(cv, 20f, 14f, 20f, 16.5f);
                    line(cv, 14f, 17f, 14f, 20f);
                    line(cv, 17f, 17.5f, 20f, 17.5f);
                    line(cv, 17f, 20f, 20f, 20f);
                    break;
                case SMILE:
                    cv.drawCircle(12f, 12f, 8.5f, p);
                    path.reset();
                    r.set(8f, 8.5f, 16f, 16.5f);
                    path.arcTo(r, 30f, 120f, true);
                    cv.drawPath(path, p);
                    dot(cv, 9f, 9.8f);
                    dot(cv, 15f, 9.8f);
                    break;
                case GRID:
                    box(cv, 4f, 4f, 10.5f, 10.5f, 2f);
                    box(cv, 13.5f, 4f, 20f, 10.5f, 2f);
                    box(cv, 4f, 13.5f, 10.5f, 20f, 2f);
                    box(cv, 13.5f, 13.5f, 20f, 20f, 2f);
                    break;
                case BULB:
                    path.reset();
                    r.set(6.5f, 4.5f, 17.5f, 15.5f);
                    path.arcTo(r, 125f, 290f, true);
                    path.lineTo(14.5f, 17.2f);
                    path.lineTo(9.5f, 17.2f);
                    path.close();
                    cv.drawPath(path, p);
                    line(cv, 10f, 20.2f, 14f, 20.2f);
                    break;
                case DOWNLOAD:
                    line(cv, 12f, 4f, 12f, 15f);
                    line(cv, 7.5f, 10.5f, 12f, 15f);
                    line(cv, 16.5f, 10.5f, 12f, 15f);
                    line(cv, 5f, 20f, 19f, 20f);
                    break;
                case CLOCK:
                    cv.drawCircle(12f, 12f, 8.5f, p);
                    line(cv, 12f, 7.5f, 12f, 12f);
                    line(cv, 12f, 12f, 15.5f, 14f);
                    break;
                case COPY:
                    box(cv, 9f, 9f, 20f, 20f, 3f);
                    path.reset();
                    path.moveTo(15f, 5f);
                    path.lineTo(7f, 5f);
                    path.quadTo(5f, 5f, 5f, 7f);
                    path.lineTo(5f, 15f);
                    path.quadTo(5f, 17f, 7f, 17f);
                    path.lineTo(9f, 17f);
                    cv.drawPath(path, p);
                    break;
                case EYEOFF:
                    path.reset();
                    path.moveTo(3f, 12f);
                    path.cubicTo(6f, 6.5f, 18f, 6.5f, 21f, 12f);
                    path.cubicTo(18f, 17.5f, 6f, 17.5f, 3f, 12f);
                    cv.drawPath(path, p);
                    cv.drawCircle(12f, 12f, 2.8f, p);
                    line(cv, 5f, 19.5f, 19f, 4.5f);
                    break;
                case TUNE:
                    line(cv, 4f, 7f, 7f, 7f);
                    line(cv, 11f, 7f, 20f, 7f);
                    cv.drawCircle(9f, 7f, 2f, p);
                    line(cv, 4f, 12f, 13f, 12f);
                    line(cv, 17f, 12f, 20f, 12f);
                    cv.drawCircle(15f, 12f, 2f, p);
                    line(cv, 4f, 17f, 6f, 17f);
                    line(cv, 10f, 17f, 20f, 17f);
                    cv.drawCircle(8f, 17f, 2f, p);
                    break;
                default:
                    break;
            }
            cv.restore();
        }
    }

    public static Icon iconButton(Context c, int type, int color, int bg, int sizeDp, View.OnClickListener l) {
        Icon i = new Icon(c, type, color);
        int pad = dp(c, sizeDp * 0.24f);
        i.setPadding(pad, pad, pad, pad);
        i.setBackground(ripple(c, bg, sizeDp / 2f));
        i.setLayoutParams(new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp)));
        i.setOnClickListener(l);
        return i;
    }

    public static LinearLayout sheet(Context c) {
        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(rounded(c, SURFACE, 24));
        root.setPadding(dp(c, 18), dp(c, 20), dp(c, 18), dp(c, 12));
        return root;
    }

    public static void clearSheet(LinearLayout root) {
        root.removeAllViews();
    }

    public static int maxBodyHeight(Context a) {
        return heightFraction(a, 0.55f);
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
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        e.setSingleLine(true);
        StateListDrawable bg = new StateListDrawable();
        GradientDrawable focused = rounded(c, SURFACE, 14);
        focused.setStroke(dp(c, 2), ACCENT);
        bg.addState(new int[]{android.R.attr.state_focused}, focused);
        bg.addState(new int[]{}, bordered(c, SURFACE, BORDER, 14));
        e.setBackground(bg);
        e.setMinHeight(dp(c, 44));
        e.setPadding(dp(c, 16), dp(c, 10), dp(c, 16), dp(c, 10));
        return e;
    }

    public static Dialog show(Context a, View content) {
        Dialog d = new Dialog(a);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        d.setContentView(content);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            DisplayMetrics m = a.getResources().getDisplayMetrics();
            int width = Math.min((int) (m.widthPixels * 0.9f), dp(a, 380));
            w.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setDimAmount(0.45f);
            w.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        d.show();
        return d;
    }
}
