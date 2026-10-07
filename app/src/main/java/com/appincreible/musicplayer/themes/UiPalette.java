package com.appincreible.musicplayer.themes;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.LruCache;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.palette.graphics.Palette;

import com.appincreible.musicplayer.R;
import com.appincreible.musicplayer.power.PowerSaverManager;
import com.google.android.material.color.MaterialColors;

/**
 * Small, UI-only palette helper. Callers should pass a stable cache key such as a
 * media id, artwork URI or background id so Palette work is performed once per source.
 */
public final class UiPalette {

    public static final double MIN_TEXT_CONTRAST = 4.5d;
    private static final int CACHE_SIZE = 64;
    private static final LruCache<String, UiPalette> CACHE = new LruCache<>(CACHE_SIZE);

    @ColorInt public final int accent;
    @ColorInt public final int surface;
    @ColorInt public final int onSurface;
    @ColorInt public final int scrim;

    private UiPalette(@ColorInt int accent, @ColorInt int surface,
                      @ColorInt int onSurface, @ColorInt int scrim) {
        this.accent = accent;
        this.surface = surface;
        this.onSurface = onSurface;
        this.scrim = scrim;
    }

    @NonNull
    public static UiPalette fromBitmap(@NonNull Context context,
                                       @NonNull String cacheKey,
                                       @Nullable Bitmap bitmap) {
        UiPalette cached = CACHE.get(cacheKey);
        if (cached != null) return cached;

        UiPalette fallback = fallback(context);
        if (PowerSaverManager.isActive(context)) {
            // Super saver never starts Palette work for a cache miss. Use the last cached value
            // when available, otherwise a fixed/theme-safe fallback until normal mode returns.
            return fallback;
        }
        if (bitmap == null || bitmap.isRecycled() || bitmap.getWidth() <= 0 || bitmap.getHeight() <= 0) {
            // Do not cache a placeholder: the same key may receive the real artwork later.
            return fallback;
        }

        try {
            Palette palette = Palette.from(bitmap).maximumColorCount(16).generate();
            int surface = palette.getMutedColor(palette.getDominantColor(fallback.surface));
            int accent = palette.getVibrantColor(
                    palette.getLightVibrantColor(
                            palette.getDarkVibrantColor(fallback.accent)));

            Readability readability = resolveText(surface, fallback.onSurface);
            UiPalette result = new UiPalette(accent, surface,
                    readability.textColor, readability.scrimColor);
            CACHE.put(cacheKey, result);
            return result;
        } catch (RuntimeException ignored) {
            CACHE.put(cacheKey, fallback);
            return fallback;
        }
    }

    @NonNull
    public static UiPalette fallback(@NonNull Context context) {
        int accentFallback = ContextCompat.getColor(context, R.color.ui_accent_light);
        int surfaceFallback = ContextCompat.getColor(context, R.color.ui_surface_light);
        int accent = MaterialColors.getColor(context, com.google.android.material.R.attr.colorPrimary, accentFallback);
        int surface = MaterialColors.getColor(context, com.google.android.material.R.attr.colorSurface, surfaceFallback);
        int preferredText = MaterialColors.getColor(
                context, com.google.android.material.R.attr.colorOnSurface, readableTextColor(surface));
        Readability readability = resolveText(surface, preferredText);
        return new UiPalette(accent, surface, readability.textColor, readability.scrimColor);
    }

    /** Returns black or white, choosing the one with the highest WCAG contrast. */
    @ColorInt
    public static int readableTextColor(@ColorInt int backgroundColor) {
        int opaqueBackground = opaque(backgroundColor);
        double whiteContrast = ColorUtils.calculateContrast(Color.WHITE, opaqueBackground);
        double blackContrast = ColorUtils.calculateContrast(Color.BLACK, opaqueBackground);
        return whiteContrast >= blackContrast ? Color.WHITE : Color.BLACK;
    }

    /**
     * Keeps the preferred text color when possible. If it misses 4.5:1, this method
     * first finds a light/dark scrim that restores AA contrast; if that is not enough,
     * it falls back to black or white text.
     */
    @NonNull
    public static Readability resolveText(@ColorInt int backgroundColor,
                                          @ColorInt int preferredTextColor) {
        int background = opaque(backgroundColor);
        int preferred = opaque(preferredTextColor);
        if (ColorUtils.calculateContrast(preferred, background) >= MIN_TEXT_CONTRAST) {
            return new Readability(preferred, Color.TRANSPARENT);
        }

        boolean lightText = ColorUtils.calculateLuminance(preferred) >= 0.5d;
        int scrimBase = lightText ? Color.BLACK : Color.WHITE;
        for (int alpha = 24; alpha <= 184; alpha += 8) {
            int scrim = ColorUtils.setAlphaComponent(scrimBase, alpha);
            int composited = ColorUtils.compositeColors(scrim, background);
            if (ColorUtils.calculateContrast(preferred, composited) >= MIN_TEXT_CONTRAST) {
                return new Readability(preferred, scrim);
            }
        }

        return new Readability(readableTextColor(background), Color.TRANSPARENT);
    }

    public static void clearMemoryCache() {
        CACHE.evictAll();
    }

    @ColorInt
    private static int opaque(@ColorInt int color) {
        return Color.rgb(Color.red(color), Color.green(color), Color.blue(color));
    }

    public static final class Readability {
        @ColorInt public final int textColor;
        @ColorInt public final int scrimColor;

        private Readability(@ColorInt int textColor, @ColorInt int scrimColor) {
            this.textColor = textColor;
            this.scrimColor = scrimColor;
        }
    }
}
