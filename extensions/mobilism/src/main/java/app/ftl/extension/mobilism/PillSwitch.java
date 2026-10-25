package app.ftl.extension.mobilism;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.View;

public final class PillSwitch extends View {
    public interface Listener {
        void onChanged(boolean checked);
    }

    public static final int COLOR_ON = 0xFF2E9E5B;
    public static final int COLOR_OFF = 0xFF8E8799;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final int w;
    private final int h;
    private final float pad;
    private boolean checked;
    private float pos;
    private ValueAnimator anim;
    private Listener listener;

    public PillSwitch(Context c) {
        super(c);
        w = ModUi.dp(c, 62);
        h = ModUi.dp(c, 34);
        pad = ModUi.dp(c, 4);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTypeface(Typeface.DEFAULT_BOLD);
        textPaint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11, c.getResources().getDisplayMetrics()));
        textPaint.setColor(Color.WHITE);
        setClickable(false);
    }

    public void setListener(Listener l) {
        listener = l;
    }

    public boolean isChecked() {
        return checked;
    }

    public void setChecked(boolean value) {
        apply(value, false);
    }

    public void toggle() {
        apply(!checked, true);
    }

    private void apply(boolean value, boolean animate) {
        boolean changed = checked != value;
        checked = value;
        float target = value ? 1f : 0f;
        if (anim != null) {
            anim.cancel();
            anim = null;
        }
        if (animate && changed && isShown()) {
            anim = ValueAnimator.ofFloat(pos, target);
            anim.setDuration(160);
            anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator a) {
                    pos = (Float) a.getAnimatedValue();
                    invalidate();
                }
            });
            anim.start();
        } else {
            pos = target;
            invalidate();
        }
        if (changed && listener != null) {
            listener.onChanged(value);
        }
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (anim != null) {
            anim.cancel();
            anim = null;
        }
        pos = checked ? 1f : 0f;
    }

    private static int blend(int a, int b, float t) {
        int r = (int) (Color.red(a) + (Color.red(b) - Color.red(a)) * t);
        int g = (int) (Color.green(a) + (Color.green(b) - Color.green(a)) * t);
        int bl = (int) (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t);
        return Color.rgb(r, g, bl);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float t = pos;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(blend(COLOR_OFF, COLOR_ON, t));
        rect.set(0, 0, w, h);
        canvas.drawRoundRect(rect, h / 2f, h / 2f, paint);

        float cy = h / 2f;
        float base = cy - (textPaint.ascent() + textPaint.descent()) / 2f;
        textPaint.setAlpha((int) (255 * t));
        canvas.drawText("ON", (w - h) / 2f + pad / 2f, base, textPaint);
        textPaint.setAlpha((int) (255 * (1f - t)));
        canvas.drawText("OFF", (w + h) / 2f - pad / 2f, base, textPaint);

        float r = h / 2f - pad;
        float cx = h / 2f + (w - h) * t;
        paint.setColor(Color.WHITE);
        canvas.drawCircle(cx, cy, r, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1f);
        paint.setColor(0x26000000);
        canvas.drawCircle(cx, cy, r, paint);
    }
}
