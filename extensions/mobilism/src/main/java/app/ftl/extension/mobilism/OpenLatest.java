package app.ftl.extension.mobilism;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

public final class OpenLatest {
    private static final String PKG = "demigos.com.mobilism.";

    private OpenLatest() {
    }

    public static void onMainCreate(Object activity, Bundle saved) {
        ModFeatures.markLatest();
        if (saved != null || !(activity instanceof Activity)) {
            return;
        }
        if (!ModFeatures.isOn(ModFeatures.OPEN_LATEST)) {
            return;
        }
        try {
            Activity a = (Activity) activity;
            ClassLoader cl = a.getClassLoader();
            if (!loggedIn(cl)) {
                return;
            }
            Class<?> target = cl.loadClass(PKG + "UI.Release.AppsActivity_");
            Intent i = new Intent(a, target);
            i.putExtra("id", categoryId(cl));
            a.startActivity(i);
        } catch (Throwable ignored) {
        }
    }

    private static boolean loggedIn(ClassLoader cl) throws Exception {
        Object helper = cl.loadClass(PKG + "logic.HelperFactory").getMethod("getDatabaseHelper").invoke(null);
        Object dao = helper.getClass().getMethod("getUserDao").invoke(helper);
        return dao.getClass().getMethod("getCurrentUser").invoke(dao) != null;
    }

    private static long categoryId(ClassLoader cl) throws Exception {
        Object prefs = cl.loadClass(PKG + "logic.Utils.Preferences").getMethod("getInstance").invoke(null);
        Object type = prefs.getClass().getMethod("getContentType").invoke(prefs);
        String name = type instanceof Enum ? ((Enum<?>) type).name() : String.valueOf(type);
        name = name.toUpperCase();
        if (name.equals("GAMES")) {
            return 5L;
        }
        if (name.equals("BOOKS")) {
            return 4L;
        }
        return 3L;
    }
}
