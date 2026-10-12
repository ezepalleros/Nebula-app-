package com.appincreible.musicplayer.player.audio;

import androidx.media3.common.C;
import androidx.media3.common.ForwardingSimpleBasePlayer;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.annotation.Nullable;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.MediaSession;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.List;

@UnstableApi
public final class LogicalPlayer extends ForwardingSimpleBasePlayer {
    private final AudioEngine engine;
    @Nullable private MediaSession mediaSession;

    LogicalPlayer(Player initialPlayer, AudioEngine engine) {
        super(initialPlayer);
        this.engine = engine;
    }

    void switchPhysicalPlayer(Player newPlayer) {
        setPlayer(newPlayer);
    }

    void attachMediaSession(@Nullable MediaSession session) {
        mediaSession = session;
    }

    private boolean isExternalControllerRequest() {
        MediaSession session = mediaSession;
        if (session == null) return false;
        MediaSession.ControllerInfo controller = session.getControllerForCurrentRequest();
        if (controller == null) return false;
        String packageName = controller.getPackageName();
        return packageName == null || !engine.context().getPackageName().equals(packageName);
    }

    @Override
    protected ListenableFuture<?> handleSetPlayWhenReady(boolean playWhenReady) {
        if (isExternalControllerRequest()) {
            if (playWhenReady) engine.playFromExternalControl();
            else engine.pauseFromExternalControl();
        } else {
            if (playWhenReady) engine.play();
            else engine.pause();
        }
        return Futures.immediateVoidFuture();
    }

    @Override
    protected ListenableFuture<?> handleSetMediaItems(List<MediaItem> mediaItems, int startIndex, long startPositionMs) {
        engine.setMediaItems(mediaItems, startIndex, startPositionMs);
        return Futures.immediateVoidFuture();
    }

    @Override
    protected ListenableFuture<?> handleReplaceMediaItems(int fromIndex, int toIndex, List<MediaItem> mediaItems) {
        engine.replaceMediaItems(fromIndex, toIndex, mediaItems);
        return Futures.immediateVoidFuture();
    }

    @Override
    protected ListenableFuture<?> handlePrepare() {
        engine.prepare();
        return Futures.immediateVoidFuture();
    }

    @Override
    protected ListenableFuture<?> handleStop() {
        engine.stop();
        return Futures.immediateVoidFuture();
    }

    @Override
    protected ListenableFuture<?> handleSetRepeatMode(@Player.RepeatMode int repeatMode) {
        engine.setRepeatMode(repeatMode);
        return Futures.immediateVoidFuture();
    }

    @Override
    protected ListenableFuture<?> handleSetShuffleModeEnabled(boolean shuffleModeEnabled) {
        engine.setShuffleModeEnabled(shuffleModeEnabled);
        return Futures.immediateVoidFuture();
    }

    @Override
    protected ListenableFuture<?> handleSeek(int mediaItemIndex, long positionMs, @Player.Command int seekCommand) {
        if (isExternalControllerRequest()) {
            switch (seekCommand) {
                case Player.COMMAND_SEEK_TO_PREVIOUS:
                case Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM:
                    engine.previousFromExternalControl();
                    return Futures.immediateVoidFuture();
                case Player.COMMAND_SEEK_TO_NEXT:
                case Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM:
                    engine.nextFromExternalControl();
                    return Futures.immediateVoidFuture();
                default:
                    break;
            }
        }
        switch (seekCommand) {
            case Player.COMMAND_SEEK_TO_PREVIOUS:
            case Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM:
                if (engine.isCurrentSong()) {
                    engine.handlePreviousSong();
                } else if (mediaItemIndex != C.INDEX_UNSET && mediaItemIndex != engine.getCurrentMediaItemIndex()) {
                    engine.manualTransitionTo(mediaItemIndex, positionMs == C.TIME_UNSET ? 0L : positionMs);
                } else if (positionMs != C.TIME_UNSET) {
                    engine.seekTo(positionMs);
                }
                break;
            case Player.COMMAND_SEEK_TO_NEXT:
            case Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM:
                if (engine.isSongShuffleNavigationActive()) {
                    engine.seekToNextSongInHistory();
                } else if (mediaItemIndex != C.INDEX_UNSET && mediaItemIndex != engine.getCurrentMediaItemIndex()) {
                    engine.manualTransitionTo(mediaItemIndex, positionMs == C.TIME_UNSET ? 0L : positionMs);
                } else if (positionMs != C.TIME_UNSET) {
                    engine.seekTo(positionMs);
                }
                break;
            case Player.COMMAND_SEEK_TO_MEDIA_ITEM:
                if (mediaItemIndex != C.INDEX_UNSET && mediaItemIndex != engine.getCurrentMediaItemIndex()) {
                    engine.manualTransitionTo(mediaItemIndex, positionMs == C.TIME_UNSET ? 0L : positionMs);
                } else if (positionMs != C.TIME_UNSET) {
                    engine.seekTo(positionMs);
                }
                break;
            case Player.COMMAND_SEEK_BACK:
                engine.seekTo(Math.max(0L, engine.getCurrentPosition() - engine.getSeekBackIncrement()));
                break;
            case Player.COMMAND_SEEK_FORWARD:
                engine.seekTo(engine.getCurrentPosition() + engine.getSeekForwardIncrement());
                break;
            case Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM:
            case Player.COMMAND_SEEK_TO_DEFAULT_POSITION:
            default:
                if (mediaItemIndex != C.INDEX_UNSET && mediaItemIndex != engine.getCurrentMediaItemIndex()) {
                    engine.manualTransitionTo(mediaItemIndex, positionMs == C.TIME_UNSET ? 0L : positionMs);
                } else {
                    engine.seekTo(positionMs == C.TIME_UNSET ? 0L : positionMs);
                }
                break;
        }
        return Futures.immediateVoidFuture();
    }

    @Override
    protected ListenableFuture<?> handleRelease() {
        // PlaybackService owns AudioEngine lifecycle.
        return Futures.immediateVoidFuture();
    }
}
