package com.appincreible.musicplayer.database;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "library_folder_states")
public class LibraryFolderStateEntity {
    @PrimaryKey @NonNull
    public String folderKey;
    public boolean hidden;

    public LibraryFolderStateEntity(@NonNull String folderKey, boolean hidden) {
        this.folderKey = folderKey;
        this.hidden = hidden;
    }
}
