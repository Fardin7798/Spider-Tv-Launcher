package com.spidertv.launcher;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The launcher's own Settings screen, opened from the Settings tile at the end of the Home row. One
 * focusable row per choice; OK flips a switch or steps to the next value, and Home applies the change
 * when it comes back to the front.
 */
public class SpiderLauncherSettingsActivity extends android.app.Activity {

    private static final int PICK_WALLPAPER = 41;
    private static final int SAVE_BACKUP = 42, PICK_BACKUP = 43, RESTORE_BACKUP = 44;
    private static final String[] WALLPAPER_TYPES = {"image/*", "video/*"};
    /** .json files show up as any of these depending on the TV's document provider. */
    private static final String[] BACKUP_TYPES = {"application/json", "text/plain", "application/octet-stream"};

    private SpiderUserSettings store;
    private LinearLayout rows;
    /** The Choose wallpaper row, whose status names the current wallpaper. */
    private View wallpaperRow;
    private final AtomicBoolean cancelImport = new AtomicBoolean();
    private boolean importing;
    /** The Back up layout and Restore layout rows. */
    private View backupRow, restoreRow;

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

        SpiderSettingsRows.section(rows, R.string.spider_prefs_section_wallpaper);
        wallpaperRow = SpiderSettingsRows.row(rows, null, getString(R.string.spider_prefs_wallpaper_choose),
                wallpaperStatus(), false, row -> chooseWallpaper());
        SpiderSettingsRows.row(rows, null, getString(R.string.spider_prefs_wallpaper_reset), null, false, row -> {
            if (importing) return;
            SpiderWallpaperStore.reset(this);
            SpiderSettingsRows.status(wallpaperRow, wallpaperStatus(), false);
            Toast.makeText(this, R.string.spider_prefs_wallpaper_reset_done, Toast.LENGTH_SHORT).show();
        });

        SpiderSettingsRows.section(rows, R.string.spider_prefs_section_apps);
        link(R.string.spider_prefs_manage_apps, new Intent(this, SpiderAppsActivity.class));

        SpiderSettingsRows.section(rows, R.string.spider_prefs_section_motion);
        toggle(R.string.spider_prefs_animations, SpiderUserSettings.ANIMATIONS, now.motionOn);
        toggle(R.string.spider_prefs_reduced_motion, SpiderUserSettings.REDUCED_MOTION, now.motionGentle);

        SpiderSettingsRows.section(rows, R.string.spider_prefs_section_backup);
        backupRow = SpiderSettingsRows.row(rows, null, getString(R.string.spider_prefs_backup),
                getString(R.string.spider_prefs_backup_note), false, row -> backUp());
        restoreRow = SpiderSettingsRows.row(rows, null, getString(R.string.spider_prefs_restore), null, false,
                row -> pickBackup());

        SpiderSettingsRows.section(rows, R.string.spider_prefs_section_more);
        link(R.string.spider_prefs_default_home, new Intent(this, SpiderSettingsActivity.class));
        link(R.string.spider_prefs_system, SpiderHomeActivity.settingsIntent(this, Settings.ACTION_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    /** Opens the system file picker for a photo or video; says so when the TV has none. */
    private void chooseWallpaper() {
        if (importing) return;
        final Intent open = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*").putExtra(Intent.EXTRA_MIME_TYPES, WALLPAPER_TYPES);
        final Intent get = new Intent(Intent.ACTION_GET_CONTENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*").putExtra(Intent.EXTRA_MIME_TYPES, WALLPAPER_TYPES);
        for (Intent pick : new Intent[]{open, get}) {
            try {
                startActivityForResult(pick, PICK_WALLPAPER);
                return;
            } catch (ActivityNotFoundException | SecurityException none) {
                // try the next picker
            }
        }
        Toast.makeText(this, R.string.spider_unavailable, Toast.LENGTH_SHORT).show();
    }

    /**
     * Saves the layout to a JSON document the user names with the system picker; with no picker on the
     * TV, shares a copy instead.
     */
    private void backUp() {
        final Intent create = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/json").putExtra(Intent.EXTRA_TITLE, SpiderBackupStore.fileName());
        try {
            startActivityForResult(create, SAVE_BACKUP);
            return;
        } catch (ActivityNotFoundException | SecurityException none) {
            // no document picker: share it
        }
        try {
            final Uri copy = SpiderBackupStore.writeShareCopy(this);
            final Intent send = new Intent(Intent.ACTION_SEND).setType("application/json")
                    .putExtra(Intent.EXTRA_STREAM, copy)
                    .putExtra(Intent.EXTRA_SUBJECT, SpiderBackupStore.fileName())
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            send.setClipData(ClipData.newRawUri(SpiderBackupStore.fileName(), copy));
            final Intent chooser = Intent.createChooser(send, getString(R.string.spider_backup_share_title))
                    .putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS,
                            new ComponentName[]{new ComponentName(this, SpiderRestoreActivity.class)});
            startActivity(chooser);
            Toast.makeText(this, R.string.spider_backup_sharing, Toast.LENGTH_LONG).show();
        } catch (IOException | ActivityNotFoundException | SecurityException none) {
            Toast.makeText(this, R.string.spider_unavailable, Toast.LENGTH_SHORT).show();
        }
    }

    /** Opens the system picker for a backup file; the restore sheet checks it and asks first. */
    private void pickBackup() {
        final Intent typed = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*").putExtra(Intent.EXTRA_MIME_TYPES, BACKUP_TYPES);
        final Intent any = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*");
        final Intent get = new Intent(Intent.ACTION_GET_CONTENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*");
        for (Intent pick : new Intent[]{typed, any, get}) {
            try {
                startActivityForResult(pick, PICK_BACKUP);
                return;
            } catch (ActivityNotFoundException | SecurityException none) {
                // try the next picker
            }
        }
        Toast.makeText(this, R.string.spider_unavailable, Toast.LENGTH_SHORT).show();
    }

    private void onBackupResult(int request, int result, Intent data) {
        if (request == RESTORE_BACKUP) {
            if (result != RESULT_OK) return;
            // Every row may have changed: draw them again and keep focus on Restore layout.
            rows.removeAllViews();
            build();
            restoreRow.requestFocus();
            return;
        }
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        if (request == SAVE_BACKUP) {
            SpiderSettingsRows.status(backupRow, getString(R.string.spider_backup_saving), false);
            SpiderBackupStore.writeTo(this, data.getData(), ok -> {
                if (isDestroyed()) return;
                SpiderSettingsRows.status(backupRow, getString(R.string.spider_prefs_backup_note), false);
                Toast.makeText(this, ok ? R.string.spider_backup_done : R.string.spider_backup_failed,
                        Toast.LENGTH_LONG).show();
            });
        } else {
            final Intent sheet = new Intent(this, SpiderRestoreActivity.class).setData(data.getData())
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            try {
                startActivityForResult(sheet, RESTORE_BACKUP);
            } catch (ActivityNotFoundException | SecurityException none) {
                Toast.makeText(this, R.string.spider_unavailable, Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == SAVE_BACKUP || request == PICK_BACKUP || request == RESTORE_BACKUP) {
            onBackupResult(request, result, data);
            return;
        }
        if (request != PICK_WALLPAPER || result != RESULT_OK || data == null) return;
        final Uri picked = data.getData();
        if (picked == null) return;
        importing = true;
        SpiderSettingsRows.status(wallpaperRow, getString(R.string.spider_prefs_wallpaper_copying), false);
        SpiderWallpaperStore.importFrom(this, picked, data.getType(), cancelImport, (outcome, kind) -> {
            importing = false;
            if (isDestroyed()) return;
            SpiderSettingsRows.status(wallpaperRow, wallpaperStatus(), false);
            final int message = SpiderWallpaperActivity.resultMessage(outcome);
            if (message != 0) Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    protected void onDestroy() {
        // The picker's read grant ends with this screen.
        cancelImport.set(true);
        super.onDestroy();
    }

    /** Default, Photo or Video. */
    private String wallpaperStatus() {
        switch (SpiderWallpaperRules.kindOfToken(SpiderWallpaperStore.current(this))) {
            case SpiderWallpaperRules.IMAGE:
                return getString(R.string.spider_prefs_wallpaper_photo);
            case SpiderWallpaperRules.VIDEO:
                return getString(R.string.spider_prefs_wallpaper_video);
            default:
                return getString(R.string.spider_prefs_wallpaper_default);
        }
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
