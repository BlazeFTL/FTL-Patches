package app.ftl.extension.firefox;

@SuppressWarnings("unused")
public final class RangeSelectNoOp {

    public Object invoke() {
        RangeSelect.endGesture();
        return ModLambda.unit();
    }

    public Object invoke(Object a, Object b) {
        return ModLambda.unit();
    }
}
