package com.appincreible.musicplayer.ui.games;

import com.appincreible.musicplayer.data.model.Song;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Pure game-state logic for Adivina la canción. No playback or Android UI lives here. */
public final class GuessSongGameController {
    /** Kept as an alias for compatibility; durations live in GameRules. */
    public static final long[] LEVEL_DURATIONS_MS = GameRules.CLIP_DURATIONS_MS;
    public static final int[] POINTS_BY_LEVEL = {
            1000, 850, 700, 550, 450, 350, 250, 150, 100
    };

    public static final class RoundResult {
        public final boolean correct;
        public final boolean revealed;
        public final int pointsAwarded;

        RoundResult(boolean correct, boolean revealed, int pointsAwarded) {
            this.correct = correct;
            this.revealed = revealed;
            this.pointsAwarded = pointsAwarded;
        }
    }

    private final Random random = new Random();
    private final List<Song> rounds = new ArrayList<>();
    private int roundIndex = -1;
    private int levelIndex;
    private int score;
    private int hits;
    private int hitLevelSum;
    private long clipStartMs;
    private boolean revealed;

    public int start(List<Song> source, int requestedRounds) {
        rounds.clear();
        rounds.addAll(GamePlaylistSupport.uniqueSongs(source));
        Collections.shuffle(rounds, random);
        int safeRounds = Math.max(0, Math.min(requestedRounds, rounds.size()));
        if (rounds.size() > safeRounds) rounds.subList(safeRounds, rounds.size()).clear();
        score = 0;
        hits = 0;
        hitLevelSum = 0;
        roundIndex = -1;
        levelIndex = 0;
        revealed = false;
        return safeRounds;
    }

    public boolean beginNextRound() {
        if (roundIndex + 1 >= rounds.size()) return false;
        roundIndex++;
        levelIndex = 0;
        revealed = false;
        clipStartMs = chooseClipStart(rounds.get(roundIndex));
        return true;
    }

    public RoundResult submit(Song selected) {
        if (revealed || getCurrentSong() == null || selected == null) return new RoundResult(false, false, 0);
        if (selected.getId() == getCurrentSong().getId()) {
            int points = POINTS_BY_LEVEL[Math.min(levelIndex, POINTS_BY_LEVEL.length - 1)];
            score += points;
            hits++;
            hitLevelSum += levelIndex + 1;
            revealed = true;
            return new RoundResult(true, true, points);
        }
        return advanceAfterMiss();
    }

    public RoundResult skip() {
        if (revealed || getCurrentSong() == null) return new RoundResult(false, false, 0);
        return advanceAfterMiss();
    }

    private RoundResult advanceAfterMiss() {
        if (levelIndex < LEVEL_DURATIONS_MS.length - 1) {
            levelIndex++;
            return new RoundResult(false, false, 0);
        }
        revealed = true;
        return new RoundResult(false, true, 0);
    }

    private long chooseClipStart(Song song) {
        long duration = Math.max(0L, song.getDurationMs());
        long longest = LEVEL_DURATIONS_MS[LEVEL_DURATIONS_MS.length - 1];
        long maxStart = Math.max(0L, duration - longest);
        if (maxStart <= 0L) return 0L;

        long preferredLow = 60_000L;
        long preferredHigh = Math.min(180_000L, maxStart);
        if (preferredHigh >= preferredLow) return randomBetween(preferredLow, preferredHigh);
        return randomBetween(0L, maxStart);
    }

    private long randomBetween(long min, long max) {
        if (max <= min) return min;
        long span = max - min + 1L;
        long positive = random.nextLong() & Long.MAX_VALUE;
        return min + (positive % span);
    }

    public Song getCurrentSong() {
        return roundIndex >= 0 && roundIndex < rounds.size() ? rounds.get(roundIndex) : null;
    }

    public int getRoundIndex() { return roundIndex; }
    public int getRoundCount() { return rounds.size(); }
    public int getLevelIndex() { return levelIndex; }
    public long getClipStartMs() { return clipStartMs; }
    public long getCurrentClipDurationMs() {
        Song song = getCurrentSong();
        if (song == null) return 0L;
        long remaining = Math.max(0L, song.getDurationMs() - clipStartMs);
        return Math.min(LEVEL_DURATIONS_MS[levelIndex], remaining);
    }
    public long getLevelDurationMs(int index) {
        Song song = getCurrentSong();
        if (song == null) return 0L;
        int safe = Math.max(0, Math.min(index, LEVEL_DURATIONS_MS.length - 1));
        long remaining = Math.max(0L, song.getDurationMs() - clipStartMs);
        return Math.min(LEVEL_DURATIONS_MS[safe], remaining);
    }
    public int getScore() { return score; }
    public int getHits() { return hits; }
    public boolean isRevealed() { return revealed; }
    public boolean isFinished() { return roundIndex >= 0 && roundIndex >= rounds.size() - 1 && revealed; }
    public float getAverageHitLevel() { return hits == 0 ? 0f : (float) hitLevelSum / (float) hits; }
}
