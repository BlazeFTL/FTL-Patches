package app.ftl.extension.mobilism;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.ReplacementSpan;
import android.text.style.URLSpan;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Arrays;
import java.util.Comparator;

public final class CopyLinks {
    private static final long POLL_MS = 700L;
    private static final String SEP = "\u00A0\uFFFC";

    private CopyLinks() {
    }

    public static void attach(final TextView tv) {
        if (tv == null || !ModFeatures.isOn(ModFeatures.COPY_LINKS)) {
            return;
        }
        apply(tv);
        tv.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!tv.isAttachedToWindow()) {
                    return;
                }
                if (ModFeatures.isOn(ModFeatures.COPY_LINKS)) {
                    apply(tv);
                }
                tv.postDelayed(this, POLL_MS);
            }
        }, POLL_MS);
    }

    private static void apply(TextView tv) {
        CharSequence text = tv.getText();
        if (!(text instanceof Spanned)) {
            return;
        }
        Spanned sp = (Spanned) text;
        if (sp.getSpans(0, sp.length(), CopyClick.class).length > 0) {
            return;
        }
        URLSpan[] found = sp.getSpans(0, sp.length(), URLSpan.class);
        if (found.length == 0) {
            return;
        }
        final Spanned src = sp;
        URLSpan[] urls = Arrays.copyOf(found, found.length);
        Arrays.sort(urls, new Comparator<URLSpan>() {
            @Override
            public int compare(URLSpan a, URLSpan b) {
                return src.getSpanEnd(b) - src.getSpanEnd(a);
            }
        });

        int color = linkColor(tv);
        float size = tv.getTextSize();
        SpannableStringBuilder out = new SpannableStringBuilder(sp);
        int lastEnd = -1;
        boolean changed = false;
        for (URLSpan u : urls) {
            int start = src.getSpanStart(u);
            int end = src.getSpanEnd(u);
            if (start < 0 || end <= start || end == lastEnd || isImageOnly(src, start, end)) {
                continue;
            }
            lastEnd = end;
            out.insert(end, SEP);
            int icon = end + 1;
            out.setSpan(new CopyIcon(color, size), icon, icon + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            out.setSpan(new CopyClick(u.getURL()), icon, icon + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            changed = true;
        }
        if (!changed) {
            return;
        }
        tv.setText(out);
        if (tv.getMovementMethod() == null) {
            tv.setMovementMethod(LinkMovementMethod.getInstance());
        }
    }

    private static boolean isImageOnly(Spanned s, int start, int end) {
        for (int i = start; i < end; i++) {
            if (s.charAt(i) != '\uFFFC') {
                return false;
            }
        }
        return true;
    }

    private static int linkColor(TextView tv) {
        ColorStateList c = tv.getLinkTextColors();
        return c != null ? c.getDefaultColor() : ModUi.ACCENT;
    }

    private static final class CopyClick extends ClickableSpan {
        private final String url;

        CopyClick(String url) {
            this.url = url;
        }

        @Override
        public void onClick(View v) {
            Context c = v.getContext();
            ClipboardManager cm = (ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm == null || url == null) {
                return;
            }
            cm.setPrimaryClip(ClipData.newPlainText("link", url));
            Toast.makeText(c, "Link copied", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void updateDrawState(android.text.TextPaint ds) {
        }
    }

    private static final class CopyIcon extends ReplacementSpan {
        private final int color;
        private final float size;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final RectF rect = new RectF();

        CopyIcon(int color, float textSize) {
            this.color = color;
            this.size = textSize * 0.95f;
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
        }

        @Override
        public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
            return (int) (size + size * 0.6f);
        }

        @Override
        public void draw(Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, Paint paint) {
            float s = size / 24f;
            float ox = x + size * 0.3f;
            float cy = (paint.ascent() + paint.descent()) / 2f + y;
            float oy = cy - size / 2f;
            p.setColor(color);
            canvas.save();
            canvas.translate(ox, oy);
            canvas.scale(s, s);
            p.setStrokeWidth(Math.max(1.4f, 1.9f * s) / s);

            path.reset();
            path.moveTo(9f, 16f);
            path.lineTo(7f, 16f);
            path.quadTo(5f, 16f, 5f, 14f);
            path.lineTo(5f, 5f);
            path.quadTo(5f, 3f, 7f, 3f);
            path.lineTo(13f, 3f);
            path.quadTo(15f, 3f, 15f, 5f);
            path.lineTo(15f, 8f);
            canvas.drawPath(path, p);

            rect.set(9f, 8f, 19f, 21f);
            canvas.drawRoundRect(rect, 2.4f, 2.4f, p);
            canvas.restore();
        }
    }
}
