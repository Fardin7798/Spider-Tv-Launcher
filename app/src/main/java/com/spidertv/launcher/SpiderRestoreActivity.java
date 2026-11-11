package com.spidertv.launcher;

import java.util.regex.Pattern;

import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * "Restore Spider layout": a small glass sheet that reads a layout backup (picked under Settings, or
 * shared as a file or as text), says what it holds and asks Restore or Cancel before anything is
 * overwritten. A file that is not a valid backup is refused with a reason and changes nothing. After a
 * restore the settings revision moves on, so Home redraws when it comes back.
 */
public class SpiderRestoreActivity extends android.app.Activity {

    private static final Pattern DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}.*");

    private TextView status;
    private View progress;
    private LinearLayout rows;
    private SpiderBackup.Layout pending;

    @Override
    protected void onCreate(android.os.Bundle saved) {
        super.onCreate(saved);
        setContentView(R.layout.activity_spider_restore);
        status = findViewById(R.id.spiderRestoreStatus);
        progress = findViewById(R.id.spiderRestoreProgress);
        rows = findViewById(R.id.spiderRestoreRows);
        setResult(RESULT_CANCELED);

        final Intent in = getIntent();
        final Uri source = sharedUri(in);
        final CharSequence text = source == null ? sharedText(in) : null;
        final SpiderBackupStore.Read shown = (layout, error) -> {
            if (isFinishing() || isDestroyed()) return;
            if (layout != null) ask(layout);
            else refuse(error);
        };
        if (source != null) SpiderBackupStore.readFrom(this, source, shown);
        else if (text != null) SpiderBackupStore.readText(text, shown);
        else refuse(0);
    }

    /** The backup checked out: say what it holds and wait for Restore or Cancel (Cancel has focus). */
    private void ask(SpiderBackup.Layout layout) {
        pending = layout;
        progress.setVisibility(View.GONE);
        final StringBuilder b = new StringBuilder();
        if (layout.appVersion != null && !layout.appVersion.isEmpty()) {
            final String day = layout.created != null && DATE.matcher(layout.created).matches()
                    ? layout.created.substring(0, 10) : null;
            b.append(day == null ? getString(R.string.spider_restore_from, layout.appVersion)
                    : getString(R.string.spider_restore_from_on, layout.appVersion, day)).append('\n');
        }
        b.append(getString(R.string.spider_restore_counts, layout.settingCount(), count(layout.hidden),
                count(layout.order))).append("\n\n");
        b.append(getString(R.string.spider_restore_warning));
        if (SpiderBackup.WALLPAPER_PHOTO.equals(layout.wallpaper)) {
            b.append(' ').append(getString(R.string.spider_restore_wallpaper_photo));
        } else if (SpiderBackup.WALLPAPER_VIDEO.equals(layout.wallpaper)) {
            b.append(' ').append(getString(R.string.spider_restore_wallpaper_video));
        }
        status.setText(b);
        rows.removeAllViews();
        SpiderSettingsRows.row(rows, null, getString(R.string.spider_restore_confirm), null, false, row -> restore());
        final View cancel = SpiderSettingsRows.row(rows, null, getString(R.string.spider_restore_cancel), null, false,
                row -> finish());
        cancel.requestFocus();
    }

    private void restore() {
        if (pending == null) return;
        final SpiderUserSettings store = new SpiderUserSettings(this);
        final boolean ok = store.restore(SpiderBackup.merge(store.backupLayout(), pending));
        pending = null;
        if (ok) setResult(RESULT_OK);
        Toast.makeText(getApplicationContext(), ok ? R.string.spider_restore_done : R.string.spider_restore_failed,
                Toast.LENGTH_SHORT).show();
        finish();
    }

    /** Not a usable backup: say why, nothing changes, Close has focus. */
    private void refuse(int error) {
        progress.setVisibility(View.GONE);
        status.setText(reasonText(error));
        rows.removeAllViews();
        SpiderSettingsRows.row(rows, null, getString(R.string.spider_restore_close), null, false, row -> finish())
                .requestFocus();
    }

    private static int reasonText(int error) {
        switch (error) {
            case SpiderBackup.EMPTY:
                return R.string.spider_restore_err_empty;
            case SpiderBackup.TOO_BIG:
                return R.string.spider_restore_err_too_big;
            case SpiderBackup.NOT_JSON:
            case SpiderBackup.NOT_SPIDER:
                return R.string.spider_restore_err_not_backup;
            case SpiderBackup.NEWER:
                return R.string.spider_restore_err_newer;
            case SpiderBackup.BAD_VERSION:
                return R.string.spider_restore_err_version;
            case SpiderBackupStore.UNREADABLE:
                return R.string.spider_restore_err_unreadable;
            default:
                return R.string.spider_restore_err_nothing;
        }
    }

    private String count(java.util.List<String> list) {
        return list == null ? getString(R.string.spider_restore_kept) : String.valueOf(list.size());
    }

    /** A content URI from ACTION_SEND's stream, its clip, or the data (Settings starts it that way). */
    @SuppressWarnings("deprecation")
    private static Uri sharedUri(Intent in) {
        if (in == null) return null;
        Uri uri = null;
        if (Intent.ACTION_SEND.equals(in.getAction())) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                uri = in.getParcelableExtra(Intent.EXTRA_STREAM, Uri.class);
            } else {
                uri = in.getParcelableExtra(Intent.EXTRA_STREAM);
            }
        }
        if (uri == null) {
            final ClipData clip = in.getClipData();
            if (clip != null && clip.getItemCount() > 0) uri = clip.getItemAt(0).getUri();
        }
        if (uri == null) uri = in.getData();
        if (uri == null) return null;
        // content:// only: file:// would need a storage permission.
        return "content".equals(uri.getScheme()) ? uri : null;
    }

    /** Backup JSON shared as text (EXTRA_TEXT), when no file came with it. */
    private static CharSequence sharedText(Intent in) {
        if (in == null || !Intent.ACTION_SEND.equals(in.getAction())) return null;
        final CharSequence text = in.getCharSequenceExtra(Intent.EXTRA_TEXT);
        return text == null || text.length() == 0 ? null : text;
    }
}
