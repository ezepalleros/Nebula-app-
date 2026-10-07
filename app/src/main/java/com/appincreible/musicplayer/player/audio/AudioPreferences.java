package com.appincreible.musicplayer.player.audio;

import android.content.Context;
import android.content.SharedPreferences;

import com.appincreible.musicplayer.power.PowerSaverManager;

public final class AudioPreferences {
    public static final String PREFS = "audio_preferences";

    public static final String KEY_FADE_PAUSE_ENABLED = "fade_pause_enabled";
    public static final String KEY_FADE_PAUSE_MS = "fade_pause_ms";
    public static final String KEY_CROSSFADE_ENABLED = "crossfade_enabled";
    public static final String KEY_CROSSFADE_MS = "crossfade_ms";
    public static final String KEY_SKIP_SAME_ALBUM = "skip_same_album";
    public static final String KEY_GAPLESS_ENABLED = "gapless_enabled";
    public static final String KEY_NORMALIZATION_ENABLED = "normalization_enabled";
    public static final String KEY_NORMALIZATION_TARGET = "normalization_target";
    public static final String TARGET_SOFT = "soft";
    public static final String TARGET_NORMAL = "normal";
    public static final String TARGET_LOUD = "loud";

    public static final String KEY_ANALYSIS_RUNNING = "analysis_running";
    public static final String KEY_ANALYSIS_PROGRESS = "analysis_progress";
    public static final String KEY_ANALYSIS_LABEL = "analysis_label";

    public static final String KEY_DJ_ENABLED = "dj_loquendo_enabled";
    public static final String KEY_DJ_PHRASES = "dj_loquendo_phrases";
    public static final String KEY_DJ_PROBABILITY = "dj_loquendo_probability";
    public static final String KEY_DJ_VOICE = "dj_loquendo_voice";

    // Legacy shared keys kept only for one-time migration.
    public static final String KEY_SHUFFLE_ENABLED = "shuffle_enabled";
    public static final String KEY_REPEAT_MODE = "repeat_mode";
    public static final String KEY_SONG_SHUFFLE_ENABLED = "song_shuffle_enabled";
    public static final String KEY_SONG_REPEAT_MODE = "song_repeat_mode";
    public static final String KEY_RADIO_SHUFFLE_ENABLED = "radio_shuffle_enabled";
    public static final String KEY_RADIO_REPEAT_MODE = "radio_repeat_mode";
    private static final String KEY_SPLIT_ORDER_MIGRATED = "split_playback_order_migrated_v1";

    public static final String DEFAULT_DJ_PHRASES = "Seguimos con más música.\nAhora viene otro tema.\nEsto sigue sonando.";

    private AudioPreferences() { }

    public static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean fadePauseEnabled(Context c) { return prefs(c).getBoolean(KEY_FADE_PAUSE_ENABLED, true); }
    public static int fadePauseMs(Context c) { return clamp(prefs(c).getInt(KEY_FADE_PAUSE_MS, 300), 200, 1000); }
    public static boolean crossfadeEnabled(Context c) { return !PowerSaverManager.isActive(c) && prefs(c).getBoolean(KEY_CROSSFADE_ENABLED, false); }
    public static int crossfadeMs(Context c) { return clamp(prefs(c).getInt(KEY_CROSSFADE_MS, 4000), 0, 12000); }
    public static boolean skipSameAlbum(Context c) { return prefs(c).getBoolean(KEY_SKIP_SAME_ALBUM, true); }
    public static boolean gaplessEnabled(Context c) { return !PowerSaverManager.isActive(c) && prefs(c).getBoolean(KEY_GAPLESS_ENABLED, true); }
    public static boolean normalizationEnabled(Context c) { return !PowerSaverManager.isActive(c) && prefs(c).getBoolean(KEY_NORMALIZATION_ENABLED, false); }
    public static String normalizationTarget(Context c) { return prefs(c).getString(KEY_NORMALIZATION_TARGET, TARGET_NORMAL); }

    public static boolean djEnabled(Context c) { return !PowerSaverManager.isActive(c) && prefs(c).getBoolean(KEY_DJ_ENABLED, false); }
    public static String djPhrases(Context c) { return prefs(c).getString(KEY_DJ_PHRASES, DEFAULT_DJ_PHRASES); }
    public static int djProbability(Context c) { return clamp(prefs(c).getInt(KEY_DJ_PROBABILITY, 30), 0, 80); }
    public static String djVoiceName(Context c) { return prefs(c).getString(KEY_DJ_VOICE, ""); }
    public static void ensureSplitPlaybackOrder(Context c) {
        SharedPreferences p = prefs(c);
        if (p.getBoolean(KEY_SPLIT_ORDER_MIGRATED, false)) return;
        boolean oldShuffle = p.getBoolean(KEY_SHUFFLE_ENABLED, false);
        int oldRepeat = clamp(p.getInt(KEY_REPEAT_MODE, androidx.media3.common.Player.REPEAT_MODE_OFF),
                androidx.media3.common.Player.REPEAT_MODE_OFF, androidx.media3.common.Player.REPEAT_MODE_ALL);
        p.edit()
                .putBoolean(KEY_SONG_SHUFFLE_ENABLED, oldShuffle)
                .putInt(KEY_SONG_REPEAT_MODE, oldRepeat)
                .putBoolean(KEY_RADIO_SHUFFLE_ENABLED, oldShuffle)
                .putInt(KEY_RADIO_REPEAT_MODE, oldRepeat)
                .putBoolean(KEY_SPLIT_ORDER_MIGRATED, true)
                .commit();
    }

    public static boolean shuffleEnabled(Context c, boolean radio) {
        ensureSplitPlaybackOrder(c);
        return prefs(c).getBoolean(radio ? KEY_RADIO_SHUFFLE_ENABLED : KEY_SONG_SHUFFLE_ENABLED, false);
    }

    public static int repeatMode(Context c, boolean radio) {
        ensureSplitPlaybackOrder(c);
        return clamp(prefs(c).getInt(radio ? KEY_RADIO_REPEAT_MODE : KEY_SONG_REPEAT_MODE,
                        androidx.media3.common.Player.REPEAT_MODE_OFF),
                androidx.media3.common.Player.REPEAT_MODE_OFF, androidx.media3.common.Player.REPEAT_MODE_ALL);
    }

    public static void setShuffleEnabled(Context c, boolean radio, boolean enabled) {
        ensureSplitPlaybackOrder(c);
        prefs(c).edit().putBoolean(radio ? KEY_RADIO_SHUFFLE_ENABLED : KEY_SONG_SHUFFLE_ENABLED, enabled).commit();
    }

    public static void setRepeatMode(Context c, boolean radio, int mode) {
        ensureSplitPlaybackOrder(c);
        prefs(c).edit().putInt(radio ? KEY_RADIO_REPEAT_MODE : KEY_SONG_REPEAT_MODE,
                clamp(mode, androidx.media3.common.Player.REPEAT_MODE_OFF, androidx.media3.common.Player.REPEAT_MODE_ALL)).commit();
    }

    public static boolean isPlaybackOrderKey(String key) {
        return KEY_SONG_SHUFFLE_ENABLED.equals(key) || KEY_SONG_REPEAT_MODE.equals(key)
                || KEY_RADIO_SHUFFLE_ENABLED.equals(key) || KEY_RADIO_REPEAT_MODE.equals(key);
    }

    public static boolean isPlaybackOrderKeyForMode(String key, boolean radio) {
        return radio
                ? KEY_RADIO_SHUFFLE_ENABLED.equals(key) || KEY_RADIO_REPEAT_MODE.equals(key)
                : KEY_SONG_SHUFFLE_ENABLED.equals(key) || KEY_SONG_REPEAT_MODE.equals(key);
    }

    /** Compatibility for callers that still mean the song queue. */
    public static boolean shuffleEnabled(Context c) { return shuffleEnabled(c, false); }
    public static int repeatMode(Context c) { return repeatMode(c, false); }

    public static long effectiveTransitionMs(Context c) {
        if (crossfadeEnabled(c)) return crossfadeMs(c);
        if (gaplessEnabled(c)) return 0L;
        return -1L;
    }

    public static float targetDbFs(Context c) {
        String target = normalizationTarget(c);
        if (TARGET_SOFT.equals(target)) return -18f;
        if (TARGET_LOUD.equals(target)) return -11f;
        return -14f;
    }

    private static int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }
}
