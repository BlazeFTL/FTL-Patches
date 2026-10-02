package app.ftl.extension.videodownloader;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.net.Uri;

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
