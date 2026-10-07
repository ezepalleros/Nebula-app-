package com.appincreible.musicplayer.ui.games;

import androidx.annotation.NonNull;

import com.appincreible.musicplayer.R;
import com.appincreible.musicplayer.data.model.Song;
import com.appincreible.musicplayer.database.PlaylistEntity;
import com.appincreible.musicplayer.ui.music.MusicViewModel;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Shared playlist loading and round-selection helpers for offline games. */
public final class GamePlaylistSupport {
    public static final String ALL_KEY = "auto:all";

    private GamePlaylistSupport() { }

    public interface SongsCallback {
        void onSongs(List<Song> songs);
    }

    public static final class PlaylistChoice {
        public final String key;
        public final long playlistId;
        public final String name;
        public final String detail;

        public PlaylistChoice(String key, long playlistId, String name, String detail) {
            this.key = key;
            this.playlistId = playlistId;
            this.name = name;
            this.detail = detail;
        }

        @NonNull @Override public String toString() { return name; }
    }

    public static List<PlaylistChoice> buildChoices(MusicViewModel viewModel) {
        List<PlaylistChoice> choices = new ArrayList<>();
        List<Song> all = viewModel.getSongs().getValue();
        choices.add(new PlaylistChoice(ALL_KEY, -1L, "Todas las canciones",
                (all == null ? 0 : all.size()) + " pistas"));
        List<PlaylistEntity> user = viewModel.getPlaylists().getValue();
        if (user != null) {
            for (PlaylistEntity playlist : user) {
                choices.add(new PlaylistChoice("playlist:" + playlist.id, playlist.id, playlist.name, "Playlist"));
            }
        }
        return choices;
    }

    public static void loadSongs(MusicViewModel viewModel, PlaylistChoice choice, SongsCallback callback) {
        if (choice == null) {
            callback.onSongs(new ArrayList<>());
            return;
        }
        if (choice.playlistId < 0) {
            List<Song> songs = viewModel.getSongs().getValue();
            callback.onSongs(songs == null ? new ArrayList<>() : new ArrayList<>(songs));
        } else {
            viewModel.getPlaylistSongs(choice.playlistId,
                    songs -> callback.onSongs(songs == null ? new ArrayList<>() : new ArrayList<>(songs)));
        }
    }

    public static List<Song> uniqueSongs(List<Song> source) {
        List<Song> unique = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        if (source != null) {
            for (Song song : source) {
                if (song != null && seen.add(song.getId())) unique.add(song);
            }
        }
        return unique;
    }

    public static int roundsFromCheckedId(int checkedId) {
        if (checkedId == R.id.rounds5) return 5;
        if (checkedId == R.id.rounds15) return 15;
        if (checkedId == R.id.rounds20) return 20;
        return 10;
    }
}
