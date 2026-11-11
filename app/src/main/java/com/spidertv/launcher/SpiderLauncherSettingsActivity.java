package com.spidertv.launcher;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.provider.Settings;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * The launcher's own Settings screen, opened from the Settings tile at the end of the Home row. One
 * focusable row per choice; OK flips a switch or steps to the next value, and Home applies the change
 * when it comes back to the front.
 */
public class SpiderLauncherSettingsActivity extends android.app.Activity {

    private SpiderUserSettings store;
    private LinearLayout rows;

    @Override
    protected void onCreate(android.os.Bundle saved) {
        super.onCreate(saved);
        setContentView(R.layout.activity_spider_settings);
        ((TextView) findViewById(R.id.spiderSettingsTitle)).setText(R.string.spider_prefs_title);
        store = new SpiderUserSettings(this);
        rows = findViewById(R.id.spiderRows);
        build();
        if (rows.getChildCount() > 1) rows.getChildAt(1).requestFocus();
    }

    private void build() {
        final SpiderPrefs now = SpiderPrefs.read(this);
        SpiderSettingsRows.section(rows, R.string.spider_prefs_section_home);
        toggle(R.string.spider_prefs_show_clock, SpiderUserSettings.SHOW_CLOCK, now.showClock);
        final int[] formats = {R.string.spider_prefs_clock_system, R.string.spider_prefs_clock_12h,
                R.string.spider_prefs_clock_24h};
        SpiderSettingsRows.row(rows, null, getString(R.string.spider_prefs_clock_format),
                getString(formats[store.clockFormat()]), false, row -> {
                    final int next = (store.clockFormat() + 1) % formats.length;
                    store.putInt(SpiderUserSettings.CLOCK_FORMAT, next);
                    SpiderSettingsRows.status(row, getString(formats[next]), false);
                });
        toggle(R.string.spider_prefs_show_date, SpiderUserSettings.SHOW_DATE, now.showDate);
        toggle(R.string.spider_prefs_show_greeting, SpiderUserSettings.SHOW_GREETING, now.showGreeting);
        toggle(R.string.spider_prefs_phone_apps, SpiderUserSettings.PHONE_APPS, now.phoneApps);

        SpiderSettingsRows.section(rows, R.string.spider_prefs_section_motion);
        toggle(R.string.spider_prefs_animations, SpiderUserSettings.ANIMATIONS, now.motionOn);
        toggle(R.string.spider_prefs_reduced_motion, SpiderUserSettings.REDUCED_MOTION, now.motionGentle);

        SpiderSettingsRows.section(rows, R.string.spider_prefs_section_more);
        link(R.string.spider_prefs_default_home, new Intent(this, SpiderSettingsActivity.class));
        link(R.string.spider_prefs_system, SpiderHomeActivity.settingsIntent(this, Settings.ACTION_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    /** An On/Off row; the green dot shows when on. */
    private void toggle(int title, String key, boolean current) {
        SpiderSettingsRows.row(rows, null, getString(title), onOff(current), current,
                row -> {
                    final boolean on = !store.flag(key, current);
                    store.putFlag(key, on);
                    SpiderSettingsRows.status(row, onOff(on), on);
                });
    }

    private void link(int title, Intent target) {
        SpiderSettingsRows.row(rows, null, getString(title), null, false, row -> {
            try {
                startActivity(target);
            } catch (ActivityNotFoundException | SecurityException none) {
                Toast.makeText(this, R.string.spider_unavailable, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private String onOff(boolean on) {
        return getString(on ? R.string.spider_prefs_on : R.string.spider_prefs_off);
    }
}
