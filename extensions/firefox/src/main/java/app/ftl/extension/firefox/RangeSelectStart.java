package app.ftl.extension.firefox;

import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

@SuppressWarnings({"unchecked", "rawtypes", "unused"})
public final class RangeSelectStart {

    private static final String TAG = "MorpheFirefox";
    private static final String FUNCTION1 = "kotlin.jvm.functions.Function1";
    private static final int VERTICAL = 0;

    private final Object listState;
    private final Object state;

    RangeSelectStart(Object listState, Object state) {
        this.listState = listState;
        this.state = state;
    }

    public Object invoke(Object offset) {
        try {
            press(offset);
        } catch (Throwable t) {
            Log.e(TAG, "range long press failed", t);
        }
        return TabAccess.unit();
    }

    private void press(Object offset) throws Exception {
        if (listState == null || state == null || offset == null) return;
        long packed = packed(offset);

        List items = TabAccess.scrollItems(listState);
        if (items == null) return;
        boolean vertical = TabAccess.scrollOrientation(listState) == VERTICAL;
        int position = (int) Float.intBitsToFloat(vertical ? (int) packed : (int) (packed >> 32));

        Object hit = null;
        for (Object item : items) {
            int start = (Integer) TabAccess.get(item, "getOffset");
            int end = start + TabAccess.itemSize(item);
            if (start <= position && position <= end) {
                hit = item;
                break;
            }
        }
        if (hit == null) return;

        haptic();
        Object callback = callback();
        Method invoke = callback.getClass().getMethod("invoke", Object.class);
        invoke.setAccessible(true);
        invoke.invoke(callback, hit);
    }

    // Offset is a value class boxed as a single-long-field object. Its class is R8-renamed, so take the long field.
    private static long packed(Object offset) throws Exception {
        for (Field field : offset.getClass().getDeclaredFields()) {
            if (field.getType() == long.class) {
                field.setAccessible(true);
                return field.getLong(offset);
            }
        }
        throw new NoSuchFieldException("Offset.packedValue");
    }

    // Fenix ListReorderState keeps field names. The android-components interactable state is renamed:
    // its only Function1 field is the long press callback.
    private Object callback() throws Exception {
        try {
            return RangeSelect.read(state, "onLongPress");
        } catch (NoSuchFieldException ignored) {
        }
        Object found = null;
        for (Class<?> type = state.getClass(); type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (!FUNCTION1.equals(field.getType().getName())) continue;
                if (found != null) throw new NoSuchFieldException("onLongPress is ambiguous");
                field.setAccessible(true);
                found = field.get(state);
            }
        }
        if (found == null) throw new NoSuchFieldException("onLongPress");
        return found;
    }

    private void haptic() {
        try {
            Object feedback = RangeSelect.read(state, "hapticFeedback");
            for (Method method : feedback.getClass().getMethods()) {
                Class<?>[] p = method.getParameterTypes();
                if (method.getName().startsWith("performHapticFeedback") && p.length == 1 && p[0] == int.class) {
                    method.setAccessible(true);
                    method.invoke(feedback, 0);
                    return;
                }
            }
        } catch (Throwable ignored) {
        }
    }
}
