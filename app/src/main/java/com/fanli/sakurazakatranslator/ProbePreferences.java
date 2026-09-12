package com.fanli.sakurazakatranslator;

import android.content.Context;

public final class ProbePreferences {
    private static final String FILE = "probe_preferences";
    private static final String TARGET_PACKAGE = "target_package";

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
}
