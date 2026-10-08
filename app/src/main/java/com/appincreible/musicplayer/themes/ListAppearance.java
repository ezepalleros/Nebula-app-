package com.appincreible.musicplayer.themes;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;

import androidx.core.graphics.ColorUtils;

import java.util.ArrayList;
import java.util.List;

/** Shared appearance policy for every scrollable media list. */
public final class ListAppearance {
    public static final String KEY_LIST_TRANSPARENT = "list_transparent";

    public interface Listener {
        void onListAppearanceChanged();
    }

    public static final class Appearance {
        public final int containerColor;
        public final int effectiveSurface;
        public final int primaryText;
        public final int secondaryText;
        public final int accent;
        public final boolean transparent;

        Appearance(int containerColor, int effectiveSurface, int primaryText,
                   int secondaryText, int accent, boolean transparent) {
            this.containerColor = containerColor;
            this.effectiveSurface = effectiveSurface;
            this.primaryText = primaryText;
            this.secondaryText = secondaryText;
            this.accent = accent;
            this.transparent = transparent;
        }
    }

    private static final List<Listener> LISTENERS = new ArrayList<>();

    private ListAppearance() { }

    public static boolean isTransparent(Context context) {
        return AppPreferences.prefs(context).getBoolean(KEY_LIST_TRANSPARENT, false);
    }

    public static void setTransparent(Context context, boolean transparent) {
        AppPreferences.prefs(context).edit().putBoolean(KEY_LIST_TRANSPARENT, transparent).commit();
        notifyChanged();
    }

    public static synchronized void addListener(Listener listener) {
        if (listener != null && !LISTENERS.contains(listener)) LISTENERS.add(listener);
    }

    public static synchronized void removeListener(Listener listener) {
        LISTENERS.remove(listener);
    }

    private static void notifyChanged() {
        List<Listener> snapshot;
        synchronized (ListAppearance.class) {
            snapshot = new ArrayList<>(LISTENERS);
        }
        for (Listener listener : snapshot) listener.onListAppearanceChanged();
    }

    public static Appearance resolve(Context context) {
        UiPalette fallback = UiPalette.fallback(context);
        int requestedAccent = AppPreferences.getPlayerAccentColor(context);
        int[] backgrounds = backgroundSamples(context, fallback.surface, requestedAccent);
        int representative = opaque(average(backgrounds));
        boolean transparent = isTransparent(context);

        // Light mode is intentionally a light UI, independent from the decorative app background.
        // Custom backgrounds remain visible through translucent surfaces, but content never flips to
        // dark cards/white text just because a saved background color happens to be dark.
        if (!isDarkTheme(context)) {
            int containerAlpha = transparent ? 206 : 244;
            int container = ColorUtils.setAlphaComponent(Color.WHITE, containerAlpha);
            int effective = opaque(ColorUtils.compositeColors(container, representative));
            int primary = Color.rgb(24, 27, 34);
            int secondary = Color.rgb(88, 95, 108);
            int accent = ensureAccentOnLightSurface(requestedAccent, effective);
            return new Appearance(container, effective, primary, secondary, accent, transparent);
        }

        if (transparent) {
            TextChoice choice = chooseTextAndScrim(backgrounds);
            int effective = opaque(ColorUtils.compositeColors(choice.scrim, representative));
            int secondary = mutedText(choice.text, backgrounds, choice.scrim);
            int accent = readableAcross(requestedAccent, backgrounds, choice.scrim)
                    ? requestedAccent : choice.text;
            return new Appearance(choice.scrim, effective, choice.text, secondary, accent, true);
        }

        int base = Color.rgb(12, 14, 20);
        int container = ColorUtils.setAlphaComponent(base, 152);
        int effective = opaque(ColorUtils.compositeColors(container, representative));
        int primary = UiPalette.readableTextColor(effective);
        int secondary = mutedText(primary, new int[]{effective}, Color.TRANSPARENT);
        int accent = ColorUtils.calculateContrast(requestedAccent, effective) >= 3.0d
                ? requestedAccent : primary;
        return new Appearance(container, effective, primary, secondary, accent, false);
    }

    /** Representative color of the background as it is actually rendered on screen. */
    public static int resolveRenderedBackground(Context context) {
        UiPalette fallback = UiPalette.fallback(context);
        int accent = AppPreferences.getPlayerAccentColor(context);
        return opaque(average(backgroundSamples(context, fallback.surface, accent)));
    }

    private static int ensureAccentOnLightSurface(int accent, int surface) {
        int candidate = opaque(accent);
        if (ColorUtils.calculateContrast(candidate, surface) >= 3.0d) return candidate;
        for (int step = 1; step <= 10; step++) {
            candidate = ColorUtils.blendARGB(opaque(accent), Color.rgb(30, 32, 38), step / 10f);
            if (ColorUtils.calculateContrast(candidate, surface) >= 3.0d) return candidate;
        }
        return Color.rgb(48, 51, 59);
    }

    private static int[] backgroundSamples(Context context, int fallbackSurface, int accent) {
        String scene = AppPreferences.getAppBackground(context);
        boolean darkTheme = isDarkTheme(context);
        if (AppPreferences.BACKGROUND_NONE.equals(scene)) {
            return new int[]{opaque(fallbackSurface)};
        }

        String mode = AppPreferences.getAppBackgroundColorMode(context);
        int c1 = AppPreferences.getAppBackgroundColor1(context);
        int c2 = AppPreferences.getAppBackgroundColor2(context);
        int c3 = AppPreferences.getAppBackgroundColor3(context);

        // BACKGROUND_DARK controls the decorative scene, not whether the whole UI should become dark.
        // PlayerBackgroundView still draws a light base when the actual app theme is light.
        if (AppPreferences.APP_BACKGROUND_COLOR_SOLID.equals(mode)) {
            return new int[]{renderSolidSample(c1, darkTheme)};
        }
        if (AppPreferences.APP_BACKGROUND_COLOR_GRADIENT_2.equals(mode)) {
            return new int[]{renderGradient2Start(c1, darkTheme), renderGradient2End(c2, darkTheme)};
        }
        if (AppPreferences.APP_BACKGROUND_COLOR_GRADIENT_3.equals(mode)) {
            return new int[]{renderGradient3Start(c1, darkTheme), renderGradient3Middle(c2, darkTheme), renderGradient3End(c3, darkTheme)};
        }
        if (AppPreferences.APP_BACKGROUND_COLOR_MULTICOLOR.equals(mode)) {
            return new int[]{renderGradient3Start(c1, darkTheme), renderGradient3Middle(c2, darkTheme), renderGradient3End(c3, darkTheme)};
        }
        if (AppPreferences.PLAYER_BACKGROUND_COLOR_RAINBOW.equals(mode)) {
            return rainbowSamples(darkTheme);
        }
        return new int[]{renderedAccentSample(accent, darkTheme)};
    }

    private static TextChoice chooseTextAndScrim(int[] backgrounds) {
        int whiteAlpha = requiredScrimAlpha(Color.WHITE, Color.BLACK, backgrounds);
        int blackAlpha = requiredScrimAlpha(Color.BLACK, Color.WHITE, backgrounds);
        if (whiteAlpha <= blackAlpha) {
            return new TextChoice(Color.WHITE, ColorUtils.setAlphaComponent(Color.BLACK, whiteAlpha));
        }
        return new TextChoice(Color.BLACK, ColorUtils.setAlphaComponent(Color.WHITE, blackAlpha));
    }

    private static int requiredScrimAlpha(int text, int scrimBase, int[] backgrounds) {
        for (int alpha = 0; alpha <= 255; alpha++) {
            int scrim = ColorUtils.setAlphaComponent(scrimBase, alpha);
            boolean valid = true;
            for (int background : backgrounds) {
                int composited = opaque(ColorUtils.compositeColors(scrim, opaque(background)));
                if (ColorUtils.calculateContrast(text, composited) < UiPalette.MIN_TEXT_CONTRAST) {
                    valid = false;
                    break;
                }
            }
            if (valid) return alpha;
        }
        return 255;
    }


    private static boolean readableAcross(int color, int[] backgrounds, int scrim) {
        for (int background : backgrounds) {
            int effective = opaque(ColorUtils.compositeColors(scrim, opaque(background)));
            if (ColorUtils.calculateContrast(color, effective) < UiPalette.MIN_TEXT_CONTRAST) return false;
        }
        return true;
    }

    private static int mutedText(int primary, int[] backgrounds, int scrim) {
        int representative = average(backgrounds);
        int effectiveRepresentative = opaque(ColorUtils.compositeColors(scrim, opaque(representative)));
        int best = primary;
        for (int step = 1; step <= 7; step++) {
            float amount = step * 0.05f;
            int candidate = ColorUtils.blendARGB(primary, effectiveRepresentative, amount);
            boolean valid = true;
            for (int background : backgrounds) {
                int effective = opaque(ColorUtils.compositeColors(scrim, opaque(background)));
                if (ColorUtils.calculateContrast(candidate, effective) < UiPalette.MIN_TEXT_CONTRAST) {
                    valid = false;
                    break;
                }
            }
            if (!valid) break;
            best = candidate;
        }
        return best;
    }


    private static int opaque(int color) {
        return Color.rgb(Color.red(color), Color.green(color), Color.blue(color));
    }

    private static int average(int[] colors) {
        if (colors == null || colors.length == 0) return Color.BLACK;
        int result = colors[0];
        for (int i = 1; i < colors.length; i++) {
            result = ColorUtils.blendARGB(result, colors[i], 1f / (i + 1f));
        }
        return result;
    }

    private static int renderedBackgroundSample(int rawColor, boolean darkTheme) {
        return darkTheme ? opaque(rawColor) : mix(Color.rgb(248, 249, 252), opaque(rawColor), 0.12f);
    }

    private static int renderedAccentSample(int accent, boolean darkTheme) {
        int neutralTop = darkTheme ? Color.rgb(9, 12, 20) : Color.rgb(246, 247, 251);
        return darkTheme ? mix(neutralTop, accent, 0.12f) : mix(neutralTop, accent, 0.07f);
    }

    private static int renderSolidSample(int color, boolean darkTheme) {
        return darkTheme ? mix(Color.BLACK, opaque(color), 0.28f)
                : mix(Color.WHITE, opaque(color), 0.18f);
    }

    private static int renderGradient2Start(int color, boolean darkTheme) {
        int neutralTop = darkTheme ? Color.rgb(9, 12, 20) : Color.rgb(246, 247, 251);
        return darkTheme ? mix(neutralTop, opaque(color), 0.42f)
                : mix(neutralTop, opaque(color), 0.26f);
    }

    private static int renderGradient2End(int color, boolean darkTheme) {
        int neutralBottom = darkTheme ? Color.rgb(4, 7, 13) : Color.rgb(250, 250, 252);
        return darkTheme ? mix(neutralBottom, opaque(color), 0.40f)
                : mix(neutralBottom, opaque(color), 0.24f);
    }

    private static int renderGradient3Start(int color, boolean darkTheme) {
        int neutralTop = darkTheme ? Color.rgb(9, 12, 20) : Color.rgb(246, 247, 251);
        return darkTheme ? mix(neutralTop, opaque(color), 0.40f)
                : mix(neutralTop, opaque(color), 0.24f);
    }

    private static int renderGradient3Middle(int color, boolean darkTheme) {
        int neutralTop = darkTheme ? Color.rgb(9, 12, 20) : Color.rgb(246, 247, 251);
        return darkTheme ? mix(neutralTop, opaque(color), 0.36f)
                : mix(neutralTop, opaque(color), 0.22f);
    }

    private static int renderGradient3End(int color, boolean darkTheme) {
        int neutralBottom = darkTheme ? Color.rgb(4, 7, 13) : Color.rgb(250, 250, 252);
        return darkTheme ? mix(neutralBottom, opaque(color), 0.40f)
                : mix(neutralBottom, opaque(color), 0.24f);
    }

    private static int[] rainbowSamples(boolean darkTheme) {
        int[] samples = new int[6];
        int neutralTop = darkTheme ? Color.rgb(9, 12, 20) : Color.rgb(246, 247, 251);
        for (int i = 0; i < samples.length; i++) {
            int vivid = Color.HSVToColor(new float[]{i * 60f, .86f, .92f});
            samples[i] = darkTheme ? mix(neutralTop, vivid, 0.44f)
                    : mix(neutralTop, vivid, 0.24f);
        }
        return samples;
    }

    private static boolean isDarkTheme(Context context) {
        int mask = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return mask == Configuration.UI_MODE_NIGHT_YES;
    }

    private static int mix(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        return Color.rgb(
                Math.round(Color.red(a) + (Color.red(b) - Color.red(a)) * t),
                Math.round(Color.green(a) + (Color.green(b) - Color.green(a)) * t),
                Math.round(Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t));
    }

    private static final class TextChoice {
        final int text;
        final int scrim;

        TextChoice(int text, int scrim) {
            this.text = text;
            this.scrim = scrim;
        }
    }
}
