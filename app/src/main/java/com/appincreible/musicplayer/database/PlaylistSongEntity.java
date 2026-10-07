package com.appincreible.musicplayer.database;

import androidx.room.Entity;

@Entity(tableName = "playlist_songs", primaryKeys = {"playlistId", "songId"})
public class PlaylistSongEntity {
    public long playlistId;
    public long songId;
    public long addedAt;

    public PlaylistSongEntity(long playlistId, long songId, long addedAt) {
        this.playlistId = playlistId;
        this.songId = songId;
        this.addedAt = addedAt;
    }
}
