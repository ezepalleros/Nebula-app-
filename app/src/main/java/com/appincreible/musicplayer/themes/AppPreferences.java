package com.appincreible.musicplayer.themes;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;

import androidx.appcompat.app.AppCompatDelegate;

import com.appincreible.musicplayer.power.PowerSaverManager;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class AppPreferences {

    public static final String PREFS = "app_preferences";
    public static final String KEY_PLAYLIST_ENTRY_ORDER = "playlist_entry_order";
    private static final String KEY_PLAYLIST_ARTWORK_PREFIX = "playlist_artwork:";
    public static final String KEY_THEME_MODE = "theme_mode";
    public static final String THEME_SYSTEM = "system";
    public static final String THEME_LIGHT = "light";
    public static final String THEME_DARK = "dark";

    public static final String KEY_SHOW_MINI_SUBTITLE = "show_mini_subtitle";
    public static final String KEY_MINI_PLAYER_USE_PLAYER_BACKGROUND = "mini_player_use_player_background";
    public static final String KEY_SHOW_RADIO_STATION_COUNT = "show_radio_station_count";
    public static final String KEY_LARGE_ARTWORK = "large_artwork";
    public static final String KEY_CENTER_PLAYER_INFO = "center_player_info";

    public static final String KEY_SHOW_PLAYER_BACK_BUTTON = "show_player_back_button";
    public static final String KEY_SHOW_PLAYER_HEADER_LABEL = "show_player_header_label";
    public static final String KEY_SHOW_PLAYER_MUSIC_BUTTON = "show_player_music_button";
    public static final String KEY_SHOW_PLAYER_ARTWORK = "show_player_artwork";
    public static final String KEY_SHOW_PLAYER_PROGRESS_LINE = "show_player_progress_line";
    public static final String KEY_PLAYER_PROGRESS_STYLE = "player_progress_style";
    public static final String KEY_PLAYER_PROGRESS_EMOJI = "player_progress_emoji";
    public static final String KEY_SHOW_PLAYER_WAVES = "show_player_waves";
    public static final String KEY_SHOW_PLAYER_TITLE = "show_player_title";
    public static final String KEY_SHOW_PLAYER_ARTIST = "show_player_artist";
    public static final String KEY_SHOW_PLAYER_ALBUM = "show_player_album";
    public static final String KEY_SHOW_PLAYER_SHUFFLE = "show_player_shuffle";
    public static final String KEY_SHOW_PLAYER_QUEUE = "show_player_queue";
    public static final String KEY_SHOW_PLAYER_REPEAT = "show_player_repeat";
    public static final String KEY_SHOW_PLAYER_MODE_TEXT = "show_player_mode_text";

    public static final String KEY_PLAYER_STYLE = "player_style";
    public static final String STYLE_CLASSIC = "classic";
    public static final String STYLE_VINYL = "vinyl";
    public static final String STYLE_PIXEL = "pixel";
    public static final String STYLE_CASSETTE = "cassette";
    public static final String STYLE_WALKMAN = "walkman";
    public static final String STYLE_RETRO = "retro";
    public static final String STYLE_NEON = "neon";
    public static final String STYLE_GLASS = "glass";
    public static final String STYLE_MINIMAL = "minimal";
    public static final String STYLE_GAMEBOY_3D = "gameboy3d";
    public static final String STYLE_SPOTIFY_2D = "spotify2d";
    public static final String STYLE_EQUALIZER_2D = "equalizer2d";
    public static final String STYLE_RING = "ring";

    public static final String PROGRESS_STYLE_CLASSIC = "classic";
    public static final String PROGRESS_STYLE_WAVE = "wave";
    public static final String PROGRESS_STYLE_DOTS = "dots";
    public static final String PROGRESS_STYLE_CAPSULE = "capsule";
    public static final String PROGRESS_STYLE_EMOJI = "emoji";

    public static final String KEY_PLAYER_ACCENT = "player_accent";
    public static final String ACCENT_VIOLET = "violet";
    public static final String ACCENT_BLUE = "blue";
    public static final String ACCENT_CYAN = "cyan";
    public static final String ACCENT_TEAL = "teal";
    public static final String ACCENT_GREEN = "green";
    public static final String ACCENT_LIME = "lime";
    public static final String ACCENT_YELLOW = "yellow";
    public static final String ACCENT_ORANGE = "orange";
    public static final String ACCENT_RED = "red";
    public static final String ACCENT_PINK = "pink";
    public static final String KEY_USE_CUSTOM_ACCENT = "use_custom_accent";
    public static final String KEY_CUSTOM_ACCENT_COLOR = "custom_accent_color";

    public static final String KEY_ARTWORK_SHAPE = "artwork_shape";
    public static final String SHAPE_ROUNDED = "rounded";
    public static final String SHAPE_SQUARE = "square";
    public static final String SHAPE_CIRCLE = "circle";

    public static final String KEY_PLAYER_ANIMATION = "player_animation";
    public static final String ANIMATION_OFF = "off";
    public static final String ANIMATION_SOFT = "soft";
    public static final String ANIMATION_LIVELY = "lively";

    public static final String KEY_PLAYER_BACKGROUND = "player_background";
    public static final String KEY_PLAYER_BACKGROUND_COLOR_MODE = "player_background_color_mode";
    public static final String KEY_PLAYER_BACKGROUND_COLOR_1 = "player_background_color_1";
    public static final String KEY_PLAYER_BACKGROUND_COLOR_2 = "player_background_color_2";
    public static final String KEY_PLAYER_BACKGROUND_COLOR_3 = "player_background_color_3";
    public static final String KEY_APP_BACKGROUND = "app_background";
    public static final String KEY_APP_BACKGROUND_COLOR_MODE = "app_background_color_mode";
    public static final String KEY_APP_BACKGROUND_COLOR_1 = "app_background_color_1";
    public static final String KEY_APP_BACKGROUND_COLOR_2 = "app_background_color_2";
    public static final String KEY_APP_BACKGROUND_COLOR_3 = "app_background_color_3";
    public static final String APP_BACKGROUND_COLOR_ACCENT = "accent";
    public static final String APP_BACKGROUND_COLOR_SOLID = "solid";
    public static final String APP_BACKGROUND_COLOR_GRADIENT_2 = "gradient2";
    public static final String APP_BACKGROUND_COLOR_GRADIENT_3 = "gradient3";
    public static final String APP_BACKGROUND_COLOR_MULTICOLOR = "multicolor";
    public static final String PLAYER_BACKGROUND_COLOR_RAINBOW = "player_rainbow";
    public static final String PLAYER_BACKGROUND_COLOR_ARTWORK = "player_artwork";
    public static final String BACKGROUND_AURORA = "aurora";
    public static final String BACKGROUND_WAVES = "waves";
    public static final String BACKGROUND_GRID = "grid";
    public static final String BACKGROUND_PULSE = "pulse";
    public static final String BACKGROUND_DARK = "dark";
    public static final String BACKGROUND_NONE = "none";

    public static final String KEY_PLAYER_TYPOGRAPHY = "player_typography";
    public static final String TYPOGRAPHY_MODERN = "modern";
    public static final String TYPOGRAPHY_ROUNDED = "rounded";
    public static final String TYPOGRAPHY_SERIF = "serif";
    public static final String TYPOGRAPHY_MONO = "mono";
    public static final String TYPOGRAPHY_CONDENSED = "condensed";

    public static final String KEY_MUSIC_TABS_POSITION = "music_tabs_position";
    public static final String TABS_TOP = "top";
    public static final String TABS_BOTTOM = "bottom";

    public static final String KEY_LIBRARY_GROUPING_MODE = "library_grouping_mode";
    public static final String KEY_HIDE_WHATSAPP_AUDIO = "hide_whatsapp_audio";
    public static final String KEY_DISABLED_MUSIC_SOURCE_FOLDERS = "disabled_music_source_folders";
    public static final String LIBRARY_GROUPING_BOTH = "artist_album";
    public static final String LIBRARY_GROUPING_ARTIST = "artist";
    public static final String LIBRARY_GROUPING_ALBUM = "album";
    public static final String LIBRARY_GROUPING_NONE = "none";

    public static final String KEY_METADATA_SOURCE = "metadata_source";
    public static final String METADATA_SOURCE_AUTO = "auto";
    public static final String METADATA_SOURCE_TAGS = "tags";
    public static final String METADATA_SOURCE_INTERNAL = "internal";

    public static final String KEY_START_PAGE = "start_page";
    public static final String START_MUSIC = "music";
    public static final String START_RADIO = "radio";
    public static final String START_DOWNLOADS = "downloads"; // legacy migration only
    public static final String START_SETTINGS = "settings";

    public static final String KEY_BOTTOM_NAV_ORDER = "bottom_nav_order";
    public static final String KEY_SHOW_MUSIC_NAV = "show_music_nav";
    public static final String KEY_SHOW_RADIO_NAV = "show_radio_nav";
    public static final String KEY_SHOW_DOWNLOADS_NAV = "show_downloads_nav"; // legacy migration only
    public static final String KEY_SHOW_GAMES_NAV = "show_games_nav";
    public static final String KEY_SHOW_BOTTOM_NAV_NOW_PLAYING = "show_bottom_nav_now_playing";
    public static final String KEY_EQUALIZER_BACKGROUND_ENABLED = "equalizer_background_enabled";
    public static final String KEY_EQUALIZER_COLOR_MODE = "equalizer_background_color_mode";
    public static final String KEY_EQUALIZER_CUSTOM_COLOR = "equalizer_background_custom_color";
    public static final String KEY_EQUALIZER_BARS = "equalizer_background_bars";
    public static final String KEY_EQUALIZER_SENSITIVITY = "equalizer_background_sensitivity";
    public static final String KEY_EQUALIZER_SCRIM = "equalizer_background_scrim";
    public static final String KEY_EQUALIZER_SKIN_BACKGROUND_COLOR = "equalizer_skin_background_color";
    public static final String KEY_EQUALIZER_SKIN_BARS_COLOR = "equalizer_skin_bars_color";
    public static final String KEY_RING_SKIN_BACKGROUND_COLOR = "ring_skin_background_color";
    public static final String KEY_RING_SKIN_COLOR = "ring_skin_color";
    public static final String KEY_EQUALIZER_SKIN_INTENSITY = "equalizer_skin_intensity";
    public static final String KEY_RING_SKIN_INTENSITY = "ring_skin_intensity";

    public static final String EQUALIZER_COLOR_ACCENT = "accent";
    public static final String EQUALIZER_COLOR_ARTWORK = "artwork";
    public static final String EQUALIZER_COLOR_GRADIENT = "gradient";
    public static final String EQUALIZER_COLOR_CUSTOM = "custom";
    public static final String EQUALIZER_COLOR_VIOLET = "violet";
    public static final String EQUALIZER_COLOR_BLUE = "blue";
    public static final String EQUALIZER_COLOR_CYAN = "cyan";
    public static final String EQUALIZER_COLOR_GREEN = "green";
    public static final String EQUALIZER_COLOR_ORANGE = "orange";
    public static final String EQUALIZER_COLOR_RED = "red";
    public static final String EQUALIZER_COLOR_PINK = "pink";
    public static final String NAV_MUSIC = "music";
    public static final String NAV_RADIO = "radio";
    public static final String NAV_DOWNLOADS = "downloads"; // legacy migration only
    public static final String NAV_GAMES = "games";
    private static final String KEY_GUESS_SONG_BEST_PREFIX = "guess_song_best:";
    private static final String KEY_INTRUDER_BEST_PREFIX = "intruder_best:";

    private static final String LEGACY_FORCE_DARK = "force_dark";

    private AppPreferences() { }

    public static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static String getThemeMode(Context context) {
        SharedPreferences prefs = prefs(context);
        if (prefs.contains(KEY_THEME_MODE)) return prefs.getString(KEY_THEME_MODE, THEME_SYSTEM);
        return prefs.getBoolean(LEGACY_FORCE_DARK, false) ? THEME_DARK : THEME_SYSTEM;
    }

    public static void setThemeMode(Context context, String mode) {
        prefs(context).edit().putString(KEY_THEME_MODE, mode).commit();
        applyTheme(mode);
    }

    public static void applySavedTheme(Context context) { applyTheme(getThemeMode(context)); }

    private static void applyTheme(String mode) {
        int nightMode = THEME_LIGHT.equals(mode) ? AppCompatDelegate.MODE_NIGHT_NO
                : THEME_DARK.equals(mode) ? AppCompatDelegate.MODE_NIGHT_YES
                : AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        AppCompatDelegate.setDefaultNightMode(nightMode);
    }

    public static boolean showMiniSubtitle(Context context) { return prefs(context).getBoolean(KEY_SHOW_MINI_SUBTITLE, true); }
    public static boolean miniPlayerUsesPlayerBackground(Context context) { return prefs(context).getBoolean(KEY_MINI_PLAYER_USE_PLAYER_BACKGROUND, true); }
    public static boolean showRadioStationCount(Context context) { return prefs(context).getBoolean(KEY_SHOW_RADIO_STATION_COUNT, false); }
    public static boolean useLargeArtwork(Context context) { return prefs(context).getBoolean(KEY_LARGE_ARTWORK, true); }
    public static boolean centerPlayerInfo(Context context) { return prefs(context).getBoolean(KEY_CENTER_PLAYER_INFO, false); }
    public static boolean showPlayerBackButton(Context context) { return prefs(context).getBoolean(KEY_SHOW_PLAYER_BACK_BUTTON, true); }
    public static boolean showPlayerHeaderLabel(Context context) { return prefs(context).getBoolean(KEY_SHOW_PLAYER_HEADER_LABEL, true); }
    public static boolean showPlayerMusicButton(Context context) { return prefs(context).getBoolean(KEY_SHOW_PLAYER_MUSIC_BUTTON, true); }
    public static boolean showPlayerArtwork(Context context) { return prefs(context).getBoolean(KEY_SHOW_PLAYER_ARTWORK, true); }
    public static boolean showPlayerProgressLine(Context context) { return prefs(context).getBoolean(KEY_SHOW_PLAYER_PROGRESS_LINE, true); }
    public static String getPlayerProgressStyle(Context context) {
        String style = prefs(context).getString(KEY_PLAYER_PROGRESS_STYLE, PROGRESS_STYLE_CLASSIC);
        if (PROGRESS_STYLE_CLASSIC.equals(style) || PROGRESS_STYLE_WAVE.equals(style)
                || PROGRESS_STYLE_DOTS.equals(style) || PROGRESS_STYLE_CAPSULE.equals(style)
                || PROGRESS_STYLE_EMOJI.equals(style)) return style;
        return PROGRESS_STYLE_CLASSIC;
    }
    public static void setPlayerProgressStyle(Context context, String style) {
        String safe = PROGRESS_STYLE_WAVE.equals(style) || PROGRESS_STYLE_DOTS.equals(style)
                || PROGRESS_STYLE_CAPSULE.equals(style) || PROGRESS_STYLE_EMOJI.equals(style)
                ? style : PROGRESS_STYLE_CLASSIC;
        prefs(context).edit().putString(KEY_PLAYER_PROGRESS_STYLE, safe).commit();
    }
    public static String getPlayerProgressEmoji(Context context) {
        String emoji = prefs(context).getString(KEY_PLAYER_PROGRESS_EMOJI, "🎵");
        if (emoji == null || emoji.trim().isEmpty()) return "🎵";
        return emoji.trim();
    }
    public static void setPlayerProgressEmoji(Context context, String emoji) {
        String safe = emoji == null ? "" : emoji.trim();
        if (safe.isEmpty()) safe = "🎵";
        prefs(context).edit().putString(KEY_PLAYER_PROGRESS_EMOJI, safe).commit();
    }
    public static boolean showPlayerWaves(Context context) { return true; }
    public static boolean showPlayerTitle(Context context) { return prefs(context).getBoolean(KEY_SHOW_PLAYER_TITLE, true); }
    public static boolean showPlayerArtist(Context context) { return prefs(context).getBoolean(KEY_SHOW_PLAYER_ARTIST, true); }
    public static boolean showPlayerAlbum(Context context) { return prefs(context).getBoolean(KEY_SHOW_PLAYER_ALBUM, true); }
    public static boolean showPlayerShuffle(Context context) { return prefs(context).getBoolean(KEY_SHOW_PLAYER_SHUFFLE, true); }
    public static boolean showPlayerQueue(Context context) { return prefs(context).getBoolean(KEY_SHOW_PLAYER_QUEUE, true); }
    public static boolean showPlayerRepeat(Context context) { return prefs(context).getBoolean(KEY_SHOW_PLAYER_REPEAT, true); }
    public static boolean showPlayerModeText(Context context) { return prefs(context).getBoolean(KEY_SHOW_PLAYER_MODE_TEXT, true); }
    public static String getPlayerStyle(Context context) {
        String style = prefs(context).getString(KEY_PLAYER_STYLE, STYLE_CLASSIC);
        boolean supported = STYLE_CLASSIC.equals(style) || STYLE_VINYL.equals(style) || STYLE_PIXEL.equals(style)
                || STYLE_CASSETTE.equals(style) || STYLE_WALKMAN.equals(style) || STYLE_RETRO.equals(style)
                || STYLE_NEON.equals(style) || STYLE_GLASS.equals(style) || STYLE_MINIMAL.equals(style)
                || STYLE_GAMEBOY_3D.equals(style) || STYLE_SPOTIFY_2D.equals(style)
                || STYLE_EQUALIZER_2D.equals(style) || STYLE_RING.equals(style);
        if (supported) return style;
        prefs(context).edit().putString(KEY_PLAYER_STYLE, STYLE_CLASSIC).commit();
        return STYLE_CLASSIC;
    }
    public static String getPlayerAccent(Context context) { return prefs(context).getString(KEY_PLAYER_ACCENT, ACCENT_VIOLET); }
    public static boolean useCustomAccent(Context context) { return prefs(context).getBoolean(KEY_USE_CUSTOM_ACCENT, false); }
    public static int getStoredCustomAccentColor(Context context) { return prefs(context).getInt(KEY_CUSTOM_ACCENT_COLOR, Color.parseColor("#8B5CF6")); }
    public static void setCustomAccentColor(Context context, int color) { prefs(context).edit().putInt(KEY_CUSTOM_ACCENT_COLOR, color).commit(); }
    public static String getArtworkShape(Context context) { return prefs(context).getString(KEY_ARTWORK_SHAPE, SHAPE_ROUNDED); }
    public static String getMusicTabsPosition(Context context) { return prefs(context).getString(KEY_MUSIC_TABS_POSITION, TABS_TOP); }
    public static String getPlayerAnimation(Context context) {
        if (PowerSaverManager.isActive(context)) return ANIMATION_OFF;
        return prefs(context).getString(KEY_PLAYER_ANIMATION, ANIMATION_SOFT);
    }
    public static String getPlayerBackground(Context context) {
        if (PowerSaverManager.isActive(context) && PowerSaverManager.forceDarkBackground(context)) return BACKGROUND_DARK;
        return prefs(context).getString(KEY_PLAYER_BACKGROUND, BACKGROUND_AURORA);
    }
    public static String getAppBackground(Context context) {
        if (PowerSaverManager.isActive(context) && PowerSaverManager.forceDarkBackground(context)) return BACKGROUND_DARK;
        return prefs(context).getString(KEY_APP_BACKGROUND, BACKGROUND_DARK);
    }
    public static String getPlayerBackgroundColorMode(Context context) {
        String mode = prefs(context).getString(KEY_PLAYER_BACKGROUND_COLOR_MODE, APP_BACKGROUND_COLOR_ACCENT);
        if (PowerSaverManager.isActive(context)) {
            if (PowerSaverManager.forceDarkBackground(context)) return APP_BACKGROUND_COLOR_SOLID;
            if (PLAYER_BACKGROUND_COLOR_RAINBOW.equals(mode)) return APP_BACKGROUND_COLOR_SOLID;
        }
        if (APP_BACKGROUND_COLOR_ACCENT.equals(mode) || APP_BACKGROUND_COLOR_SOLID.equals(mode)
                || APP_BACKGROUND_COLOR_GRADIENT_2.equals(mode) || APP_BACKGROUND_COLOR_GRADIENT_3.equals(mode)
                || PLAYER_BACKGROUND_COLOR_RAINBOW.equals(mode)
                || PLAYER_BACKGROUND_COLOR_ARTWORK.equals(mode)) return mode;
        return APP_BACKGROUND_COLOR_ACCENT;
    }
    public static int getPlayerBackgroundColor1(Context context) {
        if (PowerSaverManager.isActive(context) && PowerSaverManager.forceDarkBackground(context))
            return getAppBackgroundColor1(context);
        return prefs(context).getInt(KEY_PLAYER_BACKGROUND_COLOR_1, getPlayerAccentColor(context));
    }
    public static int getPlayerBackgroundColor2(Context context) {
        if (PowerSaverManager.isActive(context) && PowerSaverManager.forceDarkBackground(context))
            return getAppBackgroundColor2(context);
        return prefs(context).getInt(KEY_PLAYER_BACKGROUND_COLOR_2, getAppBackgroundColor2(context));
    }
    public static int getPlayerBackgroundColor3(Context context) {
        if (PowerSaverManager.isActive(context) && PowerSaverManager.forceDarkBackground(context))
            return getAppBackgroundColor3(context);
        return prefs(context).getInt(KEY_PLAYER_BACKGROUND_COLOR_3, getAppBackgroundColor3(context));
    }
    public static void setPlayerBackgroundPalette(Context context, String mode, int c1, int c2, int c3) {
        String safe = APP_BACKGROUND_COLOR_ACCENT.equals(mode) || APP_BACKGROUND_COLOR_SOLID.equals(mode)
                || APP_BACKGROUND_COLOR_GRADIENT_2.equals(mode) || APP_BACKGROUND_COLOR_GRADIENT_3.equals(mode)
                || PLAYER_BACKGROUND_COLOR_RAINBOW.equals(mode)
                || PLAYER_BACKGROUND_COLOR_ARTWORK.equals(mode) ? mode : APP_BACKGROUND_COLOR_ACCENT;
        prefs(context).edit()
                .putString(KEY_PLAYER_BACKGROUND_COLOR_MODE, safe)
                .putInt(KEY_PLAYER_BACKGROUND_COLOR_1, c1)
                .putInt(KEY_PLAYER_BACKGROUND_COLOR_2, c2)
                .putInt(KEY_PLAYER_BACKGROUND_COLOR_3, c3)
                .commit();
    }
    public static void setAppBackground(Context context, String background) {
        String safe = BACKGROUND_AURORA.equals(background) || BACKGROUND_WAVES.equals(background)
                || BACKGROUND_GRID.equals(background) || BACKGROUND_PULSE.equals(background)
                || BACKGROUND_DARK.equals(background) || BACKGROUND_NONE.equals(background)
                ? background : BACKGROUND_DARK;
        prefs(context).edit().putString(KEY_APP_BACKGROUND, safe).commit();
    }
    public static String getPlayerTypography(Context context) { return prefs(context).getString(KEY_PLAYER_TYPOGRAPHY, TYPOGRAPHY_MODERN); }
    public static String getAppBackgroundColorMode(Context context) {
        String mode = prefs(context).getString(KEY_APP_BACKGROUND_COLOR_MODE, APP_BACKGROUND_COLOR_ACCENT);
        if (PowerSaverManager.isActive(context)) {
            if (PowerSaverManager.forceDarkBackground(context)) return APP_BACKGROUND_COLOR_SOLID;
            if (APP_BACKGROUND_COLOR_MULTICOLOR.equals(mode)) return APP_BACKGROUND_COLOR_SOLID;
        }
        if (APP_BACKGROUND_COLOR_ACCENT.equals(mode) || APP_BACKGROUND_COLOR_SOLID.equals(mode)
                || APP_BACKGROUND_COLOR_GRADIENT_2.equals(mode) || APP_BACKGROUND_COLOR_GRADIENT_3.equals(mode)
                || APP_BACKGROUND_COLOR_MULTICOLOR.equals(mode)) return mode;
        return APP_BACKGROUND_COLOR_ACCENT;
    }

    public static int getAppBackgroundColor1(Context context) {
        if (PowerSaverManager.isActive(context) && PowerSaverManager.forceDarkBackground(context)) return Color.rgb(7, 10, 16);
        return prefs(context).getInt(KEY_APP_BACKGROUND_COLOR_1, Color.parseColor("#7C4DFF"));
    }

    public static int getAppBackgroundColor2(Context context) {
        if (PowerSaverManager.isActive(context) && PowerSaverManager.forceDarkBackground(context)) return Color.rgb(7, 10, 16);
        return prefs(context).getInt(KEY_APP_BACKGROUND_COLOR_2, Color.parseColor("#24C7FF"));
    }

    public static int getAppBackgroundColor3(Context context) {
        if (PowerSaverManager.isActive(context) && PowerSaverManager.forceDarkBackground(context)) return Color.rgb(7, 10, 16);
        return prefs(context).getInt(KEY_APP_BACKGROUND_COLOR_3, Color.parseColor("#FF5FA2"));
    }

    public static void setAppBackgroundPalette(Context context, String mode, int c1, int c2, int c3) {
        String safe = APP_BACKGROUND_COLOR_ACCENT.equals(mode) || APP_BACKGROUND_COLOR_SOLID.equals(mode)
                || APP_BACKGROUND_COLOR_GRADIENT_2.equals(mode) || APP_BACKGROUND_COLOR_GRADIENT_3.equals(mode)
                || APP_BACKGROUND_COLOR_MULTICOLOR.equals(mode) ? mode : APP_BACKGROUND_COLOR_ACCENT;
        prefs(context).edit()
                .putString(KEY_APP_BACKGROUND_COLOR_MODE, safe)
                .putInt(KEY_APP_BACKGROUND_COLOR_1, c1)
                .putInt(KEY_APP_BACKGROUND_COLOR_2, c2)
                .putInt(KEY_APP_BACKGROUND_COLOR_3, c3)
                .commit();
    }


    public static float getAnimationMultiplier(Context context) {
        String animation = getPlayerAnimation(context);
        if (ANIMATION_OFF.equals(animation)) return 0f;
        if (ANIMATION_LIVELY.equals(animation)) return 1.45f;
        return 1f;
    }

    public static void setPlayerStyle(Context context, String style) {
        String safe = isSupportedPlayerStyle(style) ? style : STYLE_CLASSIC;
        prefs(context).edit().putString(KEY_PLAYER_STYLE, safe).commit();
    }

    /**
     * Kept for source compatibility with older callers. A skin now changes ONLY the player object;
     * accent, typography, animation, background and artwork preferences remain independent.
     */
    @Deprecated
    public static void applyStyleDefaults(Context context, String style) {
        setPlayerStyle(context, style);
    }

    private static boolean isSupportedPlayerStyle(String style) {
        return STYLE_CLASSIC.equals(style) || STYLE_VINYL.equals(style) || STYLE_PIXEL.equals(style)
                || STYLE_CASSETTE.equals(style) || STYLE_WALKMAN.equals(style) || STYLE_RETRO.equals(style)
                || STYLE_NEON.equals(style) || STYLE_GLASS.equals(style) || STYLE_MINIMAL.equals(style)
                || STYLE_GAMEBOY_3D.equals(style) || STYLE_SPOTIFY_2D.equals(style)
                || STYLE_EQUALIZER_2D.equals(style) || STYLE_RING.equals(style);
    }

    public static boolean isTwoDimensionalStyle(String style) {
        return STYLE_SPOTIFY_2D.equals(style)
                || STYLE_EQUALIZER_2D.equals(style)
                || STYLE_CLASSIC.equals(style)
                || STYLE_NEON.equals(style)
                || STYLE_GLASS.equals(style)
                || STYLE_MINIMAL.equals(style);
    }

    public static boolean isImmersiveTwoDimensionalStyle(String style) {
        return STYLE_SPOTIFY_2D.equals(style);
    }

    public static int getPlayerAccentColor(Context context) {
        if (useCustomAccent(context)) return getStoredCustomAccentColor(context);
        String accent = getPlayerAccent(context);
        if (ACCENT_BLUE.equals(accent)) return Color.parseColor("#4F7DFF");
        if (ACCENT_CYAN.equals(accent)) return Color.parseColor("#24C7FF");
        if (ACCENT_TEAL.equals(accent)) return Color.parseColor("#00BFA5");
        if (ACCENT_GREEN.equals(accent)) return Color.parseColor("#43C878");
        if (ACCENT_LIME.equals(accent)) return Color.parseColor("#9DDB36");
        if (ACCENT_YELLOW.equals(accent)) return Color.parseColor("#F3C84B");
        if (ACCENT_ORANGE.equals(accent)) return Color.parseColor("#FF8A3D");
        if (ACCENT_RED.equals(accent)) return Color.parseColor("#FF4D5A");
        if (ACCENT_PINK.equals(accent)) return Color.parseColor("#FF5FA2");
        return Color.parseColor("#8B5CF6");
    }

    /** @deprecated Use AppTypography.get(...); kept so existing screens still use the same provider. */
    @Deprecated
    public static Typeface getPlayerTypeface(Context context, int style) {
        return AppTypography.get(context, style);
    }

    public static String getLibraryGroupingMode(Context context) {
        String mode = prefs(context).getString(KEY_LIBRARY_GROUPING_MODE, LIBRARY_GROUPING_BOTH);
        if (LIBRARY_GROUPING_ARTIST.equals(mode) || LIBRARY_GROUPING_ALBUM.equals(mode)
                || LIBRARY_GROUPING_NONE.equals(mode) || LIBRARY_GROUPING_BOTH.equals(mode)) return mode;
        prefs(context).edit().putString(KEY_LIBRARY_GROUPING_MODE, LIBRARY_GROUPING_BOTH).commit();
        return LIBRARY_GROUPING_BOTH;
    }

    public static void setLibraryGroupingMode(Context context, String mode) {
        String safe = LIBRARY_GROUPING_ARTIST.equals(mode) || LIBRARY_GROUPING_ALBUM.equals(mode)
                || LIBRARY_GROUPING_NONE.equals(mode) || LIBRARY_GROUPING_BOTH.equals(mode) ? mode : LIBRARY_GROUPING_BOTH;
        prefs(context).edit().putString(KEY_LIBRARY_GROUPING_MODE, safe).commit();
    }

    public static boolean libraryArtistsEnabled(Context context) {
        String mode = getLibraryGroupingMode(context);
        return LIBRARY_GROUPING_BOTH.equals(mode) || LIBRARY_GROUPING_ARTIST.equals(mode);
    }

    public static boolean libraryAlbumsEnabled(Context context) {
        String mode = getLibraryGroupingMode(context);
        return LIBRARY_GROUPING_BOTH.equals(mode) || LIBRARY_GROUPING_ALBUM.equals(mode);
    }

    public static String libraryGroupingName(Context context) {
        String mode = getLibraryGroupingMode(context);
        if (LIBRARY_GROUPING_ARTIST.equals(mode)) return "Solo banda";
        if (LIBRARY_GROUPING_ALBUM.equals(mode)) return "Solo álbum";
        if (LIBRARY_GROUPING_NONE.equals(mode)) return "Nada";
        return "Banda y álbum";
    }

    public static boolean hideWhatsAppAudio(Context context) {
        return prefs(context).getBoolean(KEY_HIDE_WHATSAPP_AUDIO, true);
    }

    public static void setHideWhatsAppAudio(Context context, boolean hide) {
        prefs(context).edit().putBoolean(KEY_HIDE_WHATSAPP_AUDIO, hide).commit();
    }

    public static Set<String> getDisabledMusicSourceFolders(Context context) {
        Set<String> stored = prefs(context).getStringSet(KEY_DISABLED_MUSIC_SOURCE_FOLDERS, Collections.emptySet());
        return stored == null ? new HashSet<>() : new HashSet<>(stored);
    }

    public static void setMusicSourceFolderEnabled(Context context, String folderKey, boolean enabled) {
        if (folderKey == null || folderKey.trim().isEmpty()) return;
        Set<String> disabled = getDisabledMusicSourceFolders(context);
        if (enabled) disabled.remove(folderKey);
        else disabled.add(folderKey);
        prefs(context).edit().putStringSet(KEY_DISABLED_MUSIC_SOURCE_FOLDERS, new HashSet<>(disabled)).commit();
    }

    public static String getMetadataSource(Context context) {
        String source = prefs(context).getString(KEY_METADATA_SOURCE, METADATA_SOURCE_AUTO);
        if (METADATA_SOURCE_TAGS.equals(source)
                || METADATA_SOURCE_INTERNAL.equals(source)
                || METADATA_SOURCE_AUTO.equals(source)) {
            return source;
        }
        prefs(context).edit().putString(KEY_METADATA_SOURCE, METADATA_SOURCE_AUTO).commit();
        return METADATA_SOURCE_AUTO;
    }

    public static void setMetadataSource(Context context, String source) {
        String safeSource = METADATA_SOURCE_TAGS.equals(source)
                || METADATA_SOURCE_INTERNAL.equals(source)
                || METADATA_SOURCE_AUTO.equals(source)
                ? source : METADATA_SOURCE_AUTO;
        prefs(context).edit().putString(KEY_METADATA_SOURCE, safeSource).commit();
    }

    public static String getStartPage(Context context) {
        SharedPreferences preferences = prefs(context);
        String page = preferences.getString(KEY_START_PAGE, START_MUSIC);
        if (START_DOWNLOADS.equals(page)) {
            preferences.edit().putString(KEY_START_PAGE, START_MUSIC).commit();
            return START_MUSIC;
        }
        return page;
    }
    public static void setStartPage(Context context, String page) { prefs(context).edit().putString(KEY_START_PAGE, page).commit(); }

    public static String getBottomNavOrder(Context context) {
        SharedPreferences preferences = prefs(context);
        String order = preferences.getString(KEY_BOTTOM_NAV_ORDER, NAV_MUSIC + "," + NAV_RADIO + "," + NAV_GAMES);
        if (order != null && order.contains(NAV_DOWNLOADS)) {
            order = order.replace(NAV_DOWNLOADS, NAV_GAMES);
            preferences.edit().putString(KEY_BOTTOM_NAV_ORDER, order).commit();
        }
        return order == null || order.trim().isEmpty()
                ? NAV_MUSIC + "," + NAV_RADIO + "," + NAV_GAMES : order;
    }

    public static boolean showMusicNav(Context context) { return prefs(context).getBoolean(KEY_SHOW_MUSIC_NAV, true); }
    public static boolean showRadioNav(Context context) { return prefs(context).getBoolean(KEY_SHOW_RADIO_NAV, true); }
    public static boolean showGamesNav(Context context) {
        SharedPreferences preferences = prefs(context);
        if (preferences.contains(KEY_SHOW_GAMES_NAV)) return preferences.getBoolean(KEY_SHOW_GAMES_NAV, true);
        boolean legacy = preferences.getBoolean(KEY_SHOW_DOWNLOADS_NAV, true);
        preferences.edit().putBoolean(KEY_SHOW_GAMES_NAV, legacy).commit();
        return legacy;
    }
    @Deprecated public static boolean showDownloadsNav(Context context) { return showGamesNav(context); }
    public static boolean showBottomNavInNowPlaying(Context context) { return prefs(context).getBoolean(KEY_SHOW_BOTTOM_NAV_NOW_PLAYING, false); }

    public static int getGuessSongBestScore(Context context, String playlistKey) {
        String safe = playlistKey == null || playlistKey.trim().isEmpty() ? "unknown" : playlistKey.trim();
        return prefs(context).getInt(KEY_GUESS_SONG_BEST_PREFIX + safe, 0);
    }

    public static void setGuessSongBestScore(Context context, String playlistKey, int score) {
        String safe = playlistKey == null || playlistKey.trim().isEmpty() ? "unknown" : playlistKey.trim();
        int previous = getGuessSongBestScore(context, safe);
        if (score > previous) prefs(context).edit().putInt(KEY_GUESS_SONG_BEST_PREFIX + safe, score).commit();
    }

    public static int getIntruderBestScore(Context context, String playlistKey) {
        String safe = playlistKey == null || playlistKey.trim().isEmpty() ? "unknown" : playlistKey.trim();
        return prefs(context).getInt(KEY_INTRUDER_BEST_PREFIX + safe, 0);
    }

    public static void setIntruderBestScore(Context context, String playlistKey, int score) {
        String safe = playlistKey == null || playlistKey.trim().isEmpty() ? "unknown" : playlistKey.trim();
        int previous = getIntruderBestScore(context, safe);
        if (score > previous) prefs(context).edit().putInt(KEY_INTRUDER_BEST_PREFIX + safe, score).commit();
    }

    public static int getEqualizerSkinBackgroundColor(Context context) {
        return prefs(context).getInt(KEY_EQUALIZER_SKIN_BACKGROUND_COLOR, Color.BLACK);
    }

    public static void setEqualizerSkinBackgroundColor(Context context, int color) {
        prefs(context).edit().putInt(KEY_EQUALIZER_SKIN_BACKGROUND_COLOR, Color.rgb(Color.red(color), Color.green(color), Color.blue(color))).commit();
    }

    public static int getEqualizerSkinBarsColor(Context context) {
        return prefs(context).getInt(KEY_EQUALIZER_SKIN_BARS_COLOR, getPlayerAccentColor(context));
    }

    public static void setEqualizerSkinBarsColor(Context context, int color) {
        prefs(context).edit().putInt(KEY_EQUALIZER_SKIN_BARS_COLOR, Color.rgb(Color.red(color), Color.green(color), Color.blue(color))).commit();
    }

    public static int getRingSkinBackgroundColor(Context context) {
        return prefs(context).getInt(KEY_RING_SKIN_BACKGROUND_COLOR, Color.BLACK);
    }

    public static void setRingSkinBackgroundColor(Context context, int color) {
        prefs(context).edit().putInt(KEY_RING_SKIN_BACKGROUND_COLOR,
                Color.rgb(Color.red(color), Color.green(color), Color.blue(color))).commit();
    }

    public static int getRingSkinColor(Context context) {
        return prefs(context).getInt(KEY_RING_SKIN_COLOR, Color.parseColor("#39FF7D"));
    }

    public static void setRingSkinColor(Context context, int color) {
        prefs(context).edit().putInt(KEY_RING_SKIN_COLOR,
                Color.rgb(Color.red(color), Color.green(color), Color.blue(color))).commit();
    }

    public static int getEqualizerSkinIntensity(Context context) {
        return clampVisualizerIntensity(prefs(context).getInt(KEY_EQUALIZER_SKIN_INTENSITY, 100));
    }

    public static void setEqualizerSkinIntensity(Context context, int percent) {
        prefs(context).edit().putInt(KEY_EQUALIZER_SKIN_INTENSITY, clampVisualizerIntensity(percent)).commit();
    }

    public static int getRingSkinIntensity(Context context) {
        return clampVisualizerIntensity(prefs(context).getInt(KEY_RING_SKIN_INTENSITY, 100));
    }

    public static void setRingSkinIntensity(Context context, int percent) {
        prefs(context).edit().putInt(KEY_RING_SKIN_INTENSITY, clampVisualizerIntensity(percent)).commit();
    }

    private static int clampVisualizerIntensity(int percent) {
        return Math.max(50, Math.min(200, percent));
    }

    /** Raw user preference. Super battery saver can disable the effect without overwriting it. */
    public static boolean equalizerBackgroundRequested(Context context) {
        return prefs(context).getBoolean(KEY_EQUALIZER_BACKGROUND_ENABLED, false);
    }

    public static boolean equalizerBackgroundEnabled(Context context) {
        return equalizerBackgroundRequested(context) && !PowerSaverManager.isActive(context);
    }

    public static String getEqualizerColorMode(Context context) {
        String mode = prefs(context).getString(KEY_EQUALIZER_COLOR_MODE, EQUALIZER_COLOR_GRADIENT);
        if (EQUALIZER_COLOR_ACCENT.equals(mode) || EQUALIZER_COLOR_ARTWORK.equals(mode)
                || EQUALIZER_COLOR_GRADIENT.equals(mode) || EQUALIZER_COLOR_CUSTOM.equals(mode)
                || EQUALIZER_COLOR_VIOLET.equals(mode) || EQUALIZER_COLOR_BLUE.equals(mode)
                || EQUALIZER_COLOR_CYAN.equals(mode) || EQUALIZER_COLOR_GREEN.equals(mode)
                || EQUALIZER_COLOR_ORANGE.equals(mode) || EQUALIZER_COLOR_RED.equals(mode)
                || EQUALIZER_COLOR_PINK.equals(mode)) return mode;
        return EQUALIZER_COLOR_GRADIENT;
    }

    public static int getEqualizerCustomColor(Context context) {
        return prefs(context).getInt(KEY_EQUALIZER_CUSTOM_COLOR, Color.parseColor("#8B5CF6"));
    }

    public static int getEqualizerBars(Context context) {
        int bars = prefs(context).getInt(KEY_EQUALIZER_BARS, 24);
        return bars == 16 || bars == 24 || bars == 32 || bars == 48 ? bars : 24;
    }

    /** 50..200 percent. */
    public static int getEqualizerSensitivity(Context context) {
        return Math.max(50, Math.min(200, prefs(context).getInt(KEY_EQUALIZER_SENSITIVITY, 110)));
    }

    /** 55..90 percent attenuation. The lower bound keeps foreground text readable. */
    public static int getEqualizerScrim(Context context) {
        return Math.max(55, Math.min(90, prefs(context).getInt(KEY_EQUALIZER_SCRIM, 68)));
    }

    public static boolean isEqualizerPreference(String key) {
        return KEY_EQUALIZER_BACKGROUND_ENABLED.equals(key)
                || KEY_EQUALIZER_COLOR_MODE.equals(key)
                || KEY_EQUALIZER_CUSTOM_COLOR.equals(key)
                || KEY_EQUALIZER_BARS.equals(key)
                || KEY_EQUALIZER_SENSITIVITY.equals(key)
                || KEY_EQUALIZER_SCRIM.equals(key);
    }

    public static int equalizerSolidColor(Context context) {
        String mode = getEqualizerColorMode(context);
        if (EQUALIZER_COLOR_VIOLET.equals(mode)) return Color.parseColor("#8B5CF6");
        if (EQUALIZER_COLOR_BLUE.equals(mode)) return Color.parseColor("#4F7DFF");
        if (EQUALIZER_COLOR_CYAN.equals(mode)) return Color.parseColor("#24C7FF");
        if (EQUALIZER_COLOR_GREEN.equals(mode)) return Color.parseColor("#43C878");
        if (EQUALIZER_COLOR_ORANGE.equals(mode)) return Color.parseColor("#FF8A3D");
        if (EQUALIZER_COLOR_RED.equals(mode)) return Color.parseColor("#FF4D5A");
        if (EQUALIZER_COLOR_PINK.equals(mode)) return Color.parseColor("#FF5FA2");
        if (EQUALIZER_COLOR_CUSTOM.equals(mode)) return getEqualizerCustomColor(context);
        return getPlayerAccentColor(context);
    }
    public static java.util.List<String> getPlaylistEntryOrder(Context context) {
        String raw = prefs(context).getString(KEY_PLAYLIST_ENTRY_ORDER, "");
        java.util.List<String> result = new java.util.ArrayList<>();
        if (raw == null || raw.isEmpty()) return result;
        for (String value : raw.split(",")) {
            String clean = value.trim();
            if (!clean.isEmpty() && !result.contains(clean)) result.add(clean);
        }
        return result;
    }

    public static void setPlaylistEntryOrder(Context context, java.util.List<String> stableIds) {
        StringBuilder out = new StringBuilder();
        if (stableIds != null) {
            for (String id : stableIds) {
                if (id == null || id.trim().isEmpty()) continue;
                if (out.length() > 0) out.append(',');
                out.append(id.trim());
            }
        }
        prefs(context).edit().putString(KEY_PLAYLIST_ENTRY_ORDER, out.toString()).apply();
    }

    public static String getPlaylistArtworkUri(Context context, String stableId) {
        if (stableId == null || stableId.isEmpty()) return "";
        return prefs(context).getString(KEY_PLAYLIST_ARTWORK_PREFIX + stableId, "");
    }

    public static void setPlaylistArtworkUri(Context context, String stableId, String uri) {
        if (stableId == null || stableId.isEmpty()) return;
        SharedPreferences.Editor edit = prefs(context).edit();
        if (uri == null || uri.trim().isEmpty()) edit.remove(KEY_PLAYLIST_ARTWORK_PREFIX + stableId);
        else edit.putString(KEY_PLAYLIST_ARTWORK_PREFIX + stableId, uri.trim());
        edit.apply();
    }

    public static void removePlaylistVisualState(Context context, String stableId) {
        if (stableId == null || stableId.isEmpty()) return;
        java.util.List<String> order = getPlaylistEntryOrder(context);
        order.remove(stableId);
        prefs(context).edit()
                .remove(KEY_PLAYLIST_ARTWORK_PREFIX + stableId)
                .putString(KEY_PLAYLIST_ENTRY_ORDER, android.text.TextUtils.join(",", order))
                .apply();
    }

}
