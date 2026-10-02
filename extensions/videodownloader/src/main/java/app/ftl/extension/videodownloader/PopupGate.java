package app.ftl.extension.videodownloader;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.net.Uri;
import android.os.Build;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;

public final class PopupGate implements DialogInterface.OnClickListener, DialogInterface.OnDismissListener {
    private static boolean showing;

    private final Activity activity;
    private final String host;
    private final String url;
    private final WebView view;

    private PopupGate(Activity activity, String host, String url, WebView view) {
        this.activity = activity;
        this.host = host;
        this.url = url;
        this.view = view;
    }

    public static boolean intercept(WebView view, WebResourceRequest request) {
        try {
            if (!request.isForMainFrame() || request.hasGesture()) {
                return false;
            }
            if (Build.VERSION.SDK_INT >= 24 && request.isRedirect()) {
                return false;
            }
            Uri target = request.getUrl();
            String url = target.toString();
            if (!url.startsWith("http")) {
                return false;
            }
            String page = view.getUrl();
            if (page == null || !page.startsWith("http")) {
                return false;
            }
            String targetHost = target.getHost();
            String pageHost = Uri.parse(page).getHost();
            if (targetHost == null || pageHost == null) {
                return false;
            }
            if (PopupUtil.sameSite(PopupUtil.norm(targetHost), PopupUtil.norm(pageHost))) {
                return false;
            }
            Activity activity = PopupUtil.activityOf(view.getContext());
            if (activity == null) {
                return false;
            }
            check(activity, url, view);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static void check(Activity activity, String url, WebView view) {
        String host = Uri.parse(url).getHost();
        if (host == null) {
            host = "";
        }
        if (PopupStore.get(activity, PopupStore.REDIRECT, host) == PopupStore.BLOCK) {
            return;
        }
        if (showing || activity.isFinishing()) {
            return;
        }
        showing = true;
        PopupGate gate = new PopupGate(activity, host, url, view);
        try {
            new AlertDialog.Builder(activity)
                    .setTitle("Popup blocked")
                    .setMessage(url)
                    .setPositiveButton("Allow", gate)
                    .setNeutralButton("Block", gate)
                    .setNegativeButton("Always Block", gate)
                    .setOnDismissListener(gate)
                    .show();
        } catch (Throwable t) {
            showing = false;
        }
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        if (which == DialogInterface.BUTTON_POSITIVE) {
            view.loadUrl(url);
        } else if (which == DialogInterface.BUTTON_NEGATIVE) {
            PopupStore.put(activity, PopupStore.REDIRECT, host, PopupStore.BLOCK);
        }
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        showing = false;
    }
}
