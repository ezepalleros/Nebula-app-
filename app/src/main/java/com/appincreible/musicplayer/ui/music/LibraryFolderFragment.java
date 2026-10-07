package com.appincreible.musicplayer.ui.music;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.appincreible.musicplayer.R;
import com.appincreible.musicplayer.databinding.FragmentLibraryFolderBinding;
import com.appincreible.musicplayer.themes.AppPreferences;

import java.util.List;

public class LibraryFolderFragment extends Fragment {
    private FragmentLibraryFolderBinding binding;
    private MusicViewModel viewModel;
    private PlaylistAdapter adapter;
    private String mode = "artists";

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentLibraryFolderBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(MusicViewModel.class);
        Bundle args = getArguments();
        if (args != null) mode = args.getString("mode", "artists");

        binding.title.setText(titleForMode());
        binding.backButton.setOnClickListener(v -> Navigation.findNavController(v).navigateUp());
        adapter = new PlaylistAdapter(new PlaylistAdapter.Listener() {
            @Override public void onEntryClick(LibraryEntry entry) { handleEntryClick(entry); }
            @Override public void onEntryMenu(LibraryEntry entry, View anchor) { showMenu(entry, anchor); }
            @Override public void onDragRequested(@NonNull androidx.recyclerview.widget.RecyclerView.ViewHolder holder) {
                // Las carpetas/artistas/álbumes no son reordenables en esta pantalla.
            }
        });
        binding.folderList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.folderList.setAdapter(adapter);
        binding.folderList.setItemAnimator(null);

        viewModel.getSongs().observe(getViewLifecycleOwner(), ignored -> render());
        viewModel.getPlaylists().observe(getViewLifecycleOwner(), ignored -> render());
        viewModel.getHiddenFolderKeys().observe(getViewLifecycleOwner(), ignored -> render());
        render();
    }

    private String titleForMode() {
        if ("albums".equals(mode)) return "Por álbum";
        if ("hidden".equals(mode)) return "Carpetas ocultas";
        return "Por banda / cantante";
    }

    private void render() {
        if (binding == null || viewModel == null) return;
        List<LibraryEntry> entries = viewModel.buildFolderEntries(mode, false);
        adapter.submitList(entries);
        binding.subtitle.setText(entries.size() + (entries.size() == 1 ? " carpeta" : " carpetas"));
        boolean empty = entries.isEmpty();
        binding.emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (empty) {
            binding.emptyState.setText("hidden".equals(mode)
                    ? "No hay carpetas ocultas"
                    : "No hay carpetas disponibles");
        }
    }

    private void handleEntryClick(LibraryEntry entry) {
        if (entry.hidden || "hidden".equals(mode)) {
            Toast.makeText(requireContext(), "Mostrá la carpeta de nuevo para abrirla", Toast.LENGTH_SHORT).show();
            return;
        }
        if (entry.type == LibraryEntry.TYPE_ARTIST) {
            openCollection("artist", entry.value, entry.title, -1L);
        } else if (entry.type == LibraryEntry.TYPE_ALBUM) {
            openCollection("album", entry.value, entry.title, -1L);
        } else if (entry.type == LibraryEntry.TYPE_ALL) {
            openCollection("all", "", entry.title, -1L);
        } else if (entry.type == LibraryEntry.TYPE_ARTISTS_ROOT || entry.type == LibraryEntry.TYPE_ALBUMS_ROOT) {
            openFolder(entry.value);
        }
    }

    private void showMenu(LibraryEntry entry, View anchor) {
        if (!entry.hideable || entry.folderKey.isEmpty()) return;
        PopupMenu popup = new PopupMenu(requireContext(), anchor);
        popup.getMenu().add(entry.hidden || "hidden".equals(mode) ? "Mostrar de nuevo" : "Ocultar carpeta");
        popup.setOnMenuItemClickListener(item -> {
            viewModel.setFolderHidden(entry.folderKey, !(entry.hidden || "hidden".equals(mode)));
            return true;
        });
        popup.show();
    }

    private void openFolder(String newMode) {
        if ("artists".equals(newMode) && !AppPreferences.libraryArtistsEnabled(requireContext())) return;
        if ("albums".equals(newMode) && !AppPreferences.libraryAlbumsEnabled(requireContext())) return;
        Bundle args = new Bundle();
        args.putString("mode", newMode);
        Navigation.findNavController(requireView()).navigate(R.id.libraryFolderFragment, args);
    }

    private void openCollection(String type, String value, String title, long playlistId) {
        Bundle args = new Bundle();
        args.putString("type", type);
        args.putString("value", value);
        args.putString("title", title);
        args.putLong("playlistId", playlistId);
        Navigation.findNavController(requireView()).navigate(R.id.collectionFragment, args);
    }

    @Override public void onResume() {
        super.onResume();
        if (binding == null) return;
        boolean disabled = ("artists".equals(mode) && !AppPreferences.libraryArtistsEnabled(requireContext()))
                || ("albums".equals(mode) && !AppPreferences.libraryAlbumsEnabled(requireContext()));
        if (disabled) Navigation.findNavController(binding.getRoot()).navigateUp();
        else render();
    }

    @Override public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }
}
