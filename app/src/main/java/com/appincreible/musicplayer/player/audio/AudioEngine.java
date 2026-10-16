package com.appincreible.musicplayer.player.audio;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.SystemClock;
import android.util.Log;

import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.ShuffleOrder;
import androidx.media3.session.MediaSession;

import com.appincreible.musicplayer.radio.playback.RadioPlaybackInfo;
import com.appincreible.musicplayer.power.PowerSaverManager;
import com.appincreible.musicplayer.themes.AppPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Random;

@UnstableApi
public final class AudioEngine implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String TAG = "AudioEngine";
    private final Context appContext;
    private final AudioAttributes audioAttributes;
    private ExoPlayer activePlayer;
    private ExoPlayer standbyPlayer;
    private GainAudioProcessor activeGain;
    private GainAudioProcessor standbyGain;
    private final LogicalPlayer logicalPlayer;
    private final TransitionEngine transitionEngine;
    private final NormalizationRepository normalizationRepository;
    private final List<MediaItem> queue = new ArrayList<>();
    private final Set<String> radioRecoveryAttempts = new HashSet<>();
    private static final int SONG_HISTORY_LIMIT = 200;
    private static final long PREVIOUS_RESTART_GUARD_MS = 1500L;
    private final List<String> songHistory = new ArrayList<>();
    private final Random shuffleRandom = new Random();
    private int songHistoryCursor = -1;
    private int pendingHistoryCursor = C.INDEX_UNSET;
    private String pendingHistoryAppendId = "";
    private long shuffleSeed = shuffleRandom.nextLong();
    private int[] songShuffleOrder = new int[0];
    private String previousRestartGuardMediaId = "";
    private long previousRestartGuardUntilMs;
    private boolean internalPlayerCommand;
    private boolean released;

    public AudioEngine(Context context) {
        appContext = context.getApplicationContext();
        audioAttributes = new AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build();

        PowerSaverManager.startMonitoring(appContext);
        GainAudioProcessor gainA = new GainAudioProcessor(EqualizerSpectrum.SOURCE_A);
        ExoPlayer playerA = AudioPlayerFactory.create(appContext, gainA, true);

        activePlayer = playerA;
        activeGain = gainA;
        logicalPlayer = new LogicalPlayer(activePlayer, this);
        normalizationRepository = new NormalizationRepository(appContext);
        transitionEngine = new TransitionEngine(this);

        playerA.addListener(new SlotListener(playerA));
        if (!PowerSaverManager.isActive(appContext)) createStandbyIfNeeded();
        AudioPreferences.prefs(appContext).registerOnSharedPreferenceChangeListener(this);
        AppPreferences.prefs(appContext).registerOnSharedPreferenceChangeListener(this);
        configureAudioFocusOwner();
        transitionEngine.start();
    }

    public Player getSessionPlayer() { return logicalPlayer; }

    public void attachMediaSession(MediaSession mediaSession) {
        logicalPlayer.attachMediaSession(mediaSession);
    }

    Context context() { return appContext; }
    ExoPlayer active() { return activePlayer; }
    ExoPlayer standby() { return standbyPlayer; }
    boolean hasStandby() { return standbyPlayer != null && standbyGain != null; }
    boolean powerSaverActive() { return PowerSaverManager.isActive(appContext); }
    GainAudioProcessor activeGain() { return activeGain; }
    GainAudioProcessor standbyGain() { return standbyGain; }
    List<MediaItem> queue() { return Collections.unmodifiableList(queue); }

    /** Upcoming playback order, starting at the currently playing song.
     *
     * The raw shuffle permutation is not the playback queue: the current track
     * can be in the middle of it, and Next first replays forward song history
     * after Previous. Expose the same next-track decisions to the UI instead.
     */
    public int[] getSongShuffleOrder() {
        int size = queue.size();
        if (!isSongQueue() || !activePlayer.getShuffleModeEnabled()
                || songShuffleOrder.length != size) return new int[0];
        int current = activePlayer.getCurrentMediaItemIndex();
        if (current < 0 || current >= size) return new int[0];

        // This is the complete queue display, not just the few remaining tracks.
        // Repeat-one and reaching the final shuffled item must not collapse the
        // list to a single song: the user can still choose any queued track.
        List<Integer> upcoming = new ArrayList<>(size);
        Set<Integer> included = new HashSet<>();
        upcoming.add(current);
        included.add(current);

        int tail = current;
        boolean historyAhead = false;
        // These are precisely the songs selected by seekToNextSongInHistory().
        if (songHistoryCursor >= 0 && songHistoryCursor < songHistory.size()
                && queue.get(current).mediaId.equals(songHistory.get(songHistoryCursor))) {
            for (int i = songHistoryCursor + 1; i < songHistory.size(); i++) {
                int index = findQueueIndex(songHistory.get(i));
                if (index == C.INDEX_UNSET) break;
                historyAhead = true;
                tail = index;
                if (included.add(index)) upcoming.add(index);
            }
        }

        // The physical player decides the first unplayed item. In particular it
        // knows whether the end of the queue is reached with repeat disabled.
        if (!historyAhead) {
            int next = activePlayer.getNextMediaItemIndex();
            if (next != C.INDEX_UNSET && next != current) {
                tail = next;
                if (included.add(next)) upcoming.add(next);
            }
        }

        int tailPosition = -1;
        for (int i = 0; i < songShuffleOrder.length; i++) {
            if (songShuffleOrder[i] == tail) {
                tailPosition = i;
                break;
            }
        }
        if (tailPosition < 0) return toIndexArray(upcoming);

        // Show every queued song once. Wrapping here is only for display;
        // ExoPlayer still obeys the saved repeat mode for actual playback.
        for (int step = 1; step <= size; step++) {
            int index = songShuffleOrder[(tailPosition + step) % size];
            if (included.add(index)) upcoming.add(index);
        }
        return toIndexArray(upcoming);
    }

    private static int[] toIndexArray(List<Integer> indices) {
        int[] array = new int[indices.size()];
        for (int i = 0; i < indices.size(); i++) array[i] = indices.get(i);
        return array;
    }

    /** Allows the UI to reject a response from a replaced queue. */
    public String[] getSongQueueMediaIds() {
        if (!isSongQueue()) return new String[0];
        String[] ids = new String[queue.size()];
        for (int i = 0; i < queue.size(); i++) ids[i] = queue.get(i).mediaId;
        return ids;
    }

    public void setMediaItems(List<MediaItem> mediaItems, int startIndex, long startPositionMs) {
        transitionEngine.cancelForQueueChange();
        clearPreviousRestartGuard();
        queue.clear();
        radioRecoveryAttempts.clear();
        if (mediaItems != null) queue.addAll(mediaItems);
        int safeIndex = queue.isEmpty() ? 0 : Math.max(0, Math.min(startIndex == C.INDEX_UNSET ? 0 : startIndex, queue.size() - 1));
        shuffleSeed = shuffleRandom.nextLong();
        regenerateSongShuffleOrder();
        internal(() -> {
            if (standbyPlayer != null) {
                standbyPlayer.stop();
                standbyPlayer.clearMediaItems();
                standbyGain.setEnvelopeImmediately(0f);
            }
            activeGain.setEnvelopeImmediately(1f);
            activePlayer.setMediaItems(queue, safeIndex, Math.max(0L, startPositionMs));
            applySharedShuffleOrder();
            syncModesToStandby();
        });
        resetSongHistory(activePlayer.getCurrentMediaItem());
        applyNormalization(activePlayer, activeGain);
    }


    public void replaceMediaItems(int fromIndex, int toIndex, List<MediaItem> mediaItems) {
        if (fromIndex < 0 || toIndex < fromIndex || fromIndex > queue.size()) return;
        int safeTo = Math.min(toIndex, queue.size());
        List<MediaItem> replacement = mediaItems == null ? Collections.emptyList() : mediaItems;
        boolean structuralChange = !sameMediaIds(fromIndex, safeTo, replacement);
        transitionEngine.cancelForQueueChange();
        for (int i = safeTo - 1; i >= fromIndex; i--) queue.remove(i);
        if (!replacement.isEmpty()) queue.addAll(fromIndex, replacement);
        if (structuralChange) {
            clearPreviousRestartGuard();
            shuffleSeed = shuffleRandom.nextLong();
            regenerateSongShuffleOrder();
        }
        internal(() -> {
            activePlayer.replaceMediaItems(fromIndex, safeTo, replacement);
            if (structuralChange) applySharedShuffleOrder();
            if (standbyPlayer != null) {
                standbyPlayer.stop();
                standbyPlayer.clearMediaItems();
                standbyGain.setEnvelopeImmediately(0f);
            }
        });
        if (structuralChange) resetSongHistory(activePlayer.getCurrentMediaItem());
        applyNormalization(activePlayer, activeGain);
    }

    /** Keep the logical queue and the physical player in sync when a song is dragged. */
    public void moveMediaItems(int fromIndex, int toIndex, int newIndex) {
        int size = queue.size();
        int movedCount = toIndex - fromIndex;
        if (!isSongQueue() || fromIndex < 0 || movedCount <= 0 || toIndex > size
                || newIndex < 0 || newIndex > size - movedCount || newIndex == fromIndex) return;

        // Media3's ForwardingSimpleBasePlayer normally moves only its physical player.
        // Keep AudioEngine.queue (used by transitions and preloading) in the same order.
        List<Integer> oldIndicesInNewOrder = new ArrayList<>(size);
        for (int i = 0; i < size; i++) oldIndicesInNewOrder.add(i);
        List<Integer> movedIndices = new ArrayList<>(oldIndicesInNewOrder.subList(fromIndex, toIndex));
        oldIndicesInNewOrder.subList(fromIndex, toIndex).clear();
        oldIndicesInNewOrder.addAll(newIndex, movedIndices);

        List<MediaItem> moving = new ArrayList<>(queue.subList(fromIndex, toIndex));
        queue.subList(fromIndex, toIndex).clear();
        queue.addAll(newIndex, moving);

        // Shuffle must still point to the same songs after their timeline indices change.
        if (songShuffleOrder.length == size) {
            int[] oldToNew = new int[size];
            for (int i = 0; i < size; i++) oldToNew[oldIndicesInNewOrder.get(i)] = i;
            for (int i = 0; i < songShuffleOrder.length; i++) {
                songShuffleOrder[i] = oldToNew[songShuffleOrder[i]];
            }
        }

        transitionEngine.cancelForQueueChange();
        clearPreviousRestartGuard();
        internal(() -> {
            activePlayer.moveMediaItems(fromIndex, toIndex, newIndex);
            // Any preloaded next song may now refer to a stale index.
            if (standbyPlayer != null) {
                standbyPlayer.stop();
                standbyPlayer.clearMediaItems();
                standbyGain.setEnvelopeImmediately(0f);
            }
            activeGain.setEnvelopeImmediately(activePlayer.getPlayWhenReady() ? 1f : 0f);
            applySharedShuffleOrder();
        });
    }

    public void prepare() { internal(activePlayer::prepare); }
    public void play() { transitionEngine.resumeWithFade(); }
    public void pause() { transitionEngine.pauseWithFade(); }

    /** Fast path for hardware media buttons (Bluetooth/headsets/car controls). */
    public void playFromExternalControl() {
        transitionEngine.resumeImmediate();
    }

    /** Hardware pause must acknowledge synchronously, with no fade or delayed runnable. */
    public void pauseFromExternalControl() {
        transitionEngine.pauseImmediate();
    }

    public void togglePlayPauseFromExternalControl() {
        if (activePlayer.getPlayWhenReady()) pauseFromExternalControl();
        else playFromExternalControl();
    }

    public void nextFromExternalControl() {
        if (queue.isEmpty()) return;
        if (isSongShuffleNavigationActive()) {
            int baseCursor = pendingHistoryCursor != C.INDEX_UNSET ? pendingHistoryCursor : songHistoryCursor;
            if (baseCursor >= 0 && baseCursor + 1 < songHistory.size()) {
                int targetCursor = baseCursor + 1;
                int targetIndex = findQueueIndex(songHistory.get(targetCursor));
                if (targetIndex != C.INDEX_UNSET) {
                    pendingHistoryCursor = targetCursor;
                    pendingHistoryAppendId = "";
                    performExternalImmediateTransition(targetIndex, 0L);
                }
                return;
            }
            int targetIndex = activePlayer.getNextMediaItemIndex();
            if (targetIndex == C.INDEX_UNSET || targetIndex == activePlayer.getCurrentMediaItemIndex()) return;
            markHistoryAppend(queue.get(targetIndex).mediaId);
            performExternalImmediateTransition(targetIndex, 0L);
            return;
        }
        int targetIndex = activePlayer.getNextMediaItemIndex();
        if (targetIndex != C.INDEX_UNSET && targetIndex != activePlayer.getCurrentMediaItemIndex()) {
            performExternalImmediateTransition(targetIndex, 0L);
        }
    }

    public void previousFromExternalControl() {
        MediaItem current = activePlayer.getCurrentMediaItem();
        if (!isSong(current)) {
            int targetIndex = activePlayer.getPreviousMediaItemIndex();
            if (targetIndex != C.INDEX_UNSET && targetIndex != activePlayer.getCurrentMediaItemIndex()) {
                performExternalImmediateTransition(targetIndex, 0L);
            }
            return;
        }

        boolean restartMode = AppPreferences.previousRestartsAfterThreeSeconds(appContext);
        String mediaId = current.mediaId == null ? "" : current.mediaId;
        long now = SystemClock.elapsedRealtime();
        boolean restartJustRequested = restartMode
                && mediaId.equals(previousRestartGuardMediaId)
                && now <= previousRestartGuardUntilMs;

        if (restartMode && activePlayer.getCurrentPosition() >= 3000L && !restartJustRequested) {
            previousRestartGuardMediaId = mediaId;
            previousRestartGuardUntilMs = now + PREVIOUS_RESTART_GUARD_MS;
            seekTo(0L);
            return;
        }

        clearPreviousRestartGuard();
        if (isSongShuffleNavigationActive()) {
            int baseCursor = pendingHistoryCursor != C.INDEX_UNSET ? pendingHistoryCursor : songHistoryCursor;
            if (baseCursor <= 0) return;
            int targetCursor = baseCursor - 1;
            int targetIndex = findQueueIndex(songHistory.get(targetCursor));
            if (targetIndex == C.INDEX_UNSET) return;
            pendingHistoryCursor = targetCursor;
            pendingHistoryAppendId = "";
            performExternalImmediateTransition(targetIndex, 0L);
            return;
        }

        int targetIndex = activePlayer.getPreviousMediaItemIndex();
        if (targetIndex != C.INDEX_UNSET && targetIndex != activePlayer.getCurrentMediaItemIndex()) {
            performExternalImmediateTransition(targetIndex, 0L);
        } else {
            seekTo(0L);
        }
    }

    public void stop() {
        transitionEngine.cancelForQueueChange();
        internal(() -> {
            activePlayer.stop();
            if (standbyPlayer != null) standbyPlayer.stop();
            activeGain.setEnvelopeImmediately(0f);
            if (standbyGain != null) standbyGain.setEnvelopeImmediately(0f);
        });
    }

    public void seekTo(long positionMs) {
        transitionEngine.cancelCrossfadeKeepActive();
        internal(() -> activePlayer.seekTo(Math.max(0L, positionMs)));
    }

    public void manualTransitionTo(int mediaItemIndex, long positionMs) {
        if (queue.isEmpty()) return;
        int target = Math.max(0, Math.min(mediaItemIndex, queue.size() - 1));
        MediaItem targetItem = queue.get(target);
        if (isSongShuffleNavigationActive() && target != activePlayer.getCurrentMediaItemIndex()) {
            promoteShuffleTargetToNext(target);
            markHistoryAppend(targetItem.mediaId);
        }
        performManualTransition(target, positionMs);
    }

    void handlePreviousSong() {
        MediaItem current = activePlayer.getCurrentMediaItem();
        if (!isSong(current)) return;

        boolean restartMode = AppPreferences.previousRestartsAfterThreeSeconds(appContext);
        String mediaId = current.mediaId == null ? "" : current.mediaId;
        long now = SystemClock.elapsedRealtime();
        boolean restartJustRequested = restartMode
                && mediaId.equals(previousRestartGuardMediaId)
                && now <= previousRestartGuardUntilMs;

        if (restartMode && activePlayer.getCurrentPosition() >= 3000L && !restartJustRequested) {
            previousRestartGuardMediaId = mediaId;
            previousRestartGuardUntilMs = now + PREVIOUS_RESTART_GUARD_MS;
            seekTo(0L);
            return;
        }

        clearPreviousRestartGuard();
        if (isSongShuffleNavigationActive()) {
            seekToPreviousSongInHistory();
            return;
        }

        int targetIndex = activePlayer.getPreviousMediaItemIndex();
        if (targetIndex != C.INDEX_UNSET && targetIndex != activePlayer.getCurrentMediaItemIndex()) {
            manualTransitionTo(targetIndex, 0L);
        } else {
            seekTo(0L);
        }
    }

    private void clearPreviousRestartGuard() {
        previousRestartGuardMediaId = "";
        previousRestartGuardUntilMs = 0L;
    }

    boolean isCurrentSong() {
        return isSong(activePlayer.getCurrentMediaItem());
    }

    void seekToPreviousSongInHistory() {
        if (!isSongShuffleNavigationActive()) return;
        int baseCursor = pendingHistoryCursor != C.INDEX_UNSET ? pendingHistoryCursor : songHistoryCursor;
        if (baseCursor <= 0) return;
        int targetCursor = baseCursor - 1;
        int targetIndex = findQueueIndex(songHistory.get(targetCursor));
        if (targetIndex == C.INDEX_UNSET) return;
        pendingHistoryCursor = targetCursor;
        pendingHistoryAppendId = "";
        performManualTransition(targetIndex, 0L);
    }

    void seekToNextSongInHistory() {
        if (!isSongShuffleNavigationActive()) return;
        int baseCursor = pendingHistoryCursor != C.INDEX_UNSET ? pendingHistoryCursor : songHistoryCursor;
        if (baseCursor >= 0 && baseCursor + 1 < songHistory.size()) {
            int targetCursor = baseCursor + 1;
            int targetIndex = findQueueIndex(songHistory.get(targetCursor));
            if (targetIndex == C.INDEX_UNSET) return;
            pendingHistoryCursor = targetCursor;
            pendingHistoryAppendId = "";
            performManualTransition(targetIndex, 0L);
            return;
        }
        int targetIndex = activePlayer.getNextMediaItemIndex();
        if (targetIndex == C.INDEX_UNSET || targetIndex == activePlayer.getCurrentMediaItemIndex()) return;
        markHistoryAppend(queue.get(targetIndex).mediaId);
        performManualTransition(targetIndex, 0L);
    }

    boolean isSongShuffleNavigationActive() {
        MediaItem current = activePlayer.getCurrentMediaItem();
        return activePlayer.getShuffleModeEnabled() && isSong(current);
    }

    private void performExternalImmediateTransition(int target, long positionMs) {
        if (queue.isEmpty()) return;
        int safeTarget = Math.max(0, Math.min(target, queue.size() - 1));
        long safePosition = Math.max(0L, positionMs);
        MediaItem targetItem = queue.get(safeTarget);
        boolean keepPlaying = activePlayer.getPlayWhenReady();

        transitionEngine.cancelForQueueChange();
        clearPreviousRestartGuard();

        if (targetItem.mediaId != null && targetItem.mediaId.startsWith("radio:")) {
            radioRecoveryAttempts.removeIf(key -> key.startsWith(targetItem.mediaId + "#"));
            internal(() -> {
                activePlayer.stop();
                activeGain.setEnvelopeImmediately(1f);
                activePlayer.setMediaItems(queue, safeTarget, safePosition);
                activePlayer.prepare();
                if (keepPlaying) activePlayer.play();
            });
            applyNormalization(activePlayer, activeGain);
            return;
        }

        // If the requested song is already preloaded and ready, promote it immediately instead of
        // throwing that buffer away and seeking the active player again.
        if (standbyPlayer != null
                && standbyGain != null
                && standbyPlayer.getCurrentMediaItemIndex() == safeTarget
                && standbyPlayer.getPlaybackState() == Player.STATE_READY) {
            activeGain.setEnvelopeImmediately(0f);
            standbyGain.setEnvelopeImmediately(keepPlaying ? 1f : 0f);
            internal(() -> {
                activePlayer.pause();
                if (keepPlaying) standbyPlayer.play();
                else standbyPlayer.pause();
            });
            promoteStandby();
            activeGain.setEnvelopeImmediately(keepPlaying ? 1f : 0f);
            return;
        }

        // Fast fallback: seek the currently exposed player directly. This deliberately bypasses
        // manual two-player fades for external controls so Bluetooth/Auto responds immediately.
        internal(() -> {
            if (standbyPlayer != null) {
                standbyPlayer.pause();
                standbyPlayer.stop();
                standbyGain.setEnvelopeImmediately(0f);
            }
            activePlayer.seekTo(safeTarget, safePosition);
            activeGain.setEnvelopeImmediately(keepPlaying ? 1f : 0f);
            if (keepPlaying) activePlayer.play();
        });
        applyNormalization(activePlayer, activeGain);
    }

    private void performManualTransition(int target, long positionMs) {
        if (queue.isEmpty()) return;
        long safePosition = Math.max(0L, positionMs);

        // Radio never uses the song transition engine. Stop the previous network stream immediately,
        // cancel its buffering and prepare only the newly selected station. This also covers
        // next/previous commands coming directly from MediaSession/notification controls.
        MediaItem targetItem = queue.get(target);
        if (targetItem.mediaId != null && targetItem.mediaId.startsWith("radio:")) {
            transitionEngine.cancelForQueueChange();
            radioRecoveryAttempts.removeIf(key -> key.startsWith(targetItem.mediaId + "#"));
            internal(() -> {
                // Recreate the selected live source instead of seeking inside a stopped stream.
                // External MediaSession controls (lock screen / notification / Bluetooth) reach
                // this path directly, so the target station must be prepared from a fresh source.
                activePlayer.stop();
                activeGain.setEnvelopeImmediately(1f);
                activePlayer.setMediaItems(queue, target, safePosition);
                activePlayer.prepare();
                activePlayer.play();
            });
            applyNormalization(activePlayer, activeGain);
            return;
        }

        if (PowerSaverManager.isActive(appContext) || standbyPlayer == null) {
            transitionEngine.cancelForPowerSaver();
            internal(() -> activePlayer.seekTo(target, safePosition));
            applyNormalization(activePlayer, activeGain);
            return;
        }
        transitionEngine.manualTransitionTo(target, safePosition);
    }

    public void setRepeatMode(int repeatMode) {
        internal(() -> {
            activePlayer.setRepeatMode(repeatMode);
            if (standbyPlayer != null) standbyPlayer.setRepeatMode(repeatMode);
        });
    }

    public void setShuffleModeEnabled(boolean enabled) {
        boolean changed = activePlayer.getShuffleModeEnabled() != enabled;
        internal(() -> {
            if (changed && enabled && isSongQueue()
                    && isSong(activePlayer.getCurrentMediaItem())) {
                // Every OFF -> ON starts a fresh shuffle cycle with the current
                // song first. Otherwise, if it was the last item in the old
                // permutation, Media3 reports no next item and the UI shrinks.
                shuffleSeed = shuffleRandom.nextLong();
                regenerateSongShuffleOrder();
                rotateSongShuffleOrderToCurrent();
                applySharedShuffleOrder();
            }
            activePlayer.setShuffleModeEnabled(enabled);
            if (standbyPlayer != null) standbyPlayer.setShuffleModeEnabled(enabled);
        });
        if (changed) {
            if (enabled && isSong(activePlayer.getCurrentMediaItem())) resetSongHistory(activePlayer.getCurrentMediaItem());
            else clearSongHistory();
        }
    }

    int getCurrentMediaItemIndex() { return activePlayer.getCurrentMediaItemIndex(); }
    long getCurrentPosition() { return Math.max(0L, activePlayer.getCurrentPosition()); }
    long getSeekBackIncrement() { return activePlayer.getSeekBackIncrement(); }
    long getSeekForwardIncrement() { return activePlayer.getSeekForwardIncrement(); }

    void prepareStandby(int targetIndex, long positionMs) {
        if (PowerSaverManager.isActive(appContext)) return;
        if (queue.isEmpty() || targetIndex < 0 || targetIndex >= queue.size()) return;
        createStandbyIfNeeded();
        if (standbyPlayer == null) return;
        internal(() -> {
            standbyPlayer.stop();
            standbyGain.setEnvelopeImmediately(0f);
            standbyPlayer.setMediaItems(queue, targetIndex, Math.max(0L, positionMs));
            if (isSongQueue() && songShuffleOrder.length == queue.size()) {
                standbyPlayer.setShuffleOrder(new ShuffleOrder.DefaultShuffleOrder(songShuffleOrder.clone(), shuffleSeed));
            }
            standbyPlayer.setRepeatMode(activePlayer.getRepeatMode());
            standbyPlayer.setShuffleModeEnabled(activePlayer.getShuffleModeEnabled());
            standbyPlayer.prepare();
        });
        applyNormalization(standbyPlayer, standbyGain);
    }

    void promoteStandby() {
        if (standbyPlayer == null || standbyGain == null) return;
        ExoPlayer oldActive = activePlayer;
        GainAudioProcessor oldGain = activeGain;
        activePlayer = standbyPlayer;
        activeGain = standbyGain;
        standbyPlayer = oldActive;
        standbyGain = oldGain;

        configureAudioFocusOwner();
        logicalPlayer.switchPhysicalPlayer(activePlayer);
        internal(() -> {
            standbyPlayer.pause();
            standbyPlayer.stop();
            standbyGain.setEnvelopeImmediately(0f);
        });
        commitSongTransition(activePlayer.getCurrentMediaItem());
        applyNormalization(activePlayer, activeGain);
    }

    void syncModesToStandby() {
        if (standbyPlayer == null) return;
        standbyPlayer.setRepeatMode(activePlayer.getRepeatMode());
        standbyPlayer.setShuffleModeEnabled(activePlayer.getShuffleModeEnabled());
    }

    boolean isInternalPlayerCommand() { return internalPlayerCommand; }

    void internal(Runnable action) {
        boolean old = internalPlayerCommand;
        internalPlayerCommand = true;
        try { action.run(); }
        finally { internalPlayerCommand = old; }
    }

    private void configureAudioFocusOwner() {
        internal(() -> {
            // Exactly one physical ExoPlayer owns focus/noisy handling at a time.
            if (standbyPlayer != null) {
                standbyPlayer.setHandleAudioBecomingNoisy(false);
                standbyPlayer.setAudioAttributes(audioAttributes, false);
            }
            activePlayer.setAudioAttributes(audioAttributes, true);
            activePlayer.setHandleAudioBecomingNoisy(true);
        });
    }

    void applyNormalization(ExoPlayer player, GainAudioProcessor processor) {
        if (!AudioPreferences.normalizationEnabled(appContext)) {
            processor.setNormalizationGain(1f);
            return;
        }
        MediaItem item = player.getCurrentMediaItem();
        if (item == null || item.mediaId == null || !item.mediaId.startsWith("song:")) {
            processor.setNormalizationGain(1f);
            return;
        }
        final String requestedMediaId = item.mediaId;
        normalizationRepository.getGainFactorAsync(item, AudioPreferences.normalizationTarget(appContext), factor -> {
            MediaItem current = player.getCurrentMediaItem();
            if (current != null && requestedMediaId.equals(current.mediaId)) processor.setNormalizationGain(factor);
        });
    }

    private boolean tryRecoverRadioStream(ExoPlayer player, PlaybackException error) {
        MediaItem current = player.getCurrentMediaItem();
        if (current == null || current.mediaId == null || !current.mediaId.startsWith("radio:")) return false;

        int index = player.getCurrentMediaItemIndex();
        if (index < 0 || index >= queue.size()) return false;
        String key = current.mediaId;
        String fallback = RadioPlaybackInfo.fallbackFrom(current);
        boolean useFallback = !fallback.isEmpty() && radioRecoveryAttempts.add(key + "#fallback");
        boolean retrySame = fallback.isEmpty() && radioRecoveryAttempts.add(key + "#same");
        if (!useFallback && !retrySame) return false;

        Log.w(TAG, "Radio stream failed for " + key + ": " + error.getErrorCodeName());
        String targetUrl;
        if (useFallback) {
            targetUrl = fallback;
        } else {
            targetUrl = RadioPlaybackInfo.primaryFrom(current);
            if (targetUrl.isEmpty() && current.localConfiguration != null && current.localConfiguration.uri != null) {
                targetUrl = current.localConfiguration.uri.toString();
            }
            if (targetUrl.isEmpty()) return false;
        }

        Log.i(TAG, "Retrying radio " + key + (useFallback ? " with alternate URL" : " with same URL"));
        MediaItem.Builder builder = current.buildUpon().setUri(Uri.parse(targetUrl));
        String mimeType = RadioPlaybackInfo.mimeTypeFrom(current, targetUrl);
        builder.setMimeType(mimeType);
        MediaItem retryItem = builder.build();
        queue.set(index, retryItem);

        internal(() -> {
            transitionEngine.cancelForQueueChange();
            player.stop();
            player.replaceMediaItem(index, retryItem);
            player.seekTo(index, 0L);
            activeGain.setEnvelopeImmediately(1f);
            player.prepare();
            player.play();
        });
        return true;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (PowerSaverManager.isPowerSaverPreference(key)) {
            handlePowerSaverChanged();
            return;
        }
        if (AudioPreferences.KEY_NORMALIZATION_ENABLED.equals(key)
                || AudioPreferences.KEY_NORMALIZATION_TARGET.equals(key)) {
            applyNormalization(activePlayer, activeGain);
            if (standbyPlayer != null && standbyGain != null) applyNormalization(standbyPlayer, standbyGain);
        }
        transitionEngine.onPreferencesChanged();
    }

    private void createStandbyIfNeeded() {
        if (released || standbyPlayer != null || PowerSaverManager.isActive(appContext)) return;
        GainAudioProcessor gain = new GainAudioProcessor(EqualizerSpectrum.SOURCE_B);
        ExoPlayer player = AudioPlayerFactory.create(appContext, gain, false);
        standbyGain = gain;
        standbyPlayer = player;
        player.addListener(new SlotListener(player));
        syncModesToStandby();
        configureAudioFocusOwner();
    }

    private void regenerateSongShuffleOrder() {
        if (!isSongQueue()) {
            songShuffleOrder = new int[0];
            return;
        }
        ShuffleOrder generated = new ShuffleOrder.DefaultShuffleOrder(queue.size(), shuffleSeed);
        int[] indices = new int[queue.size()];
        int count = 0;
        for (int index = generated.getFirstIndex(); index != C.INDEX_UNSET && count < indices.length;
             index = generated.getNextIndex(index)) {
            indices[count++] = index;
        }
        if (count == indices.length) songShuffleOrder = indices;
    }

    private void rotateSongShuffleOrderToCurrent() {
        int current = activePlayer.getCurrentMediaItemIndex();
        int size = songShuffleOrder.length;
        if (size != queue.size() || current < 0 || current >= size) return;
        int pivot = -1;
        for (int i = 0; i < size; i++) {
            if (songShuffleOrder[i] == current) {
                pivot = i;
                break;
            }
        }
        if (pivot <= 0) return;
        int[] rotated = new int[size];
        for (int i = 0; i < size; i++) rotated[i] = songShuffleOrder[(pivot + i) % size];
        songShuffleOrder = rotated;
    }

    private void promoteShuffleTargetToNext(int targetIndex) {
        if (!isSongQueue() || songShuffleOrder.length != queue.size()) return;
        int currentIndex = activePlayer.getCurrentMediaItemIndex();
        if (currentIndex == C.INDEX_UNSET || targetIndex == currentIndex) return;

        List<Integer> order = new ArrayList<>(songShuffleOrder.length);
        for (int index : songShuffleOrder) order.add(index);
        if (!order.remove(Integer.valueOf(targetIndex))) return;
        int currentPosition = order.indexOf(currentIndex);
        if (currentPosition < 0) return;
        order.add(currentPosition + 1, targetIndex);

        int[] updated = new int[order.size()];
        for (int i = 0; i < order.size(); i++) updated[i] = order.get(i);
        songShuffleOrder = updated;
        applySharedShuffleOrder();
    }

    private void applySharedShuffleOrder() {
        if (!isSongQueue() || songShuffleOrder.length != queue.size()) return;
        activePlayer.setShuffleOrder(new ShuffleOrder.DefaultShuffleOrder(songShuffleOrder.clone(), shuffleSeed));
        if (standbyPlayer != null && standbyPlayer.getMediaItemCount() == queue.size()) {
            standbyPlayer.setShuffleOrder(new ShuffleOrder.DefaultShuffleOrder(songShuffleOrder.clone(), shuffleSeed));
        }
    }

    private boolean sameMediaIds(int fromIndex, int toIndex, List<MediaItem> replacement) {
        int oldCount = Math.max(0, toIndex - fromIndex);
        if (oldCount != replacement.size()) return false;
        for (int i = 0; i < oldCount; i++) {
            String oldId = queue.get(fromIndex + i).mediaId;
            String newId = replacement.get(i) == null ? null : replacement.get(i).mediaId;
            if (!java.util.Objects.equals(oldId, newId)) return false;
        }
        return true;
    }

    private int findQueueIndex(String mediaId) {
        if (mediaId == null || mediaId.isEmpty()) return C.INDEX_UNSET;
        for (int i = 0; i < queue.size(); i++) {
            MediaItem item = queue.get(i);
            if (item != null && mediaId.equals(item.mediaId)) return i;
        }
        return C.INDEX_UNSET;
    }

    private void resetSongHistory(MediaItem current) {
        clearSongHistory();
        if (!isSong(current)) return;
        songHistory.add(current.mediaId);
        songHistoryCursor = 0;
    }

    private void clearSongHistory() {
        songHistory.clear();
        songHistoryCursor = -1;
        pendingHistoryCursor = C.INDEX_UNSET;
        pendingHistoryAppendId = "";
    }

    private void markHistoryAppend(String mediaId) {
        pendingHistoryCursor = C.INDEX_UNSET;
        pendingHistoryAppendId = mediaId == null ? "" : mediaId;
    }

    private void commitSongTransition(MediaItem item) {
        if (!isSong(item) || !activePlayer.getShuffleModeEnabled()) return;
        String mediaId = item.mediaId;
        if (pendingHistoryCursor != C.INDEX_UNSET) {
            int target = pendingHistoryCursor;
            pendingHistoryCursor = C.INDEX_UNSET;
            pendingHistoryAppendId = "";
            if (target >= 0 && target < songHistory.size() && mediaId.equals(songHistory.get(target))) {
                songHistoryCursor = target;
                return;
            }
        }
        if (!pendingHistoryAppendId.isEmpty()) {
            pendingHistoryAppendId = "";
            appendHistory(mediaId);
            return;
        }
        if (songHistoryCursor >= 0 && songHistoryCursor < songHistory.size()
                && mediaId.equals(songHistory.get(songHistoryCursor))) return;
        if (songHistoryCursor + 1 < songHistory.size()
                && mediaId.equals(songHistory.get(songHistoryCursor + 1))) {
            songHistoryCursor++;
            return;
        }
        appendHistory(mediaId);
    }

    private void appendHistory(String mediaId) {
        if (mediaId == null || mediaId.isEmpty()) return;
        while (songHistory.size() > songHistoryCursor + 1) songHistory.remove(songHistory.size() - 1);
        if (songHistoryCursor >= 0 && songHistoryCursor < songHistory.size()
                && mediaId.equals(songHistory.get(songHistoryCursor))) return;
        songHistory.add(mediaId);
        songHistoryCursor = songHistory.size() - 1;
        while (songHistory.size() > SONG_HISTORY_LIMIT) {
            songHistory.remove(0);
            songHistoryCursor--;
        }
    }

    private boolean isSongQueue() {
        return !queue.isEmpty() && isSong(queue.get(0));
    }

    private static boolean isSong(MediaItem item) {
        return item != null && item.mediaId != null && item.mediaId.startsWith("song:");
    }

    private void releaseStandbyForPowerSaver() {
        if (standbyPlayer == null) return;
        ExoPlayer player = standbyPlayer;
        standbyPlayer = null;
        standbyGain = null;
        internal(() -> {
            player.pause();
            player.stop();
            player.clearMediaItems();
            player.release();
        });
        configureAudioFocusOwner();
    }

    private void handlePowerSaverChanged() {
        if (PowerSaverManager.isActive(appContext)) {
            transitionEngine.cancelForPowerSaver();
            releaseStandbyForPowerSaver();
            activeGain.setNormalizationGain(1f);
        } else {
            createStandbyIfNeeded();
            applyNormalization(activePlayer, activeGain);
            transitionEngine.onPreferencesChanged();
        }
    }

    public void release() {
        if (released) return;
        released = true;
        AudioPreferences.prefs(appContext).unregisterOnSharedPreferenceChangeListener(this);
        AppPreferences.prefs(appContext).unregisterOnSharedPreferenceChangeListener(this);
        transitionEngine.release();
        normalizationRepository.close();
        internal(() -> {
            activePlayer.release();
            if (standbyPlayer != null) standbyPlayer.release();
        });
    }

    private final class SlotListener implements Player.Listener {
        private final ExoPlayer player;
        SlotListener(ExoPlayer player) { this.player = player; }

        @Override public void onPlaybackStateChanged(int playbackState) {
            transitionEngine.onPlayerStateChanged(player, playbackState);
        }

        @Override public void onMediaItemTransition(MediaItem mediaItem, int reason) {
            if (player == activePlayer) {
                clearPreviousRestartGuard();
                commitSongTransition(mediaItem);
                applyNormalization(activePlayer, activeGain);
                transitionEngine.onActiveMediaItemTransition(reason);
            }
        }

        @Override public void onPlayerError(PlaybackException error) {
            if (player == activePlayer) tryRecoverRadioStream(player, error);
        }

        @Override public void onPlaybackSuppressionReasonChanged(int playbackSuppressionReason) {
            if (player == activePlayer && playbackSuppressionReason != Player.PLAYBACK_SUPPRESSION_REASON_NONE) {
                transitionEngine.onExternalInterruption();
            }
        }

        @Override public void onPlayWhenReadyChanged(boolean playWhenReady, int reason) {
            transitionEngine.onPlayWhenReadyChanged(player, playWhenReady, reason);
            if (player != activePlayer || playWhenReady) return;
            if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS
                    || reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY
                    || reason == Player.PLAY_WHEN_READY_CHANGE_REASON_SUPPRESSED_TOO_LONG) {
                transitionEngine.onExternalInterruption();
            }
        }
    }
}
