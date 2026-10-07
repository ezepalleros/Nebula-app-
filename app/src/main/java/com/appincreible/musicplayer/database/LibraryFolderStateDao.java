package com.appincreible.musicplayer.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface LibraryFolderStateDao {
    @Query("SELECT * FROM library_folder_states WHERE hidden = 1")
    List<LibraryFolderStateEntity> getHidden();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(LibraryFolderStateEntity item);

    @Query("DELETE FROM library_folder_states WHERE folderKey = :folderKey")
    void delete(String folderKey);
}
