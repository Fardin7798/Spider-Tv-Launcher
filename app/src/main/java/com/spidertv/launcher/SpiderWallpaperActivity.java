package com.spidertv.launcher;

import java.util.concurrent.atomic.AtomicBoolean;

import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.widget.Toast;

/**
 * "Set Spider wallpaper": the share (and "Use as") target that makes a photo or video the Home wallpaper.
 * It shows a small sheet while the file is copied into the launcher, then says how it went and closes.
 * Back cancels a copy still running. No storage permission: the sender's content URI grant is enough.
 */
public class SpiderWallpaperActivity extends android.app.Activity {

    private final AtomicBoolean cancel = new AtomicBoolean();

    @Override
    protected void onCreate(android.os.Bundle saved) {
        super.onCreate(saved);
        setContentView(R.layout.activity_spider_wallpaper);
        final Intent in = getIntent();
        final Uri source = sharedUri(in);
        if (source == null) {
            Toast.makeText(this, R.string.spider_wallpaper_nothing, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        SpiderWallpaperStore.importFrom(this, source, in.getType(), cancel, (result, kind) -> {
            final int message = resultMessage(result);
            if (message != 0) Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
            if (!isFinishing() && !isDestroyed()) finish();
        });
    }

    @Override
    protected void onDestroy() {
        // Back, or the system closing it: the read grant ends with this activity, so the copy stops too.
        if (isFinishing()) cancel.set(true);
        super.onDestroy();
    }

    /** The toast for an import outcome; 0 for none (cancelled). */
    static int resultMessage(int result) {
        switch (result) {
            case SpiderWallpaperStore.OK:
                return R.string.spider_wallpaper_done;
            case SpiderWallpaperStore.UNSUPPORTED:
                return R.string.spider_wallpaper_unsupported;
            case SpiderWallpaperStore.TOO_BIG:
                return R.string.spider_wallpaper_too_big;
            case SpiderWallpaperStore.CANCELLED:
                return 0;
            default:
                return R.string.spider_wallpaper_failed;
        }
    }

    /** ACTION_SEND's EXTRA_STREAM (or its clip), else the data URI ("Use as" sends ATTACH_DATA). */
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
        final String scheme = uri.getScheme();
        // content:// only: file:// would need a storage permission and could point at the launcher's own files.
        return "content".equals(scheme) ? uri : null;
    }
}
