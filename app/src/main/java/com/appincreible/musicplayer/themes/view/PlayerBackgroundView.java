package com.appincreible.musicplayer.themes.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import com.appincreible.musicplayer.themes.AppPreferences;

public class PlayerBackgroundView extends View {

    private static final long ANIMATION_FRAME_MS = 80L; // Slow decorative motion does not need 30/60 fps.

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final PowerManager powerManager;

    private String backgroundMode = AppPreferences.BACKGROUND_AURORA;
    private int accent = Color.rgb(139, 92, 246);
    private float animationMultiplier = 1f;
    private boolean lightMode;

    private String paletteMode = AppPreferences.APP_BACKGROUND_COLOR_ACCENT;
    private int color1 = Color.parseColor("#7C4DFF");
    private int color2 = Color.parseColor("#24C7FF");
    private int color3 = Color.parseColor("#FF5FA2");

    public PlayerBackgroundView(Context context) {
        super(context);
        powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        init();
    }

    public PlayerBackgroundView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        init();
    }

    public PlayerBackgroundView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        init();
    }

    private void init() {
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
    }

    public void setLightMode(boolean lightMode) {
        this.lightMode = lightMode;
        invalidate();
    }

    public void setScene(String backgroundMode, int accent, float animationMultiplier) {
        this.backgroundMode = backgroundMode == null ? AppPreferences.BACKGROUND_AURORA : backgroundMode;
        this.accent = accent;
        this.animationMultiplier = animationMultiplier;
        invalidate();
    }

    public void setPalette(String paletteMode, int color1, int color2, int color3) {
        this.paletteMode = paletteMode == null ? AppPreferences.APP_BACKGROUND_COLOR_ACCENT : paletteMode;
        this.color1 = color1;
        this.color2 = color2;
        this.color3 = color3;
        invalidate();
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return;

        int p1 = AppPreferences.APP_BACKGROUND_COLOR_ACCENT.equals(paletteMode) ? accent : color1;
        int p2 = AppPreferences.APP_BACKGROUND_COLOR_ACCENT.equals(paletteMode) ? mix(accent, Color.CYAN, .38f) : color2;
        int p3 = AppPreferences.APP_BACKGROUND_COLOR_ACCENT.equals(paletteMode) ? mix(accent, Color.WHITE, .18f) : color3;

        drawBase(c, w, h, p1, p2, p3);

        if (!AppPreferences.BACKGROUND_NONE.equals(backgroundMode)) {
            if (AppPreferences.BACKGROUND_DARK.equals(backgroundMode)) {
                drawGlow(c, w * .16f, h * .18f, w * .38f, withAlpha(p1, lightMode ? 20 : 38));
                drawGlow(c, w * .84f, h * .70f, w * .55f, withAlpha(p2, lightMode ? 14 : 28));
            } else if (AppPreferences.BACKGROUND_GRID.equals(backgroundMode)) {
                float drift = animationMultiplier <= 0f ? 0f : (float) ((SystemClock.uptimeMillis() / 28.0 * animationMultiplier) % dp(34));
                stroke.setStrokeWidth(dp(1f));
                stroke.setColor(withAlpha(p1, lightMode ? 34 : 46));
                for (float y = drift; y < h; y += dp(34)) c.drawLine(0, y, w, y, stroke);
                for (float x = drift; x < w; x += dp(34)) c.drawLine(x, 0, x, h, stroke);
                drawGlow(c, w * .82f, h * .22f, w * .28f, withAlpha(p2, lightMode ? 25 : 42));
            } else if (AppPreferences.BACKGROUND_WAVES.equals(backgroundMode)) {
                float p = phase(5200);
                stroke.setStrokeWidth(dp(2f));
                int[] waveColors = new int[]{p1, p2, p3};
                for (int row = 0; row < 7; row++) {
                    float base = h * (0.11f + row * .11f);
                    stroke.setColor(withAlpha(waveColors[row % 3], lightMode ? 44 : 58));
                    path.reset();
                    for (int i = 0; i <= 42; i++) {
                        float x = w * i / 42f;
                        float y = base + (float) Math.sin((i * .38f) + p + row * .72f) * dp(11 + row);
                        if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
                    }
                    c.drawPath(path, stroke);
                }
            } else if (AppPreferences.BACKGROUND_PULSE.equals(backgroundMode)) {
                float p = phase(2800);
                float pulse = .74f + .26f * (float) ((Math.sin(p) + 1f) * .5f);
                drawGlow(c, w * .50f, h * .28f, w * (.55f * pulse), withAlpha(p1, lightMode ? 38 : 68));
                drawGlow(c, w * .48f, h * .68f, w * (.40f * pulse), withAlpha(p2, lightMode ? 28 : 50));
            } else {
                float p = phase(8600);
                drawGlow(c, w * (.16f + .05f * (float) Math.sin(p)), h * .16f, w * .42f, withAlpha(p1, lightMode ? 34 : 70));
                drawGlow(c, w * (.82f + .04f * (float) Math.cos(p * .82f)), h * .62f, w * .56f, withAlpha(p2, lightMode ? 28 : 58));
                drawGlow(c, w * .46f, h * (.82f + .03f * (float) Math.sin(p * .65f)), w * .48f, withAlpha(p3, lightMode ? 24 : 44));
            }
        }

        if (needsContinuousAnimation()) {
            postInvalidateDelayed(ANIMATION_FRAME_MS);
        }
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        // Restart animation only when the Activity/window becomes visible again.
        if (visibility == VISIBLE) invalidate();
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility == VISIBLE && isAttachedToWindow()) invalidate();
    }

    private void drawBase(Canvas c, float w, float h, int p1, int p2, int p3) {
        int neutralTop = lightMode ? Color.rgb(246, 247, 251) : Color.rgb(9, 12, 20);
        int neutralBottom = lightMode ? Color.rgb(250, 250, 252) : Color.rgb(4, 7, 13);

        if (AppPreferences.APP_BACKGROUND_COLOR_SOLID.equals(paletteMode)) {
            int solid = lightMode ? mix(Color.WHITE, p1, .18f) : mix(Color.BLACK, p1, .28f);
            c.drawColor(solid);
            return;
        }

        if (AppPreferences.APP_BACKGROUND_COLOR_GRADIENT_2.equals(paletteMode)) {
            int a = lightMode ? mix(neutralTop, p1, .26f) : mix(neutralTop, p1, .42f);
            int b = lightMode ? mix(neutralBottom, p2, .24f) : mix(neutralBottom, p2, .40f);
            paint.setShader(new LinearGradient(0, 0, w, h, a, b, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, paint);
            paint.setShader(null);
            return;
        }

        if (AppPreferences.APP_BACKGROUND_COLOR_GRADIENT_3.equals(paletteMode)) {
            int a = lightMode ? mix(neutralTop, p1, .24f) : mix(neutralTop, p1, .40f);
            int b = lightMode ? mix(neutralTop, p2, .22f) : mix(neutralTop, p2, .36f);
            int d = lightMode ? mix(neutralBottom, p3, .24f) : mix(neutralBottom, p3, .40f);
            paint.setShader(new LinearGradient(0, 0, w, h,
                    new int[]{a, b, d}, new float[]{0f, .52f, 1f}, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, paint);
            paint.setShader(null);
            return;
        }

        if (AppPreferences.APP_BACKGROUND_COLOR_MULTICOLOR.equals(paletteMode)) {
            float p = phase(9000);
            int a = mix(p1, p2, .5f + .5f * (float) Math.sin(p));
            int b = mix(p2, p3, .5f + .5f * (float) Math.sin(p + 2.1f));
            int d = mix(p3, p1, .5f + .5f * (float) Math.sin(p + 4.2f));
            a = lightMode ? mix(neutralTop, a, .22f) : mix(neutralTop, a, .42f);
            b = lightMode ? mix(neutralTop, b, .20f) : mix(neutralTop, b, .38f);
            d = lightMode ? mix(neutralBottom, d, .22f) : mix(neutralBottom, d, .42f);
            paint.setShader(new LinearGradient(0, 0, w, h,
                    new int[]{a, b, d}, new float[]{0f, .5f, 1f}, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, paint);
            paint.setShader(null);
            return;
        }

        if (AppPreferences.PLAYER_BACKGROUND_COLOR_RAINBOW.equals(paletteMode)) {
            float phase = animationMultiplier > 0f ? phase(12000) : 0f;
            float shift = (phase / ((float) Math.PI * 2f)) * 360f;
            int[] rainbow = new int[7];
            for (int i = 0; i < rainbow.length; i++) {
                float hue = (shift + i * 60f) % 360f;
                int vivid = Color.HSVToColor(new float[]{hue, .86f, .92f});
                rainbow[i] = lightMode ? mix(neutralTop, vivid, .24f) : mix(neutralTop, vivid, .44f);
            }
            paint.setShader(new LinearGradient(0, 0, w, h, rainbow, null, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, paint);
            paint.setShader(null);
            return;
        }

        int baseA = neutralBottom;
        int baseB = lightMode ? mix(neutralTop, p1, .07f) : mix(neutralTop, p1, .12f);
        paint.setShader(new LinearGradient(0, 0, 0, h, baseB, baseA, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, paint);
        paint.setShader(null);
    }

    private void drawGlow(Canvas c, float cx, float cy, float radius, int color) {
        paint.setShader(new RadialGradient(cx, cy, radius, color, Color.TRANSPARENT, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, radius, paint);
        paint.setShader(null);
    }

    private boolean needsContinuousAnimation() {
        if (animationMultiplier <= 0f || !isAttachedToWindow() || !isShown()
                || getWindowVisibility() != VISIBLE
                || (powerManager != null && !powerManager.isInteractive())) return false;

        boolean movingPalette = AppPreferences.APP_BACKGROUND_COLOR_MULTICOLOR.equals(paletteMode)
                || AppPreferences.PLAYER_BACKGROUND_COLOR_RAINBOW.equals(paletteMode);
        boolean movingScene = AppPreferences.BACKGROUND_GRID.equals(backgroundMode)
                || AppPreferences.BACKGROUND_WAVES.equals(backgroundMode)
                || AppPreferences.BACKGROUND_PULSE.equals(backgroundMode)
                || AppPreferences.BACKGROUND_AURORA.equals(backgroundMode);
        return movingPalette || movingScene;
    }

    private float phase(long periodMs) {
        if (animationMultiplier <= 0f) return 0f;
        double p = (SystemClock.uptimeMillis() % periodMs) / (double) periodMs;
        return (float) (p * Math.PI * 2d * animationMultiplier);
    }

    private int mix(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        return Color.rgb((int) (Color.red(a) + (Color.red(b) - Color.red(a)) * t),
                (int) (Color.green(a) + (Color.green(b) - Color.green(a)) * t),
                (int) (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t));
    }

    private int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private float dp(float value) { return value * getResources().getDisplayMetrics().density; }
}
