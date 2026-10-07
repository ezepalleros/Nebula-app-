package com.appincreible.musicplayer.database;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "favorite_radios")
public class FavoriteRadioEntity {

    @PrimaryKey
    @NonNull
    public final String stationUuid;

    public final long addedAt;

    public FavoriteRadioEntity(@NonNull String stationUuid, long addedAt) {
        this.stationUuid = stationUuid;
        this.addedAt = addedAt;
    }
}
