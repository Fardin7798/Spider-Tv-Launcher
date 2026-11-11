package com.spidertv.launcher;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

/**
 * Moves layout backups between {@link SpiderUserSettings} and the outside: writes one to a document the
 * user created, to a shareable cache file when the TV has no file picker, and reads one back from a
 * content URI or shared text. All file work runs off the main thread; nothing needs a storage permission.
 */
final class SpiderBackupStore {

    private static final String TAG = "SpiderTv.Backup";
    /** cacheDir subfolder shared through the FileProvider (res/xml/spider_backup_paths.xml). */
    private static final String SHARE_DIR = "backup";
    /** Read failures that are not a refusal by {@link SpiderBackup}. */
    static final int UNREADABLE = 100;

    interface Wrote {
        void onDone(boolean ok);
    }

    interface Read {
        /** On the main thread: a layout and 0, or null and a SpiderBackup reason (or UNREADABLE). */
        void onDone(SpiderBackup.Layout layout, int error);
    }

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();

    private SpiderBackupStore() {
    }

    static String authority(Context context) {
        return context.getPackageName() + ".files";
    }

    /** spider-backup-YYYYMMDD.json for today. */
    static String fileName() {
        final Calendar now = Calendar.getInstance();
        return SpiderBackup.fileName(now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1, now.get(Calendar.DAY_OF_MONTH));
    }

    /** The current layout as backup JSON. */
    static String build(Context context) {
        String version = "";
        int code = 0;
        try {
            final PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            version = info.versionName == null ? "" : info.versionName;
            code = (int) info.getLongVersionCode();
        } catch (PackageManager.NameNotFoundException ignored) {
            // our own package is always there
        }
        final String created = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.ROOT).format(new Date());
        return SpiderBackup.serialize(new SpiderUserSettings(context).backupLayout(), version, code, created);
    }

    /** Writes the current layout into a document the user just created. */
    static void writeTo(Context context, Uri target, Wrote done) {
        final Context app = context.getApplicationContext();
        final String json = build(app);
        final Handler main = new Handler(Looper.getMainLooper());
        WORKER.execute(() -> {
            boolean ok = false;
            try (OutputStream out = openForWrite(app.getContentResolver(), target)) {
                out.write(json.getBytes(StandardCharsets.UTF_8));
                ok = true;
            } catch (IOException | RuntimeException e) {
                Log.w(TAG, "backup write failed", e);
            }
            final boolean r = ok;
            main.post(() -> done.onDone(r));
        });
    }

    /**
     * Writes the current layout to cacheDir/backup for sharing and returns its FileProvider URI. Small
     * enough to do on the calling thread. Throws when the file or the provider is not usable.
     */
    static Uri writeShareCopy(Context context) throws IOException {
        final File dir = new File(context.getCacheDir(), SHARE_DIR);
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("no folder");
        final File[] old = dir.listFiles();
        if (old != null) {
            for (File f : old) {
                if (!f.delete()) Log.w(TAG, "could not delete " + f.getName());
            }
        }
        final File file = new File(dir, fileName());
        try (OutputStream out = new FileOutputStream(file)) {
            out.write(build(context).getBytes(StandardCharsets.UTF_8));
        }
        try {
            return androidx.core.content.FileProvider.getUriForFile(context, authority(context), file);
        } catch (IllegalArgumentException e) {
            throw new IOException(e);
        }
    }

    /** Reads and checks a backup from a content URI, at most {@link SpiderBackup#MAX_BYTES}. */
    static void readFrom(Context context, Uri source, Read done) {
        final ContentResolver resolver = context.getApplicationContext().getContentResolver();
        final Handler main = new Handler(Looper.getMainLooper());
        WORKER.execute(() -> {
            SpiderBackup.Layout layout = null;
            int error;
            try (InputStream in = resolver.openInputStream(source)) {
                if (in == null) throw new IOException("no stream");
                layout = SpiderBackup.parse(SpiderBackup.readCapped(in, SpiderBackup.MAX_BYTES));
                error = 0;
            } catch (SpiderBackup.Invalid refused) {
                Log.i(TAG, "refused backup: " + refused.getMessage());
                error = refused.reason;
            } catch (IOException | RuntimeException | OutOfMemoryError e) {
                Log.w(TAG, "backup read failed", e);
                error = UNREADABLE;
            }
            final SpiderBackup.Layout l = layout;
            final int err = error;
            main.post(() -> done.onDone(l, err));
        });
    }

    /** Checks backup JSON shared as plain text. */
    static void readText(CharSequence text, Read done) {
        final String body = text.toString();
        final Handler main = new Handler(Looper.getMainLooper());
        WORKER.execute(() -> {
            SpiderBackup.Layout layout = null;
            int error = 0;
            try {
                if (body.getBytes(StandardCharsets.UTF_8).length > SpiderBackup.MAX_BYTES) {
                    throw new SpiderBackup.Invalid(SpiderBackup.TOO_BIG, "shared text");
                }
                layout = SpiderBackup.parse(body);
            } catch (SpiderBackup.Invalid refused) {
                error = refused.reason;
            }
            final SpiderBackup.Layout l = layout;
            final int err = error;
            main.post(() -> done.onDone(l, err));
        });
    }

    /** "wt" truncates an existing document; some providers only know "w". */
    private static OutputStream openForWrite(ContentResolver resolver, Uri target) throws IOException {
        OutputStream out;
        try {
            out = resolver.openOutputStream(target, "wt");
        } catch (FileNotFoundException | IllegalArgumentException | UnsupportedOperationException noTruncate) {
            out = resolver.openOutputStream(target, "w");
        }
        if (out == null) throw new IOException("no stream");
        return out;
    }
}
