package com.fanli.sakurazakatranslator;

import android.content.Context;

public final class ProbePreferences {
    private static final String FILE = "probe_preferences";
    private static final String TARGET_PACKAGE = "target_package";
    private static final String SYNTHETIC_MODE = "synthetic_mode";

    private ProbePreferences() { }

    public static String targetPackage(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
                .getString(TARGET_PACKAGE, "").trim();
    }

    public static void saveTargetPackage(Context context, String value) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
                .putString(TARGET_PACKAGE, value == null ? "" : value.trim())
                .apply();
    }

    public static boolean syntheticMode(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
                .getBoolean(SYNTHETIC_MODE, false);
    }

    public static void saveSyntheticMode(Context context, boolean enabled) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
                .putBoolean(SYNTHETIC_MODE, enabled)
                .apply();
    }
}
