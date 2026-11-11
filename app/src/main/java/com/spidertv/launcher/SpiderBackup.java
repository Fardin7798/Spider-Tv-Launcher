package com.spidertv.launcher;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * The layout backup file: Settings screen choices plus the Manage apps hidden list and order, as a small
 * versioned JSON document. Only the wallpaper's kind is written, never the photo or video itself, so the
 * file stays a few KB. Pure Java (org.json only) so it runs in plain unit tests.
 *
 * <pre>
 * {"format":"spider-backup","version":1,"app_version":"1.5.0","app_version_code":9,
 *  "created":"2026-11-11T23:40:00+0530",
 *  "settings":{"show_clock":true,"clock_format":2},
 *  "apps":{"hidden":["com.example.tv"],"order":["com.netflix.ninja"]},
 *  "wallpaper":{"type":"photo","media_included":false}}
 * </pre>
 *
 * Reading is forgiving inside a valid file (unknown keys and wrong-typed values are skipped, a missing
 * key keeps the current value) and strict about the envelope (format and version must match).
 */
final class SpiderBackup {

    static final String FORMAT = "spider-backup";
    static final int VERSION = 1;
    /** Larger inputs are refused before parsing. */
    static final int MAX_BYTES = 1024 * 1024;
    /** Longest package list kept from a file. */
    static final int MAX_PACKAGES = 2000;

    /** Wallpaper kinds as written to the file. */
    static final String WALLPAPER_DEFAULT = "default", WALLPAPER_PHOTO = "photo", WALLPAPER_VIDEO = "video";

    /** Why a file was refused. */
    static final int EMPTY = 1, TOO_BIG = 2, NOT_JSON = 3, NOT_SPIDER = 4, NEWER = 5, BAD_VERSION = 6;

    /** Settings keys a backup carries, all on/off switches except {@link SpiderUserSettings#CLOCK_FORMAT}. */
    static final List<String> FLAG_KEYS = Collections.unmodifiableList(java.util.Arrays.asList(
            SpiderUserSettings.SHOW_CLOCK, SpiderUserSettings.SHOW_DATE, SpiderUserSettings.SHOW_GREETING,
            SpiderUserSettings.PHONE_APPS, SpiderUserSettings.ANIMATIONS, SpiderUserSettings.REDUCED_MOTION));

    /** Plausible Android package name: letters, digits, underscores and dots. */
    private static final Pattern PACKAGE = Pattern.compile("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)*");

    static final class Invalid extends Exception {
        final int reason;

        Invalid(int reason, String detail) {
            super(detail);
            this.reason = reason;
        }
    }

    /** What a backup holds. A null list, or a key missing from {@code settings}, means "not in the file". */
    static final class Layout {
        /** Boolean values for {@link #FLAG_KEYS}, an Integer for clock_format. */
        final Map<String, Object> settings = new LinkedHashMap<>();
        List<String> hidden;
        List<String> order;
        /** One of the WALLPAPER_ kinds, or null when the file did not say. */
        String wallpaper;
        // Only filled when read from a file.
        String appVersion;
        String created;

        int settingCount() {
            return settings.size();
        }
    }

    private SpiderBackup() {
    }

    /** "spider-backup-YYYYMMDD.json". */
    static String fileName(int year, int month, int day) {
        return String.format(java.util.Locale.ROOT, "spider-backup-%04d%02d%02d.json", year, month, day);
    }

    /** The wallpaper kind for a stored wallpaper token. */
    static String wallpaperKind(String token) {
        switch (SpiderWallpaperRules.kindOfToken(token)) {
            case SpiderWallpaperRules.IMAGE:
                return WALLPAPER_PHOTO;
            case SpiderWallpaperRules.VIDEO:
                return WALLPAPER_VIDEO;
            default:
                return WALLPAPER_DEFAULT;
        }
    }

    // ---- writing --------------------------------------------------------------------------------

    static String serialize(Layout layout, String appVersion, int appVersionCode, String created) {
        try {
            final JSONObject root = new JSONObject();
            root.put("format", FORMAT);
            root.put("version", VERSION);
            root.put("app_version", appVersion);
            root.put("app_version_code", appVersionCode);
            if (created != null) root.put("created", created);
            final JSONObject settings = new JSONObject();
            for (Map.Entry<String, Object> e : clean(layout.settings).entrySet()) settings.put(e.getKey(), e.getValue());
            root.put("settings", settings);
            final JSONObject apps = new JSONObject();
            apps.put("hidden", new JSONArray(layout.hidden == null ? Collections.<String>emptyList() : layout.hidden));
            apps.put("order", new JSONArray(layout.order == null ? Collections.<String>emptyList() : layout.order));
            root.put("apps", apps);
            final JSONObject wallpaper = new JSONObject();
            wallpaper.put("type", layout.wallpaper == null ? WALLPAPER_DEFAULT : layout.wallpaper);
            wallpaper.put("media_included", false);
            root.put("wallpaper", wallpaper);
            return root.toString(2);
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    // ---- reading --------------------------------------------------------------------------------

    /** Reads at most {@code max} bytes; throws TOO_BIG rather than truncating a longer stream. */
    static String readCapped(InputStream in, int max) throws IOException, Invalid {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final byte[] buf = new byte[16 * 1024];
        int n;
        while ((n = in.read(buf)) > 0) {
            if (out.size() + n > max) throw new Invalid(TOO_BIG, "over " + max + " bytes");
            out.write(buf, 0, n);
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    static Layout parse(String text) throws Invalid {
        if (text == null || text.trim().isEmpty()) throw new Invalid(EMPTY, "empty");
        if (text.length() > MAX_BYTES) throw new Invalid(TOO_BIG, "too long");
        String body = text.trim();
        if (body.charAt(0) == '\uFEFF') body = body.substring(1).trim();
        final JSONObject root;
        try {
            final Object value = new org.json.JSONTokener(body).nextValue();
            if (!(value instanceof JSONObject)) throw new Invalid(NOT_JSON, "not a JSON object");
            root = (JSONObject) value;
        } catch (JSONException e) {
            throw new Invalid(NOT_JSON, e.getMessage());
        }
        if (!FORMAT.equals(root.opt("format"))) throw new Invalid(NOT_SPIDER, "format");
        final Object version = root.opt("version");
        if (!(version instanceof Integer) && !(version instanceof Long)) throw new Invalid(BAD_VERSION, "version");
        final long v = ((Number) version).longValue();
        if (v > VERSION) throw new Invalid(NEWER, "version " + v);
        if (v < 1) throw new Invalid(BAD_VERSION, "version " + v);

        final Layout out = new Layout();
        final Object appVersion = root.opt("app_version");
        if (appVersion instanceof String) out.appVersion = (String) appVersion;
        final Object created = root.opt("created");
        if (created instanceof String) out.created = (String) created;

        final JSONObject settings = root.optJSONObject("settings");
        if (settings != null) {
            for (String key : FLAG_KEYS) {
                final Object raw = settings.opt(key);
                if (raw instanceof Boolean) out.settings.put(key, raw);
            }
            final Object clock = settings.opt(SpiderUserSettings.CLOCK_FORMAT);
            if (clock instanceof Integer && validClock((Integer) clock)) {
                out.settings.put(SpiderUserSettings.CLOCK_FORMAT, clock);
            }
        }
        final JSONObject apps = root.optJSONObject("apps");
        if (apps != null) {
            out.hidden = packages(apps.opt("hidden"));
            out.order = packages(apps.opt("order"));
        }
        final JSONObject wallpaper = root.optJSONObject("wallpaper");
        if (wallpaper != null) {
            final Object type = wallpaper.opt("type");
            if (WALLPAPER_DEFAULT.equals(type) || WALLPAPER_PHOTO.equals(type) || WALLPAPER_VIDEO.equals(type)) {
                out.wallpaper = (String) type;
            }
        }
        return out;
    }

    // ---- restoring ------------------------------------------------------------------------------

    /**
     * The layout to save after restoring {@code backup} over {@code current}: whatever the backup holds
     * wins, anything it leaves out keeps its current value. The wallpaper is never changed by a restore.
     */
    static Layout merge(Layout current, Layout backup) {
        final Layout out = new Layout();
        out.settings.putAll(clean(current.settings));
        out.settings.putAll(clean(backup.settings));
        out.hidden = backup.hidden != null ? new ArrayList<>(backup.hidden) : copy(current.hidden);
        out.order = backup.order != null ? new ArrayList<>(backup.order) : copy(current.order);
        out.wallpaper = current.wallpaper;
        return out;
    }

    // ---- helpers --------------------------------------------------------------------------------

    private static boolean validClock(int v) {
        return v >= SpiderUserSettings.CLOCK_SYSTEM && v <= SpiderUserSettings.CLOCK_24H;
    }

    /** Only known keys with the right value type. */
    private static Map<String, Object> clean(Map<String, Object> in) {
        final Map<String, Object> out = new LinkedHashMap<>();
        if (in == null) return out;
        for (String key : FLAG_KEYS) {
            if (in.get(key) instanceof Boolean) out.put(key, in.get(key));
        }
        final Object clock = in.get(SpiderUserSettings.CLOCK_FORMAT);
        if (clock instanceof Integer && validClock((Integer) clock)) out.put(SpiderUserSettings.CLOCK_FORMAT, clock);
        return out;
    }

    /** Trimmed package names; non-strings, empties, repeats and anything not shaped like a package dropped. */
    private static List<String> packages(Object raw) {
        if (!(raw instanceof JSONArray)) return null;
        final JSONArray list = (JSONArray) raw;
        final LinkedHashSet<String> out = new LinkedHashSet<>();
        for (int i = 0; i < list.length() && out.size() < MAX_PACKAGES; i++) {
            final Object entry = list.opt(i);
            if (!(entry instanceof String)) continue;
            final String pkg = ((String) entry).trim();
            if (pkg.length() <= 255 && PACKAGE.matcher(pkg).matches()) out.add(pkg);
        }
        return new ArrayList<>(out);
    }

    private static List<String> copy(List<String> in) {
        return in == null ? new ArrayList<String>() : new ArrayList<>(in);
    }
}
