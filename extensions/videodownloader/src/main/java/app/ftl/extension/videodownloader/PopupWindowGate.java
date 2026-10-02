package app.ftl.extension.videodownloader;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Message;
import android.webkit.WebView;

import java.lang.ref.WeakReference;

public final class PopupWindowGate implements DialogInterface.OnClickListener, DialogInterface.OnDismissListener {
    private static boolean showing;
    private static WeakReference<WebView> openerView;
    private static String openerUrl;
    private static String hitExtra;

    private final Activity activity;
    private final String url;
    private final String key;

    private PopupWindowGate(Activity activity, String url, String key) {
        this.activity = activity;
        this.url = url;
        this.key = key;
    }

    public static void captureOpener(WebView view) {
        try {
            openerView = new WeakReference<WebView>(view);
            openerUrl = view.getUrl();
            WebView.HitTestResult result = view.getHitTestResult();
            hitExtra = result != null ? result.getExtra() : null;
        } catch (Throwable t) {
            openerView = null;
            openerUrl = null;
            hitExtra = null;
        }
    }

    public static boolean shouldBlock(Message resultMsg) {
        try {
            WebView view = openerView != null ? openerView.get() : null;
            if (view == null) {
                return false;
            }
            Activity activity = PopupUtil.activityOf(view.getContext());
            if (activity == null) {
                return false;
            }
            return isBlocked(activity, openerUrl != null ? openerUrl : "", hitExtra, resultMsg);
        } catch (Throwable t) {
            showing = false;
            return false;
        }
    }

    private static boolean isBlocked(Activity activity, String opener, String hit, Message msg) {
        if (showing) {
            cancel(msg);
            return true;
        }
        showing = true;
        if (msg == null) {
            decide(activity, opener, hit);
            return true;
        }
        PopupProbe.start(activity, opener, msg);
        return true;
    }

    private static void cancel(Message msg) {
        if (msg != null && msg.obj instanceof WebView.WebViewTransport) {
            ((WebView.WebViewTransport) msg.obj).setWebView(null);
            msg.sendToTarget();
        }
    }

    static void decide(Activity activity, String opener, String url) {
        String host = PopupUtil.hostOf(url);
        if (host != null && !PopupUtil.sameSite(PopupUtil.hostOf(opener), host)) {
            int rule = PopupStore.get(activity, PopupStore.WINDOW, host);
            if (rule == PopupStore.BLOCK) {
                showing = false;
                return;
            }
            if (rule == PopupStore.ALLOW) {
                showing = false;
                PopupUtil.openUrl(activity, url);
                return;
            }
        }
        showDialog(activity, opener, url);
    }

    private static void showDialog(Activity activity, String opener, String url) {
        String host = PopupUtil.hostOf(url);
        String key = null;
        if (host != null && !PopupUtil.sameSite(PopupUtil.hostOf(opener), host)) {
            key = host;
        }
        PopupWindowGate gate = new PopupWindowGate(activity, url, key);
        try {
            AlertDialog.Builder builder = new AlertDialog.Builder(activity)
                    .setTitle("Popup blocked")
                    .setMessage("Popup from: " + PopupUtil.shorten(opener) + "\nOpened URL: "
                            + (url != null ? PopupUtil.shorten(url) : "N/A (script popup)"));
            if (key != null) {
                builder.setPositiveButton("Always allow", gate)
                        .setNeutralButton("Allow once", gate)
                        .setNegativeButton("Always Block", gate);
            } else {
                if (url != null) {
                    builder.setPositiveButton("Allow once", gate);
                }
                builder.setNegativeButton("Block", gate);
            }
            builder.setOnDismissListener(gate).show();
        } catch (Throwable t) {
            showing = false;
        }
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        if (which == DialogInterface.BUTTON_NEUTRAL) {
            PopupUtil.openUrl(activity, url);
        } else if (which == DialogInterface.BUTTON_POSITIVE) {
            if (key != null) {
                PopupStore.put(activity, PopupStore.WINDOW, key, PopupStore.ALLOW);
            }
            PopupUtil.openUrl(activity, url);
        } else if (which == DialogInterface.BUTTON_NEGATIVE && key != null) {
            PopupStore.put(activity, PopupStore.WINDOW, key, PopupStore.BLOCK);
        }
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        showing = false;
    }
}
