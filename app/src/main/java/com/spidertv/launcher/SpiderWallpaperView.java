package com.spidertv.launcher;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

/**
 * The custom Home wallpaper, drawn as the first child of the Home root so everything else sits on top.
 * With no custom wallpaper it hides itself and the default night backdrop shows through.
 *
 * A photo is decoded off the main thread and cropped to fill. A video plays muted and looping on a
 * TextureView while Home is in front; its first-frame poster shows while it loads, when it cannot play,
 * and instead of playback when motion is off or reduced. The player is released whenever Home stops,
 * so the TV's video decoder is free for the app the user opens.
 */
final class SpiderWallpaperView extends FrameLayout implements TextureView.SurfaceTextureListener {

    private static final String TAG = "SpiderTv.Wallpaper";

    private static final ExecutorService DECODER = Executors.newSingleThreadExecutor();

    private final ImageView still;
    private final TextureView video;
    private final Handler ui = new Handler(Looper.getMainLooper());

    private String token = "";
    private boolean playVideo;
    private boolean resumed;
    private File videoFile;
    private Surface surface;
    private MediaPlayer player;
    private int resumeAt;
    private int videoW, videoH;
    /** Bumped per apply(), so a slow decode for an older choice is dropped. */
    private int generation;

    SpiderWallpaperView(Context context) {
        super(context);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        setFocusable(false);
        // No background of its own: until the photo or poster is decoded the default backdrop shows through.

        still = new ImageView(context);
        still.setScaleType(ImageView.ScaleType.CENTER_CROP);
        addView(still, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        video = new TextureView(context);
        video.setSurfaceTextureListener(this);
        video.setAlpha(0f);
        addView(video, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Light scrim, darker at the top (clock, greeting) and bottom (app names), so text stays readable.
        final View scrim = new View(context);
        scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x8C000000, 0x40000000, 0x40000000, 0x99000000}));
        addView(scrim, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        video.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> fitVideo());
        setVisibility(GONE);
    }

    /**
     * Shows the wallpaper for {@code newToken} ("" for the default backdrop). {@code motion} false (motion
     * off or reduced) shows a video's poster instead of playing it. A repeat call with the same values
     * does nothing.
     */
    void apply(String newToken, boolean motion) {
        final String t = newToken == null ? "" : newToken;
        final int kind = SpiderWallpaperRules.kindOfToken(t);
        final boolean play = motion && kind == SpiderWallpaperRules.VIDEO;
        if (t.equals(token) && play == playVideo) return;
        token = t;
        playVideo = play;
        generation++;
        releasePlayer();
        resumeAt = 0;
        video.setAlpha(0f);
        still.setImageDrawable(null);
        if (kind == SpiderWallpaperRules.NONE) {
            videoFile = null;
            setVisibility(GONE);
            return;
        }
        setVisibility(VISIBLE);
        final Context app = getContext().getApplicationContext();
        final int mine = generation;
        DECODER.execute(() -> {
            final Bitmap bitmap = SpiderWallpaperStore.loadStill(app, t);
            final File clip = play ? SpiderWallpaperStore.videoFor(app, t) : null;
            ui.post(() -> {
                if (mine != generation) {
                    if (bitmap != null) bitmap.recycle();
                    return;
                }
                if (bitmap == null && clip == null) {
                    // Files gone (cleared app data, failed import): fall back to the default backdrop.
                    setVisibility(GONE);
                    return;
                }
                if (bitmap != null) still.setImageBitmap(bitmap);
                videoFile = clip;
                startIfReady();
            });
        });
    }

    /** Home is in front: play the video, if any. */
    void resume() {
        resumed = true;
        startIfReady();
    }

    /** Home lost the front: pause where it is. */
    void pause() {
        resumed = false;
        if (player != null) {
            try {
                if (player.isPlaying()) player.pause();
                resumeAt = player.getCurrentPosition();
            } catch (IllegalStateException ignored) {
                // not prepared yet
            }
        }
    }

    /** Home stopped: release the player (and its decoder); the poster stays until it plays again. */
    void stop() {
        pause();
        releasePlayer();
    }

    /** Home destroyed. */
    void release() {
        generation++;
        resumed = false;
        releasePlayer();
        if (surface != null) {
            surface.release();
            surface = null;
        }
    }

    private void startIfReady() {
        if (!resumed || !playVideo || videoFile == null || surface == null) return;
        if (player != null) {
            try {
                player.start();
            } catch (IllegalStateException notYet) {
                // still preparing: onPrepared starts it
            }
            return;
        }
        final MediaPlayer mp = new MediaPlayer();
        player = mp;
        try {
            mp.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build());
            mp.setVolume(0f, 0f);
            mp.setLooping(true);
            mp.setSurface(surface);
            mp.setDataSource(videoFile.getAbsolutePath());
            mp.setOnPreparedListener(p -> {
                if (p != player) return;
                p.setVolume(0f, 0f);
                videoW = p.getVideoWidth();
                videoH = p.getVideoHeight();
                fitVideo();
                if (resumeAt > 0) p.seekTo(resumeAt);
                if (resumed) p.start();
            });
            mp.setOnInfoListener((p, what, extra) -> {
                if (p == player && what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) video.setAlpha(1f);
                return false;
            });
            mp.setOnVideoSizeChangedListener((p, w, h) -> {
                videoW = w;
                videoH = h;
                fitVideo();
            });
            mp.setOnErrorListener((p, what, extra) -> {
                Log.w(TAG, "video error " + what + "/" + extra);
                // Unplayable here: keep the poster and stop trying until the choice changes.
                playVideo = false;
                releasePlayer();
                return true;
            });
            mp.prepareAsync();
        } catch (IOException | RuntimeException e) {
            Log.w(TAG, "video start failed", e);
            playVideo = false;
            releasePlayer();
        }
    }

    private void releasePlayer() {
        if (player == null) return;
        final MediaPlayer mp = player;
        player = null;
        try {
            mp.reset();
        } catch (RuntimeException ignored) {
            // already gone
        }
        mp.release();
        video.setAlpha(0f);
    }

    /** Center-crop the video on the TextureView (it stretches to fill by default). */
    private void fitVideo() {
        final int vw = video.getWidth(), vh = video.getHeight();
        if (vw == 0 || vh == 0 || videoW <= 0 || videoH <= 0) return;
        final float scale = Math.max(vw / (float) videoW, vh / (float) videoH);
        final Matrix m = new Matrix();
        m.setScale(videoW * scale / vw, videoH * scale / vh, vw / 2f, vh / 2f);
        video.setTransform(m);
    }

    // ---- TextureView ----------------------------------------------------------------------------

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
        surface = new Surface(texture);
        startIfReady();
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
        fitVideo();
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
        releasePlayer();
        if (surface != null) {
            surface.release();
            surface = null;
        }
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture texture) {
        // frames only
    }
}
