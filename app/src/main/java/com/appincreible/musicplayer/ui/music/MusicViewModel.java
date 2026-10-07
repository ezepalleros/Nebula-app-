package com.appincreible.musicplayer.ui.music;

import android.app.Application;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.appincreible.musicplayer.data.model.Song;
import com.appincreible.musicplayer.data.repository.MusicRepository;
import com.appincreible.musicplayer.database.MediaOverrideEntity;
import com.appincreible.musicplayer.database.PlaylistEntity;
import com.appincreible.musicplayer.database.LibraryFolderStateEntity;
import com.appincreible.musicplayer.database.SongStateEntity;
import com.appincreible.musicplayer.library.repository.LibraryRepository;
import com.appincreible.musicplayer.metadata.repository.MediaMetadataRepository;
import com.appincreible.musicplayer.themes.AppPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.Locale;

public class MusicViewModel extends AndroidViewModel implements SharedPreferences.OnSharedPreferenceChangeListener {

    public static final String FOLDER_ALL = "auto:all";
    public static final String FOLDER_ARTISTS = "auto:artists";
    public static final String FOLDER_ALBUMS = "auto:albums";

    private final MusicRepository repository;
    private final MediaMetadataRepository metadataRepository;
    private final LibraryRepository libraryRepository;

    private final MutableLiveData<List<Song>> allSongs = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<List<Song>> visibleSongs = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<List<Song>> favoriteSongs = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<List<Song>> hiddenSongs = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<List<PlaylistEntity>> playlists = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<Set<String>> hiddenFolderKeys = new MutableLiveData<>(Collections.emptySet());
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>();

    private List<Song> baseSongs = Collections.emptyList();
    private List<Song> renderedSongs = Collections.emptyList();
    private Map<Long, SongStateEntity> states = new HashMap<>();
    private Set<String> hiddenFolders = new HashSet<>();
    private int musicSection = 0;
    private int playlistScrollPosition = 0;
    private int playlistScrollOffset = 0;

    public MusicViewModel(@NonNull Application application) {
        super(application);
        repository = new MusicRepository(application);
        metadataRepository = new MediaMetadataRepository(application);
        libraryRepository = new LibraryRepository(application);
        AppPreferences.prefs(application).registerOnSharedPreferenceChangeListener(this);
    }

    public LiveData<List<Song>> getSongs() { return visibleSongs; }
    public LiveData<List<Song>> getAllSongs() { return allSongs; }
    public LiveData<List<Song>> getFavoriteSongs() { return favoriteSongs; }
    public LiveData<List<Song>> getHiddenSongs() { return hiddenSongs; }
    public LiveData<List<PlaylistEntity>> getPlaylists() { return playlists; }
    public LiveData<Set<String>> getHiddenFolderKeys() { return hiddenFolderKeys; }
    public LiveData<Boolean> getLoading() { return loading; }
    public LiveData<String> getError() { return error; }

    public void loadSongs() {
        loading.setValue(true);
        repository.loadSongs(new MusicRepository.Callback() {
            @Override public void onLoaded(List<Song> loadedSongs) {
                baseSongs = loadedSongs;
                refreshLibrary();
                error.setValue(null);
            }

            @Override public void onError(Exception throwable) {
                loading.setValue(false);
                error.setValue(throwable.getMessage());
            }
        });
    }

    public void refreshOverrides() {
        if (baseSongs.isEmpty()) loadSongs(); else refreshLibrary();
    }

    public void refreshLibrary() {
        metadataRepository.getByPrefix("song:", overrides -> {
            List<Song> rendered = new ArrayList<>(baseSongs.size());
            String source = AppPreferences.getMetadataSource(getApplication());
            for (Song song : baseSongs) {
                MediaOverrideEntity override = overrides.get(song.getMediaKey());
                if (AppPreferences.METADATA_SOURCE_TAGS.equals(source)) {
                    rendered.add(song);
                } else if (AppPreferences.METADATA_SOURCE_INTERNAL.equals(source)) {
                    rendered.add(song.withInternalMetadata(override));
                } else {
                    rendered.add(song.withOverride(override));
                }
            }
            renderedSongs = rendered;
            allSongs.setValue(rendered);
            libraryRepository.loadStates(loadedStates -> {
                states = loadedStates;
                publishSections();
                libraryRepository.loadPlaylists(result -> playlists.setValue(result));
                refreshFolderStates();
            });
        });
    }

    private void publishSections() {
        List<Song> visible = new ArrayList<>();
        List<Song> favorites = new ArrayList<>();
        List<Song> hidden = new ArrayList<>();
        for (Song song : renderedSongs) {
            SongStateEntity state = states.get(song.getId());
            boolean isHidden = state != null && state.hidden;
            boolean isFavorite = state != null && state.favorite;
            if (isHidden) hidden.add(song); else visible.add(song);
            if (!isHidden && isFavorite) favorites.add(song);
        }
        visibleSongs.setValue(visible);
        favoriteSongs.setValue(favorites);
        hiddenSongs.setValue(hidden);
        loading.setValue(false);
    }

    public void refreshFolderStates() {
        libraryRepository.loadFolderStates(items -> {
            Set<String> keys = new HashSet<>();
            for (LibraryFolderStateEntity item : items) if (item.hidden) keys.add(item.folderKey);
            hiddenFolders = keys;
            hiddenFolderKeys.setValue(new HashSet<>(keys));
        });
    }

    public boolean isFolderHidden(String folderKey) {
        return hiddenFolders.contains(folderKey);
    }

    public void setFolderHidden(String folderKey, boolean hidden) {
        if (folderKey == null || folderKey.isEmpty()) return;
        libraryRepository.setFolderHidden(folderKey, hidden, this::refreshFolderStates);
    }

    public List<LibraryEntry> buildRootLibraryEntries() {
        List<LibraryEntry> result = new ArrayList<>();
        List<Song> visible = visibleSongs.getValue() == null ? Collections.emptyList() : visibleSongs.getValue();
        List<PlaylistEntity> userPlaylists = playlists.getValue() == null ? Collections.emptyList() : playlists.getValue();

        if (!isFolderHidden(FOLDER_ALL)) {
            result.add(new LibraryEntry("root:all", LibraryEntry.TYPE_ALL,
                    "Todas las canciones descargadas", visible.size() + " pistas",
                    "", -1L, FOLDER_ALL, true, false));
        }
        if (AppPreferences.libraryArtistsEnabled(getApplication()) && !isFolderHidden(FOLDER_ARTISTS)) {
            int count = uniqueArtists(false).size();
            result.add(new LibraryEntry("root:artists", LibraryEntry.TYPE_ARTISTS_ROOT,
                    "Por banda / cantante", count + (count == 1 ? " artista" : " artistas"),
                    "artists", -1L, FOLDER_ARTISTS, true, false));
        }
        if (AppPreferences.libraryAlbumsEnabled(getApplication()) && !isFolderHidden(FOLDER_ALBUMS)) {
            int count = uniqueAlbums(false).size();
            result.add(new LibraryEntry("root:albums", LibraryEntry.TYPE_ALBUMS_ROOT,
                    "Por álbum", count + (count == 1 ? " álbum" : " álbumes"),
                    "albums", -1L, FOLDER_ALBUMS, true, false));
        }

        for (PlaylistEntity playlist : userPlaylists) {
            result.add(new LibraryEntry("playlist:" + playlist.id, LibraryEntry.TYPE_USER_PLAYLIST,
                    playlist.name, "Playlist", String.valueOf(playlist.id), playlist.id,
                    "", false, false));
        }

        int hiddenCount = buildFolderEntries("hidden", false).size();
        if (hiddenCount > 0) {
            result.add(new LibraryEntry("root:hidden", LibraryEntry.TYPE_HIDDEN_ROOT,
                    "Carpetas ocultas", hiddenCount + (hiddenCount == 1 ? " elemento" : " elementos"),
                    "hidden", -1L, "", false, false));
        }
        java.util.List<String> savedOrder = AppPreferences.getPlaylistEntryOrder(getApplication());
        if (!savedOrder.isEmpty()) {
            java.util.Map<String, Integer> rank = new java.util.HashMap<>();
            for (int i = 0; i < savedOrder.size(); i++) rank.put(savedOrder.get(i), i);
            java.util.Map<String, Integer> original = new java.util.HashMap<>();
            for (int i = 0; i < result.size(); i++) original.put(result.get(i).stableId, i);
            result.sort((a, b) -> {
                int ra = rank.containsKey(a.stableId) ? rank.get(a.stableId) : Integer.MAX_VALUE;
                int rb = rank.containsKey(b.stableId) ? rank.get(b.stableId) : Integer.MAX_VALUE;
                if (ra != rb) return Integer.compare(ra, rb);
                return Integer.compare(original.get(a.stableId), original.get(b.stableId));
            });
        }
        return result;
    }

    public List<LibraryEntry> buildFolderEntries(String mode, boolean hiddenOnly) {
        List<LibraryEntry> result = new ArrayList<>();
        if ("artists".equals(mode) && !AppPreferences.libraryArtistsEnabled(getApplication())) return result;
        if ("albums".equals(mode) && !AppPreferences.libraryAlbumsEnabled(getApplication())) return result;
        if ("artists".equals(mode)) {
            Map<String, FolderAggregate> groups = uniqueArtists(true);
            for (FolderAggregate group : groups.values()) {
                String key = artistFolderKey(group.title);
                boolean hidden = isFolderHidden(key);
                if (hidden != hiddenOnly) continue;
                result.add(new LibraryEntry("artist:" + normalize(group.title), LibraryEntry.TYPE_ARTIST,
                        group.title, group.count + (group.count == 1 ? " pista" : " pistas"),
                        group.title, -1L, key, true, hidden));
            }
        } else if ("albums".equals(mode)) {
            Map<String, FolderAggregate> groups = uniqueAlbums(true);
            for (FolderAggregate group : groups.values()) {
                String key = albumFolderKey(group.title);
                boolean hidden = isFolderHidden(key);
                if (hidden != hiddenOnly) continue;
                result.add(new LibraryEntry("album:" + normalize(group.title), LibraryEntry.TYPE_ALBUM,
                        group.title, group.count + (group.count == 1 ? " pista" : " pistas"),
                        group.title, -1L, key, true, hidden));
            }
        } else if ("hidden".equals(mode)) {
            if (isFolderHidden(FOLDER_ALL)) result.add(new LibraryEntry("hidden:all", LibraryEntry.TYPE_ALL,
                    "Todas las canciones descargadas", "Carpeta principal", "", -1L, FOLDER_ALL, true, true));
            if (AppPreferences.libraryArtistsEnabled(getApplication()) && isFolderHidden(FOLDER_ARTISTS)) {
                result.add(new LibraryEntry("hidden:artists", LibraryEntry.TYPE_ARTISTS_ROOT,
                        "Por banda / cantante", "Carpeta principal", "artists", -1L, FOLDER_ARTISTS, true, true));
            }
            if (AppPreferences.libraryAlbumsEnabled(getApplication()) && isFolderHidden(FOLDER_ALBUMS)) {
                result.add(new LibraryEntry("hidden:albums", LibraryEntry.TYPE_ALBUMS_ROOT,
                        "Por álbum", "Carpeta principal", "albums", -1L, FOLDER_ALBUMS, true, true));
            }
            if (AppPreferences.libraryArtistsEnabled(getApplication())) result.addAll(buildFolderEntries("artists", true));
            if (AppPreferences.libraryAlbumsEnabled(getApplication())) result.addAll(buildFolderEntries("albums", true));
        }
        result.sort((a, b) -> a.title.compareToIgnoreCase(b.title));
        return result;
    }

    public List<Song> allVisibleSongs() {
        List<Song> visible = visibleSongs.getValue();
        return visible == null ? Collections.emptyList() : new ArrayList<>(visible);
    }

    private Map<String, FolderAggregate> uniqueArtists(boolean includeHiddenFolders) {
        return aggregateBy(true, includeHiddenFolders);
    }

    private Map<String, FolderAggregate> uniqueAlbums(boolean includeHiddenFolders) {
        return aggregateBy(false, includeHiddenFolders);
    }

    private Map<String, FolderAggregate> aggregateBy(boolean artist, boolean includeHiddenFolders) {
        Map<String, FolderAggregate> groups = new LinkedHashMap<>();
        List<Song> visible = visibleSongs.getValue();
        if (visible == null) return groups;
        for (Song song : visible) {
            String title = artist ? song.getArtist() : song.getAlbum();
            String normalized = normalize(title);
            String key = artist ? artistFolderKey(title) : albumFolderKey(title);
            if (!includeHiddenFolders && isFolderHidden(key)) continue;
            FolderAggregate old = groups.get(normalized);
            if (old == null) groups.put(normalized, new FolderAggregate(title, 1));
            else old.count++;
        }
        return groups;
    }

    public static String artistFolderKey(String artist) { return "artist:" + normalize(artist); }
    public static String albumFolderKey(String album) { return "album:" + normalize(album); }
    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static final class FolderAggregate {
        final String title;
        int count;
        FolderAggregate(String title, int count) { this.title = title; this.count = count; }
    }

    public boolean isFavorite(long songId) {
        SongStateEntity state = states.get(songId);
        return state != null && state.favorite;
    }

    public boolean isHidden(long songId) {
        SongStateEntity state = states.get(songId);
        return state != null && state.hidden;
    }

    public void setHidden(List<Song> songs, boolean hidden) {
        List<Long> ids = idsOf(songs);
        libraryRepository.setHidden(ids, hidden, this::refreshLibrary);
    }

    public void toggleFavorite(Song song) {
        libraryRepository.setFavorite(song.getId(), !isFavorite(song.getId()), this::refreshLibrary);
    }

    public void createPlaylist(String name, LibraryRepository.PlaylistCreatedCallback callback) {
        String clean = name == null ? "" : name.trim();
        if (clean.isEmpty()) clean = "Nueva playlist";
        libraryRepository.createPlaylist(clean, id -> {
            libraryRepository.loadPlaylists(result -> playlists.setValue(result));
            callback.onCreated(id);
        });
    }

    public void renamePlaylist(long playlistId, String name, Runnable done) {
        String clean = name == null ? "" : name.trim();
        if (playlistId < 0 || clean.isEmpty()) return;
        libraryRepository.renamePlaylist(playlistId, clean, () ->
                libraryRepository.loadPlaylists(result -> {
                    playlists.setValue(result);
                    if (done != null) done.run();
                }));
    }

    public void deletePlaylist(long playlistId, Runnable done) {
        if (playlistId < 0) return;
        libraryRepository.deletePlaylist(playlistId, () ->
                libraryRepository.loadPlaylists(result -> {
                    playlists.setValue(result);
                    if (done != null) done.run();
                }));
    }

    public void setPlaylistEntryOrder(java.util.List<String> stableIds) {
        AppPreferences.setPlaylistEntryOrder(getApplication(), stableIds);
    }

    public int getMusicSection() { return musicSection; }
    public void setMusicSection(int section) { musicSection = section; }
    public void setPlaylistScroll(int position, int offset) {
        playlistScrollPosition = Math.max(0, position);
        playlistScrollOffset = offset;
    }
    public int getPlaylistScrollPosition() { return playlistScrollPosition; }
    public int getPlaylistScrollOffset() { return playlistScrollOffset; }

    public void addSongsToPlaylists(List<Song> songs, List<Long> playlistIds, Runnable done) {
        libraryRepository.addSongsToPlaylists(idsOf(songs), playlistIds, () -> {
            if (done != null) done.run();
        });
    }

    public void getPlaylistSongs(long playlistId, java.util.function.Consumer<List<Song>> callback) {
        libraryRepository.getPlaylistSongIds(playlistId, ids -> {
            Set<Long> wanted = new HashSet<>(ids);
            List<Song> result = new ArrayList<>();
            for (Long id : ids) {
                for (Song song : renderedSongs) {
                    if (song.getId() == id && !isHidden(id)) {
                        result.add(song);
                        break;
                    }
                }
            }
            callback.accept(result);
        });
    }

    public void removeSongFromPlaylist(long playlistId, Song song, Runnable done) {
        if (song == null || playlistId < 0L) return;
        libraryRepository.removeSongFromPlaylist(playlistId, song.getId(), () -> {
            if (done != null) done.run();
        });
    }

    public void removeSongsFromPlaylist(long playlistId, List<Song> songs, Runnable done) {
        if (playlistId < 0L || songs == null || songs.isEmpty()) return;
        libraryRepository.removeSongsFromPlaylist(playlistId, idsOf(songs), () -> {
            if (done != null) done.run();
        });
    }

    public List<Song> songsForAlbum(String album) {
        List<Song> result = new ArrayList<>();
        if (!AppPreferences.libraryAlbumsEnabled(getApplication())) return result;
        List<Song> visible = visibleSongs.getValue();
        if (visible == null) return result;
        for (Song song : visible) if (song.getAlbum().equalsIgnoreCase(album)) result.add(song);
        return result;
    }

    public List<Song> songsForArtist(String artist) {
        List<Song> result = new ArrayList<>();
        if (!AppPreferences.libraryArtistsEnabled(getApplication())) return result;
        List<Song> visible = visibleSongs.getValue();
        if (visible == null) return result;
        for (Song song : visible) if (song.getArtist().equalsIgnoreCase(artist)) result.add(song);
        return result;
    }

    public void cleanupDeleted(List<Song> songs) {
        libraryRepository.removeDeletedSongs(idsOf(songs), this::loadSongs);
    }

    private List<Long> idsOf(List<Song> songs) {
        List<Long> ids = new ArrayList<>();
        for (Song song : songs) ids.add(song.getId());
        return ids;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (AppPreferences.KEY_METADATA_SOURCE.equals(key)) {
            refreshLibrary();
        } else if (AppPreferences.KEY_HIDE_WHATSAPP_AUDIO.equals(key)
                || AppPreferences.KEY_DISABLED_MUSIC_SOURCE_FOLDERS.equals(key)) {
            loadSongs();
        } else if (AppPreferences.KEY_LIBRARY_GROUPING_MODE.equals(key)) {
            // Visibility-only preference: keep every tag/override untouched, but make active UIs rebuild now.
            visibleSongs.setValue(new ArrayList<>(visibleSongs.getValue() == null
                    ? Collections.emptyList() : visibleSongs.getValue()));
            playlists.setValue(new ArrayList<>(playlists.getValue() == null
                    ? Collections.emptyList() : playlists.getValue()));
            hiddenFolderKeys.setValue(new HashSet<>(hiddenFolders));
        }
    }

    @Override
    protected void onCleared() {
        AppPreferences.prefs(getApplication()).unregisterOnSharedPreferenceChangeListener(this);
        repository.close();
        metadataRepository.close();
        libraryRepository.close();
        super.onCleared();
    }
}
