package com.appincreible.musicplayer.themes.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.PowerManager;
import android.util.AttributeSet;
import android.view.View;

import com.appincreible.musicplayer.player.audio.EqualizerSpectrum;
import com.appincreible.musicplayer.power.PowerSaverManager;
import com.appincreible.musicplayer.themes.AppPreferences;

/**
 * Full-screen Canvas renderer used only by the "Ecualizador 2D" player skin.
 * It reads the existing EqualizerSpectrum bridge, never modifies audio and caps drawing at roughly 20 fps.
 */
public final class EqualizerBackgroundView extends View {
    private static final long FRAME_MS = 50L; // <= ~20 fps; enough for audio-reactive decoration
    private static final int BAR_COUNT = 28;
    private static final float SENSITIVITY = 1.55f;
    private static final float RISE = 1.00f; // immediate attack: no visible catch-up delay
    private static final float FALL = 0.30f; // short release: smooth without trailing ~0.5 s
    private static final float PEAK_FALL = 0.045f;

    private final Paint backgroundPaint = new Paint();
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint scrimPaint = new Paint();
    private final RectF block = new RectF();
    private final float[] spectrum = new float[EqualizerSpectrum.BAND_COUNT];
    private final float[] displayed = new float[EqualizerSpectrum.BAND_COUNT];
    private final float[] peaks = new float[EqualizerSpectrum.BAND_COUNT];
    private final float[] staticLevels = new float[EqualizerSpectrum.BAND_COUNT];
    private final PowerManager powerManager;

    private boolean skinActive;
    private boolean playbackActive;
    private boolean hostVisible;
    private boolean frameScheduled;
    private int backgroundColor = Color.BLACK;
    private int barsColor = Color.rgb(139, 92, 246);
    private float intensityScale = 1f;

    private final Runnable frame = new Runnable() {
        @Override public void run() {
            frameScheduled = false;
            if (!shouldAnimate()) return;
            invalidate();
            scheduleFrame();
        }
    };

    public EqualizerBackgroundView(Context context) {
        super(context);
        powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        init();
    }

    public EqualizerBackgroundView(Context context, AttributeSet attrs) {
        super(context, attrs);
        powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        init();
    }

    public EqualizerBackgroundView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        init();
    }

    private void init() {
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        buildStaticLevels();
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
        } else {
            stopFrames();
            EqualizerSpectrum.setAnalysisEnabled(false);
        }
        updateAnalysisState();
    }

    public void applyPreferences() {
        backgroundColor = AppPreferences.getEqualizerSkinBackgroundColor(getContext());
        barsColor = AppPreferences.getEqualizerSkinBarsColor(getContext());
        intensityScale = AppPreferences.getEqualizerSkinIntensity(getContext()) / 100f;
        backgroundPaint.setColor(backgroundColor);
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

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        // Stronger at top/bottom where header, metadata, progress and controls sit.
        scrimPaint.setShader(new LinearGradient(0f, 0f, 0f, Math.max(1, h),
                new int[]{0x99000000, 0x36000000, 0x30000000, 0xA6000000},
                new float[]{0f, 0.30f, 0.56f, 1f}, Shader.TileMode.CLAMP));
    }

    @Override protected void onDetachedFromWindow() {
        stopFrames();
        EqualizerSpectrum.setAnalysisEnabled(false);
        super.onDetachedFromWindow();
    }

    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility == VISIBLE) scheduleFrame(); else stopFrames();
        updateAnalysisState();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!skinActive || getWidth() <= 0 || getHeight() <= 0) return;

        canvas.drawRect(0f, 0f, getWidth(), getHeight(), backgroundPaint);

        if (PowerSaverManager.isActive(getContext())) {
            drawStaticState();
        } else {
            EqualizerSpectrum.snapshot(spectrum);
            updateAnimationState();
        }
        drawBars(canvas);
        // Keeps the existing white foreground controls readable on bright user colors.
        canvas.drawRect(0f, 0f, getWidth(), getHeight(), scrimPaint);
    }

    private void drawStaticState() {
        for (int i = 0; i < displayed.length; i++) {
            displayed[i] = clamp(staticLevels[i] * intensityScale, 0f, 1f);
            peaks[i] = Math.min(1f, displayed[i] + 0.08f);
        }
    }

    private void updateAnimationState() {
        for (int i = 0; i < displayed.length; i++) {
            float target = clamp(spectrum[i] * SENSITIVITY * intensityScale, 0f, 1f);
            float factor = target >= displayed[i] ? RISE : FALL;
            displayed[i] += (target - displayed[i]) * factor;
            if (displayed[i] >= peaks[i]) peaks[i] = displayed[i];
            else peaks[i] = Math.max(displayed[i], peaks[i] - PEAK_FALL);
        }
    }

    private void drawBars(Canvas canvas) {
        final float w = getWidth();
        final float h = getHeight();
        final float side = dp(5f);
        final float available = Math.max(dp(160f), w - side * 2f);
        final float slot = available / BAR_COUNT;
        final float barWidth = Math.max(dp(5f), slot * 0.70f);
        final float baseY = h * 0.79f;
        final float maxBarHeight = h * 0.62f;
        final float blockHeight = clamp(slot * 0.34f, dp(5f), dp(11f));
        final float blockGap = Math.max(dp(2.2f), blockHeight * 0.32f);
        final float step = blockHeight + blockGap;
        final int maxBlocks = Math.max(10, (int) (maxBarHeight / step));
        final float radius = Math.max(dp(1.6f), blockHeight * 0.22f);

        for (int bar = 0; bar < BAR_COUNT; bar++) {
            float level = sampledLevel(displayed, bar, BAR_COUNT);
            float peak = sampledLevel(peaks, bar, BAR_COUNT);
            int lit = Math.min(maxBlocks, Math.max(1, Math.round(level * maxBlocks)));
            float x = side + bar * slot + (slot - barWidth) * 0.5f;

            barPaint.setColor(barsColor);
            for (int seg = 0; seg < lit; seg++) {
                float y = baseY - (seg + 1) * step;
                float vertical = seg / (float) Math.max(1, maxBlocks - 1);
                barPaint.setAlpha(Math.round(205f + 45f * vertical));
                block.set(x, y, x + barWidth, y + blockHeight);
                canvas.drawRoundRect(block, radius, radius, barPaint);
            }

            int peakSeg = Math.min(maxBlocks - 1, Math.max(0, Math.round(peak * (maxBlocks - 1))));
            float peakY = baseY - (peakSeg + 1) * step - blockGap * 0.55f;
            barPaint.setAlpha(255);
            block.set(x, peakY, x + barWidth, peakY + Math.max(dp(2.5f), blockHeight * 0.50f));
            canvas.drawRoundRect(block, radius, radius, barPaint);

            int reflected = Math.min(lit, Math.max(3, maxBlocks / 3));
            for (int seg = 0; seg < reflected; seg++) {
                float y = baseY + dp(7f) + seg * step;
                if (y > h) break;
                float fade = 1f - seg / (float) Math.max(1, reflected);
                barPaint.setAlpha(Math.round(50f * fade));
                block.set(x, y, x + barWidth, y + blockHeight);
                canvas.drawRoundRect(block, radius, radius, barPaint);
            }
        }
    }

    private float sampledLevel(float[] source, int outputIndex, int outputCount) {
        int start = outputIndex * source.length / outputCount;
        int end = Math.max(start + 1, (outputIndex + 1) * source.length / outputCount);
        float max = 0f;
        for (int i = start; i < end && i < source.length; i++) max = Math.max(max, source[i]);
        return max;
    }

    private boolean shouldAnimate() {
        return skinActive && playbackActive && hostVisible && isShown() && getWindowVisibility() == VISIBLE
                && (powerManager == null || powerManager.isInteractive())
                && !PowerSaverManager.isActive(getContext());
    }

    private void scheduleFrame() {
        if (frameScheduled || !shouldAnimate()) return;
        frameScheduled = true;
        postOnAnimationDelayed(frame, FRAME_MS);
    }

    private void updateAnalysisState() {
        EqualizerSpectrum.setAnalysisEnabled(shouldAnimate());
    }

    private void stopFrames() {
        if (frameScheduled) removeCallbacks(frame);
        frameScheduled = false;
    }

    private void buildStaticLevels() {
        for (int i = 0; i < staticLevels.length; i++) {
            float a = (float) Math.abs(Math.sin(i * 0.63 + 0.8));
            float b = (float) Math.abs(Math.sin(i * 0.21 + 2.1));
            staticLevels[i] = 0.18f + 0.52f * a * (0.55f + 0.45f * b);
        }
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
