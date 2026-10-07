package com.appincreible.musicplayer.themes.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import com.appincreible.musicplayer.themes.AppPreferences;

/**
 * Lightweight, static preview for the native Now Playing layout.
 * Reads the same appearance preferences as NowPlayingFragment and never animates.
 */
public final class PlayerAppearancePreviewView extends View {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();

    private int accent;
    private boolean showBack;
    private boolean showHeader;
    private boolean showMusic;
    private boolean showArtwork;
    private boolean showProgress;
    private boolean showWaves;
    private boolean showTitle;
    private boolean showArtist;
    private boolean showAlbum;
    private boolean showShuffle;
    private boolean showRepeat;
    private boolean showMode;
    private boolean largeArtwork;
    private boolean centeredInfo;
    private String artworkShape;

    public PlayerAppearancePreviewView(Context context) {
        super(context);
        init();
    }

    public PlayerAppearancePreviewView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public PlayerAppearancePreviewView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        text.setTypeface(android.graphics.Typeface.DEFAULT);
        applyPreferences();
    }

    public void applyPreferences() {
        accent = AppPreferences.getPlayerAccentColor(getContext());
        showBack = AppPreferences.showPlayerBackButton(getContext());
        showHeader = AppPreferences.showPlayerHeaderLabel(getContext());
        showMusic = AppPreferences.showPlayerMusicButton(getContext());
        showArtwork = AppPreferences.showPlayerArtwork(getContext());
        showProgress = AppPreferences.showPlayerProgressLine(getContext());
        showWaves = AppPreferences.showPlayerWaves(getContext());
        showTitle = AppPreferences.showPlayerTitle(getContext());
        showArtist = AppPreferences.showPlayerArtist(getContext());
        showAlbum = AppPreferences.showPlayerAlbum(getContext());
        showShuffle = AppPreferences.showPlayerShuffle(getContext());
        showRepeat = AppPreferences.showPlayerRepeat(getContext());
        showMode = AppPreferences.showPlayerModeText(getContext());
        largeArtwork = AppPreferences.useLargeArtwork(getContext());
        centeredInfo = AppPreferences.centerPlayerInfo(getContext());
        artworkShape = AppPreferences.getArtworkShape(getContext());
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        if (w <= 0f || h <= 0f) return;

        fill.setColor(Color.rgb(7, 8, 13));
        canvas.drawRoundRect(0f, 0f, w, h, dp(18), dp(18), fill);

        if (showWaves) drawWaves(canvas, w, h);

        float pad = dp(14);
        boolean anyHeader = showBack || showHeader || showMusic;
        float y = pad;
        if (anyHeader) {
            drawHeader(canvas, w, y);
            y += dp(34);
        }

        if (showArtwork) {
            float maxCover = Math.min(w - dp(58), h * 0.43f);
            float size = largeArtwork ? maxCover : maxCover * 0.72f;
            float left = (w - size) * 0.5f;
            float top = y + dp(5);
            drawArtwork(canvas, left, top, size);
            y = top + size + dp(13);
        } else {
            y += dp(12);
        }

        y = drawMetadata(canvas, w, y);

        if (showProgress) {
            y += dp(9);
            stroke.setStrokeWidth(dp(2));
            stroke.setColor(Color.argb(92, 255, 255, 255));
            canvas.drawLine(pad, y, w - pad, y, stroke);
            stroke.setColor(accent);
            canvas.drawLine(pad, y, pad + (w - pad * 2f) * 0.56f, y, stroke);
            fill.setColor(accent);
            canvas.drawCircle(pad + (w - pad * 2f) * 0.56f, y, dp(3.2f), fill);
            y += dp(14);
        } else {
            y += dp(6);
        }

        drawControls(canvas, w, Math.min(h - dp(showMode ? 34 : 20), y + dp(19)));
        if (showMode) {
            text.setTextAlign(Paint.Align.CENTER);
            text.setTextSize(dp(8.5f));
            text.setColor(Color.rgb(182, 190, 214));
            canvas.drawText("Aleatorio · Repetición desactivada", w * 0.5f, h - dp(11), text);
        }
    }

    private void drawHeader(Canvas canvas, float w, float y) {
        text.setTextSize(dp(9));
        text.setColor(Color.WHITE);
        if (showBack) {
            text.setTextAlign(Paint.Align.LEFT);
            canvas.drawText("‹", dp(16), y + dp(14), text);
        }
        if (showHeader) {
            text.setTextAlign(Paint.Align.CENTER);
            text.setColor(accent);
            text.setFakeBoldText(true);
            canvas.drawText("REPRODUCIENDO", w * 0.5f, y + dp(13), text);
            text.setFakeBoldText(false);
        }
        if (showMusic) {
            text.setTextAlign(Paint.Align.RIGHT);
            text.setColor(accent);
            canvas.drawText("Música", w - dp(14), y + dp(13), text);
        }
    }

    private void drawArtwork(Canvas canvas, float left, float top, float size) {
        float radius;
        if (AppPreferences.SHAPE_CIRCLE.equals(artworkShape)) radius = size * 0.5f;
        else if (AppPreferences.SHAPE_SQUARE.equals(artworkShape)) radius = 0f;
        else radius = dp(14);
        rect.set(left, top, left + size, top + size);
        fill.setColor(Color.rgb(23, 25, 34));
        canvas.drawRoundRect(rect, radius, radius, fill);
        fill.setColor(Color.argb(92, Color.red(accent), Color.green(accent), Color.blue(accent)));
        rect.inset(dp(8), dp(8));
        canvas.drawRoundRect(rect, Math.max(0f, radius - dp(5)), Math.max(0f, radius - dp(5)), fill);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTextSize(size * 0.22f);
        text.setColor(Color.WHITE);
        canvas.drawText("♪", left + size * 0.5f, top + size * 0.58f, text);
    }

    private float drawMetadata(Canvas canvas, float w, float y) {
        float x = centeredInfo ? w * 0.5f : dp(18);
        text.setTextAlign(centeredInfo ? Paint.Align.CENTER : Paint.Align.LEFT);
        if (showTitle) {
            text.setFakeBoldText(true);
            text.setTextSize(dp(12));
            text.setColor(Color.WHITE);
            canvas.drawText("Tu canción", x, y + dp(10), text);
            text.setFakeBoldText(false);
            y += dp(18);
        }
        if (showArtist) {
            text.setTextSize(dp(9));
            text.setColor(Color.rgb(210, 218, 238));
            canvas.drawText("Tu banda / artista", x, y + dp(8), text);
            y += dp(14);
        }
        if (showAlbum) {
            text.setTextSize(dp(8));
            text.setColor(Color.rgb(164, 175, 205));
            canvas.drawText("Tu álbum", x, y + dp(7), text);
            y += dp(12);
        }
        return y;
    }

    private void drawControls(Canvas canvas, float w, float cy) {
        float cx = w * 0.5f;
        float step = dp(42);
        if (showShuffle) drawSmallControl(canvas, cx - step * 2f, cy, "↝");
        drawSmallControl(canvas, cx - step, cy, "‹");
        fill.setColor(accent);
        canvas.drawCircle(cx, cy, dp(17), fill);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTextSize(dp(12));
        text.setColor(Color.WHITE);
        canvas.drawText("▶", cx + dp(1), cy + dp(4), text);
        drawSmallControl(canvas, cx + step, cy, "›");
        if (showRepeat) drawSmallControl(canvas, cx + step * 2f, cy, "↻");
    }

    private void drawSmallControl(Canvas canvas, float cx, float cy, String glyph) {
        fill.setColor(Color.argb(56, Color.red(accent), Color.green(accent), Color.blue(accent)));
        canvas.drawCircle(cx, cy, dp(12), fill);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTextSize(dp(10));
        text.setColor(Color.rgb(210, 216, 235));
        canvas.drawText(glyph, cx, cy + dp(3.5f), text);
    }

    private void drawWaves(Canvas canvas, float w, float h) {
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(dp(1.2f));
        stroke.setColor(Color.argb(46, Color.red(accent), Color.green(accent), Color.blue(accent)));
        for (int row = 0; row < 3; row++) {
            path.reset();
            float baseY = h * (0.70f + row * 0.07f);
            for (int i = 0; i <= 40; i++) {
                float x = w * i / 40f;
                float y = baseY + (float) Math.sin(i * 0.75f + row) * dp(5 + row * 2);
                if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
            }
            canvas.drawPath(path, stroke);
        }
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
