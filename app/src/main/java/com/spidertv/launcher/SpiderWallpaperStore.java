package com.spidertv.launcher;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.util.Log;

/**
 * The custom Home wallpaper on disk. An import copies a shared or picked photo or video into
 * filesDir/wallpaper (a content URI's read grant ends with the activity that got it), then saves its
 * token in {@link SpiderUserSettings}, which bumps the revision Home checks on resume. Photos are
 * re-encoded at screen size; videos are copied as they are (up to 200 MB) with a first-frame poster.
 * Nothing here needs a storage permission: everything is read through the ContentResolver.
 */
final class SpiderWallpaperStore {

    private static final String TAG = "SpiderTv.Wallpaper";
    private static final String DIR = "wallpaper";
    private static final int JPEG_QUALITY = 90;
    /** Longest side a stored photo or poster keeps, even on a panel that reports more. */
    private static final int MAX_EDGE = 3840;

    /** Import outcomes passed to {@link Done}. */
    static final int OK = 0, FAILED = 1, UNSUPPORTED = 2, TOO_BIG = 3, CANCELLED = 4;

    interface Done {
        /** On the main thread. {@code kind} is IMAGE or VIDEO when {@code result} is OK. */
        void onDone(int result, int kind);
    }

    /** One worker, so two imports never write at once. */
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();

    private SpiderWallpaperStore() {
    }

    static File dir(Context context) {
        return new File(context.getFilesDir(), DIR);
    }

    /** The saved token: "" for the default backdrop. */
    static String current(Context context) {
        return new SpiderUserSettings(context).text(SpiderUserSettings.WALLPAPER);
    }

    /** Back to the default backdrop: forgets the token and deletes the stored files. */
    static void reset(Context context) {
        final Context app = context.getApplicationContext();
        new SpiderUserSettings(app).putText(SpiderUserSettings.WALLPAPER, "");
        WORKER.execute(() -> clean(app, 0));
    }

    /**
     * Copies {@code source} in the background. {@code typeHint} is the sender's MIME type (may be null).
     * Setting {@code cancel} stops a copy that is still running.
     */
    static void importFrom(Context context, Uri source, String typeHint, AtomicBoolean cancel, Done done) {
        final Context app = context.getApplicationContext();
        final Handler main = new Handler(Looper.getMainLooper());
        WORKER.execute(() -> {
            int kind = SpiderWallpaperRules.NONE;
            int result;
            try {
                final ContentResolver resolver = app.getContentResolver();
                final String[] nameSize = nameAndSize(resolver, source);
                String type = null;
                try {
                    type = resolver.getType(source);
                } catch (RuntimeException ignored) {
                    // some providers throw instead of returning null
                }
                kind = SpiderWallpaperRules.kindOf(type != null ? type : typeHint, nameSize[0]);
                if (kind == SpiderWallpaperRules.NONE) kind = SpiderWallpaperRules.kindOf(typeHint, nameSize[0]);
                final long size = nameSize[1] == null ? -1 : parseLong(nameSize[1]);
                if (kind == SpiderWallpaperRules.NONE) {
                    result = UNSUPPORTED;
                } else if (SpiderWallpaperRules.tooBig(kind, size)) {
                    result = TOO_BIG;
                } else {
                    final long stamp = System.currentTimeMillis();
                    final File folder = dir(app);
                    if (!folder.isDirectory() && !folder.mkdirs()) throw new IOException("no folder");
                    result = kind == SpiderWallpaperRules.IMAGE
                            ? saveImage(app, source, stamp, cancel)
                            : saveVideo(app, source, stamp, cancel);
                    if (result == OK) {
                        new SpiderUserSettings(app).putText(SpiderUserSettings.WALLPAPER,
                                SpiderWallpaperRules.token(kind, stamp));
                        clean(app, stamp);
                    } else {
                        deleteStamp(folder, stamp);
                    }
                }
            } catch (IOException | RuntimeException | OutOfMemoryError e) {
                Log.w(TAG, "import failed", e);
                result = FAILED;
            }
            final int r = result, k = kind;
            main.post(() -> done.onDone(r, k));
        });
    }

    // ---- photos ---------------------------------------------------------------------------------

    private static int saveImage(Context app, Uri source, long stamp, AtomicBoolean cancel) throws IOException {
        final ContentResolver resolver = app.getContentResolver();
        final BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = open(resolver, source)) {
            BitmapFactory.decodeStream(in, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return UNSUPPORTED;
        if (cancel.get()) return CANCELLED;

        int rotation = 0;
        try (InputStream in = open(resolver, source)) {
            rotation = SpiderWallpaperRules.rotationFor(new ExifInterface(in).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL));
        } catch (IOException | RuntimeException noExif) {
            // PNG, WebP without EXIF and the like: keep it as drawn
        }
        final int[] screen = screen(app);
        final boolean sideways = rotation == 90 || rotation == 270;
        final BitmapFactory.Options opts = new BitmapFactory.Options();
        // Sample in the file's own orientation, against the screen turned the same way.
        opts.inSampleSize = SpiderWallpaperRules.sampleSize(bounds.outWidth, bounds.outHeight,
                sideways ? screen[1] : screen[0], sideways ? screen[0] : screen[1]);
        Bitmap decoded;
        try (InputStream in = open(resolver, source)) {
            decoded = BitmapFactory.decodeStream(in, null, opts);
        }
        if (decoded == null) return UNSUPPORTED;
        if (cancel.get()) {
            decoded.recycle();
            return CANCELLED;
        }
        final float scale = SpiderWallpaperRules.coverScale(
                sideways ? decoded.getHeight() : decoded.getWidth(),
                sideways ? decoded.getWidth() : decoded.getHeight(), screen[0], screen[1]);
        final Bitmap fitted = transform(decoded, rotation, scale);
        try {
            writeJpeg(fitted, new File(dir(app), SpiderWallpaperRules.imageFile(stamp)));
        } finally {
            fitted.recycle();
        }
        return OK;
    }

    /** Rotates and shrinks in one pass; recycles {@code src} when a new bitmap was made. */
    private static Bitmap transform(Bitmap src, int rotation, float scale) {
        if (rotation == 0 && scale >= 0.999f) return src;
        final Matrix m = new Matrix();
        if (scale < 0.999f) m.postScale(scale, scale);
        if (rotation != 0) m.postRotate(rotation);
        final Bitmap out = Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), m, true);
        if (out != src) src.recycle();
        return out;
    }

    // ---- videos ---------------------------------------------------------------------------------

    private static int saveVideo(Context app, Uri source, long stamp, AtomicBoolean cancel) throws IOException {
        final File folder = dir(app);
        final File part = new File(folder, SpiderWallpaperRules.videoFile(stamp) + ".part");
        final File video = new File(folder, SpiderWallpaperRules.videoFile(stamp));
        long copied = 0;
        try (InputStream in = open(app.getContentResolver(), source);
             OutputStream out = new FileOutputStream(part)) {
            final byte[] buf = new byte[256 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                if (cancel.get()) return CANCELLED;
                copied += n;
                if (copied > SpiderWallpaperRules.MAX_VIDEO_BYTES) return TOO_BIG;
                out.write(buf, 0, n);
            }
        }
        if (copied == 0 || !part.renameTo(video)) return FAILED;

        // Must hold a playable video track; its first frame is the poster (reduced motion, loading).
        final MediaMetadataRetriever probe = new MediaMetadataRetriever();
        try {
            probe.setDataSource(video.getAbsolutePath());
            if (!"yes".equals(probe.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO))) {
                return UNSUPPORTED;
            }
            final Bitmap frame = probe.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            if (frame != null) {
                final int[] screen = screen(app);
                final Bitmap fitted = transform(frame, 0, SpiderWallpaperRules.coverScale(
                        frame.getWidth(), frame.getHeight(), screen[0], screen[1]));
                try {
                    writeJpeg(fitted, new File(folder, SpiderWallpaperRules.posterFile(stamp)));
                } finally {
                    fitted.recycle();
                }
            }
        } catch (RuntimeException unreadable) {
            Log.w(TAG, "video probe failed", unreadable);
            return UNSUPPORTED;
        } finally {
            try {
                probe.release();
            } catch (IOException | RuntimeException ignored) {
                // nothing left to free
            }
        }
        return OK;
    }

    // ---- reading for Home -----------------------------------------------------------------------

    /** The stored photo (or a video's poster) for this token, sampled to the screen; null if none. */
    static Bitmap loadStill(Context context, String token) {
        final long stamp = SpiderWallpaperRules.stampOf(token);
        if (stamp <= 0) return null;
        final File file = new File(dir(context), SpiderWallpaperRules.kindOfToken(token) == SpiderWallpaperRules.IMAGE
                ? SpiderWallpaperRules.imageFile(stamp) : SpiderWallpaperRules.posterFile(stamp));
        if (!file.isFile()) return null;
        final BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        final int[] screen = screen(context);
        final BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = SpiderWallpaperRules.sampleSize(bounds.outWidth, bounds.outHeight, screen[0], screen[1]);
        try {
            return BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
        } catch (OutOfMemoryError oom) {
            return null;
        }
    }

    /** The stored video for a video token, or null. */
    static File videoFor(Context context, String token) {
        if (SpiderWallpaperRules.kindOfToken(token) != SpiderWallpaperRules.VIDEO) return null;
        final File f = new File(dir(context), SpiderWallpaperRules.videoFile(SpiderWallpaperRules.stampOf(token)));
        return f.isFile() ? f : null;
    }

    // ---- helpers --------------------------------------------------------------------------------

    private static InputStream open(ContentResolver resolver, Uri source) throws IOException {
        final InputStream in = resolver.openInputStream(source);
        if (in == null) throw new IOException("no stream");
        return in;
    }

    /** {display name, size as text}; either may be null. */
    private static String[] nameAndSize(ContentResolver resolver, Uri source) {
        final String[] out = {source.getLastPathSegment(), null};
        try (Cursor c = resolver.query(source, new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE},
                null, null, null)) {
            if (c != null && c.moveToFirst()) {
                final int name = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                final int size = c.getColumnIndex(OpenableColumns.SIZE);
                if (name >= 0 && !c.isNull(name)) out[0] = c.getString(name);
                if (size >= 0 && !c.isNull(size)) out[1] = c.getString(size);
            }
        } catch (RuntimeException ignored) {
            // file:// URIs and some providers have no columns
        }
        return out;
    }

    private static long parseLong(String s) {
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** Landscape screen size in pixels, capped at 4K. */
    private static int[] screen(Context context) {
        final android.util.DisplayMetrics dm = context.getResources().getDisplayMetrics();
        int w = Math.max(dm.widthPixels, dm.heightPixels);
        int h = Math.min(dm.widthPixels, dm.heightPixels);
        if (w <= 0 || h <= 0) {
            w = 1920;
            h = 1080;
        }
        if (w > MAX_EDGE) {
            h = h * MAX_EDGE / w;
            w = MAX_EDGE;
        }
        return new int[]{w, h};
    }

    private static void writeJpeg(Bitmap bitmap, File target) throws IOException {
        final File part = new File(target.getPath() + ".part");
        try (OutputStream out = new FileOutputStream(part)) {
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)) throw new IOException("encode");
        }
        if (!part.renameTo(target)) throw new IOException("rename");
    }

    /** Deletes every stored file except those of {@code keepStamp} (0 keeps none). */
    private static void clean(Context app, long keepStamp) {
        final File[] files = dir(app).listFiles();
        if (files == null) return;
        final String keep = keepStamp > 0 ? "-" + keepStamp + "." : null;
        for (File f : files) {
            if (keep == null || !f.getName().contains(keep) || f.getName().endsWith(".part")) {
                if (!f.delete()) Log.w(TAG, "could not delete " + f.getName());
            }
        }
    }

    private static void deleteStamp(File folder, long stamp) {
        final File[] files = folder.listFiles();
        if (files == null) return;
        final String mark = "-" + stamp + ".";
        for (File f : files) {
            if (f.getName().contains(mark) && !f.delete()) Log.w(TAG, "could not delete " + f.getName());
        }
    }
}
