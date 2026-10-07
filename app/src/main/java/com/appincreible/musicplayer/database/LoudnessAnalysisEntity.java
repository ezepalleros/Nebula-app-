package com.appincreible.musicplayer.database;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "loudness_analysis")
public class LoudnessAnalysisEntity {
    @PrimaryKey @NonNull public final String mediaId;
    public final float gainDb;
    public final float rmsDb;
    public final float peak;
    @NonNull public final String source;
    public final long analyzedAt;

    public LoudnessAnalysisEntity(@NonNull String mediaId, float gainDb, float rmsDb, float peak,
                                  @NonNull String source, long analyzedAt) {
        this.mediaId = mediaId;
        this.gainDb = gainDb;
        this.rmsDb = rmsDb;
        this.peak = peak;
        this.source = source;
        this.analyzedAt = analyzedAt;
    }
}
