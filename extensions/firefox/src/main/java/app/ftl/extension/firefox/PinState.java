package app.ftl.extension.firefox;

import android.graphics.Bitmap;

final class PinState {
    volatile Bitmap bitmap;
    Integer bg;
    Boolean enabled;
    Integer fg;
    boolean hidden;
    String id;
    Object loader;
    String text;
    String title;
}
