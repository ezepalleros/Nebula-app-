package com.appincreible.musicplayer.themes.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatSeekBar;
import androidx.core.graphics.ColorUtils;

import com.appincreible.musicplayer.R;
import com.appincreible.musicplayer.themes.AppPreferences;

/** SeekBar táctil con estilos visuales ligeros para Reproduciendo. */
public final class PlayerProgressSeekBar extends AppCompatSeekBar {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private String progressStyle = AppPreferences.PROGRESS_STYLE_CLASSIC;
    private String progressEmoji = "🎵";
    private int activeColor = Color.WHITE;
    private int inactiveColor = ColorUtils.setAlphaComponent(Color.WHITE, 96);
    private boolean touchEmphasis;

    public PlayerProgressSeekBar(@NonNull Context context) {
        super(context);
        init();
    }

    public PlayerProgressSeekBar(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public PlayerProgressSeekBar(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setSplitTrack(false);
        setThumb(null);
    }

    public void setProgressStyle(@Nullable String style) {
        progressStyle = style == null ? AppPreferences.PROGRESS_STYLE_CLASSIC : style;
        requestLayout();
        invalidate();
    }

    public void setProgressEmoji(@Nullable String emoji) {
        String safe = emoji == null ? "" : emoji.trim();
        progressEmoji = safe.isEmpty() ? "🎵" : safe;
        requestLayout();
        invalidate();
    }

    public void setPlayerColors(int active, int inactive) {
        activeColor = active;
        inactiveColor = ColorUtils.setAlphaComponent(inactive, 112);
        invalidate();
    }

    public void setTouchEmphasis(boolean emphasized) {
        touchEmphasis = emphasized;
        invalidate();
    }

    @Override
    protected synchronized void onDraw(Canvas canvas) {
        float thumbRoom = AppPreferences.PROGRESS_STYLE_EMOJI.equals(progressStyle)
                ? emojiHorizontalRoom() : getResources().getDimension(R.dimen.space_12);
        float left = getPaddingLeft() + thumbRoom;
        float right = getWidth() - getPaddingRight() - thumbRoom;
        float centerY = getHeight() * 0.5f;
        if (right <= left) return;

        float range = Math.max(1f, getMax() - getMin());
        float ratio = Math.max(0f, Math.min(1f, (getProgress() - getMin()) / range));
        float progressX = left + (right - left) * ratio;
        float thin = Math.max(1f, getResources().getDimension(R.dimen.space_4) * 0.5f);
        float medium = getResources().getDimension(R.dimen.space_4);
        float thick = getResources().getDimension(R.dimen.space_8);
        float thumbRadius = getResources().getDimension(touchEmphasis ? R.dimen.space_8 : R.dimen.space_4);

        if (AppPreferences.PROGRESS_STYLE_WAVE.equals(progressStyle)) {
            drawWave(canvas, left, right, centerY, inactiveColor, thin);
            canvas.save();
            canvas.clipRect(left, 0f, progressX, getHeight());
            drawWave(canvas, left, right, centerY, activeColor, medium);
            canvas.restore();
            drawCircleThumb(canvas, progressX, centerY, thumbRadius);
            return;
        }

        if (AppPreferences.PROGRESS_STYLE_DOTS.equals(progressStyle)) {
            float spacing = getResources().getDimension(R.dimen.space_12);
            float radius = Math.max(1f, getResources().getDimension(R.dimen.space_4) * 0.5f);
            for (float x = left; x <= right; x += spacing) {
                paint.setColor(x <= progressX ? activeColor : inactiveColor);
                paint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(x, centerY, x <= progressX ? radius : radius * 0.72f, paint);
            }
            drawCircleThumb(canvas, progressX, centerY, thumbRadius);
            return;
        }

        if (AppPreferences.PROGRESS_STYLE_CAPSULE.equals(progressStyle)) {
            drawLine(canvas, left, right, centerY, inactiveColor, thick);
            drawLine(canvas, left, progressX, centerY, activeColor, thick);
            drawCircleThumb(canvas, progressX, centerY, thumbRadius * 1.15f);
            return;
        }

        drawLine(canvas, left, right, centerY, inactiveColor, thin);
        drawLine(canvas, left, progressX, centerY, activeColor, medium);
        if (AppPreferences.PROGRESS_STYLE_EMOJI.equals(progressStyle)) {
            drawEmojiThumb(canvas, progressX, centerY);
        } else {
            drawCircleThumb(canvas, progressX, centerY, thumbRadius);
        }
    }

    private float emojiHorizontalRoom() {
        configureEmojiPaint();
        float padding = getResources().getDimension(R.dimen.space_4);
        return Math.max(getResources().getDimension(R.dimen.space_12), paint.measureText(progressEmoji) * 0.5f + padding);
    }

    private void configureEmojiPaint() {
        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(Typeface.DEFAULT);
        float base = getResources().getDimension(R.dimen.text_body);
        paint.setTextSize(base + (touchEmphasis ? getResources().getDimension(R.dimen.space_4) : 0f));
        paint.setColor(activeColor);
    }

    private void drawEmojiThumb(Canvas canvas, float x, float centerY) {
        configureEmojiPaint();
        Paint.FontMetrics fm = paint.getFontMetrics();
        float baseline = centerY - (fm.ascent + fm.descent) * 0.5f;
        canvas.drawText(progressEmoji, x, baseline, paint);
    }

    private void drawLine(Canvas canvas, float left, float right, float y, int color, float width) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(width);
        paint.setColor(color);
        canvas.drawLine(left, y, right, y, paint);
    }

    private void drawCircleThumb(Canvas canvas, float x, float y, float radius) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(activeColor);
        canvas.drawCircle(x, y, radius, paint);
    }

    private void drawWave(Canvas canvas, float left, float right, float centerY, int color, float width) {
        float amplitude = getResources().getDimension(R.dimen.space_4);
        float wavelength = getResources().getDimension(R.dimen.space_24);
        float step = Math.max(1f, getResources().getDimension(R.dimen.space_4));
        path.reset();
        path.moveTo(left, centerY);
        for (float x = left + step; x <= right; x += step) {
            float phase = (x - left) / wavelength * (float) (Math.PI * 2.0);
            path.lineTo(x, centerY + (float) Math.sin(phase) * amplitude);
        }
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeWidth(width);
        paint.setColor(color);
        canvas.drawPath(path, paint);
    }
}
