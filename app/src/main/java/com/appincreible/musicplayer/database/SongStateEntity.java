package com.appincreible.musicplayer.database;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "song_states")
public class SongStateEntity {
    @PrimaryKey
    public long songId;
    public boolean hidden;
    public boolean favorite;

    public SongStateEntity(long songId, boolean hidden, boolean favorite) {
        this.songId = songId;
        this.hidden = hidden;
        this.favorite = favorite;
    }
}
