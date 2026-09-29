package app.ftl.extension.mxplayerad;

import android.os.Handler;
import android.os.Looper;

public final class EnhanceForcer implements Runnable {
    private static final int TICKS = 8;
    private static final long DELAY_MS = 300L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable reapply;
    private int left;

    public EnhanceForcer(Runnable reapply) {
        this.reapply = reapply;
    }

    public void restart() {
        handler.removeCallbacks(this);
        left = TICKS;
        run();
    }

    @Override
    public void run() {
        reapply.run();
        left--;
        if (left > 0) {
            handler.postDelayed(this, DELAY_MS);
        }
    }
}
