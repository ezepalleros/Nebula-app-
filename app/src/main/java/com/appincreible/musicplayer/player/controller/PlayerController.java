package com.appincreible.musicplayer.player.controller;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.core.content.ContextCompat;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.Timeline;
import androidx.media3.session.SessionCommand;
import androidx.media3.session.SessionResult;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;

import com.appincreible.musicplayer.player.audio.AudioPreferences;
import com.appincreible.musicplayer.themes.AppPreferences;
import com.appincreible.musicplayer.player.service.PlaybackService;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PlayerController implements SharedPreferences.OnSharedPreferenceChangeListener {

    public static final int RADIO_CONNECTION_IDLE = 0;
    public static final int RADIO_CONNECTION_CONNECTING = 1;
    public static final int RADIO_CONNECTION_READY = 2;
    public static final int RADIO_CONNECTION_ERROR = 3;
    private static final long RADIO_CONNECT_TIMEOUT_MS = 10_000L;

    public static final class QueueEntry {
        public final int mediaItemIndex;
        public final String mediaId;
        public final String title;
        public final String artist;
        public final String artworkUri;
        public final boolean current;

        QueueEntry(int mediaItemIndex, String mediaId, String title, String artist,
                   String artworkUri, boolean current) {
            this.mediaItemIndex = mediaItemIndex;
            this.mediaId = mediaId == null ? "" : mediaId;
            this.title = title == null ? "" : title;
            this.artist = artist == null ? "" : artist;
            this.artworkUri = artworkUri == null ? "" : artworkUri;
            this.current = current;
        }
    }

    public interface Callback {
        void onPlayerChanged(String title, String artist, String album, String artworkUri,
                             String mediaId, boolean isPlaying, boolean hasMedia,
                             boolean shuffleEnabled, int repeatMode);
        void onControllerReady();
        void onQueueChanged(List<QueueEntry> queue);
        void onRadioConnectionChanged(String mediaId, int state, String errorMessage);
    }

    private final Context appContext;
    private final Callback callback;
    private final SharedPreferences audioPrefs;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ListenableFuture<MediaController> controllerFuture;
    private MediaController controller;
    private Runnable pendingCommand;
    private Runnable radioTimeoutRunnable;
    private int radioRequestGeneration;
    private String radioTargetMediaId = "";
    private boolean applyingSavedModes;
    private boolean manuallyReorderedSongQueue;
    private int queueRequestGeneration;
    private int shuffleQueueRetries;
    private final SessionCommand shuffleOrderCommand = new SessionCommand(
            PlaybackService.COMMAND_GET_SONG_SHUFFLE_ORDER, Bundle.EMPTY);
    private List<MediaItem> lastRadioQueue = new ArrayList<>();
    private int lastRadioIndex = C.INDEX_UNSET;
    private boolean lastRadioQueueFavorites;

    private final Player.Listener listener = new Player.Listener() {
        @Override public void onIsPlayingChanged(boolean isPlaying) { publishState(); }
        @Override public void onMediaMetadataChanged(MediaMetadata mediaMetadata) { publishState(); }

        @Override public void onPlaybackStateChanged(int playbackState) {
            if (playbackState == Player.STATE_READY && isCurrentRadioTarget()) {
                cancelRadioTimeout();
                callback.onRadioConnectionChanged(radioTargetMediaId, RADIO_CONNECTION_READY, "");
            }
            publishState();
        }

        @Override public void onMediaItemTransition(MediaItem mediaItem, int reason) {
            if (!isRadio(mediaItem)) {
                cancelRadioConnectionState();
            } else if (mediaItem != null && !mediaItem.mediaId.equals(radioTargetMediaId)) {
                trackExternalRadioTransition(mediaItem);
            }
            publishState();
            publishQueue();
        }

        @Override public void onTimelineChanged(Timeline timeline, int reason) {
            publishQueue();
        }

        @Override public void onPlayerError(PlaybackException error) {
            if (!isCurrentRadioTarget()) return;
            final int generation = radioRequestGeneration;
            final String target = radioTargetMediaId;
            mainHandler.postDelayed(() -> {
                if (controller == null || generation != radioRequestGeneration || !target.equals(radioTargetMediaId)) return;
                if (!isCurrentRadioTarget()) return;
                int state = controller.getPlaybackState();
                if (state == Player.STATE_READY || state == Player.STATE_BUFFERING) return;
                failRadioConnection("No se pudo conectar con la emisora.");
            }, 600L);
        }

        @Override public void onShuffleModeEnabledChanged(boolean shuffleModeEnabled) {
            if (!applyingSavedModes) {
                AudioPreferences.setShuffleEnabled(appContext, isCurrentRadio(), shuffleModeEnabled);
            }
            publishState();
            publishQueue();
        }

        @Override public void onRepeatModeChanged(int repeatMode) {
            if (!applyingSavedModes) {
                AudioPreferences.setRepeatMode(appContext, isCurrentRadio(), repeatMode);
            }
            publishState();
            publishQueue();
        }
    };

    public PlayerController(Context context, Callback callback) {
        appContext = context.getApplicationContext();
        this.callback = callback;
        audioPrefs = AudioPreferences.prefs(appContext);
        AudioPreferences.ensureSplitPlaybackOrder(appContext);
        audioPrefs.registerOnSharedPreferenceChangeListener(this);
        connect();
    }

    private void connect() {
        SessionToken token = new SessionToken(appContext, new ComponentName(appContext, PlaybackService.class));
        controllerFuture = new MediaController.Builder(appContext, token).buildAsync();
        controllerFuture.addListener(() -> {
            try {
                controller = controllerFuture.get();
                controller.addListener(listener);
                applySavedModes(isCurrentRadio());
                publishState();
                publishQueue();
                callback.onControllerReady();
                if (pendingCommand != null) {
                    Runnable command = pendingCommand;
                    pendingCommand = null;
                    command.run();
                }
            } catch (Exception ignored) { }
        }, ContextCompat.getMainExecutor(appContext));
    }

    /**
     * Starts a local-song queue honoring the saved repeat and shuffle preferences.
     * With REPEAT_MODE_ALL, Media3 wraps at the end of the queue (also in shuffle).
     * With repeat disabled, reaching the end stops playback as requested.
     * Radio playback is unaffected.
     */
    public void playQueue(List<MediaItem> items, int startIndex) {
        if (items == null || items.isEmpty()) return;
        cancelRadioConnectionState();
        runWhenReady(() -> {
            manuallyReorderedSongQueue = false;
            controller.setMediaItems(items, startIndex, 0L);
            // Honor the user's last choice instead of forcing repeat on every selection.
            applySavedModes(false);
            controller.prepare();
            controller.play();
        });
    }

    /**
     * Restores the complete local-song queue after a UI filter/search is cleared.
     * The current song, playback position and play/pause state are preserved.
     * Radio queues are deliberately ignored.
     */
    public void syncSongQueue(List<MediaItem> items) {
        if (items == null || items.isEmpty()) return;
        runWhenReady(() -> {
            MediaItem current = controller.getCurrentMediaItem();
            if (current == null || isRadio(current) || current.mediaId == null) return;

            int targetIndex = C.INDEX_UNSET;
            for (int i = 0; i < items.size(); i++) {
                MediaItem item = items.get(i);
                if (item != null && current.mediaId.equals(item.mediaId)) {
                    targetIndex = i;
                    break;
                }
            }
            if (targetIndex == C.INDEX_UNSET) return;

            // Avoid resetting the player when the queue already matches.
            boolean sameQueue = controller.getMediaItemCount() == items.size();
            if (sameQueue) {
                for (int i = 0; i < items.size(); i++) {
                    String existingId = controller.getMediaItemAt(i).mediaId;
                    String requestedId = items.get(i).mediaId;
                    if (!java.util.Objects.equals(existingId, requestedId)) {
                        sameQueue = false;
                        break;
                    }
                }
            }
            if (sameQueue) return;

            // Clearing a search can publish the library in its original sort order.
            // Don't silently discard a user's manual order when the songs are the same.
            if (manuallyReorderedSongQueue && controller.getMediaItemCount() == items.size()) {
                List<String> existingIds = new ArrayList<>();
                List<String> requestedIds = new ArrayList<>();
                for (int i = 0; i < items.size(); i++) {
                    existingIds.add(controller.getMediaItemAt(i).mediaId);
                    requestedIds.add(items.get(i).mediaId);
                }
                Collections.sort(existingIds);
                Collections.sort(requestedIds);
                if (existingIds.equals(requestedIds)) return;
            }
            manuallyReorderedSongQueue = false;
            long positionMs = Math.max(0L, controller.getCurrentPosition());
            boolean playWhenReady = controller.getPlayWhenReady();
            controller.setMediaItems(items, targetIndex, positionMs);
            applySavedModes(false);
            controller.prepare();
            controller.setPlayWhenReady(playWhenReady);
        });
    }

    public void playRadioQueue(List<MediaItem> items, int startIndex) {
        playRadioQueue(items, startIndex, false);
    }

    public void playRadioQueue(List<MediaItem> items, int startIndex, boolean favoritesQueue) {
        if (items == null || items.isEmpty()) return;
        List<MediaItem> copy = new ArrayList<>(items);
        int safeIndex = Math.max(0, Math.min(startIndex, copy.size() - 1));
        runWhenReady(() -> startRadioRequest(copy, safeIndex, favoritesQueue));
    }

    public void retryRadioConnection() {
        if (lastRadioQueue.isEmpty() || lastRadioIndex == C.INDEX_UNSET) return;
        List<MediaItem> copy = new ArrayList<>(lastRadioQueue);
        int safeIndex = Math.max(0, Math.min(lastRadioIndex, copy.size() - 1));
        runWhenReady(() -> startRadioRequest(copy, safeIndex, lastRadioQueueFavorites));
    }

    private void startRadioRequest(List<MediaItem> items, int startIndex, boolean favoritesQueue) {
        if (controller == null || items.isEmpty()) return;
        int safeIndex = Math.max(0, Math.min(startIndex, items.size() - 1));
        MediaItem target = items.get(safeIndex);
        if (!isRadio(target)) return;

        radioRequestGeneration++;
        cancelRadioTimeout();
        radioTargetMediaId = target.mediaId == null ? "" : target.mediaId;
        lastRadioQueue = new ArrayList<>(items);
        lastRadioIndex = safeIndex;
        lastRadioQueueFavorites = favoritesQueue;

        // Radio switching must be immediate: stop cancels the old stream/buffer instead of fading it.
        controller.stop();
        controller.setMediaItems(items, safeIndex, 0L);
        applySavedModes(true);
        publishState();
        callback.onRadioConnectionChanged(radioTargetMediaId, RADIO_CONNECTION_CONNECTING, "");

        final int generation = radioRequestGeneration;
        final String targetId = radioTargetMediaId;
        radioTimeoutRunnable = () -> {
            if (controller == null || generation != radioRequestGeneration || !targetId.equals(radioTargetMediaId)) return;
            if (!isCurrentRadioTarget() || controller.getPlaybackState() == Player.STATE_READY) return;
            failRadioConnection("La emisora tardó demasiado en responder.");
        };
        mainHandler.postDelayed(radioTimeoutRunnable, RADIO_CONNECT_TIMEOUT_MS);
        controller.prepare();
        controller.play();
    }

    private void trackExternalRadioTransition(MediaItem mediaItem) {
        if (controller == null || mediaItem == null || mediaItem.mediaId == null) return;
        radioRequestGeneration++;
        cancelRadioTimeout();
        radioTargetMediaId = mediaItem.mediaId;
        lastRadioQueue = snapshotQueue();
        lastRadioIndex = controller.getCurrentMediaItemIndex();
        callback.onRadioConnectionChanged(radioTargetMediaId, RADIO_CONNECTION_CONNECTING, "");

        final int generation = radioRequestGeneration;
        final String targetId = radioTargetMediaId;
        radioTimeoutRunnable = () -> {
            if (controller == null || generation != radioRequestGeneration || !targetId.equals(radioTargetMediaId)) return;
            if (!isCurrentRadioTarget() || controller.getPlaybackState() == Player.STATE_READY) return;
            failRadioConnection("La emisora tardó demasiado en responder.");
        };
        mainHandler.postDelayed(radioTimeoutRunnable, RADIO_CONNECT_TIMEOUT_MS);
    }

    private void failRadioConnection(String message) {
        if (controller == null || !isCurrentRadioTarget()) return;
        cancelRadioTimeout();
        controller.stop();
        callback.onRadioConnectionChanged(radioTargetMediaId, RADIO_CONNECTION_ERROR, message);
        publishState();
    }

    private void cancelRadioConnectionState() {
        cancelRadioTimeout();
        radioRequestGeneration++;
        radioTargetMediaId = "";
        lastRadioQueueFavorites = false;
        callback.onRadioConnectionChanged("", RADIO_CONNECTION_IDLE, "");
    }

    private void cancelRadioTimeout() {
        if (radioTimeoutRunnable != null) {
            mainHandler.removeCallbacks(radioTimeoutRunnable);
            radioTimeoutRunnable = null;
        }
    }

    public void updateCurrentMetadata(String title, String artist, String album, String artworkUri) {
        runWhenReady(() -> {
            int index = controller.getCurrentMediaItemIndex();
            MediaItem current = controller.getCurrentMediaItem();
            if (current == null || index < 0) return;
            MediaMetadata metadata = current.mediaMetadata.buildUpon()
                    .setTitle(title)
                    .setArtist(artist)
                    .setAlbumTitle(album)
                    .setArtworkUri(artworkUri == null || artworkUri.isEmpty() ? null : Uri.parse(artworkUri))
                    .build();
            MediaItem updated = current.buildUpon().setMediaMetadata(metadata).build();
            controller.replaceMediaItem(index, updated);
            publishState();
        });
    }

    private void runWhenReady(Runnable command) {
        if (controller != null) command.run();
        else pendingCommand = command; // latest command wins while the controller is connecting.
    }

    public void togglePlayPause() {
        if (controller == null) return;
        if (controller.isPlaying()) controller.pause(); else controller.play();
    }

    public void pause() {
        if (controller != null) controller.pause();
    }

    public void previous() {
        if (controller == null) return;
        if (isCurrentRadio()) {
            int target = controller.getPreviousMediaItemIndex();
            if (target != C.INDEX_UNSET) {
                startRadioRequest(snapshotQueue(), target, lastRadioQueueFavorites);
            }
        } else {
            controller.seekToPreviousMediaItem();
        }
    }

    public void next() {
        if (controller == null) return;
        if (isCurrentRadio()) {
            int target = controller.getNextMediaItemIndex();
            if (target == C.INDEX_UNSET && lastRadioQueueFavorites
                    && AppPreferences.radioFavoritesWrap(appContext)
                    && controller.getMediaItemCount() > 0) {
                target = 0;
            }
            if (target != C.INDEX_UNSET) {
                startRadioRequest(snapshotQueue(), target, lastRadioQueueFavorites);
            }
        } else {
            controller.seekToNextMediaItem();
        }
    }

    public void jumpToQueueItem(int mediaItemIndex) {
        if (controller == null || isCurrentRadio()) return;
        if (mediaItemIndex < 0 || mediaItemIndex >= controller.getMediaItemCount()) return;
        // The seek itself performs the manual two-player transition. Calling play() immediately
        // would cancel that pending transition in TransitionEngine.resumeWithFade().
        controller.seekTo(mediaItemIndex, 0L);
    }

    public void removeQueueItem(int mediaItemIndex) {
        if (controller == null || isCurrentRadio()) return;
        if (mediaItemIndex < 0 || mediaItemIndex >= controller.getMediaItemCount()) return;
        controller.replaceMediaItems(mediaItemIndex, mediaItemIndex + 1, Collections.emptyList());
    }

    /** Reorder local media items without resetting playback, seek position or saved modes. */
    public void moveQueueItem(int fromIndex, int toIndex) {
        if (controller == null || isCurrentRadio()) return;
        int size = controller.getMediaItemCount();
        if (fromIndex < 0 || fromIndex >= size || toIndex < 0 || toIndex >= size
                || fromIndex == toIndex || !controller.isCommandAvailable(Player.COMMAND_CHANGE_MEDIA_ITEMS)) return;
        manuallyReorderedSongQueue = true;
        controller.moveMediaItem(fromIndex, toIndex);
    }

    public void seekTo(long positionMs) { if (controller != null) controller.seekTo(positionMs); }
    public void toggleShuffle() { if (controller != null) controller.setShuffleModeEnabled(!controller.getShuffleModeEnabled()); }
    public void enableShuffle() { runWhenReady(() -> controller.setShuffleModeEnabled(true)); }
    public void cycleRepeatMode() {
        if (controller == null) return;
        int current = controller.getRepeatMode();
        controller.setRepeatMode(current == Player.REPEAT_MODE_OFF ? Player.REPEAT_MODE_ALL
                : current == Player.REPEAT_MODE_ALL ? Player.REPEAT_MODE_ONE : Player.REPEAT_MODE_OFF);
    }
    public long getCurrentPosition() { return controller == null ? 0L : Math.max(0L, controller.getCurrentPosition()); }
    public long getDuration() {
        if (controller == null) return 0L;
        long duration = controller.getDuration();
        return duration < 0 ? 0L : duration;
    }
    public boolean hasMedia() { return controller != null && controller.getCurrentMediaItem() != null; }

    private List<MediaItem> snapshotQueue() {
        List<MediaItem> items = new ArrayList<>();
        if (controller == null) return items;
        for (int i = 0; i < controller.getMediaItemCount(); i++) items.add(controller.getMediaItemAt(i));
        return items;
    }

    private boolean isCurrentRadioTarget() {
        if (radioTargetMediaId.isEmpty() || controller == null) return false;
        MediaItem current = controller.getCurrentMediaItem();
        return current != null && radioTargetMediaId.equals(current.mediaId);
    }

    private boolean isCurrentRadio() {
        return controller != null && isRadio(controller.getCurrentMediaItem());
    }

    private static boolean isRadio(MediaItem item) {
        return item != null && item.mediaId != null && item.mediaId.startsWith("radio:");
    }

    private void applySavedModes(boolean radio) {
        if (controller == null) return;
        boolean old = applyingSavedModes;
        applyingSavedModes = true;
        try {
            controller.setShuffleModeEnabled(AudioPreferences.shuffleEnabled(appContext, radio));
            controller.setRepeatMode(AudioPreferences.repeatMode(appContext, radio));
        } finally {
            applyingSavedModes = old;
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (controller == null || applyingSavedModes || !AudioPreferences.isPlaybackOrderKey(key)) return;
        boolean radio = isCurrentRadio();
        if (AudioPreferences.isPlaybackOrderKeyForMode(key, radio)) applySavedModes(radio);
    }

    private void publishQueue() {
        if (controller == null) return;
        final MediaController currentController = controller;
        final int request = ++queueRequestGeneration;
        MediaItem currentItem = currentController.getCurrentMediaItem();
        int currentIndex = currentController.getCurrentMediaItemIndex();
        if (currentItem == null || isRadio(currentItem) || currentIndex == C.INDEX_UNSET) {
            shuffleQueueRetries = 0;
            callback.onQueueChanged(Collections.emptyList());
            return;
        }

        int count = currentController.getMediaItemCount();
        if (!currentController.getShuffleModeEnabled()) {
            shuffleQueueRetries = 0;
            // Timeline order is the actual order only with shuffle off.
            List<QueueEntry> normal = new ArrayList<>(count);
            for (int i = 0; i < count; i++) normal.add(toQueueEntry(i, i == currentIndex));
            callback.onQueueChanged(normal);
            return;
        }

        // The session reports the actual shuffle permutation and forward history.
        // Do not replace an entire visible queue with a one-song placeholder when
        // a mode switch races the asynchronous session command.
        if (!currentController.getAvailableSessionCommands().contains(shuffleOrderCommand)) {
            android.util.Log.w("NebulaQueue", "Shuffle order command not ready");
            retryShuffleQueue(currentController, request);
            return;
        }
        ListenableFuture<SessionResult> future = currentController.sendCustomCommand(
                shuffleOrderCommand, Bundle.EMPTY);
        future.addListener(() -> {
            if (currentController != controller || request != queueRequestGeneration
                    || !currentController.getShuffleModeEnabled()) return;
            try {
                SessionResult result = Futures.getDone(future);
                if (result.resultCode != SessionResult.RESULT_SUCCESS) {
                    retryShuffleQueue(currentController, request);
                    return;
                }
                int[] order = result.extras.getIntArray(PlaybackService.EXTRA_SONG_SHUFFLE_ORDER);
                String[] ids = result.extras.getStringArray(PlaybackService.EXTRA_SONG_QUEUE_IDS);
                int size = currentController.getMediaItemCount();
                if (order == null || ids == null || size == 0
                        || order.length != size || ids.length != size) {
                    retryShuffleQueue(currentController, request);
                    return;
                }
                for (int i = 0; i < size; i++) {
                    if (!java.util.Objects.equals(ids[i], currentController.getMediaItemAt(i).mediaId)) {
                        retryShuffleQueue(currentController, request);
                        return;
                    }
                }
                if (order[0] != currentController.getCurrentMediaItemIndex()) {
                    retryShuffleQueue(currentController, request);
                    return;
                }
                boolean[] seen = new boolean[size];
                List<QueueEntry> shuffled = new ArrayList<>(size);
                int playingIndex = currentController.getCurrentMediaItemIndex();
                for (int index : order) {
                    if (index < 0 || index >= size || seen[index]) {
                        retryShuffleQueue(currentController, request);
                        return;
                    }
                    seen[index] = true;
                    shuffled.add(toQueueEntry(index, index == playingIndex));
                }
                shuffleQueueRetries = 0;
                callback.onQueueChanged(shuffled);
            } catch (Exception error) {
                android.util.Log.w("NebulaQueue", "Cannot read actual shuffle order", error);
                retryShuffleQueue(currentController, request);
            }
        }, ContextCompat.getMainExecutor(appContext));
    }

    /** Keep the previous full list visible during transient session updates. */
    private void retryShuffleQueue(MediaController source, int request) {
        if (source != controller || request != queueRequestGeneration
                || !source.getShuffleModeEnabled() || shuffleQueueRetries >= 4) return;
        shuffleQueueRetries++;
        mainHandler.postDelayed(() -> {
            if (source == controller && request == queueRequestGeneration
                    && source.getShuffleModeEnabled()) publishQueue();
        }, 180L);
    }

    private QueueEntry toQueueEntry(int index, boolean current) {
        MediaItem item = controller.getMediaItemAt(index);
        MediaMetadata metadata = item.mediaMetadata;
        String title = metadata.title == null ? "Sin título" : metadata.title.toString();
        String artist = metadata.artist == null ? "" : metadata.artist.toString();
        String artwork = metadata.artworkUri == null ? "" : metadata.artworkUri.toString();
        return new QueueEntry(index, item.mediaId, title, artist, artwork, current);
    }

    private void publishState() {
        if (controller == null) return;
        MediaMetadata metadata = controller.getMediaMetadata();
        String title = metadata.title == null ? "Nada reproduciéndose" : metadata.title.toString();
        String artist = metadata.artist == null ? "" : metadata.artist.toString();
        String album = metadata.albumTitle == null ? "" : metadata.albumTitle.toString();
        String artwork = metadata.artworkUri == null ? "" : metadata.artworkUri.toString();
        MediaItem currentItem = controller.getCurrentMediaItem();
        String mediaId = currentItem == null ? "" : currentItem.mediaId;
        callback.onPlayerChanged(title, artist, album, artwork, mediaId, controller.isPlaying(),
                currentItem != null, controller.getShuffleModeEnabled(), controller.getRepeatMode());
    }

    public void release() {
        queueRequestGeneration++;
        cancelRadioTimeout();
        audioPrefs.unregisterOnSharedPreferenceChangeListener(this);
        mainHandler.removeCallbacksAndMessages(null);
        if (controller != null) {
            controller.removeListener(listener);
            controller = null;
        }
        if (controllerFuture != null) {
            MediaController.releaseFuture(controllerFuture);
            controllerFuture = null;
        }
    }
}
