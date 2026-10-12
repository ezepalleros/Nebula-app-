package com.appincreible.musicplayer.ui.games;

import com.appincreible.musicplayer.data.model.Song;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Pure state for Mezcla doble. Playback and Android UI live elsewhere. */
public final class DoubleMixGameController {
    public static final long CLIP_DURATION_MS = 8_000L;

    public static final class Round {
        public final Song first;
        public final Song second;
        public final long firstStartMs;
        public final long secondStartMs;

        Round(Song first, Song second, long firstStartMs, long secondStartMs) {
            this.first = first;
            this.second = second;
            this.firstStartMs = firstStartMs;
            this.secondStartMs = secondStartMs;
        }
    }

    public static final class Result {
        public final int correctCount;
        public final int points;

        Result(int correctCount, int points) {
            this.correctCount = correctCount;
            this.points = points;
        }
    }

    private final Random random = new Random();
    private final List<Round> rounds = new ArrayList<>();
    private int roundIndex = -1;
    private int score;
    private int fullHits;
    private boolean revealed;

    public int start(List<Song> source, int requestedRounds) {
        rounds.clear();
        roundIndex = -1;
        score = 0;
        fullHits = 0;
        revealed = false;

        List<Song> songs = GamePlaylistSupport.uniqueSongs(source);
        if (songs.size() < 2) return 0;

        Collections.shuffle(songs, random);
        int count = Math.max(1, requestedRounds);
        for (int i = 0; i < count; i++) {
            Song first = songs.get((i * 2) % songs.size());
            Song second = songs.get((i * 2 + 1) % songs.size());
            if (second.getId() == first.getId()) {
                second = songs.get((i * 2 + 2) % songs.size());
            }
            rounds.add(new Round(first, second, chooseStart(first), chooseStart(second)));
        }
        return rounds.size();
    }

    public boolean beginNextRound() {
        if (roundIndex + 1 >= rounds.size()) return false;
        roundIndex++;
        revealed = false;
        return true;
    }

    public Result submit(Song answerA, Song answerB) {
        Round round = getCurrentRound();
        if (round == null || revealed) return new Result(0, 0);
        revealed = true;

        boolean aFirst = same(answerA, round.first);
        boolean aSecond = same(answerA, round.second);
        boolean bFirst = same(answerB, round.first);
        boolean bSecond = same(answerB, round.second);

        int correct = ((aFirst || bFirst) ? 1 : 0) + ((aSecond || bSecond) ? 1 : 0);
        // A duplicated answer cannot count as both songs.
        if (answerA != null && answerB != null && answerA.getId() == answerB.getId()) correct = Math.min(correct, 1);

        int points = correct == 2 ? 100 : correct == 1 ? 40 : 0;
        score += points;
        if (correct == 2) fullHits++;
        return new Result(correct, points);
    }

    private boolean same(Song answer, Song target) {
        return answer != null && target != null && answer.getId() == target.getId();
    }

    private long chooseStart(Song song) {
        long duration = Math.max(0L, song.getDurationMs());
        long maxStart = Math.max(0L, duration - CLIP_DURATION_MS);
        if (maxStart <= 0L) return 0L;
        long preferredMin = Math.min(30_000L, maxStart);
        long preferredMax = Math.min(150_000L, maxStart);
        if (preferredMax <= preferredMin) return preferredMin;
        long span = preferredMax - preferredMin + 1L;
        long positive = random.nextLong() & Long.MAX_VALUE;
        return preferredMin + positive % span;
    }

    public Round getCurrentRound() {
        return roundIndex >= 0 && roundIndex < rounds.size() ? rounds.get(roundIndex) : null;
    }

    public int getRoundIndex() { return roundIndex; }
    public int getRoundCount() { return rounds.size(); }
    public int getScore() { return score; }
    public int getFullHits() { return fullHits; }
    public boolean isRevealed() { return revealed; }
    public boolean isFinished() { return roundIndex >= rounds.size() - 1 && revealed; }
}
