package com.spidertv.launcher;

import java.util.Locale;

/**
 * Plain rules behind the custom Home wallpaper, kept free of Android calls so they run in unit tests:
 * what kind of file was shared, how far to shrink a photo, and the saved choice's token.
 *
 * The token is what Settings stores: "" for the default backdrop, else "image:STAMP" or "video:STAMP",
 * where STAMP names the files of that import inside filesDir/wallpaper.
 */
final class SpiderWallpaperRules {

    static final int NONE = 0, IMAGE = 1, VIDEO = 2;

    /** Largest video copied into the app (bytes). */
    static final long MAX_VIDEO_BYTES = 200L * 1024 * 1024;
    /** Largest photo read (bytes); photos are re-encoded at screen size, so only the source is this big. */
    static final long MAX_IMAGE_BYTES = 64L * 1024 * 1024;

    private static final String IMAGE_PREFIX = "image:";
    private static final String VIDEO_PREFIX = "video:";

    private static final String[] IMAGE_EXT = {"jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif"};
    private static final String[] VIDEO_EXT = {"mp4", "m4v", "mkv", "webm", "3gp", "mov", "ts"};

    private SpiderWallpaperRules() {
    }

    /**
     * IMAGE, VIDEO or NONE from a MIME type, falling back to the file name's extension when the type is
     * missing or generic (such as application/octet-stream or a bare wildcard).
     */
    static int kindOf(String mime, String displayName) {
        if (mime != null) {
            final String m = mime.trim().toLowerCase(Locale.ROOT);
            if (m.startsWith("image/") && !m.equals("image/*")) return IMAGE;
            if (m.startsWith("video/") && !m.equals("video/*")) return VIDEO;
        }
        final int byName = kindOfName(displayName);
        if (byName != NONE) return byName;
        // A wildcard type with no usable name still says which kind the sender meant.
        if (mime != null) {
            final String m = mime.trim().toLowerCase(Locale.ROOT);
            if (m.startsWith("image/")) return IMAGE;
            if (m.startsWith("video/")) return VIDEO;
        }
        return NONE;
    }

    static int kindOfName(String name) {
        if (name == null) return NONE;
        final int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) return NONE;
        final String ext = name.substring(dot + 1).trim().toLowerCase(Locale.ROOT);
        for (String e : IMAGE_EXT) if (e.equals(ext)) return IMAGE;
        for (String e : VIDEO_EXT) if (e.equals(ext)) return VIDEO;
        return NONE;
    }

    /** True when a known size is over the cap for that kind; an unknown size (below 0) passes here. */
    static boolean tooBig(int kind, long bytes) {
        if (bytes < 0) return false;
        return bytes > (kind == VIDEO ? MAX_VIDEO_BYTES : MAX_IMAGE_BYTES);
    }

    /**
     * BitmapFactory inSampleSize: the largest power of two that still leaves the decoded image at least
     * as big as the screen on both sides (so it can fill it), and 1 for anything already small.
     */
    static int sampleSize(int width, int height, int screenW, int screenH) {
        if (width <= 0 || height <= 0 || screenW <= 0 || screenH <= 0) return 1;
        int sample = 1;
        while (width / (sample * 2) >= screenW && height / (sample * 2) >= screenH) {
            sample *= 2;
        }
        return sample;
    }

    /**
     * Factor (at most 1) that shrinks an image to the smallest size still covering the screen; the Home
     * view crops the rest. Never enlarges.
     */
    static float coverScale(int width, int height, int screenW, int screenH) {
        if (width <= 0 || height <= 0 || screenW <= 0 || screenH <= 0) return 1f;
        final float scale = Math.max(screenW / (float) width, screenH / (float) height);
        return Math.min(1f, scale);
    }

    /** Clockwise turn for an EXIF orientation value (1 normal, 3, 6, 8 rotated; mirrored ones by their turn). */
    static int rotationFor(int exifOrientation) {
        switch (exifOrientation) {
            case 3: // ROTATE_180
            case 4: // FLIP_VERTICAL
                return 180;
            case 5: // TRANSPOSE
            case 6: // ROTATE_90
                return 90;
            case 7: // TRANSVERSE
            case 8: // ROTATE_270
                return 270;
            default:
                return 0;
        }
    }

    static String token(int kind, long stamp) {
        if (stamp <= 0) return "";
        if (kind == IMAGE) return IMAGE_PREFIX + stamp;
        if (kind == VIDEO) return VIDEO_PREFIX + stamp;
        return "";
    }

    static int kindOfToken(String token) {
        if (stampOf(token) <= 0) return NONE;
        return token.startsWith(IMAGE_PREFIX) ? IMAGE : VIDEO;
    }

    /** The import stamp inside a token, or 0 when the token is empty or malformed. */
    static long stampOf(String token) {
        if (token == null) return 0;
        final String digits;
        if (token.startsWith(IMAGE_PREFIX)) {
            digits = token.substring(IMAGE_PREFIX.length());
        } else if (token.startsWith(VIDEO_PREFIX)) {
            digits = token.substring(VIDEO_PREFIX.length());
        } else {
            return 0;
        }
        if (digits.isEmpty() || digits.length() > 18) return 0;
        for (int i = 0; i < digits.length(); i++) {
            if (digits.charAt(i) < '0' || digits.charAt(i) > '9') return 0;
        }
        return Long.parseLong(digits);
    }

    /** File names of one import inside filesDir/wallpaper. */
    static String imageFile(long stamp) {
        return "image-" + stamp + ".jpg";
    }

    static String videoFile(long stamp) {
        return "video-" + stamp + ".bin";
    }

    static String posterFile(long stamp) {
        return "poster-" + stamp + ".jpg";
    }
}
