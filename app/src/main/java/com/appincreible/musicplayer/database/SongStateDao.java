package com.appincreible.musicplayer.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface SongStateDao {
    @Query("SELECT * FROM song_states")
    List<SongStateEntity> getAll();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(SongStateEntity state);

    @Query("DELETE FROM song_states WHERE songId = :songId")
    void delete(long songId);
}
