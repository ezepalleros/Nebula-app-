package com.appincreible.musicplayer.themes.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.PowerManager;
import android.util.AttributeSet;
import android.view.View;

import com.appincreible.musicplayer.player.audio.EqualizerSpectrum;
import com.appincreible.musicplayer.power.PowerSaverManager;
import com.appincreible.musicplayer.themes.AppPreferences;

/**
 * Canvas renderer for the "Anillo" player skin. It only reads the shared FFT spectrum.
 * No audio is modified and drawing is capped to roughly 20 fps.
 */
public final class RingVisualizerView extends View {
    private static final long FRAME_MS = 50L; // <= ~20 fps; enough for audio-reactive decoration
    private static final int RAY_COUNT = 96;
    private static final float SENSITIVITY = 1.55f;
    private static final float ATTACK = 0.78f;
    private static final float RELEASE = 0.16f;
    private static final float MAX_PULSE = 0.035f;

    private final Paint artworkPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint fallbackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path artworkClip = new Path();
    private final Rect sourceRect = new Rect();
    private final RectF artworkRect = new RectF();
    private final float[] spectrum = new float[EqualizerSpectrum.BAND_COUNT];
    private final float[] smoothed = new float[EqualizerSpectrum.BAND_COUNT];
    private final float[] staticSpectrum = new float[EqualizerSpectrum.BAND_COUNT];
    private final PowerManager powerManager;

    private Bitmap artwork;
    private boolean artworkResolved;
    private boolean skinActive;
    private boolean playbackActive;
    private boolean hostVisible;
    private boolean frameScheduled;
    private boolean artworkVisible = true;
    private int ringColor = Color.rgb(57, 255, 125);
    private float intensityScale = 1f;

    private final Runnable frame = new Runnable() {
        @Override public void run() {
            frameScheduled = false;
            if (!shouldAnimate()) return;
            invalidate();
            scheduleFrame();
        }
    };

    public RingVisualizerView(Context context) {
        super(context);
        powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        init();
    }

    public RingVisualizerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        init();
    }

    public RingVisualizerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        init();
    }

    private void init() {
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeCap(Paint.Cap.ROUND);
        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStrokeCap(Paint.Cap.ROUND);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(dp(1.5f));
        fallbackPaint.setStyle(Paint.Style.FILL);
        buildStaticSpectrum();
        applyPreferences();
        setVisibility(GONE);
    }

    public void setSkinActive(boolean active) {
        skinActive = active;
        setVisibility(active ? VISIBLE : GONE);
        if (active) {
            applyPreferences();
            scheduleFrame();
            invalidate();
            updateAnalysisState();
        } else {
            stopFrames();
        }
    }

    public void applyPreferences() {
        ringColor = AppPreferences.getRingSkinColor(getContext());
        intensityScale = AppPreferences.getRingSkinIntensity(getContext()) / 100f;
        ringPaint.setColor(ringColor);
        glowPaint.setColor(ringColor);
        borderPaint.setColor(ringColor);
        invalidate();
    }

    public void setArtworkLoading() {
        artworkResolved = false;
        artwork = null;
        invalidate();
    }

    public void setArtwork(Bitmap bitmap) {
        artwork = bitmap;
        artworkResolved = true;
        invalidate();
    }

    public void setArtworkVisible(boolean visible) {
        artworkVisible = visible;
        invalidate();
    }

    public void setNoArtwork() {
        artwork = null;
        artworkResolved = true;
        invalidate();
    }

    public void setPlaybackActive(boolean active) {
        playbackActive = active;
        if (active) scheduleFrame(); else stopFrames();
        updateAnalysisState();
        invalidate();
    }

    public void setHostVisible(boolean visible) {
        hostVisible = visible;
        if (visible) scheduleFrame(); else stopFrames();
        updateAnalysisState();
        invalidate();
    }

    public boolean shouldAnalyze() {
        return shouldAnimate();
    }

    @Override protected void onDetachedFromWindow() {
        stopFrames();
        if (skinActive) EqualizerSpectrum.setAnalysisEnabled(false);
        artwork = null;
        super.onDetachedFromWindow();
    }

    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility == VISIBLE) scheduleFrame(); else stopFrames();
        updateAnalysisState();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!skinActive || !artworkResolved || getWidth() <= 0 || getHeight() <= 0) return;

        boolean saver = PowerSaverManager.isActive(getContext());
        if (saver) {
            for (int i = 0; i < smoothed.length; i++) smoothed[i] = staticSpectrum[i];
        } else if (shouldAnimate()) {
            EqualizerSpectrum.snapshot(spectrum);
            smoothSpectrum();
        }

        float width = getWidth();
        float height = getHeight();
        float coverRadius = Math.min(width * 0.285f, height * 0.295f);
        float cx = width * 0.5f;
        float cy = height * 0.50f;
        float bass = saver ? 0f : bassLevel();
        float pulse = 1f + Math.min(MAX_PULSE, bass * MAX_PULSE);
        float artworkRadius = coverRadius * pulse;

        if (artworkVisible) drawArtwork(canvas, cx, cy, artworkRadius);
        drawSpectrumRing(canvas, cx, cy, coverRadius);
    }

    private void drawArtwork(Canvas canvas, float cx, float cy, float radius) {
        artworkRect.set(cx - radius, cy - radius, cx + radius, cy + radius);
        artworkClip.reset();
        artworkClip.addCircle(cx, cy, radius, Path.Direction.CW);
        int save = canvas.save();
        canvas.clipPath(artworkClip);
        if (artwork != null && !artwork.isRecycled()) {
            sourceRect.set(0, 0, artwork.getWidth(), artwork.getHeight());
            canvas.drawBitmap(artwork, sourceRect, artworkRect, artworkPaint);
        } else {
            fallbackPaint.setColor(Color.rgb(18, 20, 27));
            canvas.drawCircle(cx, cy, radius, fallbackPaint);
        }
        canvas.restoreToCount(save);

        borderPaint.setAlpha(205);
        borderPaint.setStrokeWidth(dp(1.5f));
        canvas.drawCircle(cx, cy, radius, borderPaint);
    }

    private void drawSpectrumRing(Canvas canvas, float cx, float cy, float coverRadius) {
        final float baseRadius = coverRadius + dp(7f);
        final float minRay = dp(3f);
        final float maxRay = Math.min(dp(42f), Math.max(dp(18f), getWidth() * 0.105f));

        glowPaint.setStrokeWidth(dp(7f));
        glowPaint.setAlpha(52);
        ringPaint.setStrokeWidth(dp(2.2f));
        ringPaint.setAlpha(238);

        borderPaint.setStrokeWidth(dp(2.2f));
        borderPaint.setAlpha(92);
        canvas.drawCircle(cx, cy, baseRadius, borderPaint);

        for (int i = 0; i < RAY_COUNT; i++) {
            float angle = (float) (-Math.PI * 0.5 + (Math.PI * 2.0 * i / RAY_COUNT));
            // sin(angle) is identical at horizontally mirrored points, so the left side
            // is an exact reflection of the right side and the ring closes without a seam.
            float mirrored = ((float) Math.sin(angle) + 1f) * 0.5f;
            float level = sample(smoothed, mirrored) * SENSITIVITY * intensityScale;
            level = clamp(level, 0f, 1f);
            float length = minRay + maxRay * (0.16f + level * 0.84f);
            float cos = (float) Math.cos(angle);
            float sin = (float) Math.sin(angle);
            float x1 = cx + cos * baseRadius;
            float y1 = cy + sin * baseRadius;
            float x2 = cx + cos * (baseRadius + length);
            float y2 = cy + sin * (baseRadius + length);
            canvas.drawLine(x1, y1, x2, y2, glowPaint);
            canvas.drawLine(x1, y1, x2, y2, ringPaint);
        }
    }

    private void smoothSpectrum() {
        for (int i = 0; i < smoothed.length; i++) {
            float target = clamp(spectrum[i], 0f, 1f);
            float factor = target >= smoothed[i] ? ATTACK : RELEASE;
            smoothed[i] += (target - smoothed[i]) * factor;
        }
    }

    private float bassLevel() {
        int count = Math.min(6, smoothed.length);
        float sum = 0f;
        for (int i = 0; i < count; i++) sum += smoothed[i];
        return count == 0 ? 0f : clamp(sum / count * 1.35f, 0f, 1f);
    }

    private float sample(float[] values, float normalized) {
        float p = clamp(normalized, 0f, 1f) * (values.length - 1);
        int i0 = (int) p;
        int i1 = Math.min(values.length - 1, i0 + 1);
        float f = p - i0;
        return values[i0] + (values[i1] - values[i0]) * f;
    }

    private boolean shouldAnimate() {
        return skinActive && playbackActive && hostVisible && isShown() && getWindowVisibility() == VISIBLE
                && (powerManager == null || powerManager.isInteractive())
                && !PowerSaverManager.isActive(getContext());
    }

    private void updateAnalysisState() {
        if (skinActive) EqualizerSpectrum.setAnalysisEnabled(shouldAnimate());
    }

    private void scheduleFrame() {
        if (frameScheduled || !shouldAnimate()) return;
        frameScheduled = true;
        postOnAnimationDelayed(frame, FRAME_MS);
    }

    private void stopFrames() {
        if (frameScheduled) removeCallbacks(frame);
        frameScheduled = false;
    }

    private void buildStaticSpectrum() {
        for (int i = 0; i < staticSpectrum.length; i++) {
            float a = (float) Math.abs(Math.sin(i * 0.41 + 0.7));
            float b = (float) Math.abs(Math.sin(i * 0.18 + 2.0));
            staticSpectrum[i] = 0.14f + 0.42f * a * (0.55f + 0.45f * b);
        }
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
