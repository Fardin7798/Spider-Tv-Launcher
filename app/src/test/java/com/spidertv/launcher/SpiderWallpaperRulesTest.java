package com.spidertv.launcher;

import static com.spidertv.launcher.SpiderWallpaperRules.IMAGE;
import static com.spidertv.launcher.SpiderWallpaperRules.NONE;
import static com.spidertv.launcher.SpiderWallpaperRules.VIDEO;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** File-type, sizing and token rules behind the custom Home wallpaper. */
public class SpiderWallpaperRulesTest {

    @Test
    public void kindFromMimeType() {
        assertEquals(IMAGE, SpiderWallpaperRules.kindOf("image/jpeg", null));
        assertEquals(IMAGE, SpiderWallpaperRules.kindOf(" IMAGE/PNG ", "x.bin"));
        assertEquals(VIDEO, SpiderWallpaperRules.kindOf("video/mp4", null));
        assertEquals(VIDEO, SpiderWallpaperRules.kindOf("video/x-matroska", "clip.jpg"));
        assertEquals(NONE, SpiderWallpaperRules.kindOf("audio/mpeg", "song.mp3"));
        assertEquals(NONE, SpiderWallpaperRules.kindOf("text/plain", null));
    }

    @Test
    public void kindFallsBackToFileName() {
        assertEquals(IMAGE, SpiderWallpaperRules.kindOf(null, "Photo.JPEG"));
        assertEquals(VIDEO, SpiderWallpaperRules.kindOf("application/octet-stream", "loop.webm"));
        assertEquals(VIDEO, SpiderWallpaperRules.kindOf("image/*", "loop.mp4"));
        assertEquals(NONE, SpiderWallpaperRules.kindOf(null, "notes"));
        assertEquals(NONE, SpiderWallpaperRules.kindOf(null, "trailing."));
        assertEquals(NONE, SpiderWallpaperRules.kindOf(null, null));
    }

    @Test
    public void wildcardTypeWithoutNameStillCounts() {
        assertEquals(IMAGE, SpiderWallpaperRules.kindOf("image/*", null));
        assertEquals(VIDEO, SpiderWallpaperRules.kindOf("video/*", "12345"));
    }

    @Test
    public void sizeCaps() {
        assertFalse(SpiderWallpaperRules.tooBig(VIDEO, -1));
        assertFalse(SpiderWallpaperRules.tooBig(VIDEO, SpiderWallpaperRules.MAX_VIDEO_BYTES));
        assertTrue(SpiderWallpaperRules.tooBig(VIDEO, SpiderWallpaperRules.MAX_VIDEO_BYTES + 1));
        assertTrue(SpiderWallpaperRules.tooBig(IMAGE, SpiderWallpaperRules.MAX_IMAGE_BYTES + 1));
        assertFalse(SpiderWallpaperRules.tooBig(IMAGE, 5_000_000));
    }

    @Test
    public void sampleSizeKeepsImageCoveringScreen() {
        assertEquals(1, SpiderWallpaperRules.sampleSize(1920, 1080, 1920, 1080));
        assertEquals(1, SpiderWallpaperRules.sampleSize(1280, 720, 1920, 1080));
        assertEquals(2, SpiderWallpaperRules.sampleSize(3840, 2160, 1920, 1080));
        assertEquals(2, SpiderWallpaperRules.sampleSize(4000, 3000, 1920, 1080));
        assertEquals(4, SpiderWallpaperRules.sampleSize(8000, 6000, 1920, 1080));
        // A tall photo is limited by its width.
        assertEquals(1, SpiderWallpaperRules.sampleSize(3000, 4000, 1920, 1080));
        assertEquals(1, SpiderWallpaperRules.sampleSize(0, 100, 1920, 1080));
        assertEquals(1, SpiderWallpaperRules.sampleSize(100, 100, 0, 0));
    }

    @Test
    public void coverScaleNeverEnlarges() {
        assertEquals(0.5f, SpiderWallpaperRules.coverScale(3840, 2160, 1920, 1080), 1e-6f);
        assertEquals(1f, SpiderWallpaperRules.coverScale(1280, 720, 1920, 1080), 1e-6f);
        // 4:3 is limited by width: 2000 -> 1920 keeps the height above 1080.
        assertEquals(0.96f, SpiderWallpaperRules.coverScale(2000, 1500, 1920, 1080), 1e-6f);
        assertEquals(1f, SpiderWallpaperRules.coverScale(-1, 5, 1920, 1080), 1e-6f);
    }

    @Test
    public void exifRotation() {
        assertEquals(0, SpiderWallpaperRules.rotationFor(1));
        assertEquals(0, SpiderWallpaperRules.rotationFor(0));
        assertEquals(180, SpiderWallpaperRules.rotationFor(3));
        assertEquals(90, SpiderWallpaperRules.rotationFor(6));
        assertEquals(270, SpiderWallpaperRules.rotationFor(8));
    }

    @Test
    public void tokensRoundTrip() {
        assertEquals("image:42", SpiderWallpaperRules.token(IMAGE, 42));
        assertEquals("video:1700000000000", SpiderWallpaperRules.token(VIDEO, 1700000000000L));
        assertEquals("", SpiderWallpaperRules.token(NONE, 42));
        assertEquals("", SpiderWallpaperRules.token(IMAGE, 0));
        assertEquals(IMAGE, SpiderWallpaperRules.kindOfToken("image:42"));
        assertEquals(VIDEO, SpiderWallpaperRules.kindOfToken("video:7"));
        assertEquals(1700000000000L, SpiderWallpaperRules.stampOf("video:1700000000000"));
    }

    @Test
    public void badTokensMeanDefault() {
        assertEquals(NONE, SpiderWallpaperRules.kindOfToken(""));
        assertEquals(NONE, SpiderWallpaperRules.kindOfToken(null));
        assertEquals(NONE, SpiderWallpaperRules.kindOfToken("image:"));
        assertEquals(NONE, SpiderWallpaperRules.kindOfToken("image:../x"));
        assertEquals(NONE, SpiderWallpaperRules.kindOfToken("gif:12"));
        assertEquals(NONE, SpiderWallpaperRules.kindOfToken("video:1234567890123456789"));
        assertEquals(0, SpiderWallpaperRules.stampOf("image:-5"));
    }

    @Test
    public void fileNames() {
        assertEquals("image-9.jpg", SpiderWallpaperRules.imageFile(9));
        assertEquals("video-9.bin", SpiderWallpaperRules.videoFile(9));
        assertEquals("poster-9.jpg", SpiderWallpaperRules.posterFile(9));
    }
}
