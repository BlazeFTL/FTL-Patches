package app.ftl.extension.firefox;

public final class PinRun implements Runnable {

    @Override
    public void run() {
        ExtensionPin.sync();
    }
}
