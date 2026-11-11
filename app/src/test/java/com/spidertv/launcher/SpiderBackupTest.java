package com.spidertv.launcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.json.JSONObject;
import org.junit.Test;

/** Layout backup file: writing, reading, refusing and merging. */
public class SpiderBackupTest {

    private static List<String> list(String... s) {
        return new ArrayList<>(Arrays.asList(s));
    }

    private static SpiderBackup.Layout sample() {
        final SpiderBackup.Layout l = new SpiderBackup.Layout();
        l.settings.put(SpiderUserSettings.SHOW_CLOCK, false);
        l.settings.put(SpiderUserSettings.REDUCED_MOTION, true);
        l.settings.put(SpiderUserSettings.CLOCK_FORMAT, SpiderUserSettings.CLOCK_24H);
        l.hidden = list("com.example.hidden");
        l.order = list("com.netflix.ninja", "org.videolan.vlc");
        l.wallpaper = SpiderBackup.WALLPAPER_PHOTO;
        return l;
    }

    private static int reasonOf(String text) {
        try {
            SpiderBackup.parse(text);
        } catch (SpiderBackup.Invalid e) {
            return e.reason;
        }
        fail("accepted: " + text);
        return 0;
    }

    @Test
    public void roundTripKeepsSettingsListsAndWallpaperKind() throws Exception {
        final String json = SpiderBackup.serialize(sample(), "1.5.0", 9, "2026-11-11T23:40:00+0530");
        final SpiderBackup.Layout back = SpiderBackup.parse(json);
        assertEquals(sample().settings, back.settings);
        assertEquals(list("com.example.hidden"), back.hidden);
        assertEquals(list("com.netflix.ninja", "org.videolan.vlc"), back.order);
        assertEquals(SpiderBackup.WALLPAPER_PHOTO, back.wallpaper);
        assertEquals("1.5.0", back.appVersion);
        assertEquals("2026-11-11T23:40:00+0530", back.created);
    }

    @Test
    public void serializeWritesTheEnvelopeAndNoMedia() throws Exception {
        final JSONObject root = new JSONObject(SpiderBackup.serialize(sample(), "1.5.0", 9, null));
        assertEquals("spider-backup", root.getString("format"));
        assertEquals(1, root.getInt("version"));
        assertEquals(9, root.getInt("app_version_code"));
        assertFalse(root.has("created"));
        assertFalse(root.getJSONObject("wallpaper").getBoolean("media_included"));
        assertEquals(2, root.getJSONObject("wallpaper").length());
    }

    @Test
    public void serializeDropsUnknownAndWrongTypedSettings() throws Exception {
        final SpiderBackup.Layout l = new SpiderBackup.Layout();
        l.settings.put("something_else", true);
        l.settings.put(SpiderUserSettings.SHOW_DATE, "yes");
        l.settings.put(SpiderUserSettings.CLOCK_FORMAT, 7);
        final JSONObject settings = new JSONObject(SpiderBackup.serialize(l, "x", 1, null)).getJSONObject("settings");
        assertEquals(0, settings.length());
    }

    @Test
    public void refusesGarbageWithAReason() {
        assertEquals(SpiderBackup.EMPTY, reasonOf(""));
        assertEquals(SpiderBackup.EMPTY, reasonOf("   \n"));
        assertEquals(SpiderBackup.NOT_JSON, reasonOf("hello there"));
        assertEquals(SpiderBackup.NOT_JSON, reasonOf("[1,2,3]"));
        assertEquals(SpiderBackup.NOT_JSON, reasonOf("{\"format\":"));
        assertEquals(SpiderBackup.NOT_SPIDER, reasonOf("{\"dock_apps\":[]}"));
        assertEquals(SpiderBackup.NOT_SPIDER, reasonOf("{\"format\":\"other\",\"version\":1}"));
        assertEquals(SpiderBackup.BAD_VERSION, reasonOf("{\"format\":\"spider-backup\"}"));
        assertEquals(SpiderBackup.BAD_VERSION, reasonOf("{\"format\":\"spider-backup\",\"version\":\"1\"}"));
        assertEquals(SpiderBackup.BAD_VERSION, reasonOf("{\"format\":\"spider-backup\",\"version\":0}"));
        assertEquals(SpiderBackup.BAD_VERSION, reasonOf("{\"format\":\"spider-backup\",\"version\":1.5}"));
        assertEquals(SpiderBackup.NEWER, reasonOf("{\"format\":\"spider-backup\",\"version\":2}"));
    }

    @Test
    public void readsAMinimalFileAsNothingToChange() throws Exception {
        final SpiderBackup.Layout l = SpiderBackup.parse("\uFEFF {\"format\":\"spider-backup\",\"version\":1}");
        assertEquals(0, l.settingCount());
        assertNull(l.hidden);
        assertNull(l.order);
        assertNull(l.wallpaper);
    }

    @Test
    public void skipsUnknownKeysWrongTypesAndBadPackages() throws Exception {
        final SpiderBackup.Layout l = SpiderBackup.parse("{\"format\":\"spider-backup\",\"version\":1,\"extra\":{},"
                + "\"settings\":{\"show_clock\":\"no\",\"show_date\":false,\"clock_format\":9,\"mystery\":true},"
                + "\"apps\":{\"hidden\":[\" a.b \",5,\"\",\"a.b\",\"bad,pkg\",\"x y\",\"c.d\"],\"order\":\"a.b\"},"
                + "\"wallpaper\":{\"type\":\"hologram\"}}");
        assertEquals(1, l.settingCount());
        assertEquals(Boolean.FALSE, l.settings.get(SpiderUserSettings.SHOW_DATE));
        assertEquals(list("a.b", "c.d"), l.hidden);
        assertNull(l.order);
        assertNull(l.wallpaper);
    }

    @Test
    public void mergeLetsTheBackupWinAndKeepsWhatItLeavesOut() throws Exception {
        final SpiderBackup.Layout current = new SpiderBackup.Layout();
        current.settings.put(SpiderUserSettings.SHOW_CLOCK, true);
        current.settings.put(SpiderUserSettings.ANIMATIONS, false);
        current.hidden = list("old.hidden");
        current.order = list("old.first", "old.second");
        current.wallpaper = SpiderBackup.WALLPAPER_VIDEO;

        final SpiderBackup.Layout backup = SpiderBackup.parse("{\"format\":\"spider-backup\",\"version\":1,"
                + "\"settings\":{\"show_clock\":false,\"clock_format\":1},"
                + "\"apps\":{\"hidden\":[\"not.installed.here\"]},\"wallpaper\":{\"type\":\"default\"}}");
        final SpiderBackup.Layout merged = SpiderBackup.merge(current, backup);

        assertEquals(Boolean.FALSE, merged.settings.get(SpiderUserSettings.SHOW_CLOCK));
        assertEquals(Boolean.FALSE, merged.settings.get(SpiderUserSettings.ANIMATIONS));
        assertEquals(1, merged.settings.get(SpiderUserSettings.CLOCK_FORMAT));
        assertEquals(list("not.installed.here"), merged.hidden);
        assertEquals(list("old.first", "old.second"), merged.order);
        assertEquals(SpiderBackup.WALLPAPER_VIDEO, merged.wallpaper);
        // inputs untouched
        assertEquals(list("old.hidden"), current.hidden);
    }

    @Test
    public void anEmptyListInTheBackupClearsTheCurrentOne() throws Exception {
        final SpiderBackup.Layout current = new SpiderBackup.Layout();
        current.hidden = list("a.b");
        final SpiderBackup.Layout backup = SpiderBackup.parse(
                "{\"format\":\"spider-backup\",\"version\":1,\"apps\":{\"hidden\":[],\"order\":[]}}");
        final SpiderBackup.Layout merged = SpiderBackup.merge(current, backup);
        assertTrue(merged.hidden.isEmpty());
        assertTrue(merged.order.isEmpty());
    }

    @Test
    public void readCappedRefusesOversizedInput() throws Exception {
        final byte[] small = "{\"a\":1}".getBytes(StandardCharsets.UTF_8);
        assertEquals("{\"a\":1}", SpiderBackup.readCapped(new ByteArrayInputStream(small), 64));
        try {
            SpiderBackup.readCapped(new ByteArrayInputStream(new byte[SpiderBackup.MAX_BYTES + 1]), SpiderBackup.MAX_BYTES);
            fail("read past the cap");
        } catch (SpiderBackup.Invalid e) {
            assertEquals(SpiderBackup.TOO_BIG, e.reason);
        }
    }

    @Test
    public void fileNameAndWallpaperKind() {
        assertEquals("spider-backup-20261105.json", SpiderBackup.fileName(2026, 11, 5));
        assertEquals(SpiderBackup.WALLPAPER_DEFAULT, SpiderBackup.wallpaperKind(""));
        assertEquals(SpiderBackup.WALLPAPER_DEFAULT, SpiderBackup.wallpaperKind(null));
        assertEquals(SpiderBackup.WALLPAPER_PHOTO, SpiderBackup.wallpaperKind(
                SpiderWallpaperRules.token(SpiderWallpaperRules.IMAGE, 1234L)));
        assertEquals(SpiderBackup.WALLPAPER_VIDEO, SpiderBackup.wallpaperKind(
                SpiderWallpaperRules.token(SpiderWallpaperRules.VIDEO, 1234L)));
    }

    @Test
    public void packageListsAreCapped() throws Exception {
        final StringBuilder b = new StringBuilder("{\"format\":\"spider-backup\",\"version\":1,\"apps\":{\"order\":[");
        for (int i = 0; i < SpiderBackup.MAX_PACKAGES + 50; i++) b.append(i == 0 ? "" : ",").append("\"p.n").append(i).append('"');
        b.append("]}}");
        assertEquals(SpiderBackup.MAX_PACKAGES, SpiderBackup.parse(b.toString()).order.size());
    }
}
