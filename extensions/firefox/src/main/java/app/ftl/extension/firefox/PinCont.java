package app.ftl.extension.firefox;

import android.graphics.Bitmap;

public final class PinCont {

    private final PinState state;

    PinCont(PinState state) {
        this.state = state;
    }

    public void resumeWith(Object result) {
        try {
            if (result instanceof Bitmap) {
                state.bitmap = (Bitmap) result;
                ExtensionPin.refresh();
            }
        } catch (Throwable ignored) {
        }
    }
}
