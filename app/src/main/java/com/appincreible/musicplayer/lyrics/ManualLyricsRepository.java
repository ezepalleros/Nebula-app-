package com.appincreible.musicplayer.lyrics;

import android.content.Context;
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** Guardado interno e importación/exportación SAF. Ejecutar sus operaciones en un executor. */
public final class ManualLyricsRepository {
    private final Context context;
    private final LocalLyricsStore store;

    public ManualLyricsRepository(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.store = new LocalLyricsStore(this.context);
    }

    public LrcLyrics saveText(@NonNull String songKey, @NonNull String text,
                              boolean replaceExisting) throws IOException {
        if (!replaceExisting && store.loadImported(songKey) != null)
            throw new IOException("La canción ya tiene una letra guardada; confirmar reemplazo");
        store.save(songKey, text);
        return LrcParser.parse(text);
    }

    public LrcLyrics saveTimed(@NonNull String songKey, @NonNull ManualLyricsSession session,
                               boolean replaceExisting) throws IOException {
        return saveText(songKey, session.toLrc(), replaceExisting);
    }

    public LrcLyrics importLrc(@NonNull String songKey, @NonNull Uri source,
                               boolean replaceExisting) throws IOException {
        if (!replaceExisting && store.loadImported(songKey) != null)
            throw new IOException("La canción ya tiene una letra guardada; confirmar reemplazo");
        try (InputStream in = context.getContentResolver().openInputStream(source)) {
            if (in == null) throw new FileNotFoundException("No se pudo abrir el LRC");
            return saveText(songKey, LrcParser.read(in), true);
        }
    }

    /** destination: URI devuelta por ACTION_CREATE_DOCUMENT, o documento de carpeta SAF. */
    public void exportLrc(@NonNull String songKey, @NonNull Uri destination) throws IOException {
        LrcLyrics lyrics = store.loadImported(songKey);
        if (lyrics == null) throw new FileNotFoundException("No hay letra interna para exportar");
        // Preservar tiempos al reconstruir el formato interoperable.
        StringBuilder out = new StringBuilder();
        for (LrcLyrics.Line line : lyrics.lines) {
            if (lyrics.synchronizedLines) {
                long t = Math.max(0L, line.timeMs);
                out.append(String.format(java.util.Locale.US, "[%02d:%02d.%02d]",
                        t / 60000, (t / 1000) % 60, (t % 1000) / 10));
            }
            out.append(line.text).append('\n');
        }
        try (OutputStream stream = context.getContentResolver().openOutputStream(destination, "wt")) {
            if (stream == null) throw new IOException("No se pudo escribir el documento");
            stream.write(out.toString().getBytes(StandardCharsets.UTF_8));
            stream.flush();
        }
    }

    public void delete(@NonNull String songKey) throws IOException {
        if (!store.delete(songKey)) throw new IOException("No se pudo borrar la letra guardada");
    }

    @Nullable public LrcLyrics load(@NonNull String songKey) throws IOException {
        return store.loadImported(songKey);
    }
}
