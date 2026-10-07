package com.appincreible.musicplayer.themes.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import com.appincreible.musicplayer.themes.AppPreferences;

public class PlayerSkinView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();

    private String style = AppPreferences.STYLE_CLASSIC;
    private int accent = Color.rgb(139, 92, 246);
    private boolean playing;
    private float animationMultiplier = 1f;
    private String title = "";
    private String artist = "";
    private Bitmap artworkBitmap;

    public PlayerSkinView(Context context) { super(context); init(); }
    public PlayerSkinView(Context context, @Nullable AttributeSet attrs) { super(context, attrs); init(); }
    public PlayerSkinView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) { super(context, attrs, defStyleAttr); init(); }

    private void init() {
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        textPaint.setTextAlign(Paint.Align.LEFT);
    }

    public void setScene(String style, int accent, boolean playing, float animationMultiplier) {
        this.style = style == null ? AppPreferences.STYLE_CLASSIC : style;
        this.accent = accent;
        this.playing = playing;
        this.animationMultiplier = animationMultiplier;
        invalidate();
    }

    public void setBackgroundMode(String mode) {
        // compat: unused now in this view.
    }

    public void setMetadata(String title, String artist) {
        this.title = title == null ? "" : title;
        this.artist = artist == null ? "" : artist;
        invalidate();
    }

    public void setArtworkBitmap(@Nullable Bitmap bitmap) {
        this.artworkBitmap = bitmap;
        invalidate();
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return;

        if (AppPreferences.STYLE_VINYL.equals(style)) drawVinyl(c, w, h);
        else if (AppPreferences.STYLE_CASSETTE.equals(style)) drawCassette3D(c, w, h);
        else if (AppPreferences.STYLE_WALKMAN.equals(style)) drawWalkman3D(c, w, h);
        else if (AppPreferences.STYLE_PIXEL.equals(style)) drawNesCart3D(c, w, h);
        else if (AppPreferences.STYLE_RETRO.equals(style)) drawFloppy3D(c, w, h);
        else if (AppPreferences.STYLE_GAMEBOY_3D.equals(style)) drawGameboy3D(c, w, h);
        else if (AppPreferences.STYLE_NEON.equals(style)) drawNeon(c, w, h);
        else if (AppPreferences.STYLE_GLASS.equals(style)) drawGlass(c, w, h);
        else if (AppPreferences.STYLE_MINIMAL.equals(style)) drawMinimal(c, w, h);
        else drawClassic(c, w, h);

        if (animationMultiplier > 0f) postInvalidateOnAnimation();
    }

    private void drawClassic(Canvas c, float w, float h) {
        float p = phase(4600);
        drawShadow(c, w * .5f, h * .78f, w * .28f, dp(16));
        RectF cover = new RectF(w * .23f, h * .17f, w * .77f, h * .66f);
        drawArtworkCard(c, cover, dp(26), false);
        paint.setColor(withAlpha(accent, 170));
        for (int i = 0; i < 14; i++) {
            float x = w * .18f + i * dp(13);
            float amp = .25f + .75f * Math.abs((float) Math.sin(p + i * .42f));
            rounded(c, x, h * .81f - dp(18) * amp, x + dp(7), h * .81f, dp(3), paint);
        }
        drawCaption(c, w * .22f, h * .70f, w * .56f, Color.WHITE, withAlpha(Color.WHITE, 185));
    }

    private void drawMinimal(Canvas c, float w, float h) {
        drawShadow(c, w * .5f, h * .74f, w * .26f, dp(14));
        RectF cover = new RectF(w * .24f, h * .20f, w * .76f, h * .68f);
        drawArtworkCard(c, cover, dp(24), false);
        paint.setColor(withAlpha(accent, 68));
        c.drawCircle(w * .18f, h * .20f, dp(12), paint);
        c.drawCircle(w * .82f, h * .74f, dp(10), paint);
        drawCaption(c, w * .22f, h * .71f, w * .56f, Color.WHITE, withAlpha(Color.WHITE, 185));
    }

    private void drawNeon(Canvas c, float w, float h) {
        float p = phase(2800);
        int a = (int) (120 + 110 * ((Math.sin(p) + 1f) * .5f));
        stroke.setStrokeWidth(dp(2.5f));
        stroke.setColor(withAlpha(accent, a));
        RectF frame = new RectF(dp(16), dp(16), w - dp(16), h - dp(18));
        c.drawRoundRect(frame, dp(28), dp(28), stroke);
        drawShadow(c, w * .5f, h * .77f, w * .28f, dp(16));
        RectF cover = new RectF(w * .23f, h * .18f, w * .77f, h * .66f);
        drawArtworkCard(c, cover, dp(26), false);
        drawCaption(c, w * .22f, h * .70f, w * .56f, Color.WHITE, withAlpha(Color.WHITE, 185));
    }

    private void drawGlass(Canvas c, float w, float h) {
        paint.setShader(new LinearGradient(0, 0, w, h, withAlpha(Color.WHITE, 42), withAlpha(Color.WHITE, 8), Shader.TileMode.CLAMP));
        rounded(c, dp(16), dp(14), w - dp(16), h - dp(16), dp(28), paint);
        paint.setShader(null);
        stroke.setStrokeWidth(dp(1.2f));
        stroke.setColor(withAlpha(Color.WHITE, 86));
        rounded(c, dp(16), dp(14), w - dp(16), h - dp(16), dp(28), stroke);
        drawShadow(c, w * .5f, h * .77f, w * .28f, dp(16));
        RectF cover = new RectF(w * .23f, h * .18f, w * .77f, h * .66f);
        drawArtworkCard(c, cover, dp(26), false);
        drawCaption(c, w * .22f, h * .70f, w * .56f, Color.WHITE, withAlpha(Color.WHITE, 185));
    }

    private void drawVinyl(Canvas c, float w, float h) {
        float cx = w * .5f;
        float cy = h * .48f;
        float r = Math.min(w, h) * .34f;
        drawShadow(c, cx, h * .80f, w * .30f, dp(18));
        float rot = playing ? phase(2200) : phase(12000) * .2f;
        c.save();
        c.rotate((float) Math.toDegrees(rot), cx, cy);
        paint.setColor(Color.rgb(12,12,14));
        c.drawCircle(cx, cy, r, paint);
        for (int i = 0; i < 14; i++) {
            stroke.setStrokeWidth(dp(i % 4 == 0 ? 1.6f : 1f));
            stroke.setColor(i % 2 == 0 ? Color.rgb(44,44,50) : Color.rgb(26,26,31));
            c.drawCircle(cx, cy, r - dp(10 + i * 5), stroke);
        }
        RectF art = new RectF(cx - r * .17f, cy - r * .17f, cx + r * .17f, cy + r * .17f);
        drawArtworkCard(c, art, dp(8), false);
        c.restore();
        paint.setColor(mix(accent, Color.rgb(232,98,78), .35f));
        c.drawCircle(cx, cy, r * .18f, paint);
        stroke.setStrokeWidth(dp(6));
        stroke.setColor(Color.rgb(190,195,204));
        path.reset();
        float bx = w - dp(40), by = dp(58);
        path.moveTo(bx, by); path.quadTo(w - dp(52), dp(120), w * .72f, h * .30f);
        c.drawPath(path, stroke);
        paint.setColor(Color.rgb(98,101,107)); c.drawCircle(bx, by, dp(11), paint);
        // title on a floating chip
        paint.setColor(withAlpha(Color.BLACK, 125)); rounded(c, dp(24), h - dp(76), w - dp(24), h - dp(28), dp(18), paint);
        drawCaption(c, dp(38), h - dp(52), w - dp(76), Color.WHITE, withAlpha(Color.WHITE, 180));
    }

    private void drawCassette3D(Canvas c, float w, float h) {
        float cx = w * .5f, cy = h * .50f;
        float bodyW = w * .80f, bodyH = h * .56f;
        float yaw = dynamicYaw(.35f, 3600);
        float[] f = drawBox3D(c, cx, cy, bodyW, bodyH, dp(18), yaw, Color.rgb(223,214,191), Color.rgb(165,155,131), Color.rgb(119,111,92));
        float l=f[0], t=f[1], r=f[2], b=f[3];

        RectF sticker = new RectF(l + dp(18), t + dp(18), r - dp(18), t + dp(94));
        paint.setColor(Color.rgb(246,245,239)); rounded(c, sticker.left, sticker.top, sticker.right, sticker.bottom, dp(12), paint);
        paint.setColor(withAlpha(accent, 210)); rounded(c, sticker.left, sticker.bottom - dp(14), sticker.right, sticker.bottom - dp(4), dp(4), paint);

        RectF art = new RectF(sticker.left + dp(10), sticker.top + dp(10), sticker.left + dp(64), sticker.top + dp(64));
        drawArtworkCard(c, art, dp(10), true);
        drawObjectText(c, art.right + dp(10), sticker.top + dp(28), sticker.right - art.right - dp(22), Color.rgb(44,42,38), Color.rgb(92,90,86));

        paint.setColor(withAlpha(Color.BLACK, 24)); rounded(c, l + dp(28), t + dp(114), r - dp(28), t + dp(184), dp(14), paint);
        drawReel(c, l + (r-l)*.34f, t + dp(149), dp(27), playing ? phase(2200) : 0f, Color.rgb(235,234,228), Color.rgb(56,52,46));
        drawReel(c, l + (r-l)*.66f, t + dp(149), dp(27), playing ? -phase(2480) : 0f, Color.rgb(235,234,228), Color.rgb(56,52,46));
        paint.setColor(Color.rgb(82,78,70));
        for (int i=0;i<5;i++) rounded(c, l + dp(20+i*44), b - dp(18), l + dp(36+i*44), b - dp(12), dp(2), paint);
    }

    private void drawWalkman3D(Canvas c, float w, float h) {
        float cx = w * .5f, cy = h * .51f;
        float bodyW = w * .82f, bodyH = h * .72f;
        float yaw = dynamicYaw(.26f, 4200);
        float[] f = drawBox3D(c, cx, cy, bodyW, bodyH, dp(28), yaw, Color.rgb(118,127,141), Color.rgb(73,82,94), Color.rgb(150,159,173));
        float l=f[0], t=f[1], r=f[2], b=f[3];
        paint.setColor(accent); rounded(c, l + dp(18), t + dp(16), r - dp(18), t + dp(28), dp(5), paint);
        paint.setColor(withAlpha(Color.WHITE, 30)); rounded(c, l + dp(24), t + dp(42), r - dp(24), t + dp(206), dp(20), paint);

        RectF art = new RectF(l + dp(38), t + dp(60), r - dp(38), t + dp(180));
        drawArtworkCard(c, art, dp(16), true);
        paint.setColor(withAlpha(Color.BLACK, 70)); rounded(c, art.left, art.bottom - dp(38), art.right, art.bottom, dp(0), paint);
        drawObjectText(c, art.left + dp(10), art.bottom - dp(16), art.width() - dp(20), Color.WHITE, withAlpha(Color.WHITE, 180));

        paint.setColor(Color.rgb(250,250,252)); rounded(c, l + dp(28), b - dp(76), r - dp(28), b - dp(26), dp(16), paint);
        paint.setColor(Color.rgb(42,46,53));
        for (int i=0;i<5;i++) c.drawCircle(l + dp(46 + i*46), b - dp(12), dp(7), paint);
        paint.setColor(accent); c.drawCircle(cx, b - dp(12), dp(7), paint);
    }

    private void drawNesCart3D(Canvas c, float w, float h) {
        float cx=w*.5f, cy=h*.5f;
        float bodyW=w*.72f, bodyH=h*.64f;
        float yaw=dynamicYaw(.40f, 3600);
        float[] f=drawBox3D(c,cx,cy,bodyW,bodyH,dp(14),yaw,Color.rgb(79,81,87),Color.rgb(49,50,55),Color.rgb(35,36,40));
        float l=f[0], t=f[1], r=f[2], b=f[3];
        paint.setColor(Color.rgb(61,63,70));
        for(int i=0;i<7;i++) rounded(c,l+dp(14+i*26),t+dp(10),l+dp(30+i*26),t+dp(16),dp(2),paint);
        paint.setColor(Color.rgb(22,22,25)); rounded(c,l+dp(18),t+dp(28),r-dp(18),b-dp(28),dp(14),paint);
        RectF label = new RectF(l + dp(28), t + dp(42), r - dp(28), b - dp(48));
        paint.setColor(Color.rgb(228,230,235)); rounded(c, label.left, label.top, label.right, label.bottom, dp(12), paint);
        RectF art = new RectF(label.left + dp(12), label.top + dp(12), label.right - dp(12), label.top + dp(112));
        drawArtworkCard(c, art, dp(8), true);
        drawObjectText(c, label.left + dp(16), art.bottom + dp(26), label.width() - dp(32), Color.rgb(35,35,40), Color.rgb(98,102,108));
    }

    private void drawFloppy3D(Canvas c, float w, float h) {
        float cx=w*.5f, cy=h*.5f;
        float bodyW=w*.64f, bodyH=h*.70f;
        float yaw=dynamicYaw(.32f, 3800);
        float[] f = drawFloppyBody(c,cx,cy,bodyW,bodyH,yaw);
        float l=f[0], t=f[1], r=f[2], b=f[3];
        paint.setColor(Color.rgb(186,191,199)); rounded(c,l+dp(20),t+dp(18),r-dp(20),t+dp(70),dp(8),paint);
        paint.setColor(Color.rgb(245,245,243)); rounded(c,l+dp(28),t+dp(88),r-dp(28),b-dp(26),dp(10),paint);
        RectF art = new RectF(l+dp(40),t+dp(104),r-dp(40),t+dp(182));
        drawArtworkCard(c, art, dp(8), true);
        drawObjectText(c, l+dp(40), art.bottom + dp(22), r-l-dp(80), Color.rgb(42,44,50), Color.rgb(96,102,112));
    }

    private void drawGameboy2D(Canvas c, float w, float h) {
        float l=w*.19f,t=h*.10f,r=w*.81f,b=h*.92f;
        drawShadow(c,w*.5f,h*.94f,w*.28f,dp(14));
        paint.setColor(Color.rgb(205,211,190)); rounded(c,l,t,r,b,dp(28),paint);
        stroke.setStrokeWidth(dp(2)); stroke.setColor(Color.rgb(122,130,116)); rounded(c,l,t,r,b,dp(28),stroke);
        paint.setColor(Color.rgb(122,88,165)); rounded(c,l+dp(18),t+dp(24),r-dp(18),t+dp(30),dp(3),paint);
        RectF screen = new RectF(l+dp(26), t+dp(52), r-dp(26), t+dp(210));
        paint.setColor(Color.rgb(108,122,84)); rounded(c, screen.left, screen.top, screen.right, screen.bottom, dp(10), paint);
        RectF art = new RectF(screen.left + dp(18), screen.top + dp(16), screen.right - dp(18), screen.top + dp(98));
        drawArtworkCard(c, art, dp(6), true);
        drawObjectText(c, screen.left + dp(16), art.bottom + dp(20), screen.width() - dp(32), Color.rgb(28,36,24), Color.rgb(44,56,38));
        // controls
        paint.setColor(Color.rgb(92,94,101)); c.drawCircle(l+dp(58), b-dp(118), dp(18), paint); c.drawCircle(l+dp(58), b-dp(154), dp(18), paint);
        rounded(c,l+dp(40),b-dp(144),l+dp(76),b-dp(128),dp(4),paint); rounded(c,l+dp(50),b-dp(164),l+dp(66),b-dp(108),dp(4),paint);
        paint.setColor(withAlpha(accent,220)); c.drawCircle(r-dp(78), b-dp(136), dp(18), paint); c.drawCircle(r-dp(42), b-dp(116), dp(18), paint);
        paint.setColor(Color.rgb(120,124,114)); rounded(c,l+dp(96),b-dp(54),l+dp(144),b-dp(44),dp(4),paint); rounded(c,r-dp(142),b-dp(54),r-dp(94),b-dp(44),dp(4),paint);
    }

    private void drawGameboy3D(Canvas c, float w, float h) {
        float cx=w*.5f, cy=h*.52f;
        float bodyW=w*.66f, bodyH=h*.84f;
        float yaw=dynamicYaw(.24f, 4300);
        float[] f=drawBox3D(c,cx,cy,bodyW,bodyH,dp(26),yaw,Color.rgb(205,211,190),Color.rgb(154,160,141),Color.rgb(228,232,216));
        float l=f[0], t=f[1], r=f[2], b=f[3];
        paint.setColor(Color.rgb(122,88,165)); rounded(c,l+dp(18),t+dp(24),r-dp(18),t+dp(30),dp(3),paint);
        RectF screen = new RectF(l+dp(28), t+dp(58), r-dp(28), t+dp(230));
        paint.setColor(Color.rgb(108,122,84)); rounded(c, screen.left, screen.top, screen.right, screen.bottom, dp(10), paint);
        RectF art = new RectF(screen.left + dp(18), screen.top + dp(18), screen.right - dp(18), screen.top + dp(106));
        drawArtworkCard(c, art, dp(6), true);
        drawObjectText(c, screen.left + dp(16), art.bottom + dp(22), screen.width() - dp(32), Color.rgb(28,36,24), Color.rgb(44,56,38));
        paint.setColor(Color.rgb(92,94,101)); c.drawCircle(l+dp(64), b-dp(120), dp(18), paint); c.drawCircle(l+dp(64), b-dp(156), dp(18), paint);
        rounded(c,l+dp(46),b-dp(146),l+dp(82),b-dp(130),dp(4),paint); rounded(c,l+dp(56),b-dp(166),l+dp(72),b-dp(110),dp(4),paint);
        paint.setColor(withAlpha(accent,220)); c.drawCircle(r-dp(84), b-dp(136), dp(18), paint); c.drawCircle(r-dp(46), b-dp(114), dp(18), paint);
    }

    private float[] drawBox3D(Canvas c, float cx, float cy, float width, float height, float radius, float yaw,
                               int frontColor, int sideColor, int topColor) {
        float[] face = frontFace(cx, cy, width, height, yaw);
        float l=face[0], t=face[1], r=face[2], b=face[3], d=face[4];
        drawShadow(c, cx, b + dp(16), width * .40f, dp(18));
        if (yaw > 0.02f) {
            path.reset(); path.moveTo(r, t + dp(12)); path.lineTo(r + d, t + dp(2)); path.lineTo(r + d, b - dp(10)); path.lineTo(r, b); path.close();
            paint.setColor(sideColor); c.drawPath(path, paint);
        } else if (yaw < -0.02f) {
            path.reset(); path.moveTo(l, t + dp(12)); path.lineTo(l - d, t + dp(2)); path.lineTo(l - d, b - dp(10)); path.lineTo(l, b); path.close();
            paint.setColor(sideColor); c.drawPath(path, paint);
        }
        path.reset(); path.moveTo(l + dp(10), t); path.lineTo(r - dp(10), t);
        if (yaw > 0) { path.lineTo(r - dp(10) + d, t - d * .55f); path.lineTo(l + dp(10) + d, t - d * .55f); }
        else { path.lineTo(r - dp(10) - d, t - d * .55f); path.lineTo(l + dp(10) - d, t - d * .55f); }
        path.close(); paint.setColor(topColor); c.drawPath(path, paint);
        paint.setColor(frontColor); rounded(c,l,t,r,b,radius,paint);
        stroke.setStrokeWidth(dp(2f)); stroke.setColor(withAlpha(Color.BLACK, 65)); rounded(c,l,t,r,b,radius,stroke);
        return face;
    }

    private float[] drawFloppyBody(Canvas c, float cx, float cy, float width, float height, float yaw) {
        float[] face = frontFace(cx, cy, width, height, yaw);
        float l=face[0], t=face[1], r=face[2], b=face[3], d=face[4];
        drawShadow(c, cx, b + dp(14), width * .38f, dp(16));
        path.reset(); path.moveTo(l + dp(18), t); path.lineTo(r - dp(38), t); path.lineTo(r, t + dp(38)); path.lineTo(r, b); path.lineTo(l, b); path.lineTo(l, t + dp(18)); path.close();
        paint.setColor(Color.rgb(46,47,55)); c.drawPath(path, paint);
        if (yaw > 0.02f) {
            Path side = new Path(); side.moveTo(r, t + dp(38)); side.lineTo(r + d, t + dp(28)); side.lineTo(r + d, b - dp(10)); side.lineTo(r, b); side.close();
            paint.setColor(Color.rgb(28,29,33)); c.drawPath(side, paint);
        } else if (yaw < -0.02f) {
            Path side = new Path(); side.moveTo(l, t + dp(18)); side.lineTo(l - d, t + dp(8)); side.lineTo(l - d, b - dp(10)); side.lineTo(l, b); side.close();
            paint.setColor(Color.rgb(28,29,33)); c.drawPath(side, paint);
        }
        stroke.setStrokeWidth(dp(2)); stroke.setColor(withAlpha(Color.BLACK,80)); c.drawPath(path, stroke);
        return face;
    }

    private float[] frontFace(float cx, float cy, float width, float height, float yaw) {
        float abs=Math.abs((float)Math.sin(yaw));
        float visibleWidth=width*(0.84f + .16f*(1f-abs));
        float depth=dp(28)*abs;
        float shift=yaw<0? depth*.50f : -depth*.50f;
        float l=cx-visibleWidth/2f+shift, r=cx+visibleWidth/2f+shift, t=cy-height/2f, b=cy+height/2f;
        return new float[]{l,t,r,b,depth};
    }

    private void drawArtworkCard(Canvas c, RectF area, float radius, boolean drawBorder) {
        if (artworkBitmap != null) {
            c.save();
            path.reset();
            path.addRoundRect(area, radius, radius, Path.Direction.CW);
            c.clipPath(path);
            c.drawBitmap(artworkBitmap, null, area, paint);
            c.restore();
        } else {
            paint.setShader(new LinearGradient(area.left, area.top, area.right, area.bottom,
                    mix(accent, Color.WHITE, .12f), mix(accent, Color.BLACK, .15f), Shader.TileMode.CLAMP));
            c.drawRoundRect(area, radius, radius, paint);
            paint.setShader(null);
            textPaint.setColor(withAlpha(Color.WHITE, 235));
            textPaint.setFakeBoldText(true);
            textPaint.setTextSize(Math.min(area.width(), area.height()) * .15f);
            c.drawText("♪", area.left + area.width() * .38f, area.top + area.height() * .60f, textPaint);
            textPaint.setFakeBoldText(false);
        }
        if (drawBorder) {
            stroke.setStrokeWidth(dp(1.4f));
            stroke.setColor(withAlpha(Color.BLACK, 60));
            c.drawRoundRect(area, radius, radius, stroke);
        }
    }

    private void drawCaption(Canvas c, float x, float y, float width, int titleColor, int subColor) {
        drawTextLine(c, safeTitle(), x, y, width, dp(16), true, titleColor);
        drawTextLine(c, safeArtist(), x, y + dp(19), width, dp(11), false, subColor);
    }

    private void drawObjectText(Canvas c, float x, float y, float width, int titleColor, int subColor) {
        drawTextLine(c, safeTitle(), x, y, width, dp(15), true, titleColor);
        drawTextLine(c, safeArtist(), x, y + dp(18), width, dp(11), false, subColor);
    }

    private void drawTextLine(Canvas c, String text, float x, float y, float width, float size, boolean bold, int color) {
        textPaint.setTextSize(size);
        textPaint.setFakeBoldText(bold);
        textPaint.setColor(color);
        drawEllipsized(c, text, x, y, width, textPaint);
    }

    private void drawReel(Canvas c, float cx, float cy, float r, float rotation, int outerColor, int innerColor) {
        paint.setColor(outerColor); c.drawCircle(cx, cy, r, paint);
        paint.setColor(innerColor); c.drawCircle(cx, cy, r * .62f, paint);
        paint.setColor(withAlpha(Color.WHITE, 220));
        c.save(); c.rotate((float) Math.toDegrees(rotation), cx, cy);
        for (int i=0;i<6;i++) {
            double a=i*Math.PI/3d; c.drawCircle(cx + (float)Math.cos(a)*r*.38f, cy + (float)Math.sin(a)*r*.38f, r*.10f, paint);
        }
        c.restore(); paint.setColor(withAlpha(Color.BLACK, 80)); c.drawCircle(cx, cy, r * .12f, paint);
    }

    private void drawShadow(Canvas c, float cx, float cy, float rx, float ry) {
        paint.setColor(withAlpha(Color.BLACK, 74));
        rect.set(cx-rx, cy-ry, cx+rx, cy+ry);
        c.drawOval(rect, paint);
    }

    private float dynamicYaw(float maxRad, long period) {
        if (animationMultiplier <= 0f) return 0f;
        float p = phase(period);
        float base = playing ? (float) Math.sin(p) : (float) Math.sin(p) * .28f;
        return base * maxRad;
    }

    private float phase(long periodMs) {
        if (animationMultiplier <= 0f) return 0f;
        double p = (SystemClock.uptimeMillis() % periodMs) / (double) periodMs;
        return (float) (p * Math.PI * 2d * animationMultiplier);
    }

    private String safeTitle() { return title == null || title.isEmpty() ? "Now Playing" : title; }
    private String safeArtist() { return artist == null || artist.isEmpty() ? "Tu artista" : artist; }

    private void drawEllipsized(Canvas c, String value, float x, float y, float maxWidth, Paint p) {
        String text = value == null ? "" : value;
        while (text.length() > 1 && p.measureText(text) > maxWidth) text = text.substring(0, text.length() - 1);
        if (!text.equals(value) && text.length() > 2) text = text.substring(0, text.length() - 2) + "…";
        c.drawText(text, x, y, p);
    }

    private int mix(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        return Color.rgb((int)(Color.red(a)+(Color.red(b)-Color.red(a))*t), (int)(Color.green(a)+(Color.green(b)-Color.green(a))*t), (int)(Color.blue(a)+(Color.blue(b)-Color.blue(a))*t));
    }
    private int withAlpha(int color, int alpha) { return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color)); }
    private float dp(float value) { return value * getResources().getDisplayMetrics().density; }
    private void rounded(Canvas c, float l, float t, float r, float b, float rad, Paint p) { c.drawRoundRect(new RectF(l,t,r,b), rad, rad, p); }
}
