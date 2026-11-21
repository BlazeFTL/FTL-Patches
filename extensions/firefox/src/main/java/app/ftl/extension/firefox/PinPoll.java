package app.ftl.extension.firefox;

// Fallback when the store subscription cannot be resumed.
public final class PinPoll implements Runnable {

    @Override
    public void run() {
        ExtensionPin.tick();
    }
}
