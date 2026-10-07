package com.appincreible.musicplayer.themes;

import android.content.Context;
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

        if (transparent) {
            TextChoice choice = chooseTextAndScrim(backgrounds);
            int effective = opaque(ColorUtils.compositeColors(choice.scrim, representative));
            int secondary = mutedText(choice.text, backgrounds, choice.scrim);
            int accent = readableAcross(requestedAccent, backgrounds, choice.scrim)
                    ? requestedAccent : choice.text;
            return new Appearance(choice.scrim, effective, choice.text, secondary, accent, true);
        }

        boolean light = ColorUtils.calculateLuminance(representative) > 0.55d;
        int base = light ? Color.WHITE : Color.rgb(12, 14, 20);
        int container = ColorUtils.setAlphaComponent(base, light ? 168 : 152);
        int effective = opaque(ColorUtils.compositeColors(container, representative));
        int primary = UiPalette.readableTextColor(effective);
        int secondary = mutedText(primary, new int[]{effective}, Color.TRANSPARENT);
        int accent = ColorUtils.calculateContrast(requestedAccent, effective) >= 3.0d
                ? requestedAccent : primary;
        return new Appearance(container, effective, primary, secondary, accent, false);
    }

    private static int[] backgroundSamples(Context context, int fallbackSurface, int accent) {
        String scene = AppPreferences.getAppBackground(context);
        if (AppPreferences.BACKGROUND_NONE.equals(scene)) return new int[]{opaque(fallbackSurface)};
        if (AppPreferences.BACKGROUND_DARK.equals(scene)) return new int[]{Color.rgb(10, 12, 18)};

        String mode = AppPreferences.getAppBackgroundColorMode(context);
        int c1 = AppPreferences.getAppBackgroundColor1(context);
        int c2 = AppPreferences.getAppBackgroundColor2(context);
        int c3 = AppPreferences.getAppBackgroundColor3(context);
        if (AppPreferences.APP_BACKGROUND_COLOR_SOLID.equals(mode)) return new int[]{opaque(c1)};
        if (AppPreferences.APP_BACKGROUND_COLOR_GRADIENT_2.equals(mode)) return new int[]{opaque(c1), opaque(c2)};
        if (AppPreferences.APP_BACKGROUND_COLOR_GRADIENT_3.equals(mode)
                || AppPreferences.APP_BACKGROUND_COLOR_MULTICOLOR.equals(mode)) {
            return new int[]{opaque(c1), opaque(c2), opaque(c3)};
        }
        return new int[]{opaque(accent)};
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

    private static final class TextChoice {
        final int text;
        final int scrim;

        TextChoice(int text, int scrim) {
            this.text = text;
            this.scrim = scrim;
        }
    }
}
