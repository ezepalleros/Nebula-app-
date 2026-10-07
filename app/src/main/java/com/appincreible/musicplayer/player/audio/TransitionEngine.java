package com.appincreible.musicplayer.player.audio;

import android.os.Handler;
import android.os.Looper;

import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;

@UnstableApi
final class TransitionEngine {
    private static final long TICK_MS = 100L;
    private static final long MANUAL_FADE_MS = 260L;

    private final AudioEngine engine;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final DjLoquendoEngine dj;
    private boolean running;
    private boolean tickerScheduled;
    private boolean crossfading;
    private boolean manualPending;
    private int pendingTargetIndex = C.INDEX_UNSET;
    private long pendingTargetPositionMs;
    private boolean manualWasPlaying;
    private int preloadedTargetIndex = C.INDEX_UNSET;
    private int generation;
    private String djDecisionKey = "";
    private boolean djArmed;
    private boolean djSpeaking;
    private boolean djAtBoundary;
    private boolean djWaitingForStandby;
    private int djTargetIndex = C.INDEX_UNSET;

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            tickerScheduled = false;
            if (!running) return;
            evaluateTransitions();
            scheduleNextEvaluation();
        }
    };

    TransitionEngine(AudioEngine engine) { this.engine = engine; this.dj = new DjLoquendoEngine(engine.context()); }

    void start() {
        if (running) return;
        running = true;
        requestEvaluation(0L);
    }

    private void requestEvaluation() {
        if (!running) return;
        if (tickerScheduled) {
            handler.removeCallbacks(ticker);
            tickerScheduled = false;
        }
        requestEvaluation(0L);
    }

    private void requestEvaluation(long delayMs) {
        if (!running || tickerScheduled) return;
        tickerScheduled = true;
        if (delayMs <= 0L) handler.post(ticker);
        else handler.postDelayed(ticker, delayMs);
    }

    private void scheduleNextEvaluation() {
        long delay = nextEvaluationDelayMs();
        if (delay >= 0L) requestEvaluation(delay);
    }

    private long nextEvaluationDelayMs() {
        if (!running || !engine.hasStandby()) return -1L;
        if (crossfading || manualPending || djWaitingForStandby) return TICK_MS;

        ExoPlayer active = engine.active();
        if (!active.isPlaying()) return -1L;
        MediaItem current = active.getCurrentMediaItem();
        if (current == null || current.mediaId == null || !current.mediaId.startsWith("song:")) return -1L;

        long transitionMs = AudioPreferences.effectiveTransitionMs(engine.context());
        boolean djEnabled = AudioPreferences.djEnabled(engine.context());
        if (transitionMs <= 0L && !djEnabled) return -1L;

        long duration = active.getDuration();
        if (duration == C.TIME_UNSET || duration <= 0L) return 1_000L;
        long remaining = Math.max(0L, duration - active.getCurrentPosition());
        long watchThreshold = 0L;
        if (djEnabled) watchThreshold = Math.max(watchThreshold, 6_000L);
        if (transitionMs > 0L) watchThreshold = Math.max(watchThreshold, transitionMs + 3_000L);

        if (remaining > watchThreshold + 5_000L) {
            return Math.min(5_000L, Math.max(1_000L, remaining - watchThreshold - 3_000L));
        }
        if (remaining > watchThreshold + 1_000L) return 500L;
        return TICK_MS;
    }

    private void cancelTicker() {
        handler.removeCallbacks(ticker);
        tickerScheduled = false;
    }

    void onPreferencesChanged() {
        if (!engine.hasStandby()) {
            cancelForPowerSaver();
            return;
        }
        if (crossfading && AudioPreferences.effectiveTransitionMs(engine.context()) <= 0L) {
            cancelCrossfadeKeepActive();
        }
        if (!AudioPreferences.djEnabled(engine.context()) && (djArmed || djSpeaking)) clearDjState(true);
        evaluateTransitions();
        requestEvaluation();
    }

    void resumeWithFade() {
        // If playback is paused at the end of an item for a DJ break, pressing play skips the speech
        // and continues the native queue immediately.
        if (djAtBoundary || djSpeaking) {
            clearDjState(true);
            engine.internal(() -> {
                engine.active().setPauseAtEndOfMediaItems(false);
                engine.active().play();
            });
            requestEvaluation();
            return;
        }
        // Invalidate a pending delayed pause/fade completion before resuming.
        generation++;
        cancelCrossfadeKeepActive();
        ExoPlayer active = engine.active();
        GainAudioProcessor gain = engine.activeGain();
        long duration = AudioPreferences.fadePauseEnabled(engine.context())
                ? AudioPreferences.fadePauseMs(engine.context()) : 0L;
        engine.internal(() -> {
            gain.setEnvelopeImmediately(duration > 0 ? 0f : 1f);
            active.play();
            if (duration > 0) gain.rampEnvelopeTo(1f, duration);
        });
        requestEvaluation();
    }

    void pauseWithFade() {
        long duration = AudioPreferences.fadePauseEnabled(engine.context())
                ? AudioPreferences.fadePauseMs(engine.context()) : 0L;
        pauseWithFade(duration);
    }

    void pauseWithFade(long durationMs) {
        if (djSpeaking) clearDjState(true);
        generation++;
        int localGeneration = generation;
        boolean promoteAfterPause = engine.hasStandby() && crossfading
                && engine.standby().isPlaying()
                && engine.standbyGain().getEnvelopeGain() > engine.activeGain().getEnvelopeGain();
        crossfading = false;
        manualPending = false;
        pendingTargetIndex = C.INDEX_UNSET;
        preloadedTargetIndex = C.INDEX_UNSET;
        long fade = Math.max(0L, durationMs);
        engine.activeGain().rampEnvelopeTo(0f, fade);
        if (engine.hasStandby()) engine.standbyGain().rampEnvelopeTo(0f, fade);
        Runnable finish = () -> {
            if (generation != localGeneration) return;
            engine.internal(() -> {
                engine.active().pause();
                if (engine.hasStandby()) engine.standby().pause();
            });
            if (promoteAfterPause) engine.promoteStandby();
            engine.activeGain().setEnvelopeImmediately(0f);
            if (engine.hasStandby()) engine.standbyGain().setEnvelopeImmediately(0f);
            cancelTicker();
        };
        if (fade == 0L) finish.run(); else handler.postDelayed(finish, fade + 20L);
    }

    void onExternalInterruption() {
        clearDjState(true);
        generation++;
        crossfading = false;
        manualPending = false;
        engine.activeGain().rampEnvelopeTo(0f, 80L);
        if (engine.hasStandby()) engine.standbyGain().rampEnvelopeTo(0f, 80L);
        handler.postDelayed(() -> engine.internal(() -> {
            engine.active().pause();
            if (engine.hasStandby()) engine.standby().pause();
        }), 90L);
    }

    void manualTransitionTo(int targetIndex, long targetPositionMs) {
        if (!engine.hasStandby()) return;
        boolean wasPlayingOrDjBreak = engine.active().getPlayWhenReady() || djSpeaking || djAtBoundary;
        clearDjState(true);
        generation++;
        crossfading = false;
        manualPending = true;
        pendingTargetIndex = targetIndex;
        pendingTargetPositionMs = targetPositionMs;
        manualWasPlaying = wasPlayingOrDjBreak;
        preloadedTargetIndex = targetIndex;
        engine.internal(() -> {
            engine.standby().pause();
            engine.standby().stop();
        });
        engine.prepareStandby(targetIndex, targetPositionMs);
        tryStartManualTransition();
        requestEvaluation();
    }

    void onPlayerStateChanged(ExoPlayer player, int playbackState) {
        if (player == engine.standby() && playbackState == Player.STATE_READY && manualPending) {
            tryStartManualTransition();
        }
        if (player == engine.standby() && playbackState == Player.STATE_READY && djWaitingForStandby) {
            startNextAfterDj();
        }
        if (player == engine.active() || player == engine.standby()) requestEvaluation();
    }

    void onPlayWhenReadyChanged(ExoPlayer player, boolean playWhenReady, int reason) {
        if (player != engine.active()) return;
        if (playWhenReady) {
            requestEvaluation();
            return;
        }
        if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM && djArmed) {
            djAtBoundary = true;
            startDjSpeech();
        } else {
            cancelTicker();
        }
    }

    void onActiveMediaItemTransition(int reason) {
        if (!crossfading) {
            preloadedTargetIndex = C.INDEX_UNSET;
            engine.activeGain().setEnvelopeImmediately(engine.active().getPlayWhenReady() ? 1f : 0f);
        }
        if (!djArmed && !djSpeaking) {
            djDecisionKey = "";
            djTargetIndex = C.INDEX_UNSET;
            engine.internal(() -> engine.active().setPauseAtEndOfMediaItems(false));
        }
        requestEvaluation();
    }

    void cancelForQueueChange() {
        clearDjState(true);
        generation++;
        crossfading = false;
        manualPending = false;
        pendingTargetIndex = C.INDEX_UNSET;
        preloadedTargetIndex = C.INDEX_UNSET;
        if (engine.hasStandby()) engine.standbyGain().setEnvelopeImmediately(0f);
    }

    void cancelCrossfadeKeepActive() {
        if (!crossfading && !manualPending) return;
        generation++;
        crossfading = false;
        manualPending = false;
        pendingTargetIndex = C.INDEX_UNSET;
        preloadedTargetIndex = C.INDEX_UNSET;
        if (engine.hasStandby()) {
            engine.internal(() -> {
                engine.standby().pause();
                engine.standby().stop();
            });
            engine.standbyGain().setEnvelopeImmediately(0f);
        }
        engine.activeGain().rampEnvelopeTo(1f, 120L);
    }

    void cancelForPowerSaver() {
        clearDjState(true);
        generation++;
        crossfading = false;
        manualPending = false;
        pendingTargetIndex = C.INDEX_UNSET;
        preloadedTargetIndex = C.INDEX_UNSET;
        cancelTicker();
        engine.internal(() -> engine.active().setPauseAtEndOfMediaItems(false));
        engine.activeGain().setEnvelopeImmediately(engine.active().getPlayWhenReady() ? 1f : 0f);
        if (engine.hasStandby()) {
            engine.internal(() -> {
                engine.standby().pause();
                engine.standby().stop();
                engine.standby().clearMediaItems();
            });
            engine.standbyGain().setEnvelopeImmediately(0f);
        }
    }

    boolean isCrossfading() { return crossfading; }

    private void evaluateTransitions() {
        if (!engine.hasStandby() || engine.powerSaverActive()) return;
        if (crossfading || djSpeaking) return;
        if (manualPending) {
            tryStartManualTransition();
            return;
        }
        ExoPlayer active = engine.active();
        MediaItem current = active.getCurrentMediaItem();
        if (current == null || !current.mediaId.startsWith("song:") || !active.isPlaying()) return;

        int target = active.getNextMediaItemIndex();
        if (target == C.INDEX_UNSET || target == active.getCurrentMediaItemIndex()) {
            if (djArmed || djSpeaking) clearDjState(true);
            else resetDjDecisionIfNeeded();
            return;
        }
        if (target < 0 || target >= engine.queue().size()) return;

        MediaItem nextItem = engine.queue().get(target);
        String decisionKey = current.mediaId + "->" + nextItem.mediaId;
        if (!decisionKey.equals(djDecisionKey)) {
            clearDjState(false);
            djDecisionKey = decisionKey;
            djArmed = dj.shouldAnnounce();
            djTargetIndex = djArmed ? target : C.INDEX_UNSET;
            engine.internal(() -> active.setPauseAtEndOfMediaItems(djArmed));
        }

        long duration = active.getDuration();
        if (duration == C.TIME_UNSET || duration <= 0L) return;
        long remaining = Math.max(0L, duration - active.getCurrentPosition());

        if (djArmed) {
            // Preload early and let ExoPlayer pause exactly at the media-item boundary. The TTS
            // runs only after PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM is received.
            if (remaining <= 6_000L && preloadedTargetIndex != target) {
                preloadedTargetIndex = target;
                engine.prepareStandby(target, 0L);
            }
            return;
        }

        long configured = AudioPreferences.effectiveTransitionMs(engine.context());
        if (configured <= 0L) {
            // duration 0 uses ExoPlayer's native queued transition, which is the gapless path.
            return;
        }
        if (AudioPreferences.skipSameAlbum(engine.context()) && isSameAlbum(active, target)) return;

        long fadeMs = Math.min(configured, Math.max(1L, duration / 2L));
        if (remaining <= fadeMs + 3_000L && preloadedTargetIndex != target) {
            preloadedTargetIndex = target;
            engine.prepareStandby(target, 0L);
        }
        if (remaining <= fadeMs + 90L && preloadedTargetIndex == target
                && engine.standby().getPlaybackState() == Player.STATE_READY) {
            startCrossfade(fadeMs);
        }
    }

    private void startDjSpeech() {
        if (!djArmed || djSpeaking || djTargetIndex == C.INDEX_UNSET) return;
        djSpeaking = true;
        djAtBoundary = true;
        engine.activeGain().setEnvelopeImmediately(0f);
        if (preloadedTargetIndex != djTargetIndex) {
            preloadedTargetIndex = djTargetIndex;
            engine.prepareStandby(djTargetIndex, 0L);
        }
        dj.speakRandom(() -> {
            djSpeaking = false;
            if (!djArmed || djTargetIndex == C.INDEX_UNSET || !engine.hasStandby()) {
                clearDjState(true);
                return;
            }
            if (engine.standby().getPlaybackState() == Player.STATE_READY) startNextAfterDj();
            else djWaitingForStandby = true;
        });
    }

    private void startNextAfterDj() {
        if (!djArmed || djTargetIndex == C.INDEX_UNSET || !engine.hasStandby()) {
            clearDjState(true);
            return;
        }
        djWaitingForStandby = false;
        int target = djTargetIndex;
        dj.cancel();
        engine.internal(() -> {
            engine.active().setPauseAtEndOfMediaItems(false);
            if (engine.hasStandby()) engine.standby().setPauseAtEndOfMediaItems(false);
        });
        engine.promoteStandby();
        engine.activeGain().setEnvelopeImmediately(0f);
        engine.internal(engine.active()::play);
        engine.activeGain().rampEnvelopeTo(1f, 240L);
        djArmed = false;
        djSpeaking = false;
        djAtBoundary = false;
        djTargetIndex = C.INDEX_UNSET;
        djDecisionKey = "";
        preloadedTargetIndex = C.INDEX_UNSET;
    }

    private void clearDjState(boolean cancelSpeech) {
        if (cancelSpeech) dj.cancel();
        djArmed = false;
        djSpeaking = false;
        djAtBoundary = false;
        djWaitingForStandby = false;
        djTargetIndex = C.INDEX_UNSET;
        djDecisionKey = "";
        engine.internal(() -> {
            engine.active().setPauseAtEndOfMediaItems(false);
            if (engine.hasStandby()) engine.standby().setPauseAtEndOfMediaItems(false);
        });
    }

    private void resetDjDecisionIfNeeded() {
        if (!djArmed && !djSpeaking) {
            djDecisionKey = "";
            djTargetIndex = C.INDEX_UNSET;
        }
    }

    private void startCrossfade(long fadeMs) {
        if (crossfading || !engine.hasStandby()) return;
        crossfading = true;
        generation++;
        int localGeneration = generation;
        engine.standbyGain().setEnvelopeImmediately(0f);
        engine.activeGain().rampEnvelopeTo(0f, fadeMs);
        engine.internal(engine.standby()::play);
        engine.standbyGain().rampEnvelopeTo(1f, fadeMs);

        handler.postDelayed(() -> {
            if (generation != localGeneration || !crossfading) return;
            crossfading = false;
            engine.promoteStandby();
            engine.activeGain().setEnvelopeImmediately(1f);
            preloadedTargetIndex = C.INDEX_UNSET;
        }, Math.max(1L, fadeMs));
    }

    private void tryStartManualTransition() {
        if (!engine.hasStandby() || !manualPending || pendingTargetIndex == C.INDEX_UNSET) return;
        if (engine.standby().getPlaybackState() != Player.STATE_READY) return;
        manualPending = false;
        if (!manualWasPlaying) {
            crossfading = false;
            engine.promoteStandby();
            engine.activeGain().setEnvelopeImmediately(0f);
            pendingTargetIndex = C.INDEX_UNSET;
            preloadedTargetIndex = C.INDEX_UNSET;
            return;
        }
        crossfading = true;
        generation++;
        int localGeneration = generation;
        engine.standbyGain().setEnvelopeImmediately(0f);
        engine.activeGain().rampEnvelopeTo(0f, MANUAL_FADE_MS);
        engine.internal(engine.standby()::play);
        engine.standbyGain().rampEnvelopeTo(1f, MANUAL_FADE_MS);
        handler.postDelayed(() -> {
            if (generation != localGeneration || !crossfading) return;
            crossfading = false;
            engine.promoteStandby();
            engine.activeGain().setEnvelopeImmediately(1f);
            pendingTargetIndex = C.INDEX_UNSET;
            preloadedTargetIndex = C.INDEX_UNSET;
        }, MANUAL_FADE_MS);
    }

    private boolean isSameAlbum(ExoPlayer active, int targetIndex) {
        MediaMetadata current = active.getMediaMetadata();
        MediaMetadata next = engine.queue().get(targetIndex).mediaMetadata;
        String a = current.albumTitle == null ? "" : current.albumTitle.toString().trim();
        String b = next.albumTitle == null ? "" : next.albumTitle.toString().trim();
        return !a.isEmpty() && a.equalsIgnoreCase(b);
    }

    void release() {
        running = false;
        tickerScheduled = false;
        generation++;
        handler.removeCallbacksAndMessages(null);
        dj.release();
    }
}
