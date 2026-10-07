package com.appincreible.musicplayer.data.model;

import android.net.Uri;

import com.appincreible.musicplayer.database.MediaOverrideEntity;

public class Song {
    private final long id;
    private final String title;
    private final String artist;
    private final String album;
    private final long durationMs;
    private final Uri contentUri;
    private final String artworkUri;

    public Song(long id, String title, String artist, String album, long durationMs, Uri contentUri) {
        this(id, title, artist, album, durationMs, contentUri, "");
    }

    public Song(long id, String title, String artist, String album, long durationMs,
                Uri contentUri, String artworkUri) {
        this.id = id;
        this.title = safe(title, "Sin título");
        this.artist = safe(artist, "Artista desconocido");
        this.album = safe(album, "Álbum desconocido");
        this.durationMs = durationMs;
        this.contentUri = contentUri;
        this.artworkUri = artworkUri == null ? "" : artworkUri.trim();
    }

    public long getId() { return id; }
    public String getMediaKey() { return "song:" + id; }
    public String getTitle() { return title; }
    public String getArtist() { return artist; }
    public String getAlbum() { return album; }
    public long getDurationMs() { return durationMs; }
    public Uri getContentUri() { return contentUri; }
    public String getArtworkUri() { return artworkUri; }

    public Song withOverride(MediaOverrideEntity override) {
        if (override == null) return this;
        return new Song(
                id,
                override.title.isEmpty() ? title : override.title,
                override.artist.isEmpty() ? artist : override.artist,
                override.album.isEmpty() ? album : override.album,
                durationMs,
                contentUri,
                override.artworkUri.isEmpty() ? artworkUri : override.artworkUri
        );
    }


    public Song withInternalMetadata(MediaOverrideEntity override) {
        if (override == null) {
            return new Song(id, "Sin título", "Artista desconocido", "Álbum desconocido", durationMs, contentUri, "");
        }
        return new Song(
                id,
                override.title.isEmpty() ? "Sin título" : override.title,
                override.artist.isEmpty() ? "Artista desconocido" : override.artist,
                override.album.isEmpty() ? "Álbum desconocido" : override.album,
                durationMs,
                contentUri,
                override.artworkUri
        );
    }

    private static String safe(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }
}
