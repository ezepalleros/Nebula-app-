package com.appincreible.musicplayer.data.repository;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;

import com.appincreible.musicplayer.data.artwork.TrackArtworkResolver;
import com.appincreible.musicplayer.data.model.Song;
import com.appincreible.musicplayer.themes.AppPreferences;

import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MusicRepository {

    public interface Callback {
        void onLoaded(List<Song> songs);
        void onError(Exception error);
    }

    private final Context appContext;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final TrackArtworkResolver artworkResolver;

    public MusicRepository(Context context) {
        this.appContext = context.getApplicationContext();
        this.artworkResolver = new TrackArtworkResolver(this.appContext);
    }

    public void loadSongs(Callback callback) {
        executor.execute(() -> {
            try {
                List<Song> songs = querySongs();
                mainHandler.post(() -> callback.onLoaded(songs));
            } catch (Exception error) {
                mainHandler.post(() -> callback.onError(error));
            }
        });
    }

    private List<Song> querySongs() {
        List<Song> result = new ArrayList<>();
        ContentResolver resolver = appContext.getContentResolver();
        Uri collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;

        String pathColumn = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                ? MediaStore.Audio.Media.RELATIVE_PATH
                : MediaStore.Audio.Media.DATA;
        String[] projection = new String[]{
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.DATE_MODIFIED,
                pathColumn
        };

        String selection = MediaStore.Audio.Media.IS_MUSIC + " != 0";
        String sortOrder = MediaStore.Audio.Media.DATE_ADDED + " DESC";

        try (Cursor cursor = resolver.query(collection, projection, selection, null, sortOrder)) {
            if (cursor == null) {
                return result;
            }

            int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
            int titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
            int artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
            int albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM);
            int albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID);
            int durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);
            int modifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED);
            int pathColumnIndex = cursor.getColumnIndexOrThrow(pathColumn);
            boolean hideWhatsAppAudio = AppPreferences.hideWhatsAppAudio(appContext);
            Set<String> disabledFolders = new HashSet<>(AppPreferences.getDisabledMusicSourceFolders(appContext));

            while (cursor.moveToNext()) {
                String rawPath = cursor.getString(pathColumnIndex);
                if (hideWhatsAppAudio && isWhatsAppAudioPath(rawPath)) continue;
                if (disabledFolders.contains(folderKey(rawPath))) continue;
                long id = cursor.getLong(idColumn);
                String title = safeText(cursor.getString(titleColumn), "Sin título");
                String artist = safeText(cursor.getString(artistColumn), "Artista desconocido");
                String album = safeText(cursor.getString(albumColumn), "Álbum desconocido");
                long albumId = cursor.getLong(albumIdColumn);
                long duration = cursor.getLong(durationColumn);
                long modified = cursor.getLong(modifiedColumn);
                Uri contentUri = ContentUris.withAppendedId(collection, id);
                Uri albumArtworkUri = albumId > 0
                        ? ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId)
                        : null;
                String artworkUri = artworkResolver.resolve(contentUri, id, modified, albumArtworkUri);
                result.add(new Song(id, title, artist, album, duration, contentUri, artworkUri));
            }
        }

        return result;
    }

    private String folderKey(String rawPath) {
        if (rawPath == null || rawPath.trim().isEmpty()) return "";
        String normalized = rawPath.replace('\\', '/').trim();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            int slash = normalized.lastIndexOf('/');
            if (slash > 0) normalized = normalized.substring(0, slash);
        }
        while (normalized.endsWith("/")) normalized = normalized.substring(0, normalized.length() - 1);
        return normalized.toLowerCase(Locale.ROOT);
    }

    private boolean isWhatsAppAudioPath(String rawPath) {
        if (rawPath == null || rawPath.trim().isEmpty()) return false;
        String path = rawPath.replace('\\', '/').toLowerCase(Locale.ROOT);
        return path.contains("whatsapp/media/whatsapp audio/")
                || path.contains("whatsapp/media/whatsapp voice notes/")
                || path.contains("whatsapp business/media/whatsapp business audio/")
                || path.contains("whatsapp business/media/whatsapp business voice notes/");
    }

    private String safeText(String value, String fallback) {
        if (value == null || value.trim().isEmpty() || "<unknown>".equalsIgnoreCase(value)) {
            return fallback;
        }
        return value;
    }

    public void close() {
        executor.shutdownNow();
    }
}
