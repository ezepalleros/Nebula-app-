package com.appincreible.musicplayer.ui.library;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.appincreible.musicplayer.data.model.Song;
import com.appincreible.musicplayer.databinding.FragmentLibraryBinding;
import com.appincreible.musicplayer.themes.AppPreferences;
import com.appincreible.musicplayer.ui.music.MusicViewModel;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class LibraryFragment extends Fragment {

    private FragmentLibraryBinding binding;
    private MusicViewModel viewModel;
    private List<Song> lastSongs;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentLibraryBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        viewModel = new ViewModelProvider(requireActivity()).get(MusicViewModel.class);
        viewModel.getSongs().observe(getViewLifecycleOwner(), this::renderCounts);
    }

    private void renderCounts(List<Song> songs) {
        lastSongs = songs;
        Set<String> artists = new HashSet<>();
        Set<String> albums = new HashSet<>();
        for (Song song : songs) {
            artists.add(song.getArtist());
            albums.add(song.getAlbum());
        }
        binding.songCount.setText(String.valueOf(songs.size()));
        binding.artistCount.setText(String.valueOf(artists.size()));
        binding.albumCount.setText(String.valueOf(albums.size()));
        binding.artistStatsCard.setVisibility(AppPreferences.libraryArtistsEnabled(requireContext()) ? View.VISIBLE : View.GONE);
        binding.albumStatsCard.setVisibility(AppPreferences.libraryAlbumsEnabled(requireContext()) ? View.VISIBLE : View.GONE);
    }

    @Override public void onResume() {
        super.onResume();
        if (binding != null && lastSongs != null) renderCounts(lastSongs);
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }
}
