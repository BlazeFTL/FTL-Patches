package app.ftl.extension.videodownloader;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.net.Uri;
import android.os.SystemClock;

import java.util.Locale;

final class PopupUtil {
    private PopupUtil() {
    }

    static Activity activityOf(Context context) {
        Context c = context;
        while (c instanceof ContextWrapper) {
            if (c instanceof Activity) {
                return (Activity) c;
            }
            c = ((ContextWrapper) c).getBaseContext();
        }
        return null;
    }

    static String norm(String host) {
        if (host == null) {
            return null;
        }
        String s = host.toLowerCase(Locale.ROOT);
        return s.startsWith("www.") ? s.substring(4) : s;
    }

    static String hostOf(String url) {
        if (url == null) {
            return null;
        }
        return norm(Uri.parse(url).getHost());
    }

    static boolean sameSite(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return a.equals(b) || b.endsWith("." + a) || a.endsWith("." + b);
    }

    private static final int SHORT_MAX = 60;
    private static final long BUSY_TIMEOUT_MS = 60000L;
    private static boolean busy;
    private static long busySince;

    static synchronized boolean tryAcquire() {
        long now = SystemClock.uptimeMillis();
        if (busy && now - busySince < BUSY_TIMEOUT_MS) {
            return false;
        }
        busy = true;
        busySince = now;
        return true;
    }

    static synchronized void release() {
        busy = false;
    }

    static String shorten(String url) {
        if (url == null) {
            return null;
        }
        String s = url;
        if (s.startsWith("https://")) {
            s = s.substring(8);
        } else if (s.startsWith("http://")) {
            s = s.substring(7);
        }
        return s.length() > SHORT_MAX ? s.substring(0, SHORT_MAX) + "\u2026" : s;
    }

    static void openUrl(Context ctx, String url) {
        if (url == null) {
            return;
        }
        Activity activity = activityOf(ctx);
        if (activity instanceof PopupTabOpener) {
            ((PopupTabOpener) activity).openPopupTab(url);
        }
    }
}
