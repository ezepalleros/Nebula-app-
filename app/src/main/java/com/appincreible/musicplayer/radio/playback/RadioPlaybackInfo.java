package com.appincreible.musicplayer.radio.playback;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.MimeTypes;

import com.appincreible.musicplayer.radio.model.RadioStation;

import java.util.Locale;

public final class RadioPlaybackInfo {
    public static final String EXTRA_PRIMARY_URL = "radio_primary_url";
    public static final String EXTRA_FALLBACK_URL = "radio_fallback_url";
    public static final String EXTRA_CODEC = "radio_codec";
    public static final String EXTRA_HLS = "radio_hls";

    private RadioPlaybackInfo() { }

    public static String primaryUrl(RadioStation station) {
        String original = station.getOriginalStreamUrl();
        String resolved = station.getResolvedStreamUrl();
        // Prefer the station-provided URL when it is a direct stream or stable redirect endpoint.
        // Radio Browser's resolved URL is essential only when the original is a playlist container.
        if (!original.isEmpty() && !looksLikePlaylistFile(original)) return original;
        return !resolved.isEmpty() ? resolved : original;
    }

    public static String fallbackUrl(RadioStation station) {
        String primary = primaryUrl(station);
        String resolved = station.getResolvedStreamUrl();
        String original = station.getOriginalStreamUrl();
        if (!resolved.isEmpty() && !resolved.equals(primary)) return resolved;
        if (!original.isEmpty() && !original.equals(primary) && !looksLikePlaylistFile(original)) return original;
        return "";
    }

    public static @Nullable String mimeType(RadioStation station, String url) {
        String lowerUrl = safe(url).toLowerCase(Locale.ROOT);
        if (station.isHls() || lowerUrl.contains(".m3u8")) return MimeTypes.APPLICATION_M3U8;
        // Progressive MP3/AAC/OGG streams are intentionally left without a MIME hint so
        // ExoPlayer can inspect the actual response/extractor. HLS does need an explicit hint
        // when the URL has no .m3u8 suffix.
        return null;
    }

    public static Bundle extras(RadioStation station) {
        Bundle extras = new Bundle();
        String primary = primaryUrl(station);
        extras.putString(EXTRA_PRIMARY_URL, primary);
        extras.putString(EXTRA_FALLBACK_URL, fallbackUrl(station));
        extras.putString(EXTRA_CODEC, station.getCodec());
        extras.putBoolean(EXTRA_HLS, station.isHls());
        return extras;
    }


    public static String primaryFrom(MediaItem item) {
        MediaMetadata metadata = item == null ? null : item.mediaMetadata;
        Bundle extras = metadata == null ? null : metadata.extras;
        return extras == null ? "" : safe(extras.getString(EXTRA_PRIMARY_URL));
    }

    public static String fallbackFrom(MediaItem item) {
        MediaMetadata metadata = item == null ? null : item.mediaMetadata;
        Bundle extras = metadata == null ? null : metadata.extras;
        return extras == null ? "" : safe(extras.getString(EXTRA_FALLBACK_URL));
    }

    public static @Nullable String mimeTypeFrom(MediaItem item, String url) {
        MediaMetadata metadata = item == null ? null : item.mediaMetadata;
        Bundle extras = metadata == null ? null : metadata.extras;
        if (extras != null && extras.getBoolean(EXTRA_HLS, false)) return MimeTypes.APPLICATION_M3U8;
        String lowerUrl = safe(url).toLowerCase(Locale.ROOT);
        if (lowerUrl.contains(".m3u8")) return MimeTypes.APPLICATION_M3U8;
        return null;
    }

    private static boolean looksLikePlaylistFile(String url) {
        String lower = safe(url).toLowerCase(Locale.ROOT);
        return lower.endsWith(".pls") || lower.endsWith(".m3u") || lower.endsWith(".m3u8")
                || lower.endsWith(".asx") || lower.endsWith(".xspf");
    }

    private static String safe(String value) { return value == null ? "" : value.trim(); }
}
