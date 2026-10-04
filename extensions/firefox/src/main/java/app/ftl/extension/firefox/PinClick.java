package app.ftl.extension.firefox;

public final class PinClick {

    final Object base;
    final Object onLong;

    PinClick(Object base, Object onLong) {
        this.base = base;
        this.onLong = onLong;
    }

    public Object invoke() {
        return ExtensionPin.invokeFunction0(base);
    }
}
