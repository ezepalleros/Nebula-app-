package com.appincreible.musicplayer.player.service;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Bundle;
import android.os.Build;
import android.util.Log;
import android.view.KeyEvent;

import androidx.annotation.Nullable;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.DefaultMediaNotificationProvider;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;
import androidx.media3.session.SessionCommand;
import androidx.media3.session.SessionCommands;
import androidx.media3.session.SessionResult;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import com.appincreible.musicplayer.MainActivity;
import com.appincreible.musicplayer.R;
import com.appincreible.musicplayer.player.audio.AudioEngine;

@UnstableApi
public class PlaybackService extends MediaSessionService {

    private static final String TAG = "PlaybackService";
    // Used only by Nebula's own controller to show the order the audio engine actually plays.
    public static final String COMMAND_GET_SONG_SHUFFLE_ORDER =
            "com.appincreible.musicplayer.GET_SONG_SHUFFLE_ORDER";
    public static final String EXTRA_SONG_SHUFFLE_ORDER = "song_shuffle_order";
    public static final String EXTRA_SONG_QUEUE_IDS = "song_queue_ids";
    private static final String PLAYBACK_CHANNEL_ID = "appincreible_playback_v2";
    private static final int PLAYBACK_NOTIFICATION_ID = 2101;
    // Legacy wired/Bluetooth headset hook. Using the platform keycode value keeps
    // this compatible with SDK stubs where KEYCODE_HEADSETOOK is not exposed.
    private static final int KEYCODE_HEADSET_HOOK = 79;

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
                .setCallback(new MediaSession.Callback() {
                    // Media3 1.8.0 exposes onConnect(), not onConnectAsync() (added later).
                    @Override
                    public MediaSession.ConnectionResult onConnect(
                            MediaSession session, MediaSession.ControllerInfo controllerInfo) {
                        if (!getPackageName().equals(controllerInfo.getPackageName())) {
                            return MediaSession.Callback.super.onConnect(session, controllerInfo);
                        }
                        SessionCommands commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
                                .buildUpon()
                                .add(new SessionCommand(COMMAND_GET_SONG_SHUFFLE_ORDER, Bundle.EMPTY))
                                .build();
                        return new MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                                .setAvailableSessionCommands(commands)
                                .build();
                    }

                    @Override
                    public ListenableFuture<SessionResult> onCustomCommand(
                            MediaSession session, MediaSession.ControllerInfo controllerInfo,
                            SessionCommand customCommand, Bundle args) {
                        if (COMMAND_GET_SONG_SHUFFLE_ORDER.equals(customCommand.customAction)
                                && getPackageName().equals(controllerInfo.getPackageName())
                                && audioEngine != null) {
                            Bundle result = new Bundle();
                            result.putIntArray(EXTRA_SONG_SHUFFLE_ORDER, audioEngine.getSongShuffleOrder());
                            result.putStringArray(EXTRA_SONG_QUEUE_IDS, audioEngine.getSongQueueMediaIds());
                            return Futures.immediateFuture(
                                    new SessionResult(SessionResult.RESULT_SUCCESS, result));
                        }
                        return MediaSession.Callback.super.onCustomCommand(
                                session, controllerInfo, customCommand, args);
                    }

                    @Override
                    public boolean onMediaButtonEvent(MediaSession session,
                                                      MediaSession.ControllerInfo controllerInfo,
                                                      Intent intent) {
                        if (!Intent.ACTION_MEDIA_BUTTON.equals(intent.getAction()) || audioEngine == null) {
                            return false;
                        }
                        KeyEvent event = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
                        if (event == null || !isFastMediaKey(event.getKeyCode())) return false;

                        // Consume ACTION_UP and key repeats too. The action itself runs once on the
                        // first ACTION_DOWN so legacy Bluetooth stacks cannot debounce it for seconds.
                        if (event.getAction() != KeyEvent.ACTION_DOWN || event.getRepeatCount() > 0) {
                            return true;
                        }

                        switch (event.getKeyCode()) {
                            case KeyEvent.KEYCODE_MEDIA_PLAY:
                                audioEngine.playFromExternalControl();
                                return true;
                            case KeyEvent.KEYCODE_MEDIA_PAUSE:
                                audioEngine.pauseFromExternalControl();
                                return true;
                            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                            case KEYCODE_HEADSET_HOOK:
                                audioEngine.togglePlayPauseFromExternalControl();
                                return true;
                            case KeyEvent.KEYCODE_MEDIA_NEXT:
                                audioEngine.nextFromExternalControl();
                                return true;
                            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                                audioEngine.previousFromExternalControl();
                                return true;
                            case KeyEvent.KEYCODE_MEDIA_STOP:
                                audioEngine.stop();
                                return true;
                            default:
                                return false;
                        }
                    }
                })
                .build();
        audioEngine.attachMediaSession(mediaSession);
    }

    private static boolean isFastMediaKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_MEDIA_PLAY
                || keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE
                || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                || keyCode == KEYCODE_HEADSET_HOOK
                || keyCode == KeyEvent.KEYCODE_MEDIA_NEXT
                || keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS
                || keyCode == KeyEvent.KEYCODE_MEDIA_STOP;
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
