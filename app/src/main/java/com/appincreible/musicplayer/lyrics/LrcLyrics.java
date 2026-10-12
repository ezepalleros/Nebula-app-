package com.appincreible.musicplayer.lyrics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable, presentation-independent lyrics representation. */
public final class LrcLyrics {
    public static final class Line {
        public final long timeMs;
        public final String text;

        public Line(long timeMs, String text) {
            this.timeMs = timeMs;
            this.text = text;
        }
    }

    public final List<Line> lines;
    public final boolean synchronizedLines;

    public LrcLyrics(List<Line> lines, boolean synchronizedLines) {
        this.lines = Collections.unmodifiableList(new ArrayList<>(lines));
        this.synchronizedLines = synchronizedLines;
    }

    public int activeLineAt(long positionMs, long additionalOffsetMs) {
        if (!synchronizedLines || lines.isEmpty()) return -1;
        long effective = positionMs - additionalOffsetMs;
        int left = 0, right = lines.size() - 1, result = -1;
        while (left <= right) {
            int middle = (left + right) >>> 1;
            if (lines.get(middle).timeMs <= effective) {
                result = middle;
                left = middle + 1;
            } else {
                right = middle - 1;
            }
        }
        return result;
    }
}
