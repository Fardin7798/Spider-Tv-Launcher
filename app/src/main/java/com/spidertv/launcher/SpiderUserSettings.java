package com.spidertv.launcher;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Choices made on the in-app Settings screen, kept in SharedPreferences. Every write bumps a revision
 * counter so Home can tell on its next resume that something changed.
 */
final class SpiderUserSettings {

    static final String SHOW_CLOCK = "show_clock";
    static final String SHOW_DATE = "show_date";
    static final String SHOW_GREETING = "show_greeting";
    static final String PHONE_APPS = "phone_apps";
    static final String CLOCK_FORMAT = "clock_format";
    static final String ANIMATIONS = "animations";
    static final String REDUCED_MOTION = "reduced_motion";
    /** Comma-separated package lists written by Manage apps. */
    static final String HIDDEN_APPS = "hidden_apps";
    static final String APP_ORDER = "app_order";
    private static final String REVISION = "revision";

    /** Clock format values: follow the TV, always 12-hour, always 24-hour. */
    static final int CLOCK_SYSTEM = 0, CLOCK_12H = 1, CLOCK_24H = 2;

    private final SharedPreferences store;

    SpiderUserSettings(Context context) {
        store = context.getSharedPreferences(SpiderPrefs.STORE_NAME, Context.MODE_PRIVATE);
    }

    boolean flag(String key, boolean fallback) {
        return store.getBoolean(key, fallback);
    }

    /** True once the user has set this key on the Settings screen. */
    boolean has(String key) {
        return store.contains(key);
    }

    int clockFormat() {
        int v = store.getInt(CLOCK_FORMAT, CLOCK_SYSTEM);
        return v >= CLOCK_SYSTEM && v <= CLOCK_24H ? v : CLOCK_SYSTEM;
    }

    String text(String key) {
        return store.getString(key, "");
    }

    int revision() {
        return store.getInt(REVISION, 0);
    }

    void putFlag(String key, boolean on) {
        store.edit().putBoolean(key, on).putInt(REVISION, revision() + 1).apply();
    }

    void putInt(String key, int value) {
        store.edit().putInt(key, value).putInt(REVISION, revision() + 1).apply();
    }

    void putText(String key, String value) {
        store.edit().putString(key, value).putInt(REVISION, revision() + 1).apply();
    }
}
