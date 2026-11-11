package com.spidertv.launcher;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import android.app.AlertDialog;
import android.view.KeyEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Manage apps: every app the Home row can show, in row order. OK on an app opens its actions (hide or
 * show on Home, move); while moving, Up and Down carry the app through the list and OK or Back drops it.
 * Changes are saved at once and Home redraws its row when it comes back to the front.
 */
public class SpiderAppsActivity extends android.app.Activity {

    private SpiderUserSettings store;
    private LinearLayout rows;
    /** Apps in the order shown; row i is apps.get(i). */
    private final List<SpiderCarousel.Item> apps = new ArrayList<>();
    private List<String> hidden = new ArrayList<>();
    private List<String> savedOrder = new ArrayList<>();
    /** Package being moved, or null. */
    private String moving;

    @Override
    protected void onCreate(android.os.Bundle saved) {
        super.onCreate(saved);
        setContentView(R.layout.activity_spider_settings);
        ((TextView) findViewById(R.id.spiderSettingsTitle)).setText(R.string.spider_apps_title);
        store = new SpiderUserSettings(this);
        rows = findViewById(R.id.spiderRows);
        load(null);
    }

    /** Reads the apps and the saved lists, then redraws, focusing {@code focusPkg} (or the first row). */
    private void load(String focusPkg) {
        final SpiderPrefs cfg = SpiderPrefs.read(this);
        hidden = new ArrayList<>(cfg.userHidden);
        savedOrder = new ArrayList<>(cfg.appOrder);
        apps.clear();
        apps.addAll(SpiderHomeController.installedApps(this, cfg, new HashSet<>(cfg.hiddenPackages),
                new HashSet<String>()));
        rows.removeAllViews();
        View focus = null;
        for (SpiderCarousel.Item app : apps) {
            final View row = SpiderSettingsRows.row(rows, app.icon, app.label, null, false, v -> clicked(app));
            row.setTag(app.pkg);
            paint(row, app.pkg);
            if (app.pkg.equals(focusPkg) || focus == null) focus = row;
        }
        if (focus != null) focus.requestFocus();
    }

    private View rowOf(String pkg) {
        return rows.findViewWithTag(pkg);
    }

    private void paint(View row, String pkg) {
        if (row == null) return;
        if (pkg.equals(moving)) {
            SpiderSettingsRows.status(row, getString(R.string.spider_apps_moving), false);
        } else if (hidden.contains(pkg)) {
            SpiderSettingsRows.status(row, getString(R.string.spider_apps_hidden), false);
        } else {
            SpiderSettingsRows.status(row, getString(R.string.spider_apps_shown), true);
        }
    }

    private void clicked(SpiderCarousel.Item app) {
        if (moving != null) {
            drop();
            return;
        }
        final boolean isHidden = hidden.contains(app.pkg);
        final List<String> labels = new ArrayList<>();
        final List<Runnable> actions = new ArrayList<>();
        labels.add(getString(isHidden ? R.string.spider_apps_show : R.string.spider_apps_hide));
        actions.add(() -> setHidden(app.pkg, !isHidden));
        labels.add(getString(R.string.spider_apps_move));
        actions.add(() -> startMove(app.pkg));
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(app.label)
                .setItems(labels.toArray(new CharSequence[0]), (d, which) -> actions.get(which).run())
                .show();
    }

    private void setHidden(String pkg, boolean hide) {
        hidden = SpiderAppOrder.setHidden(hidden, pkg, hide);
        store.putText(SpiderUserSettings.HIDDEN_APPS, SpiderAppOrder.join(hidden));
        paint(rowOf(pkg), pkg);
    }

    // ---- move mode ------------------------------------------------------------------------------

    private void startMove(String pkg) {
        moving = pkg;
        final View row = rowOf(pkg);
        paint(row, pkg);
        if (row != null) row.requestFocus();
    }

    private void drop() {
        final String pkg = moving;
        moving = null;
        if (pkg != null) paint(rowOf(pkg), pkg);
    }

    /** Moves the app being moved one row up (-1) or down (+1) and saves the new order. */
    private void step(int by) {
        final List<String> shown = new ArrayList<>();
        for (SpiderCarousel.Item app : apps) shown.add(app.pkg);
        final int from = shown.indexOf(moving);
        final List<String> next = SpiderAppOrder.move(shown, moving, by);
        final int to = next.indexOf(moving);
        if (from < 0 || from == to) return;
        apps.add(to, apps.remove(from));
        final View row = rows.getChildAt(from);
        rows.removeViewAt(from);
        rows.addView(row, to);
        row.requestFocus();
        savedOrder = SpiderAppOrder.keepRest(next, savedOrder);
        store.putText(SpiderUserSettings.APP_ORDER, SpiderAppOrder.join(savedOrder));
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent key) {
        if (moving == null) return super.dispatchKeyEvent(key);
        final boolean down = key.getAction() == KeyEvent.ACTION_DOWN;
        switch (key.getKeyCode()) {
            case KeyEvent.KEYCODE_DPAD_UP:
                if (down) step(-1);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (down) step(1);
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_BACK:
                if (!down) drop();
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                return true;
            default:
                return super.dispatchKeyEvent(key);
        }
    }
}
