package com.appincreible.musicplayer.ui.music;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.res.ColorStateList;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.graphics.ColorUtils;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.appincreible.musicplayer.R;
import com.appincreible.musicplayer.data.model.Song;
import com.appincreible.musicplayer.database.PlaylistEntity;
import com.appincreible.musicplayer.databinding.FragmentCollectionBinding;
import com.appincreible.musicplayer.themes.AppPreferences;
import com.appincreible.musicplayer.themes.ListAppearance;
import com.appincreible.musicplayer.themes.UiPalette;
import com.appincreible.musicplayer.ui.common.ModernUiDialogs;
import com.appincreible.musicplayer.ui.metadata.MediaEditorBottomSheet;
import com.appincreible.musicplayer.ui.player.PlayerViewModel;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class CollectionFragment extends Fragment {
    private FragmentCollectionBinding binding;
    private MusicViewModel musicViewModel;
    private PlayerViewModel playerViewModel;
    private SongAdapter adapter;
    private String type = "album";
    private String value = "";
    private long playlistId = -1L;
    private boolean playlistAddMode;
    private List<Song> songs = Collections.emptyList();
    private List<Song> pendingDelete = Collections.emptyList();
    private String originalTitle = "Colección";
    private String sortMode = "date";
    private final ListAppearance.Listener listAppearanceListener = () -> {
        if (binding != null) applyListAppearance();
    };

    private final ActivityResultLauncher<IntentSenderRequest> deleteLauncher = registerForActivityResult(
            new ActivityResultContracts.StartIntentSenderForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK && !pendingDelete.isEmpty()) {
                    musicViewModel.cleanupDeleted(new ArrayList<>(pendingDelete));
                    pendingDelete = Collections.emptyList();
                    loadCurrentCollection();
                }
            });

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentCollectionBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        musicViewModel = new ViewModelProvider(requireActivity()).get(MusicViewModel.class);
        playerViewModel = new ViewModelProvider(requireActivity()).get(PlayerViewModel.class);
        Bundle args = getArguments();
        if (args != null) {
            type = args.getString("type", "album");
            value = args.getString("value", "");
            playlistId = args.getLong("playlistId", -1L);
            playlistAddMode = args.getBoolean("playlistAddMode", false);
            originalTitle = args.getString("title", value);
            binding.title.setText(originalTitle);
        }

        adapter = new SongAdapter(new SongAdapter.Listener() {
            @Override public void onSongClick(int position, Song song) { playerViewModel.playQueue(new ArrayList<>(adapter.getCurrentList()), position); }
            @Override public void onSongLongPress(Song song) { adapter.startSelection(song); }
            @Override public void onSongMenu(Song song, View anchor) { showMenu(song, anchor); }
            @Override public void onSelectionChanged(int count) { renderSelection(count); }
        });
        adapter.setSelectionOnly(playlistAddMode);
        binding.songList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.songList.setAdapter(adapter);
        binding.songList.setItemAnimator(null);
        ListAppearance.addListener(listAppearanceListener);
        applyListAppearance();
        binding.backButton.setOnClickListener(v -> {
            if (adapter.isSelectionMode()) adapter.clearSelection();
            else Navigation.findNavController(v).navigateUp();
        });
        binding.addSelectedButton.setOnClickListener(v -> {
            List<Song> selected = adapter.getSelectedSongs();
            if (playlistAddMode) {
                if (selected.isEmpty()) return;
                musicViewModel.addSongsToPlaylists(selected, Collections.singletonList(playlistId), () -> {
                    if (binding != null) Navigation.findNavController(binding.getRoot()).navigateUp();
                });
            } else {
                showAddToPlaylists(selected);
            }
        });
        binding.hideSelectedButton.setOnClickListener(v -> {
            List<Song> selected = adapter.getSelectedSongs();
            if (!selected.isEmpty()) musicViewModel.setHidden(selected, true);
            adapter.clearSelection();
        });
        binding.deleteSelectedButton.setOnClickListener(v -> {
            List<Song> selected = adapter.getSelectedSongs();
            if (isOwnPlaylist()) {
                musicViewModel.removeSongsFromPlaylist(playlistId, selected, () -> {
                    adapter.clearSelection();
                    loadCurrentCollection();
                });
            } else {
                requestDelete(selected);
            }
        });
        binding.sortButton.setOnClickListener(this::showSortMenu);
        binding.addSongsButton.setVisibility(isOwnPlaylist() ? View.VISIBLE : View.GONE);
        binding.addSongsButton.setOnClickListener(v -> showAddSongsToCurrentPlaylist());
        if (playlistAddMode) {
            binding.shuffleButton.setVisibility(View.GONE);
            binding.playButton.setVisibility(View.GONE);
            binding.addSelectedButton.setText("Añadir");
            binding.hideSelectedButton.setVisibility(View.GONE);
            binding.deleteSelectedButton.setVisibility(View.GONE);
        } else if (isOwnPlaylist()) {
            binding.deleteSelectedButton.setText("Quitar");
            binding.deleteSelectedButton.setIconResource(android.R.drawable.ic_menu_close_clear_cancel);
        }

        binding.playButton.setOnClickListener(v -> {
            List<Song> queue = new ArrayList<>(adapter.getCurrentList());
            if (!queue.isEmpty()) playerViewModel.playQueue(queue, 0);
        });
        binding.shuffleButton.setOnClickListener(v -> {
            List<Song> queue = new ArrayList<>(adapter.getCurrentList());
            if (queue.isEmpty()) return;
            playerViewModel.playQueue(queue, new Random().nextInt(queue.size()));
            playerViewModel.enableShuffle();
        });

        playerViewModel.getMediaKey().observe(getViewLifecycleOwner(), key ->
                adapter.setNowPlaying(key, Boolean.TRUE.equals(playerViewModel.getPlaying().getValue())));
        playerViewModel.getPlaying().observe(getViewLifecycleOwner(), playing ->
                adapter.setNowPlaying(playerViewModel.getMediaKey().getValue(), Boolean.TRUE.equals(playing)));

        musicViewModel.getSongs().observe(getViewLifecycleOwner(), ignored -> loadCurrentCollection());
        musicViewModel.getPlaylists().observe(getViewLifecycleOwner(), ignored -> {
            if ("playlist".equals(type) || playlistAddMode) loadCurrentCollection();
        });
        loadCurrentCollection();
    }

    private void loadCurrentCollection() {
        if (musicViewModel == null) return;
        if (playlistAddMode) {
            musicViewModel.getPlaylistSongs(playlistId, existing -> {
                Set<Long> existingIds = new HashSet<>();
                if (existing != null) for (Song song : existing) existingIds.add(song.getId());
                List<Song> available = new ArrayList<>();
                for (Song song : musicViewModel.allVisibleSongs()) {
                    if (!existingIds.contains(song.getId())) available.add(song);
                }
                render(available);
            });
        } else if ("playlist".equals(type)) musicViewModel.getPlaylistSongs(playlistId, this::render);
        else if ("artist".equals(type)) render(musicViewModel.songsForArtist(value));
        else if ("all".equals(type)) render(musicViewModel.allVisibleSongs());
        else render(musicViewModel.songsForAlbum(value));
    }

    private void render(List<Song> result) {
        if (binding == null) return;
        songs = result == null ? Collections.emptyList() : new ArrayList<>(result);
        submitSortedSongs();
        binding.subtitle.setText(songs.size() + (songs.size() == 1 ? " pista" : " pistas"));
        binding.emptyState.setVisibility(songs.isEmpty() ? View.VISIBLE : View.GONE);
        if (songs.isEmpty() && playlistAddMode) {
            binding.emptyState.setText("Todas las canciones ya están en esta playlist");
        }
    }

    private void submitSortedSongs() {
        List<Song> sorted = new ArrayList<>(songs);
        if ("title".equals(sortMode)) {
            sorted.sort(Comparator.comparing(Song::getTitle, String.CASE_INSENSITIVE_ORDER));
        } else if ("artist".equals(sortMode)) {
            sorted.sort(Comparator.comparing(Song::getArtist, String.CASE_INSENSITIVE_ORDER));
        }
        adapter.submitList(sorted);
    }

    private void renderSelection(int count) {
        boolean selecting = count > 0;
        binding.selectionActions.setVisibility(selecting ? View.VISIBLE : View.GONE);
        binding.addSelectedButton.setEnabled(selecting);
        binding.hideSelectedButton.setEnabled(selecting);
        binding.deleteSelectedButton.setEnabled(selecting);
        binding.listHeader.setVisibility(selecting ? View.GONE : View.VISIBLE);
        binding.subtitle.setVisibility(selecting ? View.GONE : View.VISIBLE);
        binding.title.setText(selecting ? "Se seleccionó " + count : originalTitle);
        binding.backButton.setText(selecting ? "×" : "‹");
        if (playlistAddMode) {
            binding.addSelectedButton.setVisibility(View.VISIBLE);
            binding.hideSelectedButton.setVisibility(View.GONE);
            binding.deleteSelectedButton.setVisibility(View.GONE);
        } else if (isOwnPlaylist()) {
            binding.deleteSelectedButton.setText("Quitar");
            binding.deleteSelectedButton.setIconResource(android.R.drawable.ic_menu_close_clear_cancel);
        } else {
            binding.deleteSelectedButton.setText("Eliminar");
            binding.deleteSelectedButton.setIconResource(android.R.drawable.ic_menu_delete);
        }
    }

    private boolean isOwnPlaylist() {
        return !playlistAddMode && "playlist".equals(type) && playlistId >= 0L;
    }

    private void showSortMenu(View anchor) {
        PopupMenu popup = new PopupMenu(requireContext(), anchor);
        popup.getMenu().add(0, 1, 0, "Fecha añadida");
        popup.getMenu().add(0, 2, 1, "Título");
        if (AppPreferences.libraryArtistsEnabled(requireContext())) {
            popup.getMenu().add(0, 3, 2, "Artista");
        }
        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                sortMode = "date";
                binding.sortButton.setText("Fecha añadido");
            } else if (item.getItemId() == 2) {
                sortMode = "title";
                binding.sortButton.setText("Título");
            } else {
                sortMode = "artist";
                binding.sortButton.setText("Artista");
            }
            submitSortedSongs();
            return true;
        });
        popup.show();
    }

    private void applyListAppearance() {
        ListAppearance.Appearance list = ListAppearance.resolve(requireContext());
        binding.collectionListSurface.setCardBackgroundColor(list.containerColor);
        binding.collectionListSurface.setStrokeWidth(0);
        binding.sortButton.setTextColor(list.secondaryText);
        binding.sortButton.setIconTint(ColorStateList.valueOf(list.secondaryText));
        binding.shuffleButton.setIconTint(ColorStateList.valueOf(list.primaryText));
        binding.shuffleButton.setBackgroundTintList(ColorStateList.valueOf(
                ColorUtils.setAlphaComponent(list.primaryText, 20)));
        binding.playButton.setBackgroundTintList(ColorStateList.valueOf(list.accent));
        binding.playButton.setIconTint(ColorStateList.valueOf(UiPalette.readableTextColor(list.accent)));
        binding.listDivider.setBackgroundTintList(ColorStateList.valueOf(
                ColorUtils.setAlphaComponent(list.secondaryText, 76)));
        adapter.setAdaptiveColors(list.effectiveSurface, list.primaryText, list.secondaryText, list.accent);
    }

    private void showMenu(Song song, View anchor) {
        boolean ownPlaylist = "playlist".equals(type) && playlistId >= 0L;
        ModernUiDialogs.showSongActions(this, song, false, musicViewModel.isFavorite(song.getId()), ownPlaylist,
                new ModernUiDialogs.SongActions() {
                    @Override public void addToPlaylist() { showAddToPlaylists(Collections.singletonList(song)); }
                    @Override public void toggleFavorite() { musicViewModel.toggleFavorite(song); }
                    @Override public void toggleHidden() { musicViewModel.setHidden(Collections.singletonList(song), true); }
                    @Override public void details() { showDetails(song); }
                    @Override public void album() { openCollection("album", song.getAlbum(), song.getAlbum(), -1L); }
                    @Override public void artist() { openCollection("artist", song.getArtist(), song.getArtist(), -1L); }
                    @Override public void editMetadata() { openEditor(song); }
                    @Override public void removeFromPlaylist() {
                        musicViewModel.removeSongFromPlaylist(playlistId, song, CollectionFragment.this::loadCurrentCollection);
                    }
                    @Override public void deleteFromPhone() { requestDelete(Collections.singletonList(song)); }
                });
    }

    private void showAddSongsToCurrentPlaylist() {
        if (!isOwnPlaylist()) return;
        Bundle args = new Bundle();
        args.putString("type", "all");
        args.putString("value", "");
        args.putString("title", "Añadir canciones");
        args.putLong("playlistId", playlistId);
        args.putBoolean("playlistAddMode", true);
        Navigation.findNavController(requireView()).navigate(R.id.collectionFragment, args);
    }

    private void showAddToPlaylists(List<Song> songsToAdd) {
        List<PlaylistEntity> playlists = musicViewModel.getPlaylists().getValue();
        if (playlists == null) playlists = Collections.emptyList();
        if (playlists.isEmpty()) {
            promptCreatePlaylist(songsToAdd);
            return;
        }
        final List<PlaylistEntity> available = new ArrayList<>(playlists);
        String[] names = new String[available.size()];
        boolean[] checked = new boolean[available.size()];
        for (int i = 0; i < available.size(); i++) names[i] = available.get(i).name;
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Añadir a playlist")
                .setMultiChoiceItems(names, checked, (dialog, which, isChecked) -> checked[which] = isChecked)
                .setNeutralButton("Nueva", (dialog, which) -> promptCreatePlaylist(songsToAdd))
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Añadir", (dialog, which) -> {
                    List<Long> ids = new ArrayList<>();
                    for (int i = 0; i < checked.length; i++) if (checked[i]) ids.add(available.get(i).id);
                    if (!ids.isEmpty()) musicViewModel.addSongsToPlaylists(songsToAdd, ids, null);
                }).show();
    }

    private void promptCreatePlaylist(List<Song> songsToAdd) {
        ModernUiDialogs.showCreatePlaylist(this, name -> musicViewModel.createPlaylist(name, id ->
                musicViewModel.addSongsToPlaylists(songsToAdd, Collections.singletonList(id), null)));
    }

    private void openCollection(String newType, String newValue, String title, long newPlaylistId) {
        if ("artist".equals(newType) && !AppPreferences.libraryArtistsEnabled(requireContext())) return;
        if ("album".equals(newType) && !AppPreferences.libraryAlbumsEnabled(requireContext())) return;
        Bundle args = new Bundle();
        args.putString("type", newType);
        args.putString("value", newValue);
        args.putString("title", title);
        args.putLong("playlistId", newPlaylistId);
        Navigation.findNavController(requireView()).navigate(R.id.collectionFragment, args);
    }

    private void requestDelete(List<Song> songsToDelete) {
        if (songsToDelete == null || songsToDelete.isEmpty()) return;
        pendingDelete = new ArrayList<>(songsToDelete);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            List<android.net.Uri> uris = new ArrayList<>();
            for (Song song : songsToDelete) uris.add(song.getContentUri());
            PendingIntent request = MediaStore.createDeleteRequest(requireContext().getContentResolver(), uris);
            deleteLauncher.launch(new IntentSenderRequest.Builder(request.getIntentSender()).build());
            return;
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Eliminar del teléfono")
                .setMessage("El archivo se borrará físicamente del dispositivo.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Eliminar", (dialog, which) -> deleteLegacy(songsToDelete)).show();
    }

    private void deleteLegacy(List<Song> songsToDelete) {
        ContentResolver resolver = requireContext().getContentResolver();
        for (Song song : songsToDelete) {
            try { resolver.delete(song.getContentUri(), null, null); } catch (Exception ignored) { }
        }
        musicViewModel.cleanupDeleted(new ArrayList<>(songsToDelete));
    }

    private void showDetails(Song song) {
        StringBuilder text = new StringBuilder("Título\n").append(song.getTitle());
        if (AppPreferences.libraryArtistsEnabled(requireContext())) {
            text.append("\n\nArtista\n").append(song.getArtist());
        }
        if (AppPreferences.libraryAlbumsEnabled(requireContext())) {
            text.append("\n\nÁlbum\n").append(song.getAlbum());
        }
        text.append("\n\n").append(song.getContentUri());
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Detalles de la pista")
                .setMessage(text.toString())
                .setNegativeButton("Cerrar", null)
                .setPositiveButton("Editar", (dialog, which) -> openEditor(song)).show();
    }

    private void openEditor(Song song) {
        MediaEditorBottomSheet.newInstance(song.getMediaKey(), false, song.getTitle(), song.getArtist(), song.getAlbum(), song.getArtworkUri())
                .show(getParentFragmentManager(), "edit-song");
    }

    @Override public void onResume() {
        super.onResume();
        if (binding == null) return;
        if (!isGroupingAllowed()) {
            Navigation.findNavController(binding.getRoot()).navigateUp();
            return;
        }
        if (!AppPreferences.libraryArtistsEnabled(requireContext()) && "artist".equals(sortMode)) {
            sortMode = "date";
            binding.sortButton.setText("Fecha añadido");
            submitSortedSongs();
        }
        applyListAppearance();
    }

    private boolean isGroupingAllowed() {
        if ("artist".equals(type)) return AppPreferences.libraryArtistsEnabled(requireContext());
        if ("album".equals(type)) return AppPreferences.libraryAlbumsEnabled(requireContext());
        return true;
    }

    @Override public void onDestroyView() {
        ListAppearance.removeListener(listAppearanceListener);
        binding = null;
        super.onDestroyView();
    }
}
