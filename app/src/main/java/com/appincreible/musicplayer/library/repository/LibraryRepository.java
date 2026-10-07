package com.appincreible.musicplayer.library.repository;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.appincreible.musicplayer.database.MusicDatabase;
import com.appincreible.musicplayer.database.PlaylistEntity;
import com.appincreible.musicplayer.database.PlaylistSongEntity;
import com.appincreible.musicplayer.database.LibraryFolderStateEntity;
import com.appincreible.musicplayer.database.SongStateEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LibraryRepository {
    public interface StateCallback { void onLoaded(Map<Long, SongStateEntity> states); }
    public interface PlaylistCallback { void onLoaded(List<PlaylistEntity> playlists); }
    public interface IdsCallback { void onLoaded(List<Long> songIds); }
    public interface DoneCallback { void onDone(); }
    public interface PlaylistCreatedCallback { void onCreated(long playlistId); }
    public interface FolderStateCallback { void onLoaded(List<LibraryFolderStateEntity> states); }

    private final MusicDatabase database;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    public LibraryRepository(Context context) {
        database = MusicDatabase.getInstance(context.getApplicationContext());
    }

    public void loadStates(StateCallback callback) {
        executor.execute(() -> {
            Map<Long, SongStateEntity> map = new HashMap<>();
            for (SongStateEntity state : database.songStateDao().getAll()) map.put(state.songId, state);
            main.post(() -> callback.onLoaded(map));
        });
    }

    public void setHidden(List<Long> songIds, boolean hidden, DoneCallback callback) {
        executor.execute(() -> {
            Map<Long, SongStateEntity> states = currentStates();
            for (long id : songIds) {
                SongStateEntity old = states.get(id);
                boolean favorite = old != null && old.favorite;
                database.songStateDao().upsert(new SongStateEntity(id, hidden, favorite));
            }
            main.post(callback::onDone);
        });
    }

    public void setFavorite(long songId, boolean favorite, DoneCallback callback) {
        executor.execute(() -> {
            Map<Long, SongStateEntity> states = currentStates();
            SongStateEntity old = states.get(songId);
            boolean hidden = old != null && old.hidden;
            database.songStateDao().upsert(new SongStateEntity(songId, hidden, favorite));
            main.post(callback::onDone);
        });
    }


    public void loadFolderStates(FolderStateCallback callback) {
        executor.execute(() -> {
            List<LibraryFolderStateEntity> result = database.libraryFolderStateDao().getHidden();
            main.post(() -> callback.onLoaded(result));
        });
    }

    public void setFolderHidden(String folderKey, boolean hidden, DoneCallback callback) {
        executor.execute(() -> {
            if (hidden) database.libraryFolderStateDao().upsert(new LibraryFolderStateEntity(folderKey, true));
            else database.libraryFolderStateDao().delete(folderKey);
            main.post(callback::onDone);
        });
    }

    public void loadPlaylists(PlaylistCallback callback) {
        executor.execute(() -> {
            List<PlaylistEntity> result = database.playlistDao().getAll();
            main.post(() -> callback.onLoaded(result));
        });
    }

    public void createPlaylist(String name, PlaylistCreatedCallback callback) {
        executor.execute(() -> {
            long id = database.playlistDao().insert(new PlaylistEntity(name, System.currentTimeMillis()));
            main.post(() -> callback.onCreated(id));
        });
    }

    public void renamePlaylist(long playlistId, String name, DoneCallback callback) {
        executor.execute(() -> {
            database.playlistDao().rename(playlistId, name);
            main.post(callback::onDone);
        });
    }

    public void deletePlaylist(long playlistId, DoneCallback callback) {
        executor.execute(() -> {
            database.playlistDao().deleteSongs(playlistId);
            database.playlistDao().delete(playlistId);
            main.post(callback::onDone);
        });
    }

    public void addSongsToPlaylists(List<Long> songIds, List<Long> playlistIds, DoneCallback callback) {
        executor.execute(() -> {
            long now = System.currentTimeMillis();
            for (long playlistId : playlistIds) {
                long offset = 0;
                for (long songId : songIds) {
                    database.playlistDao().insertSong(new PlaylistSongEntity(playlistId, songId, now + offset++));
                }
            }
            main.post(callback::onDone);
        });
    }

    public void getPlaylistSongIds(long playlistId, IdsCallback callback) {
        executor.execute(() -> {
            List<Long> ids = new ArrayList<>(database.playlistDao().getSongIds(playlistId));
            main.post(() -> callback.onLoaded(ids));
        });
    }

    public void removeSongFromPlaylist(long playlistId, long songId, DoneCallback callback) {
        executor.execute(() -> {
            database.playlistDao().removeSong(playlistId, songId);
            if (callback != null) main.post(callback::onDone);
        });
    }

    public void removeSongsFromPlaylist(long playlistId, List<Long> songIds, DoneCallback callback) {
        executor.execute(() -> {
            for (long songId : songIds) database.playlistDao().removeSong(playlistId, songId);
            if (callback != null) main.post(callback::onDone);
        });
    }

    public void removeDeletedSongs(List<Long> songIds, DoneCallback callback) {
        executor.execute(() -> {
            for (long id : songIds) {
                database.playlistDao().removeSongFromAll(id);
                database.songStateDao().delete(id);
                database.mediaOverrideDao().deleteByKey("song:" + id);
            }
            main.post(callback::onDone);
        });
    }

    private Map<Long, SongStateEntity> currentStates() {
        Map<Long, SongStateEntity> map = new HashMap<>();
        for (SongStateEntity state : database.songStateDao().getAll()) map.put(state.songId, state);
        return map;
    }

    public void close() { executor.shutdownNow(); }
}
