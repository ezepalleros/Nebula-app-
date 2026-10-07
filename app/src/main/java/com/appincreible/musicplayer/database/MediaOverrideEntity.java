package com.appincreible.musicplayer.database;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "media_overrides")
public class MediaOverrideEntity {

    @PrimaryKey
    @NonNull
    public final String mediaKey;

    public final String title;
    public final String artist;
    public final String album;
    public final String artworkUri;
    public final long updatedAt;

    public MediaOverrideEntity(@NonNull String mediaKey, String title, String artist,
                               String album, String artworkUri, long updatedAt) {
        this.mediaKey = mediaKey;
        this.title = safe(title);
        this.artist = safe(artist);
        this.album = safe(album);
        this.artworkUri = safe(artworkUri);
        this.updatedAt = updatedAt;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
