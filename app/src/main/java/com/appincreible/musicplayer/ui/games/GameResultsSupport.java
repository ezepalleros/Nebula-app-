package com.appincreible.musicplayer.ui.games;

import android.widget.TextView;

/** Shared result-card binding for the offline games. */
public final class GameResultsSupport {
    private GameResultsSupport() { }

    public static void bind(TextView scoreView, TextView hitsView, TextView averageView, TextView bestView,
                            int score, int hits, int rounds, String averageLine, int bestScore) {
        scoreView.setText("Puntaje total: " + score);
        hitsView.setText("Aciertos: " + hits + "/" + rounds);
        averageView.setText(averageLine);
        bestView.setText("Mejor puntaje en esta playlist: " + bestScore);
    }
}
