package app.ftl.extension.firefox;

public final class PinEvent {

    final String id;
    final boolean isLong;

    PinEvent(String id, boolean isLong) {
        this.id = id;
        this.isLong = isLong;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof PinEvent && ((PinEvent) other).id.equals(id) && ((PinEvent) other).isLong == isLong;
    }

    @Override
    public int hashCode() {
        return id.hashCode() * 31 + (isLong ? 1 : 0);
    }
}
