package app.ftl.extension.firefox;

public final class PinObserver {

    public Object invoke(Object state) {
        try {
            ExtensionPin.onState(state);
        } catch (Throwable ignored) {
        }
        return ModLambda.unit();
    }
}
