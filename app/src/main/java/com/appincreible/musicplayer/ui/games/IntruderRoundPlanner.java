package com.appincreible.musicplayer.ui.games;

import android.content.Context;

import com.appincreible.musicplayer.data.model.Song;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Builds non-overlapping clipped media items for one El intruso round. */
final class IntruderRoundPlanner {
    private static final long PREFERRED_START_MS = 60_000L;
    private static final long PREFERRED_END_MS = 180_000L;
    private static final int SILENCE_RETRIES = 4;

    private IntruderRoundPlanner() { }

    static List<GuessSongAudioPlayer.ClipSpec> plan(Context context,
                                                    IntruderGameController.RoundSongs songs,
                                                    int mainClipCount,
                                                    long clipDurationMs,
                                                    int intruderButtonIndex) {
        if (songs == null) return new ArrayList<>();
        int mainCount = Math.max(GameRules.MIN_INTRUDER_MAIN_CLIPS,
                Math.min(GameRules.MAX_INTRUDER_MAIN_CLIPS, mainClipCount));
        long duration = Math.max(1L, clipDurationMs);
        Random random = new Random();

        GameSilenceProbe mainProbe = GameSilenceProbe.open(context, songs.mainSong);
        GameSilenceProbe intruderProbe = GameSilenceProbe.open(context, songs.intruderSong);
        try {
            List<Long> mainStarts = chooseNonOverlappingStarts(mainProbe, songs.mainSong, mainCount, duration, random);
            if (mainStarts.size() != mainCount) return new ArrayList<>();
            long intruderStart = chooseSingleStart(intruderProbe, songs.intruderSong, duration, random);
            if (intruderStart < 0L) return new ArrayList<>();

            int total = mainCount + 1;
            int intruderIndex = Math.max(0, Math.min(intruderButtonIndex, total - 1));
            List<GuessSongAudioPlayer.ClipSpec> clips = new ArrayList<>(total);
            int mainCursor = 0;
            for (int i = 0; i < total; i++) {
                if (i == intruderIndex) {
                    clips.add(new GuessSongAudioPlayer.ClipSpec(songs.intruderSong, intruderStart, duration));
                } else {
                    clips.add(new GuessSongAudioPlayer.ClipSpec(songs.mainSong, mainStarts.get(mainCursor++), duration));
                }
            }
            return clips;
        } finally {
            if (mainProbe != null) mainProbe.close();
            if (intruderProbe != null) intruderProbe.close();
        }
    }

    /**
     * Splits one valid time window into independent slots. A fragment is chosen inside each slot,
     * which guarantees distinct, non-overlapping main-song fragments without retry/backtracking.
     */
    private static List<Long> chooseNonOverlappingStarts(GameSilenceProbe probe, Song song, int count,
                                                          long duration, Random random) {
        List<Long> starts = new ArrayList<>(count);
        long songDuration = Math.max(0L, song.getDurationMs());
        long required = count * duration;
        if (songDuration < required) return starts;

        long rangeStart = 0L;
        long rangeEnd = songDuration;
        long preferredEnd = Math.min(PREFERRED_END_MS, songDuration);
        if (preferredEnd - PREFERRED_START_MS >= required) {
            rangeStart = PREFERRED_START_MS;
            rangeEnd = preferredEnd;
        }

        long rangeLength = rangeEnd - rangeStart;
        long slotWidth = rangeLength / count;
        if (slotWidth < duration) return starts;

        for (int i = 0; i < count; i++) {
            if (Thread.currentThread().isInterrupted()) return new ArrayList<>();
            long slotStart = rangeStart + (slotWidth * i);
            long slotEndExclusive = i == count - 1 ? rangeEnd : rangeStart + (slotWidth * (i + 1));
            long maxStart = slotEndExclusive - duration;
            long candidate = chooseNonSilentInRange(probe, slotStart, maxStart, random);
            if (candidate < 0L) return new ArrayList<>();
            starts.add(candidate);
        }
        return starts;
    }

    private static long chooseSingleStart(GameSilenceProbe probe, Song song, long duration, Random random) {
        long maxStart = song.getDurationMs() - duration;
        if (maxStart < 0L) return -1L;
        long preferredMax = Math.min(maxStart, PREFERRED_END_MS - duration);
        if (preferredMax >= PREFERRED_START_MS) {
            return chooseNonSilentInRange(probe, PREFERRED_START_MS, preferredMax, random);
        }
        return chooseNonSilentInRange(probe, 0L, maxStart, random);
    }

    /** Tries alternate valid starts when the decoded PCM probe reports digital silence. */
    private static long chooseNonSilentInRange(GameSilenceProbe probe, long min, long max, Random random) {
        if (max < min) return -1L;
        long fallback = randomBetween(random, min, max);
        if (probe == null) return fallback;
        for (int i = 0; i < SILENCE_RETRIES; i++) {
            if (Thread.currentThread().isInterrupted()) return -1L;
            long candidate = i == 0 ? fallback : randomBetween(random, min, max);
            if (!probe.isLikelySilent(candidate)) return candidate;
        }
        // All sampled candidates looked silent. Keep a valid clip instead of failing the whole game.
        return fallback;
    }

    private static long randomBetween(Random random, long min, long max) {
        if (max <= min) return min;
        long span = max - min + 1L;
        long positive = random.nextLong() & Long.MAX_VALUE;
        return min + (positive % span);
    }
}
