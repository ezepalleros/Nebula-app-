package com.appincreible.musicplayer.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    List<PlaylistEntity> getAll();

    @Insert
    long insert(PlaylistEntity playlist);

    @Query("UPDATE playlists SET name = :name WHERE id = :playlistId")
    void rename(long playlistId, String name);

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId")
    void deleteSongs(long playlistId);

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    void delete(long playlistId);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertSong(PlaylistSongEntity item);

    @Query("SELECT songId FROM playlist_songs WHERE playlistId = :playlistId ORDER BY addedAt ASC")
    List<Long> getSongIds(long playlistId);

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId")
    void removeSong(long playlistId, long songId);

    @Query("DELETE FROM playlist_songs WHERE songId = :songId")
    void removeSongFromAll(long songId);
}
