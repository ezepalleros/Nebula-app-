package com.appincreible.musicplayer.database;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface MediaOverrideDao {

    @Query("SELECT * FROM media_overrides WHERE mediaKey = :mediaKey LIMIT 1")
    MediaOverrideEntity get(String mediaKey);

    @Query("SELECT * FROM media_overrides WHERE mediaKey LIKE :prefix || '%'")
    List<MediaOverrideEntity> getByPrefix(String prefix);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(MediaOverrideEntity entity);

    @Delete
    void delete(MediaOverrideEntity entity);

    @Query("DELETE FROM media_overrides WHERE mediaKey = :mediaKey")
    void deleteByKey(String mediaKey);
}
