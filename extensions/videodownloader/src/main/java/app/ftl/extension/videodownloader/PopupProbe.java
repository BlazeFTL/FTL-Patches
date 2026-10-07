package app.ftl.extension.videodownloader;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;

final class PopupProbe extends WebViewClient implements Runnable {
    private final Activity activity;
    private final String opener;
    private final WebView webView;
    private final Handler handler;
    private boolean fired;
    private boolean destroying;
    private boolean destroyed;

    private PopupProbe(Activity activity, String opener, WebView webView, Handler handler) {
        this.activity = activity;
        this.opener = opener;
        this.webView = webView;
        this.handler = handler;
    }

    static void start(Activity activity, String opener, Message msg) {
        try {
            WebView webView = new WebView(activity);
            Handler handler = new Handler(Looper.getMainLooper());
            PopupProbe probe = new PopupProbe(activity, opener, webView, handler);
            webView.setWebViewClient(probe);
            ((WebView.WebViewTransport) msg.obj).setWebView(webView);
            msg.sendToTarget();
            handler.postDelayed(probe, 300L);
        } catch (Exception e) {
            PopupWindowGate.decide(activity, opener, null);
        }
    }

    private void handle(String url) {
        if (url != null && !url.startsWith("about:")) {
            fire(url);
        }
    }

    private void fire(String url) {
        if (fired) {
            return;
        }
        fired = true;
        destroying = true;
        webView.stopLoading();
        handler.removeCallbacks(this);
        handler.post(this);
        PopupWindowGate.decide(activity, opener, url);
    }

    @Override
    public void run() {
        if (destroyed) {
            return;
        }
        if (destroying) {
            destroyed = true;
            webView.destroy();
        } else {
            fire(null);
        }
    }

    @Override
    public void onPageStarted(WebView view, String url, Bitmap favicon) {
        handle(url);
    }

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
        handle(request.getUrl().toString());
        return true;
    }
}
