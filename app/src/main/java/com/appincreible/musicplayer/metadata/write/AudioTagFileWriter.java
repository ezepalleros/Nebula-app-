package com.appincreible.musicplayer.metadata.write;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;

import com.appincreible.musicplayer.database.MediaOverrideEntity;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.images.Artwork;
import org.jaudiotagger.tag.images.ArtworkFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.Locale;

/** Blocking operations: invoke exclusively on a worker thread. */
public final class AudioTagFileWriter {
    private AudioTagFileWriter() { }

    public static Uri uriForKey(String key) {
        if (key == null || !key.startsWith("song:")) throw new IllegalArgumentException("No es una canción local");
        return ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                Long.parseLong(key.substring(5)));
    }

    public static String extension(Context context, Uri uri) {
        try (Cursor cursor = context.getContentResolver().query(uri,
                new String[]{MediaStore.Audio.Media.DISPLAY_NAME}, null, null, null)) {
            if (cursor == null || !cursor.moveToFirst()) return "";
            String name = cursor.getString(0).toLowerCase(Locale.ROOT);
            if (name.endsWith(".mp3")) return "mp3";
            if (name.endsWith(".m4a")) return "m4a";
            if (name.endsWith(".flac")) return "flac";
            if (name.endsWith(".ogg")) return "ogg";
            if (name.endsWith(".oga")) return "oga";
            // Opus is not supported by Jaudiotagger 3.0.1; do not relabel it as Ogg Vorbis.
            return "";
        }
    }

    /** Ogg is a container: Jaudiotagger's OGG reader/writer handles Vorbis, not Opus. */
    private static void validateCodec(File file, String ext) throws Exception {
        if (!"ogg".equals(ext) && !"oga".equals(ext)) return;
        byte[] header = new byte[512];
        int size;
        try (InputStream input = new FileInputStream(file)) {
            size = input.read(header);
        }
        if (size < 35 || header[0] != 'O' || header[1] != 'g'
                || header[2] != 'g' || header[3] != 'S') {
            throw new UnsupportedOperationException("Contenedor OGG no reconocido; cambio solo en la app");
        }
        // First identification packet of an Ogg Vorbis stream begins with 0x01 + "vorbis".
        boolean vorbis = false;
        for (int i = 0; i + 6 < size; i++) {
            if (header[i] == 1 && header[i + 1] == 'v' && header[i + 2] == 'o'
                    && header[i + 3] == 'r' && header[i + 4] == 'b'
                    && header[i + 5] == 'i' && header[i + 6] == 's') {
                vorbis = true;
                break;
            }
        }
        if (!vorbis) throw new UnsupportedOperationException(
                "OGG/Opus u otro códec no compatible con el escritor actual; cambio solo en la app");
    }

    private static File backup(Context context, String key, String extension) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(key.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder name = new StringBuilder();
        for (byte b : hash) name.append(String.format(Locale.ROOT, "%02x", b & 255));
        File dir = new File(context.getFilesDir(), "original_audio_tags");
        if (!dir.exists() && !dir.mkdirs()) throw new java.io.IOException("No se puede crear respaldo");
        return new File(dir, name + "." + extension);
    }

    private static void copy(InputStream from, OutputStream to) throws Exception {
        byte[] buffer = new byte[64 * 1024];
        int n;
        while ((n = from.read(buffer)) != -1) to.write(buffer, 0, n);
        to.flush();
    }

    private static void copyFile(File from, File to) throws Exception {
        try (InputStream input = new FileInputStream(from); OutputStream output = new FileOutputStream(to)) {
            copy(input, output);
        }
    }

    private static void writeBack(Context context, Uri uri, File file) throws Exception {
        try (InputStream input = new FileInputStream(file);
             OutputStream output = context.getContentResolver().openOutputStream(uri, "rwt")) {
            if (output == null) throw new java.io.IOException("No se pudo abrir el archivo para escribir");
            copy(input, output);
        }
    }

    public static void write(Context context, MediaOverrideEntity entity,
                             boolean titleChanged, boolean artistChanged, boolean albumChanged,
                             boolean artChanged) throws Exception {
        Uri uri = uriForKey(entity.mediaKey);
        String ext = extension(context, uri);
        if (ext.isEmpty()) throw new UnsupportedOperationException("Formato no compatible; cambio guardado solo en la app");
        File original = backup(context, entity.mediaKey, ext);
        File tempDir = context.getCacheDir();
        File stage = File.createTempFile("tags_stage_", "." + ext, tempDir);
        File snapshot = File.createTempFile("tags_snapshot_", "." + ext, tempDir);
        try {
            try (InputStream input = context.getContentResolver().openInputStream(uri);
                 OutputStream output = new FileOutputStream(snapshot)) {
                if (input == null) throw new java.io.IOException("No se pudo leer el original");
                copy(input, output);
            }
            validateCodec(snapshot, ext);
            if (!original.exists()) {
                File pending = new File(original.getAbsolutePath() + ".pending");
                copyFile(snapshot, pending);
                if (!pending.renameTo(original)) throw new java.io.IOException("No se pudo conservar el original");
            }
            copyFile(snapshot, stage);
            AudioFile audio = AudioFileIO.read(stage);
            Tag tag = audio.getTagOrCreateAndSetDefault();
            if (titleChanged) tag.setField(FieldKey.TITLE, entity.title);
            if (artistChanged) tag.setField(FieldKey.ARTIST, entity.artist);
            if (albumChanged) tag.setField(FieldKey.ALBUM, entity.album);
            if (artChanged) {
                tag.deleteArtworkField();
                if (entity.artworkUri != null && !entity.artworkUri.isEmpty()) {
                    try (InputStream input = context.getContentResolver().openInputStream(Uri.parse(entity.artworkUri))) {
                        if (input == null) throw new java.io.IOException("No se puede leer la portada");
                        File art = File.createTempFile("tags_art_", ".jpg", tempDir);
                        try {
                            try (OutputStream out = new FileOutputStream(art)) { copy(input, out); }
                            Artwork artwork = ArtworkFactory.createArtworkFromFile(art);
                            tag.setField(artwork);
                        } finally { art.delete(); }
                    }
                }
            }
            audio.commit();
            // Verify tags from the fully written temporary audio before touching MediaStore.
            Tag verified = AudioFileIO.read(stage).getTag();
            if (verified == null || (titleChanged && !entity.title.equals(verified.getFirst(FieldKey.TITLE)))
                    || (artistChanged && !entity.artist.equals(verified.getFirst(FieldKey.ARTIST)))
                    || (albumChanged && !entity.album.equals(verified.getFirst(FieldKey.ALBUM)))
                    || (artChanged && !entity.artworkUri.isEmpty() && verified.getFirstArtwork() == null)) {
                throw new java.io.IOException("La verificación de los tags falló");
            }
            try {
                writeBack(context, uri, stage);
            } catch (Exception failure) {
                try { writeBack(context, uri, snapshot); }
                catch (Exception rollbackFailure) { failure.addSuppressed(rollbackFailure); }
                throw failure;
            }
            context.getContentResolver().notifyChange(uri, null);
        } finally {
            stage.delete();
            snapshot.delete();
        }
    }

    public static boolean hasOriginalBackup(Context context, String mediaKey) {
        try {
            Uri uri = uriForKey(mediaKey);
            String ext = extension(context, uri);
            return !ext.isEmpty() && backup(context, mediaKey, ext).isFile();
        } catch (Exception error) { return false; }
    }

    public static void restore(Context context, String mediaKey) throws Exception {
        Uri uri = uriForKey(mediaKey);
        String ext = extension(context, uri);
        if (ext.isEmpty()) throw new UnsupportedOperationException("Formato no compatible");
        File backup = backup(context, mediaKey, ext);
        if (!backup.isFile()) return;
        File snapshot = File.createTempFile("tags_restore_", "." + ext, context.getCacheDir());
        try {
            try (InputStream input = context.getContentResolver().openInputStream(uri);
                 OutputStream output = new FileOutputStream(snapshot)) {
                if (input == null) throw new java.io.IOException("No se puede leer el archivo actual");
                copy(input, output);
            }
            try { writeBack(context, uri, backup); }
            catch (Exception error) {
                try { writeBack(context, uri, snapshot); }
                catch (Exception rollback) { error.addSuppressed(rollback); }
                throw error;
            }
            context.getContentResolver().notifyChange(uri, null);
        } finally { snapshot.delete(); }
    }
}
