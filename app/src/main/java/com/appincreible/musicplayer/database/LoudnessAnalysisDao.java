package com.appincreible.musicplayer.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

@Dao
public interface LoudnessAnalysisDao {
    @Query("SELECT * FROM loudness_analysis WHERE mediaId = :mediaId LIMIT 1")
    LoudnessAnalysisEntity get(String mediaId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(LoudnessAnalysisEntity entity);

    @Query("SELECT COUNT(*) FROM loudness_analysis")
    int count();
}
