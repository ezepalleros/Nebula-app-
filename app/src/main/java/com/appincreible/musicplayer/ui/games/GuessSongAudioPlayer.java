package com.appincreible.musicplayer.ui.games;

import android.content.Context;

import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;

import com.appincreible.musicplayer.data.model.Song;

import java.util.ArrayList;
import java.util.List;

/**
 * Standalone, notification-free fragment player shared by the offline music games.
 * It never creates a MediaSession and never publishes song metadata.
 */
@UnstableApi
public final class GuessSongAudioPlayer {
    public interface Listener {
        void onReady();
        void onError(String message);
        default void onClipPlaybackChanged(int clipIndex, boolean playing) { }
    }

    public static final class ClipSpec {
        public final Song song;
        public final long startMs;
        public final long durationMs;

        public ClipSpec(Song song, long startMs, long durationMs) {
            this.song = song;
            this.startMs = Math.max(0L, startMs);
            this.durationMs = Math.max(1L, durationMs);
        }
    }

    private final ExoPlayer player;
    private final Listener listener;
    private int activeClip;
    private boolean released;

    public GuessSongAudioPlayer(Context context, Listener listener) {
        this.listener = listener;
        AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(C.USAGE_GAME)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build();
        player = new ExoPlayer.Builder(context.getApplicationContext()).build();
        player.setAudioAttributes(attributes, true);
        player.setHandleAudioBecomingNoisy(true);
        player.setPauseAtEndOfMediaItems(true);
        player.addListener(new Player.Listener() {
            @Override public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_READY && GuessSongAudioPlayer.this.listener != null) {
                    GuessSongAudioPlayer.this.listener.onReady();
                }
            }

            @Override public void onIsPlayingChanged(boolean isPlaying) {
                if (GuessSongAudioPlayer.this.listener != null) {
                    GuessSongAudioPlayer.this.listener.onClipPlaybackChanged(activeClip, isPlaying);
                }
            }

            @Override public void onPlayerError(PlaybackException error) {
                if (GuessSongAudioPlayer.this.listener != null) {
                    GuessSongAudioPlayer.this.listener.onError("No se pudo reproducir este fragmento.");
                }
            }
        });
    }

    /** Loads arbitrary clipped fragments as one prepared playlist. */
    public void prepareClips(List<ClipSpec> clips) {
        if (released || clips == null || clips.isEmpty()) return;
        List<MediaItem> items = new ArrayList<>(clips.size());
        for (ClipSpec clip : clips) {
            if (clip == null || clip.song == null) continue;
            long songEnd = Math.max(0L, clip.song.getDurationMs());
            long requestedEnd = clip.startMs + clip.durationMs;
            long end = songEnd > 0L ? Math.min(songEnd, requestedEnd) : requestedEnd;
            if (end <= clip.startMs) end = clip.startMs + 1L;
            MediaItem.ClippingConfiguration clipping = new MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(clip.startMs)
                    .setEndPositionMs(end)
                    .build();
            items.add(new MediaItem.Builder()
                    .setUri(clip.song.getContentUri())
                    .setClippingConfiguration(clipping)
                    .build());
        }
        if (items.isEmpty()) return;
        activeClip = 0;
        player.stop();
        player.setMediaItems(items, 0, 0L);
        player.prepare();
    }

    /** Existing Adivina la canción API, now backed by the shared arbitrary-clip path. */
    public void prepareRound(Song song, long startMs, long[] durationsMs) {
        if (released || song == null || durationsMs == null || durationsMs.length == 0) return;
        List<ClipSpec> clips = new ArrayList<>(durationsMs.length);
        for (long duration : durationsMs) clips.add(new ClipSpec(song, startMs, duration));
        prepareClips(clips);
    }

    public void setActiveLevel(int level) {
        setActiveClip(level);
    }

    public void setActiveClip(int clipIndex) {
        if (released || player.getMediaItemCount() == 0) return;
        activeClip = Math.max(0, Math.min(clipIndex, player.getMediaItemCount() - 1));
        player.pause();
        player.seekTo(activeClip, 0L);
    }

    public void playCurrentFragment() {
        playClip(activeClip);
    }

    /** Only one fragment can play: selecting another pauses and seeks before starting it. */
    public void playClip(int clipIndex) {
        if (released || player.getMediaItemCount() == 0) return;
        activeClip = Math.max(0, Math.min(clipIndex, player.getMediaItemCount() - 1));
        player.pause();
        player.seekTo(activeClip, 0L);
        player.play();
    }

    public void pause() {
        if (!released) player.pause();
    }

    public boolean isReady() {
        return !released && player.getPlaybackState() == Player.STATE_READY;
    }

    public int getActiveClip() { return activeClip; }

    public void release() {
        if (released) return;
        released = true;
        player.release();
    }
}
