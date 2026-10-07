package app.ftl.extension.firefox;

public final class PinEvent {

    final String id;
    final boolean isLong;

    PinEvent(String id, boolean isLong) {
        this.id = id;
        this.isLong = isLong;
    }
}
