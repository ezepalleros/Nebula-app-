package com.appincreible.musicplayer.lyrics;

import android.content.Context;
import android.content.SharedPreferences;
import com.appincreible.musicplayer.themes.AppPreferences;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Private preferences: track negative lookup results without modifying Room. */
public final class LrclibLyricsState {
    private final SharedPreferences prefs;
    public LrclibLyricsState(Context context) { prefs = context.getSharedPreferences("lrclib_lyrics_state", Context.MODE_PRIVATE); }
    private String key(String mediaKey) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(mediaKey.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte b : digest) result.append(String.format(java.util.Locale.US, "%02x", b & 255));
            return result.toString();
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
    public boolean wasCheckedWithoutResult(String mediaKey) {
        String value = prefs.getString(key(mediaKey), "");
        return "missing".equals(value) || "instrumental".equals(value);
    }
    public boolean isDownloaded(String mediaKey) { return "downloaded".equals(prefs.getString(key(mediaKey), "")); }
    public boolean isInstrumental(String mediaKey) { return "instrumental".equals(prefs.getString(key(mediaKey), "")); }
    public void markNoResult(String mediaKey) { prefs.edit().putString(key(mediaKey), "missing").apply(); }
    public void markDownloaded(String mediaKey) { prefs.edit().putString(key(mediaKey), "downloaded").apply(); }
    public void markInstrumental(String mediaKey) { prefs.edit().putString(key(mediaKey), "instrumental").apply(); }
    public void clear() { prefs.edit().clear().apply(); }
}
