package com.appincreible.musicplayer.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface FavoriteRadioDao {

    @Query("SELECT stationUuid FROM favorite_radios ORDER BY addedAt DESC")
    List<String> getAllIds();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(FavoriteRadioEntity favorite);

    @Query("DELETE FROM favorite_radios WHERE stationUuid = :stationUuid")
    void delete(String stationUuid);
}
