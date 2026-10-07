package com.appincreible.musicplayer.player.service;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.DefaultMediaNotificationProvider;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;

import com.appincreible.musicplayer.MainActivity;
import com.appincreible.musicplayer.R;
import com.appincreible.musicplayer.player.audio.AudioEngine;

@UnstableApi
public class PlaybackService extends MediaSessionService {

    private static final String TAG = "PlaybackService";
    private static final String PLAYBACK_CHANNEL_ID = "appincreible_playback_v2";
    private static final int PLAYBACK_NOTIFICATION_ID = 2101;

    private AudioEngine audioEngine;
    private MediaSession mediaSession;
    private Player sessionPlayer;

    @Override
    public void onCreate() {
        super.onCreate();

        // Use our own channel instead of Media3's generic default channel. This also avoids
        // inheriting a muted/disabled legacy channel from earlier test builds on Samsung devices.
        ensurePlaybackChannel();
        DefaultMediaNotificationProvider notificationProvider =
                new DefaultMediaNotificationProvider.Builder(this)
                        .setChannelId(PLAYBACK_CHANNEL_ID)
                        .setChannelName(R.string.playback_channel_name)
                        .setNotificationId(PLAYBACK_NOTIFICATION_ID)
                        .build();
        notificationProvider.setSmallIcon(R.drawable.ic_notification_music);
        setMediaNotificationProvider(notificationProvider);

        // Keep the media notification available while AudioEngine temporarily swaps or retries
        // one of its two physical players. Media3 still owns the foreground lifecycle.
        setShowNotificationForIdlePlayer(SHOW_NOTIFICATION_FOR_IDLE_PLAYER_ALWAYS);
        setForegroundServiceTimeoutMs(DEFAULT_FOREGROUND_SERVICE_TIMEOUT_MS);

        audioEngine = new AudioEngine(this);
        sessionPlayer = audioEngine.getSessionPlayer();

        Intent sessionIntent = new Intent(this, MainActivity.class)
                .setAction(MainActivity.ACTION_OPEN_NOW_PLAYING)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent sessionActivity = PendingIntent.getActivity(
                this,
                PLAYBACK_NOTIFICATION_ID,
                sessionIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        mediaSession = new MediaSession.Builder(this, sessionPlayer)
                .setSessionActivity(sessionActivity)
                .build();
    }

    private void ensurePlaybackChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager == null) return;
        NotificationChannel existing = manager.getNotificationChannel(PLAYBACK_CHANNEL_ID);
        if (existing != null) return;
        NotificationChannel channel = new NotificationChannel(
                PLAYBACK_CHANNEL_ID,
                getString(R.string.playback_channel_name),
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(getString(R.string.playback_channel_description));
        channel.setShowBadge(false);
        channel.setSound(null, null);
        manager.createNotificationChannel(channel);
    }

    @Override
    public void onUpdateNotification(MediaSession session, boolean startInForegroundRequired) {
        Log.d(TAG, "Media notification update. foreground=" + startInForegroundRequired
                + " state=" + session.getPlayer().getPlaybackState()
                + " playing=" + session.getPlayer().isPlaying());
        super.onUpdateNotification(session, startInForegroundRequired);
    }

    @Nullable
    @Override
    public MediaSession onGetSession(MediaSession.ControllerInfo controllerInfo) {
        return mediaSession;
    }

    @Override
    public void onDestroy() {
        sessionPlayer = null;
        if (mediaSession != null) {
            mediaSession.release();
            mediaSession = null;
        }
        if (audioEngine != null) {
            audioEngine.release();
            audioEngine = null;
        }
        super.onDestroy();
    }
}
