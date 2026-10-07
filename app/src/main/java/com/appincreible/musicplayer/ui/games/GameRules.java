package com.appincreible.musicplayer.ui.games;

/** Shared gameplay constants used by every offline music game. */
public final class GameRules {
    private GameRules() { }

    /** Single source of truth for fragment durations across games. */
    public static final long[] CLIP_DURATIONS_MS = {
            500L, 1_000L, 2_000L, 5_000L, 7_000L, 9_000L, 11_000L, 13_000L, 15_000L
    };

    public static final int DEFAULT_ROUNDS = 10;
    public static final int MIN_INTRUDER_MAIN_CLIPS = 3;
    public static final int MAX_INTRUDER_MAIN_CLIPS = 8;

    /** Base score for El intruso before the listening-count bonus is applied. */
    public static final int INTRUDER_BASE_POINTS = 1000;

    /** Bonus by total fragment plays in the round: 1, 2, 3, 4, 5+ listens. */
    public static final int[] INTRUDER_LISTEN_BONUS = {500, 350, 220, 120, 0};

    public static int intruderPointsForListens(int listens) {
        int safe = Math.max(1, listens);
        int index = Math.min(safe - 1, INTRUDER_LISTEN_BONUS.length - 1);
        return INTRUDER_BASE_POINTS + INTRUDER_LISTEN_BONUS[index];
    }
}
