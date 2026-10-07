package com.appincreible.musicplayer.player.audio;

import android.content.Context;

import androidx.annotation.Nullable;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.audio.AudioSink;
import androidx.media3.exoplayer.audio.DefaultAudioSink;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;

import com.appincreible.musicplayer.BuildConfig;

import java.util.HashMap;
import java.util.Map;

@UnstableApi
final class AudioPlayerFactory {
    private AudioPlayerFactory() { }

    static ExoPlayer create(Context context, GainAudioProcessor gainProcessor, boolean manageAudioFocus) {
        DefaultRenderersFactory renderersFactory = new DefaultRenderersFactory(context) {
            @Override
            protected @Nullable AudioSink buildAudioSink(
                    Context context,
                    boolean enableFloatOutput,
                    boolean enableAudioOutputPlaybackParams) {
                return new DefaultAudioSink.Builder(context)
                        .setAudioProcessors(new AudioProcessor[]{gainProcessor})
                        .build();
            }
        };

        String userAgent = "AppIncreibleMusicPlayer/" + BuildConfig.VERSION_NAME + " (Android; Radio Streaming)";
        Map<String, String> headers = new HashMap<>();
        headers.put("Icy-MetaData", "1");
        headers.put("Accept", "*/*");
        headers.put("Connection", "keep-alive");

        DefaultHttpDataSource.Factory httpFactory = new DefaultHttpDataSource.Factory()
                .setUserAgent(userAgent)
                .setConnectTimeoutMs(8_000)
                .setReadTimeoutMs(12_000)
                .setAllowCrossProtocolRedirects(true)
                .setDefaultRequestProperties(headers);
        DefaultDataSource.Factory dataSourceFactory = new DefaultDataSource.Factory(context, httpFactory);
        DefaultMediaSourceFactory mediaSourceFactory = new DefaultMediaSourceFactory(context)
                .setDataSourceFactory(dataSourceFactory);

        AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build();

        return new ExoPlayer.Builder(context, renderersFactory)
                .setMediaSourceFactory(mediaSourceFactory)
                .setAudioAttributes(attributes, manageAudioFocus)
                .setHandleAudioBecomingNoisy(manageAudioFocus)
                .build();
    }
}
