package com.appincreible.musicplayer.ui.games;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;

import com.appincreible.musicplayer.data.model.Song;

/** Two notification-free ExoPlayers used only by Mezcla doble. */
@UnstableApi
public final class DoubleMixAudioPlayer {
    public interface Listener {
        void onReady();
        void onError(String message);
    }

    private final ExoPlayer firstPlayer;
    private final ExoPlayer secondPlayer;
    private final Listener listener;
    private boolean firstReady;
    private boolean secondReady;
    private boolean released;
    private boolean playWhenReady;

    public DoubleMixAudioPlayer(@NonNull Context context, Listener listener) {
        this.listener = listener;
        AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(C.USAGE_GAME)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build();
        firstPlayer = buildPlayer(context, attributes, true);
        secondPlayer = buildPlayer(context, attributes, false);
    }

    private ExoPlayer buildPlayer(Context context, AudioAttributes attributes, boolean first) {
        ExoPlayer player = new ExoPlayer.Builder(context.getApplicationContext()).build();
        // The normal app player is paused before starting the game. These two players must not
        // fight each other for audio focus or one would mute/pause the other.
        player.setAudioAttributes(attributes, false);
        player.setHandleAudioBecomingNoisy(true);
        player.addListener(new Player.Listener() {
            @Override public void onPlaybackStateChanged(int playbackState) {
                if (playbackState != Player.STATE_READY) return;
                if (first) firstReady = true;
                else secondReady = true;
                maybeReady();
            }

            @Override public void onPlayerError(PlaybackException error) {
                if (!released && DoubleMixAudioPlayer.this.listener != null) {
                    DoubleMixAudioPlayer.this.listener.onError("No se pudo reproducir una de las canciones.");
                }
            }
        });
        return player;
    }

    public void prepare(Song first, long firstStartMs, Song second, long secondStartMs,
                        long durationMs, float secondVolume) {
        if (released || first == null || second == null) return;
        firstReady = false;
        secondReady = false;
        playWhenReady = false;
        firstPlayer.stop();
        secondPlayer.stop();
        firstPlayer.setMediaItem(clippedItem(first, firstStartMs, durationMs));
        secondPlayer.setMediaItem(clippedItem(second, secondStartMs, durationMs));
        secondPlayer.setVolume(clamp(secondVolume));
        firstPlayer.setVolume(1f);
        firstPlayer.prepare();
        secondPlayer.prepare();
    }

    private MediaItem clippedItem(Song song, long startMs, long durationMs) {
        long safeStart = Math.max(0L, startMs);
        long songDuration = Math.max(0L, song.getDurationMs());
        long end = safeStart + Math.max(1L, durationMs);
        if (songDuration > 0L) end = Math.min(end, songDuration);
        if (end <= safeStart) end = safeStart + 1L;
        MediaItem.ClippingConfiguration clip = new MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(safeStart)
                .setEndPositionMs(end)
                .build();
        return new MediaItem.Builder()
                .setUri(song.getContentUri())
                .setClippingConfiguration(clip)
                .build();
    }

    public void playFromStart() {
        if (released) return;
        playWhenReady = true;
        if (!firstReady || !secondReady) return;
        firstPlayer.pause();
        secondPlayer.pause();
        firstPlayer.seekTo(0L);
        secondPlayer.seekTo(0L);
        firstPlayer.play();
        secondPlayer.play();
        playWhenReady = false;
    }

    public void pause() {
        if (released) return;
        playWhenReady = false;
        firstPlayer.pause();
        secondPlayer.pause();
    }

    public void setSecondVolume(float volume) {
        if (!released) secondPlayer.setVolume(clamp(volume));
    }

    private void maybeReady() {
        if (!firstReady || !secondReady) return;
        if (listener != null) listener.onReady();
        if (playWhenReady) playFromStart();
    }

    private float clamp(float value) {
        return Math.max(0.1f, Math.min(1f, value));
    }

    public void release() {
        if (released) return;
        released = true;
        firstPlayer.release();
        secondPlayer.release();
    }
}
