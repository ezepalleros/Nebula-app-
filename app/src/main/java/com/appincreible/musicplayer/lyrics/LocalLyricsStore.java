package com.appincreible.musicplayer.lyrics;

import android.content.Context;
import android.net.Uri;
import android.provider.DocumentsContract;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Per-song imported LRC cache; no Room or broad storage permission required. */
public final class LocalLyricsStore {
    private final Context context;
    private final File directory;

    public LocalLyricsStore(@NonNull Context context) {
        this.context = context.getApplicationContext();
        directory = new File(this.context.getFilesDir(), "lyrics");
    }

    /** Invoked on a worker thread after ACTION_OPEN_DOCUMENT returns a URI. */
    public LrcLyrics importForSong(@NonNull String mediaKey, @NonNull Uri uri) throws IOException {
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            if (input == null) throw new IOException("No se pudo abrir la letra");
            String raw = LrcParser.read(input);
            save(mediaKey, raw);
            return LrcParser.parse(raw);
        }
    }

    @Nullable
    public LrcLyrics loadImported(@NonNull String mediaKey) throws IOException {
        File file = fileFor(mediaKey);
        if (!file.isFile()) return null;
        try (InputStream input = new FileInputStream(file)) {
            return LrcParser.parse(LrcParser.read(input));
        }
    }

    public void save(@NonNull String mediaKey, @NonNull String text) throws IOException {
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("No se pudo crear la caché");
        File destination = fileFor(mediaKey);
        File temporary = File.createTempFile("lyrics_", ".tmp", directory);
        try {
            try (FileOutputStream output = new FileOutputStream(temporary)) {
                output.write(text.getBytes(StandardCharsets.UTF_8));
                output.getFD().sync();
            }
            if (!temporary.renameTo(destination)) throw new IOException("No se pudo guardar la letra");
        } finally {
            if (temporary.exists()) temporary.delete();
        }
    }

    /** Borra la letra guardada en la app para esa canción (no toca el archivo de audio). */
    public boolean delete(@NonNull String mediaKey) {
        File file = fileFor(mediaKey);
        return !file.exists() || file.delete();
    }

    private File fileFor(String key) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format(java.util.Locale.US, "%02x", b & 255));
            return new File(directory, hex + ".lrc");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
