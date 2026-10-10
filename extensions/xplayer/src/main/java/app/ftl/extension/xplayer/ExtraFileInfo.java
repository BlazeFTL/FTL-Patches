package app.ftl.extension.xplayer;

import android.content.Context;
import android.database.Cursor;
import android.provider.MediaStore;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.text.format.Formatter;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;

@SuppressWarnings("unused")
public final class ExtraFileInfo {
    private static final String BULLET = " \u2022 ";
    private static final long DAY_MS = 86400000L;
    private static final long MIN_EPOCH_MS = 1000000000000L;
    private static final HashMap<String, String> RESOLUTIONS = new HashMap<>();

    private ExtraFileInfo() {
    }

    private static boolean enabled(Context context) {
        return ModPrefs.get(context, ModPrefs.KEY_EXTRA_INFO, true);
    }

    public static void bindVideoRow(TextView f, TextView g, String path, long dateMs, long sizeBytes,
                                    String sizeText, Object dbBean) {
        try {
            Context context = f.getContext();
            if (!enabled(context)) {
                return;
            }
            if (sizeText == null) {
                sizeText = Formatter.formatFileSize(context, sizeBytes);
            }

            StringBuilder text = new StringBuilder();
            String resolution = resolution(context, path);
            if (resolution.length() != 0) {
                text.append(resolution).append('p').append(BULLET);
            }
            String year = DateFormat.format("yyyy", dateMs).toString();
            String thisYear = DateFormat.format("yyyy", System.currentTimeMillis()).toString();
            text.append(DateFormat.format(year.equals(thisYear) ? "MMM d" : "MM/dd/yyyy", dateMs));
            text.append(BULLET).append(sizeText);
            String played = played(dbBean);
            if (played.length() != 0) {
                text.append('\n').append(played);
            }

            f.setSingleLine(false);
            f.setMaxLines(2);
            f.setVisibility(View.VISIBLE);
            f.setText(text);
            alignEnd(f);
            if (g != null) {
                g.setText(text);
                alignEnd(g);
            }
        } catch (Throwable ignored) {
        }
    }

    public static void bindFolderRow(TextView name, TextView count, View row, View itemView,
                                     String nameText, String path, int videoCount, String sizeText,
                                     boolean isNew) {
        try {
            if (!enabled(name.getContext())) {
                return;
            }
            DisplayMetrics metrics = name.getResources().getDisplayMetrics();
            float density = metrics.density;
            float available = metrics.widthPixels - (isNew ? 144f : 104f) * density;

            TextPaint titlePaint = new TextPaint(name.getPaint());
            TextPaint smallPaint = new TextPaint(titlePaint);
            smallPaint.setTextSize(titlePaint.getTextSize() * 0.8f);

            CharSequence title = TextUtils.ellipsize(nameText, titlePaint, available, TextUtils.TruncateAt.END);
            CharSequence details = TextUtils.ellipsize(
                    videoCount + (videoCount == 1 ? " video" : " videos") + BULLET + sizeText,
                    smallPaint, available, TextUtils.TruncateAt.END);
            CharSequence location = TextUtils.ellipsize(path, smallPaint, available, TextUtils.TruncateAt.MIDDLE);

            SpannableStringBuilder text = new SpannableStringBuilder(title);
            text.append("\n");
            int start = text.length();
            text.append(details);
            text.append("\n");
            text.append(location);
            int end = text.length();
            text.setSpan(new RelativeSizeSpan(0.8f), start, end, Spanned.SPAN_INCLUSIVE_INCLUSIVE);
            text.setSpan(new ForegroundColorSpan(0x99808080), start, end, Spanned.SPAN_INCLUSIVE_INCLUSIVE);

            name.setSingleLine(false);
            name.setMaxLines(3);
            name.setText(text);
            name.setLineSpacing(3f * density, 1f);
            count.setVisibility(View.GONE);

            int minHeight = (int) (60f * density);
            wrap(itemView, minHeight);
            wrap(row, minHeight);
        } catch (Throwable ignored) {
        }
    }

    private static String played(Object bean) {
        if (bean == null) {
            return "";
        }
        try {
            long now = System.currentTimeMillis();
            long latest = 0L;
            for (Field field : bean.getClass().getDeclaredFields()) {
                if (field.getType() != Long.TYPE || Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                field.setAccessible(true);
                long value = field.getLong(bean);
                if (value >= MIN_EPOCH_MS && value <= now + DAY_MS && value > latest) {
                    latest = value;
                }
            }
            if (latest <= 0L) {
                return "";
            }
            if (latest > now) {
                latest = now;
            }
            return "Played " + DateUtils.getRelativeTimeSpanString(latest, now, 1000L, 0);
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String resolution(Context context, String path) {
        if (path == null) {
            return "";
        }
        synchronized (RESOLUTIONS) {
            String cached = RESOLUTIONS.get(path);
            if (cached != null) {
                return cached;
            }
            try {
                String parent = new File(path).getParent();
                if (parent != null) {
                    Cursor cursor = context.getContentResolver().query(
                            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                            new String[]{"_data", "width", "height"},
                            "_data LIKE ?",
                            new String[]{parent + "/%"},
                            null);
                    if (cursor != null) {
                        try {
                            while (cursor.moveToNext()) {
                                String data = cursor.getString(0);
                                if (data == null) {
                                    continue;
                                }
                                int width = cursor.getInt(1);
                                int height = cursor.getInt(2);
                                RESOLUTIONS.put(data, width > 0 && height > 0 ? width + "x" + height : "");
                            }
                        } finally {
                            cursor.close();
                        }
                    }
                }
            } catch (Exception ignored) {
            }
            String result = RESOLUTIONS.get(path);
            if (result == null) {
                result = "";
                RESOLUTIONS.put(path, result);
            }
            return result;
        }
    }

    private static void alignEnd(TextView view) {
        view.setGravity((view.getGravity() & Gravity.VERTICAL_GRAVITY_MASK) | Gravity.END);
    }

    private static void wrap(View view, int minHeight) {
        if (view == null) {
            return;
        }
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (params != null && params.height > 0) {
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            view.setLayoutParams(params);
        }
        view.setMinimumHeight(minHeight);
    }
}
