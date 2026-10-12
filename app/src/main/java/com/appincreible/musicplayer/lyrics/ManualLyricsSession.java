package com.appincreible.musicplayer.lyrics;

import androidx.annotation.NonNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Estado de sincronización por pulsaciones. No controla ni altera la reproducción. */
public final class ManualLyricsSession {
    private final List<String> lines = new ArrayList<>();
    private final List<Long> marks = new ArrayList<>();

    public ManualLyricsSession(@NonNull String lyricText) {
        String text = lyricText.replace("\r\n", "\n").replace('\r', '\n');
        for (String line : text.split("\n", -1)) {
            if (!line.trim().isEmpty()) lines.add(line.trim());
        }
    }

    public int lineCount() { return lines.size(); }
    public int nextIndex() { return marks.size(); }
    public boolean isFinished() { return marks.size() == lines.size(); }
    public String currentLine() { return isFinished() ? "" : lines.get(marks.size()); }
    public List<String> getLines() { return new ArrayList<>(lines); }

    /** positionMs lo aporta el controlador existente al pulsar; evita suposiciones sobre latencia. */
    public boolean mark(long positionMs) {
        if (isFinished()) return false;
        long safe = Math.max(0L, positionMs);
        if (!marks.isEmpty() && safe < marks.get(marks.size() - 1)) return false;
        marks.add(safe);
        return true;
    }

    public boolean undo() {
        if (marks.isEmpty()) return false;
        marks.remove(marks.size() - 1);
        return true;
    }

    /** Disponible para guardar al terminar todas las pulsaciones. */
    public String toLrc() {
        if (!isFinished() || lines.isEmpty()) throw new IllegalStateException("Faltan líneas por sincronizar");
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            long millis = marks.get(i);
            result.append(String.format(Locale.US, "[%02d:%02d.%02d]", millis / 60000,
                    (millis / 1000) % 60, (millis % 1000) / 10));
            result.append(lines.get(i)).append('\n');
        }
        return result.toString();
    }
}
