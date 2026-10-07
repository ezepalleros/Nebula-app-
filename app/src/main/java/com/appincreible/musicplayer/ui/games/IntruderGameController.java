package com.appincreible.musicplayer.ui.games;

import com.appincreible.musicplayer.data.model.Song;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Pure round/song/score state for El intruso. Audio clip planning is kept separate. */
public final class IntruderGameController {
    public static final class RoundSongs {
        public final Song mainSong;
        public final Song intruderSong;

        RoundSongs(Song mainSong, Song intruderSong) {
            this.mainSong = mainSong;
            this.intruderSong = intruderSong;
        }
    }

    public static final class RoundResult {
        public final boolean correct;
        public final int pointsAwarded;

        RoundResult(boolean correct, int pointsAwarded) {
            this.correct = correct;
            this.pointsAwarded = pointsAwarded;
        }
    }

    private final Random random = new Random();
    private final List<RoundSongs> rounds = new ArrayList<>();
    private int roundIndex = -1;
    private int intruderButtonIndex = -1;
    private int listenCount;
    private int score;
    private int hits;
    private int totalListens;
    private boolean revealed;

    public int start(List<Song> source, int requestedRounds, int mainClipCount, long clipDurationMs) {
        rounds.clear();
        List<Song> unique = GamePlaylistSupport.uniqueSongs(source);
        Collections.shuffle(unique, random);

        long requiredMainDuration = minimumMainDuration(mainClipCount, clipDurationMs);
        List<Song> eligible = new ArrayList<>();
        List<Song> mainCandidates = new ArrayList<>();
        for (Song song : unique) {
            if (song.getDurationMs() >= clipDurationMs) eligible.add(song);
            if (song.getDurationMs() >= requiredMainDuration) mainCandidates.add(song);
        }
        Collections.shuffle(mainCandidates, random);

        int safeRounds = Math.max(0, Math.min(requestedRounds,
                Math.min(mainCandidates.size(), eligible.size() / 2)));
        List<Song> mains = new ArrayList<>(mainCandidates.subList(0, safeRounds));
        List<Song> intruderPool = new ArrayList<>();
        for (Song song : eligible) {
            boolean usedAsMain = false;
            for (Song main : mains) {
                if (main.getId() == song.getId()) {
                    usedAsMain = true;
                    break;
                }
            }
            if (!usedAsMain) intruderPool.add(song);
        }
        Collections.shuffle(intruderPool, random);
        for (int i = 0; i < safeRounds; i++) {
            rounds.add(new RoundSongs(mains.get(i), intruderPool.get(i)));
        }

        roundIndex = -1;
        intruderButtonIndex = -1;
        listenCount = 0;
        score = 0;
        hits = 0;
        totalListens = 0;
        revealed = false;
        return rounds.size();
    }

    public boolean beginNextRound(int totalButtons) {
        if (roundIndex + 1 >= rounds.size()) return false;
        roundIndex++;
        listenCount = 0;
        revealed = false;
        intruderButtonIndex = random.nextInt(Math.max(1, totalButtons));
        return true;
    }

    public void registerListen() {
        if (!revealed) listenCount++;
    }

    public RoundResult submit(int selectedButtonIndex) {
        if (revealed || getCurrentRound() == null) return new RoundResult(false, 0);
        boolean correct = selectedButtonIndex == intruderButtonIndex;
        int points = correct ? GameRules.intruderPointsForListens(Math.max(1, listenCount)) : 0;
        if (correct) {
            score += points;
            hits++;
        }
        totalListens += listenCount;
        revealed = true;
        return new RoundResult(correct, points);
    }

    static long minimumMainDuration(int clipCount, long clipDurationMs) {
        int count = Math.max(GameRules.MIN_INTRUDER_MAIN_CLIPS,
                Math.min(GameRules.MAX_INTRUDER_MAIN_CLIPS, clipCount));
        long duration = Math.max(1L, clipDurationMs);
        return count * duration;
    }

    public RoundSongs getCurrentRound() {
        return roundIndex >= 0 && roundIndex < rounds.size() ? rounds.get(roundIndex) : null;
    }

    public int getRoundIndex() { return roundIndex; }
    public int getRoundCount() { return rounds.size(); }
    public int getIntruderButtonIndex() { return intruderButtonIndex; }
    public int getListenCount() { return listenCount; }
    public int getScore() { return score; }
    public int getHits() { return hits; }
    public boolean isRevealed() { return revealed; }
    public boolean isFinished() { return roundIndex >= 0 && roundIndex >= rounds.size() - 1 && revealed; }
    public float getAverageListens() {
        int completed = revealed ? roundIndex + 1 : Math.max(0, roundIndex);
        return completed <= 0 ? 0f : (float) totalListens / (float) completed;
    }
}
