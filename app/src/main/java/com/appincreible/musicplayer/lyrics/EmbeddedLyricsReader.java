package com.appincreible.musicplayer.lyrics;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import androidx.annotation.Nullable;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Locale;

/** Lee tags de audio mediante una copia privada; nunca modifica el archivo de la canción. */
public final class EmbeddedLyricsReader {
    private EmbeddedLyricsReader() { }

    @Nullable public static LrcLyrics read(Context context, Uri uri) throws Exception {
        String name = null;
        try (Cursor cursor = context.getContentResolver().query(uri,
                new String[]{MediaStore.Audio.Media.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) name = cursor.getString(0);
        }
        if (name == null) return null;
        String lower = name.toLowerCase(Locale.ROOT);
        String extension = null;
        for (String ext : new String[]{"mp3", "m4a", "mp4", "flac", "ogg", "oga"}) {
            if (lower.endsWith("." + ext)) { extension = ext; break; }
        }
        if (extension == null) return null;
        File copy = File.createTempFile("lyrics_read_", "." + extension, context.getCacheDir());
        try {
            try (InputStream input = context.getContentResolver().openInputStream(uri);
                 FileOutputStream output = new FileOutputStream(copy)) {
                if (input == null) return null;
                byte[] bytes = new byte[65536];
                int count;
                while ((count = input.read(bytes)) != -1) output.write(bytes, 0, count);
            }
            AudioFile audio = AudioFileIO.read(copy);
            Tag tag = audio.getTag();
            if (tag == null) return null;
            String raw = tag.getFirst(FieldKey.LYRICS);
            if (raw == null || raw.trim().isEmpty()) return null;
            LrcLyrics parsed = LrcParser.parse(raw);
            return parsed.lines.isEmpty() ? null : parsed;
        } finally {
            if (copy.exists()) copy.delete();
        }
    }
}
