package app.ftl.extension.mobilism;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

public final class PillSwitch extends View {
    public interface Listener {
        void onChanged(boolean checked);
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path path = new Path();
    private final float d1;
    private final int w;
    private final int h;
    private boolean checked;
    private float pos;
    private ValueAnimator anim;
    private Listener listener;

    public PillSwitch(Context c) {
        super(c);
        d1 = ModUi.dp(c, 1);
        w = ModUi.dp(c, 52);
        h = ModUi.dp(c, 32);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
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
            anim.setDuration(180);
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
        float cy = h / 2f;

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(blend(ModUi.TRACK_OFF, ModUi.ACCENT, t));
        rect.set(0, 0, w, h);
        canvas.drawRoundRect(rect, h / 2f, h / 2f, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2 * d1);
        paint.setColor(blend(ModUi.OUTLINE, ModUi.ACCENT, t));
        rect.set(d1, d1, w - d1, h - d1);
        canvas.drawRoundRect(rect, (h - 2 * d1) / 2f, (h - 2 * d1) / 2f, paint);

        float r = (8 + 4 * t) * d1;
        float cx = (16 + 20 * t) * d1;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(blend(ModUi.OUTLINE, Color.WHITE, t));
        canvas.drawCircle(cx, cy, r, paint);

        if (t > 0.05f) {
            path.reset();
            path.moveTo(cx - 4.5f * d1, cy + 0.5f * d1);
            path.lineTo(cx - 1.5f * d1, cy + 3.5f * d1);
            path.lineTo(cx + 4.5f * d1, cy - 3f * d1);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2f * d1);
            paint.setColor(ModUi.ACCENT);
            paint.setAlpha((int) (255 * t));
            canvas.drawPath(path, paint);
        }
    }
}
