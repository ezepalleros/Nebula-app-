package com.appincreible.musicplayer.themes.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

public class HsvColorPickerView extends View {

    public interface OnColorChangedListener { void onColorChanged(int color); }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint marker = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] hsv = new float[]{270f, .68f, .96f};
    private final RectF field = new RectF();
    private OnColorChangedListener listener;

    public HsvColorPickerView(Context context) { super(context); init(); }
    public HsvColorPickerView(Context context, @Nullable AttributeSet attrs) { super(context, attrs); init(); }
    public HsvColorPickerView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) { super(context, attrs, defStyleAttr); init(); }

    private void init() {
        marker.setStyle(Paint.Style.STROKE);
        marker.setStrokeWidth(dp(2));
        marker.setColor(Color.WHITE);
        setMinimumHeight((int) dp(180));
    }

    public void setColor(int color) {
        Color.colorToHSV(color, hsv);
        invalidate();
    }

    public int getColor() { return Color.HSVToColor(hsv); }

    public void setHue(float hue) {
        hsv[0] = Math.max(0f, Math.min(360f, hue));
        invalidate();
        notifyChanged();
    }

    public float getHue() { return hsv[0]; }

    public void setOnColorChangedListener(OnColorChangedListener listener) { this.listener = listener; }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float pad = dp(6);
        field.set(pad, pad, getWidth() - pad, getHeight() - pad);
        int hueColor = Color.HSVToColor(new float[]{hsv[0], 1f, 1f});

        paint.setShader(new LinearGradient(field.left, 0, field.right, 0,
                Color.WHITE, hueColor, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(field, dp(12), dp(12), paint);
        paint.setShader(new LinearGradient(0, field.top, 0, field.bottom,
                Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(field, dp(12), dp(12), paint);
        paint.setShader(null);

        float x = field.left + hsv[1] * field.width();
        float y = field.top + (1f - hsv[2]) * field.height();
        marker.setColor(Color.BLACK);
        marker.setStrokeWidth(dp(4));
        canvas.drawCircle(x, y, dp(9), marker);
        marker.setColor(Color.WHITE);
        marker.setStrokeWidth(dp(2));
        canvas.drawCircle(x, y, dp(9), marker);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN || event.getActionMasked() == MotionEvent.ACTION_MOVE) {
            if (field.width() <= 0f || field.height() <= 0f) return true;
            float x = Math.max(field.left, Math.min(field.right, event.getX()));
            float y = Math.max(field.top, Math.min(field.bottom, event.getY()));
            hsv[1] = (x - field.left) / field.width();
            hsv[2] = 1f - ((y - field.top) / field.height());
            invalidate();
            notifyChanged();
            return true;
        }
        return true;
    }

    private void notifyChanged() { if (listener != null) listener.onColorChanged(getColor()); }
    private float dp(float value) { return value * getResources().getDisplayMetrics().density; }
}
