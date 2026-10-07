package com.appincreible.musicplayer.ui.music;

import android.Manifest;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.appincreible.musicplayer.R;
import com.appincreible.musicplayer.data.model.Song;
import com.appincreible.musicplayer.database.PlaylistEntity;
import com.appincreible.musicplayer.databinding.FragmentMusicBinding;
import com.appincreible.musicplayer.themes.AppPreferences;
import com.appincreible.musicplayer.themes.ListAppearance;
import com.appincreible.musicplayer.themes.UiPalette;
import com.appincreible.musicplayer.ui.common.ModernUiDialogs;
import com.appincreible.musicplayer.ui.metadata.MediaEditorBottomSheet;
import com.appincreible.musicplayer.ui.player.PlayerViewModel;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public class MusicFragment extends Fragment {

    private static final int SECTION_TRACKS = 0;
    private static final int SECTION_PLAYLISTS = 1;
    private static final int SECTION_FAVORITES = 2;
    private static final int SECTION_HIDDEN = 3;

    private FragmentMusicBinding binding;
    private MusicViewModel musicViewModel;
    private PlayerViewModel playerViewModel;
    private SongAdapter songAdapter;
    private PlaylistAdapter playlistAdapter;
    private ItemTouchHelper playlistTouchHelper;
    private ModernUiDialogs.PlaylistEditorHandle activePlaylistEditor;
    private String pendingArtworkStableId = "";
    private int currentSection = SECTION_TRACKS;
    private String sortMode = "date";
    private List<Song> currentSource = Collections.emptyList();
    private List<Song> pendingDelete = Collections.emptyList();
    private boolean syncingTabs;
    private String searchQuery = "";
    private final ListAppearance.Listener listAppearanceListener = () -> {
        if (binding != null) applyAdaptiveMusicSurface();
    };

    private final ActivityResultLauncher<String> permissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                renderPermissionState();
                if (granted) musicViewModel.loadSongs();
            });

    private final ActivityResultLauncher<IntentSenderRequest> deleteLauncher = registerForActivityResult(
            new ActivityResultContracts.StartIntentSenderForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK && !pendingDelete.isEmpty()) {
                    musicViewModel.cleanupDeleted(new ArrayList<>(pendingDelete));
                    pendingDelete = Collections.emptyList();
                    if (songAdapter != null) songAdapter.clearSelection();
                }
            });

    private final ActivityResultLauncher<String[]> playlistArtworkLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri == null || !isAdded()) {
                    pendingArtworkStableId = "";
                    return;
                }
                try {
                    requireContext().getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (Exception ignored) { }
                if (activePlaylistEditor != null && activePlaylistEditor.isShowing()) {
                    activePlaylistEditor.setArtworkUri(uri.toString());
                } else if (!pendingArtworkStableId.isEmpty()) {
                    AppPreferences.setPlaylistArtworkUri(requireContext(), pendingArtworkStableId, uri.toString());
                    if (playlistAdapter != null) playlistAdapter.refreshArtwork(pendingArtworkStableId);
                }
                pendingArtworkStableId = "";
            });

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentMusicBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        musicViewModel = new ViewModelProvider(requireActivity()).get(MusicViewModel.class);
        playerViewModel = new ViewModelProvider(requireActivity()).get(PlayerViewModel.class);

        songAdapter = new SongAdapter(new SongAdapter.Listener() {
            @Override public void onSongClick(int position, Song song) {
                if (currentSection == SECTION_HIDDEN) {
                    android.widget.Toast.makeText(requireContext(), "Mostrá la pista de nuevo para reproducirla", android.widget.Toast.LENGTH_SHORT).show();
                    return;
                }
                List<Song> queue = new ArrayList<>(songAdapter.getCurrentList());
                if (!queue.isEmpty()) playerViewModel.playQueue(queue, position);
            }
            @Override public void onSongLongPress(Song song) { songAdapter.startSelection(song); }
            @Override public void onSongMenu(Song song, View anchor) { showSongMenu(song, anchor); }
            @Override public void onSelectionChanged(int count) { renderSelection(count); }
        });
        playlistAdapter = new PlaylistAdapter(new PlaylistAdapter.Listener() {
            @Override public void onEntryClick(LibraryEntry entry) { handleLibraryEntry(entry); }
            @Override public void onEntryMenu(LibraryEntry entry, View anchor) { showLibraryEntryMenu(entry, anchor); }
            @Override public void onDragRequested(@NonNull RecyclerView.ViewHolder holder) {
                if (playlistTouchHelper != null) playlistTouchHelper.startDrag(holder);
            }
        });

        binding.songList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.songList.setAdapter(songAdapter);
        binding.songList.setItemAnimator(null);
        binding.playlistList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.playlistList.setAdapter(playlistAdapter);
        binding.playlistList.setItemAnimator(null);
        playlistTouchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override public boolean isLongPressDragEnabled() { return false; }
            @Override public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder source, @NonNull RecyclerView.ViewHolder target) {
                return playlistAdapter.moveItem(source.getBindingAdapterPosition(), target.getBindingAdapterPosition());
            }
            @Override public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) { }
            @Override public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                super.clearView(recyclerView, viewHolder);
                musicViewModel.setPlaylistEntryOrder(playlistAdapter.getStableIds());
                savePlaylistScrollState();
            }
        });
        playlistTouchHelper.attachToRecyclerView(binding.playlistList);

        setupTabs();
        ListAppearance.addListener(listAppearanceListener);
        applyAdaptiveMusicSurface();
        binding.permissionButton.setOnClickListener(v -> requestAudioPermission());
        binding.cancelSelection.setOnClickListener(v -> songAdapter.clearSelection());
        binding.selectAllButton.setOnClickListener(v -> songAdapter.selectAll());
        binding.addSelectedButton.setOnClickListener(v -> showAddToPlaylists(songAdapter.getSelectedSongs()));
        binding.hideSelectedButton.setOnClickListener(v -> handleHideSelected());
        binding.deleteSelectedButton.setOnClickListener(v -> requestDelete(songAdapter.getSelectedSongs()));
        binding.newPlaylistButton.setOnClickListener(v -> promptCreatePlaylist(Collections.emptyList()));
        binding.searchButton.setOnClickListener(v -> toggleSearch());
        binding.searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchQuery = s == null ? "" : s.toString().trim();
                if (currentSection != SECTION_PLAYLISTS) renderSongs(currentSource);
            }
            @Override public void afterTextChanged(Editable s) { }
        });

        binding.sortButton.setOnClickListener(this::showSortMenu);
        binding.playAllButton.setOnClickListener(v -> {
            List<Song> songs = new ArrayList<>(songAdapter.getCurrentList());
            if (!songs.isEmpty()) playerViewModel.playQueue(songs, 0);
        });
        binding.shuffleButton.setOnClickListener(v -> {
            List<Song> songs = new ArrayList<>(songAdapter.getCurrentList());
            if (songs.isEmpty()) return;
            int start = new Random().nextInt(songs.size());
            playerViewModel.playQueue(songs, start);
            playerViewModel.enableShuffle();
        });

        getParentFragmentManager().setFragmentResultListener(
                MediaEditorBottomSheet.RESULT_KEY, getViewLifecycleOwner(), (requestKey, result) -> {
                    String key = result.getString(MediaEditorBottomSheet.RESULT_MEDIA_KEY, "");
                    if (key.startsWith("song:")) {
                        musicViewModel.refreshOverrides();
                        if (!result.getBoolean(MediaEditorBottomSheet.RESULT_RESET, false)) {
                            playerViewModel.applyCurrentMetadata(key,
                                    result.getString(MediaEditorBottomSheet.RESULT_TITLE, ""),
                                    result.getString(MediaEditorBottomSheet.RESULT_ARTIST, ""),
                                    result.getString(MediaEditorBottomSheet.RESULT_ALBUM, ""),
                                    result.getString(MediaEditorBottomSheet.RESULT_ARTWORK, ""));
                        }
                    }
                });

        playerViewModel.getMediaKey().observe(getViewLifecycleOwner(), key ->
                songAdapter.setNowPlaying(key, Boolean.TRUE.equals(playerViewModel.getPlaying().getValue())));
        playerViewModel.getPlaying().observe(getViewLifecycleOwner(), playing ->
                songAdapter.setNowPlaying(playerViewModel.getMediaKey().getValue(), Boolean.TRUE.equals(playing)));

        musicViewModel.getSongs().observe(getViewLifecycleOwner(), songs -> {
            if (currentSection == SECTION_TRACKS) renderSongs(songs);
            else if (currentSection == SECTION_PLAYLISTS) renderPlaylists(valueOrEmptyPlaylists(musicViewModel.getPlaylists().getValue()));
        });
        musicViewModel.getFavoriteSongs().observe(getViewLifecycleOwner(), songs -> { if (currentSection == SECTION_FAVORITES) renderSongs(songs); });
        musicViewModel.getHiddenSongs().observe(getViewLifecycleOwner(), songs -> { if (currentSection == SECTION_HIDDEN) renderSongs(songs); });
        musicViewModel.getPlaylists().observe(getViewLifecycleOwner(), playlists -> {
            if (currentSection == SECTION_PLAYLISTS) renderPlaylists(playlists);
        });
        musicViewModel.getHiddenFolderKeys().observe(getViewLifecycleOwner(), ignored -> {
            if (currentSection == SECTION_PLAYLISTS) renderPlaylists(valueOrEmptyPlaylists(musicViewModel.getPlaylists().getValue()));
        });
        musicViewModel.getLoading().observe(getViewLifecycleOwner(), loading ->
                binding.progress.setVisibility(Boolean.TRUE.equals(loading) ? View.VISIBLE : View.GONE));

        int initialSection = musicViewModel.getMusicSection();
        Bundle args = getArguments();
        if (args != null && args.containsKey("initialSection")) {
            initialSection = args.getInt("initialSection", initialSection);
        }
        if (initialSection < SECTION_TRACKS || initialSection > SECTION_HIDDEN) initialSection = SECTION_TRACKS;
        switchSection(initialSection);
        renderPermissionState();
        if (hasAudioPermission()) musicViewModel.loadSongs();
    }

    private void toggleSearch() {
        boolean opening = binding.searchInputLayout.getVisibility() != View.VISIBLE;
        binding.searchInputLayout.setVisibility(opening ? View.VISIBLE : View.GONE);
        android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) requireContext()
                .getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
        if (opening) {
            binding.searchInput.requestFocus();
            if (imm != null) imm.showSoftInput(binding.searchInput, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
        } else {
            // Closing search means leaving search mode completely: hide IME, clear focus/filter
            // and restore the full queue so Next/Previous no longer use the filtered results.
            if (imm != null) imm.hideSoftInputFromWindow(binding.searchInput.getWindowToken(), 0);
            binding.searchInput.clearFocus();
            searchQuery = "";
            binding.searchInput.setText("");
            renderSongs(currentSource);
            playerViewModel.syncSongQueue(buildDisplaySongs(currentSource));
        }
    }

    private void setupTabs() {
        setupTabLayout(binding.tabsTop);
        setupTabLayout(binding.tabsBottom);
        boolean bottom = AppPreferences.TABS_BOTTOM.equals(AppPreferences.getMusicTabsPosition(requireContext()));
        binding.tabsTop.setVisibility(bottom ? View.GONE : View.VISIBLE);
        binding.tabsBottom.setVisibility(bottom ? View.VISIBLE : View.GONE);
    }

    private void setupTabLayout(TabLayout tabs) {
        tabs.removeAllTabs();
        tabs.addTab(tabs.newTab().setText("Pistas"));
        tabs.addTab(tabs.newTab().setText("Playlists"));
        tabs.addTab(tabs.newTab().setText("Favoritos"));
        tabs.addTab(tabs.newTab().setText("Ocultas"));
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) {
                if (!syncingTabs) switchSection(tab.getPosition());
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) { }
            @Override public void onTabReselected(TabLayout.Tab tab) { }
        });
    }

    private void switchSection(int section) {
        if (currentSection == SECTION_PLAYLISTS && section != SECTION_PLAYLISTS) savePlaylistScrollState();
        currentSection = section;
        musicViewModel.setMusicSection(section);
        songAdapter.clearSelection();
        syncTabs(section);
        binding.newPlaylistButton.setVisibility(section == SECTION_PLAYLISTS ? View.VISIBLE : View.GONE);
        binding.searchButton.setVisibility(section == SECTION_PLAYLISTS ? View.GONE : View.VISIBLE);
        if (section == SECTION_PLAYLISTS) {
            searchQuery = "";
            binding.searchInput.setText("");
            binding.searchInputLayout.setVisibility(View.GONE);
        }
        binding.musicListSurface.setVisibility(View.VISIBLE);
        binding.listHeader.setVisibility(section == SECTION_PLAYLISTS ? View.GONE : View.VISIBLE);
        binding.songList.setVisibility(section == SECTION_PLAYLISTS ? View.GONE : View.VISIBLE);
        binding.playlistList.setVisibility(section == SECTION_PLAYLISTS ? View.VISIBLE : View.GONE);
        if (section == SECTION_TRACKS) renderSongs(valueOrEmpty(musicViewModel.getSongs().getValue()));
        else if (section == SECTION_FAVORITES) renderSongs(valueOrEmpty(musicViewModel.getFavoriteSongs().getValue()));
        else if (section == SECTION_HIDDEN) renderSongs(valueOrEmpty(musicViewModel.getHiddenSongs().getValue()));
        else renderPlaylists(valueOrEmptyPlaylists(musicViewModel.getPlaylists().getValue()));
        binding.hideSelectedButton.setText(section == SECTION_HIDDEN ? "Mostrar" : "Ocultar");
    }

    private void syncTabs(int section) {
        syncingTabs = true;
        TabLayout.Tab top = binding.tabsTop.getTabAt(section);
        TabLayout.Tab bottom = binding.tabsBottom.getTabAt(section);
        if (top != null && !top.isSelected()) top.select();
        if (bottom != null && !bottom.isSelected()) bottom.select();
        syncingTabs = false;
    }

    private void renderSongs(List<Song> songs) {
        currentSource = new ArrayList<>(songs);
        List<Song> sorted = buildDisplaySongs(songs);
        songAdapter.submitList(sorted);
        boolean empty = sorted.isEmpty() && hasAudioPermission();
        binding.emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (empty) {
            if (currentSection == SECTION_HIDDEN) {
                binding.emptyTitle.setText("No hay pistas ocultas");
                binding.emptySubtitle.setText("Las canciones que ocultes aparecerán acá y podrás recuperarlas.");
            } else if (currentSection == SECTION_FAVORITES) {
                binding.emptyTitle.setText("Todavía no hay favoritos");
                binding.emptySubtitle.setText("Usá el menú de una pista para agregarla a favoritos.");
            } else {
                binding.emptyTitle.setText("No encontré canciones");
                binding.emptySubtitle.setText("Copiá audio al dispositivo y aparecerá automáticamente acá.");
            }
        }
    }

    private List<Song> buildDisplaySongs(List<Song> songs) {
        List<Song> sorted = new ArrayList<>();
        String query = searchQuery.toLowerCase(Locale.ROOT);
        for (Song song : songs) {
            boolean matches = query.isEmpty() || song.getTitle().toLowerCase(Locale.ROOT).contains(query);
            if (!matches && AppPreferences.libraryArtistsEnabled(requireContext())) {
                matches = song.getArtist().toLowerCase(Locale.ROOT).contains(query);
            }
            if (!matches && AppPreferences.libraryAlbumsEnabled(requireContext())) {
                matches = song.getAlbum().toLowerCase(Locale.ROOT).contains(query);
            }
            if (matches) sorted.add(song);
        }
        if ("title".equals(sortMode)) sorted.sort(Comparator.comparing(Song::getTitle, String.CASE_INSENSITIVE_ORDER));
        else if ("artist".equals(sortMode)) sorted.sort(Comparator.comparing(Song::getArtist, String.CASE_INSENSITIVE_ORDER));
        return sorted;
    }

    private void renderPlaylists(List<PlaylistEntity> playlists) {
        if (currentSection == SECTION_PLAYLISTS && binding != null && binding.playlistList.getChildCount() > 0) {
            savePlaylistScrollState();
        }
        List<LibraryEntry> entries = musicViewModel.buildRootLibraryEntries();
        playlistAdapter.submitList(new ArrayList<>(entries));
        if (currentSection == SECTION_PLAYLISTS) restorePlaylistScrollState();
        boolean empty = entries.isEmpty();
        binding.emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (empty) {
            binding.emptyTitle.setText("No hay playlists");
            binding.emptySubtitle.setText("Creá una playlist y agregá una o varias canciones.");
        }
    }

    private void renderSelection(int count) {
        boolean selecting = count > 0;
        binding.normalHeader.setVisibility(selecting ? View.GONE : View.VISIBLE);
        binding.selectionHeader.setVisibility(selecting ? View.VISIBLE : View.GONE);
        binding.selectionActions.setVisibility(selecting ? View.VISIBLE : View.GONE);
        binding.addSelectedButton.setEnabled(selecting);
        binding.hideSelectedButton.setEnabled(selecting);
        binding.deleteSelectedButton.setEnabled(selecting);
        binding.tabsTop.setVisibility(selecting ? View.GONE : (AppPreferences.TABS_TOP.equals(AppPreferences.getMusicTabsPosition(requireContext())) ? View.VISIBLE : View.GONE));
        binding.tabsBottom.setVisibility(selecting ? View.GONE : (AppPreferences.TABS_BOTTOM.equals(AppPreferences.getMusicTabsPosition(requireContext())) ? View.VISIBLE : View.GONE));
        if (selecting) binding.selectionTitle.setText("Se seleccionó " + count);
    }

    @Override
    public void onPause() {
        if (currentSection == SECTION_PLAYLISTS) savePlaylistScrollState();
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (binding != null) {
            applyLibraryGroupingUi();
            applyAdaptiveMusicSurface();
        }
    }

    private void applyAdaptiveMusicSurface() {
        UiPalette palette = UiPalette.fallback(requireContext());
        int requestedAccent = AppPreferences.getPlayerAccentColor(requireContext());
        int background = resolveMusicBackgroundColor(palette.surface, requestedAccent);
        int tabText = UiPalette.readableTextColor(background);
        int selectedTab = ColorUtils.calculateContrast(requestedAccent, background) >= UiPalette.MIN_TEXT_CONTRAST
                ? requestedAccent : tabText;

        ListAppearance.Appearance list = ListAppearance.resolve(requireContext());
        binding.musicListSurface.setCardBackgroundColor(list.containerColor);
        binding.musicListSurface.setStrokeWidth(0);
        binding.listHeader.setBackgroundColor(Color.TRANSPARENT);
        binding.songList.setBackgroundColor(Color.TRANSPARENT);
        binding.playlistList.setBackgroundColor(Color.TRANSPARENT);

        binding.tabsTop.setBackgroundColor(Color.TRANSPARENT);
        binding.tabsBottom.setBackgroundColor(Color.TRANSPARENT);
        binding.tabsTop.setTabTextColors(ColorUtils.setAlphaComponent(tabText, 176), selectedTab);
        binding.tabsBottom.setTabTextColors(ColorUtils.setAlphaComponent(tabText, 176), selectedTab);
        binding.tabsTop.setSelectedTabIndicatorColor(selectedTab);
        binding.tabsBottom.setSelectedTabIndicatorColor(selectedTab);

        binding.sortButton.setText(sortModeLabel());
        binding.sortButton.setTextColor(list.accent);
        binding.sortButton.setIconTint(ColorStateList.valueOf(list.accent));
        binding.sortButton.setContentDescription("Orden actual: " + sortModeLabel());
        binding.shuffleButton.setIconTint(ColorStateList.valueOf(list.primaryText));
        binding.shuffleButton.setBackgroundTintList(
                ColorStateList.valueOf(ColorUtils.setAlphaComponent(list.primaryText, 20)));
        binding.playAllButton.setBackgroundTintList(ColorStateList.valueOf(list.accent));
        binding.playAllButton.setIconTint(
                ColorStateList.valueOf(UiPalette.readableTextColor(list.accent)));
        int playlistButtonText = UiPalette.readableTextColor(list.accent);
        binding.newPlaylistButton.setBackgroundTintList(ColorStateList.valueOf(list.accent));
        binding.newPlaylistButton.setTextColor(playlistButtonText);
        binding.newPlaylistButton.setIconTint(ColorStateList.valueOf(playlistButtonText));
        binding.listDivider.setBackgroundTintList(ColorStateList.valueOf(
                ColorUtils.setAlphaComponent(list.secondaryText, 76)));

        songAdapter.setAdaptiveColors(list.effectiveSurface, list.primaryText, list.secondaryText, list.accent);
        playlistAdapter.setAdaptiveColors(list.primaryText, list.secondaryText, list.accent);
    }

    private int resolveMusicBackgroundColor(int fallbackSurface, int accent) {
        String scene = AppPreferences.getAppBackground(requireContext());
        if (AppPreferences.BACKGROUND_NONE.equals(scene)) return fallbackSurface;
        if (AppPreferences.BACKGROUND_DARK.equals(scene)) return Color.rgb(10, 12, 18);

        String mode = AppPreferences.getAppBackgroundColorMode(requireContext());
        int c1 = AppPreferences.getAppBackgroundColor1(requireContext());
        int c2 = AppPreferences.getAppBackgroundColor2(requireContext());
        int c3 = AppPreferences.getAppBackgroundColor3(requireContext());
        if (AppPreferences.APP_BACKGROUND_COLOR_SOLID.equals(mode)) return c1;
        if (AppPreferences.APP_BACKGROUND_COLOR_GRADIENT_2.equals(mode)) {
            return ColorUtils.blendARGB(c1, c2, 0.5f);
        }
        if (AppPreferences.APP_BACKGROUND_COLOR_GRADIENT_3.equals(mode)
                || AppPreferences.APP_BACKGROUND_COLOR_MULTICOLOR.equals(mode)) {
            return ColorUtils.blendARGB(ColorUtils.blendARGB(c1, c2, 0.5f), c3, 0.34f);
        }
        return accent;
    }

    private void showSongMenu(Song song, View anchor) {
        boolean hidden = currentSection == SECTION_HIDDEN;
        ModernUiDialogs.showSongActions(this, song, hidden, musicViewModel.isFavorite(song.getId()),
                new ModernUiDialogs.SongActions() {
                    @Override public void addToPlaylist() { showAddToPlaylists(Collections.singletonList(song)); }
                    @Override public void toggleFavorite() { musicViewModel.toggleFavorite(song); }
                    @Override public void toggleHidden() {
                        musicViewModel.setHidden(Collections.singletonList(song), !hidden);
                    }
                    @Override public void details() { showDetails(song); }
                    @Override public void album() { openCollection("album", song.getAlbum(), song.getAlbum(), -1L); }
                    @Override public void artist() { openCollection("artist", song.getArtist(), song.getArtist(), -1L); }
                    @Override public void editMetadata() { openEditor(song); }
                    @Override public void deleteFromPhone() { requestDelete(Collections.singletonList(song)); }
                });
    }

    private void handleHideSelected() {
        List<Song> selected = songAdapter.getSelectedSongs();
        if (selected.isEmpty()) return;
        musicViewModel.setHidden(selected, currentSection != SECTION_HIDDEN);
        songAdapter.clearSelection();
    }

    private void showAddToPlaylists(List<Song> songs) {
        if (songs == null || songs.isEmpty()) return;
        List<PlaylistEntity> playlists = valueOrEmptyPlaylists(musicViewModel.getPlaylists().getValue());
        if (playlists.isEmpty()) {
            promptCreatePlaylist(songs);
            return;
        }
        String[] names = new String[playlists.size()];
        boolean[] checked = new boolean[playlists.size()];
        for (int i = 0; i < playlists.size(); i++) names[i] = playlists.get(i).name;
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Añadir a playlist")
                .setMultiChoiceItems(names, checked, (dialog, which, isChecked) -> checked[which] = isChecked)
                .setNeutralButton("Nueva", (dialog, which) -> promptCreatePlaylist(songs))
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Añadir", (dialog, which) -> {
                    List<Long> ids = new ArrayList<>();
                    for (int i = 0; i < checked.length; i++) if (checked[i]) ids.add(playlists.get(i).id);
                    if (!ids.isEmpty()) musicViewModel.addSongsToPlaylists(songs, ids, () -> songAdapter.clearSelection());
                }).show();
    }

    private void promptCreatePlaylist(List<Song> songsToAdd) {
        pendingArtworkStableId = "";
        activePlaylistEditor = ModernUiDialogs.showPlaylistEditor(this, "Nueva playlist", "", "", "Crear",
                () -> playlistArtworkLauncher.launch(new String[]{"image/*"}),
                (name, artworkUri) -> musicViewModel.createPlaylist(name, id -> {
                    String stableId = "playlist:" + id;
                    if (!artworkUri.isEmpty()) AppPreferences.setPlaylistArtworkUri(requireContext(), stableId, artworkUri);
                    if (songsToAdd != null && !songsToAdd.isEmpty()) {
                        musicViewModel.addSongsToPlaylists(songsToAdd, Collections.singletonList(id), () -> songAdapter.clearSelection());
                    }
                    activePlaylistEditor = null;
                }));
    }

    private void handleLibraryEntry(LibraryEntry entry) {
        if (entry == null) return;
        if (entry.type == LibraryEntry.TYPE_ALL) {
            openCollection("all", "", entry.title, -1L);
        } else if (entry.type == LibraryEntry.TYPE_ARTISTS_ROOT || entry.type == LibraryEntry.TYPE_ALBUMS_ROOT
                || entry.type == LibraryEntry.TYPE_HIDDEN_ROOT) {
            openFolder(entry.value);
        } else if (entry.type == LibraryEntry.TYPE_USER_PLAYLIST) {
            openCollection("playlist", entry.value, entry.title, entry.playlistId);
        }
    }

    private void showLibraryEntryMenu(LibraryEntry entry, View anchor) {
        if (entry == null || entry.type == LibraryEntry.TYPE_HIDDEN_ROOT) return;
        boolean editable = entry.type == LibraryEntry.TYPE_USER_PLAYLIST;
        ModernUiDialogs.showPlaylistActions(this, entry.title, editable, new ModernUiDialogs.PlaylistActions() {
            @Override public void editName() { showRenamePlaylist(entry); }
            @Override public void changeArtwork() {
                activePlaylistEditor = null;
                pendingArtworkStableId = entry.stableId;
                playlistArtworkLauncher.launch(new String[]{"image/*"});
            }
            @Override public void play() { playLibraryEntry(entry, false); }
            @Override public void shuffle() { playLibraryEntry(entry, true); }
            @Override public void delete() { confirmDeletePlaylist(entry); }
        });
    }

    private void showRenamePlaylist(LibraryEntry entry) {
        if (entry.type != LibraryEntry.TYPE_USER_PLAYLIST) return;
        pendingArtworkStableId = "";
        String cover = AppPreferences.getPlaylistArtworkUri(requireContext(), entry.stableId);
        activePlaylistEditor = ModernUiDialogs.showPlaylistEditor(this, "Editar playlist", entry.title, cover, "Guardar",
                () -> playlistArtworkLauncher.launch(new String[]{"image/*"}),
                (name, artworkUri) -> {
                    AppPreferences.setPlaylistArtworkUri(requireContext(), entry.stableId, artworkUri);
                    musicViewModel.renamePlaylist(entry.playlistId, name, () -> {
                        if (playlistAdapter != null) playlistAdapter.refreshArtwork(entry.stableId);
                    });
                    activePlaylistEditor = null;
                });
    }

    private void confirmDeletePlaylist(LibraryEntry entry) {
        if (entry.type != LibraryEntry.TYPE_USER_PLAYLIST) return;
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Eliminar playlist")
                .setMessage("Se eliminará “" + entry.title + "”. Las canciones seguirán en el teléfono.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Eliminar", (dialog, which) ->
                        musicViewModel.deletePlaylist(entry.playlistId, () ->
                                AppPreferences.removePlaylistVisualState(requireContext(), entry.stableId)))
                .show();
    }

    private void playLibraryEntry(LibraryEntry entry, boolean shuffle) {
        java.util.function.Consumer<List<Song>> play = list -> {
            if (list == null || list.isEmpty()) return;
            List<Song> queue = new ArrayList<>(list);
            int start = shuffle ? new Random().nextInt(queue.size()) : 0;
            playerViewModel.playQueue(queue, start);
            if (shuffle) playerViewModel.enableShuffle();
        };
        if (entry.type == LibraryEntry.TYPE_USER_PLAYLIST) {
            musicViewModel.getPlaylistSongs(entry.playlistId, play);
        } else if (entry.type == LibraryEntry.TYPE_ALL
                || entry.type == LibraryEntry.TYPE_ARTISTS_ROOT
                || entry.type == LibraryEntry.TYPE_ALBUMS_ROOT) {
            play.accept(musicViewModel.allVisibleSongs());
        }
    }

    private void savePlaylistScrollState() {
        if (binding == null || !(binding.playlistList.getLayoutManager() instanceof LinearLayoutManager)) return;
        LinearLayoutManager lm = (LinearLayoutManager) binding.playlistList.getLayoutManager();
        int position = lm.findFirstVisibleItemPosition();
        if (position == RecyclerView.NO_POSITION) return;
        View first = lm.findViewByPosition(position);
        int offset = first == null ? 0 : first.getTop() - binding.playlistList.getPaddingTop();
        musicViewModel.setPlaylistScroll(position, offset);
    }

    private void restorePlaylistScrollState() {
        if (binding == null) return;
        binding.playlistList.post(() -> {
            if (binding == null || !(binding.playlistList.getLayoutManager() instanceof LinearLayoutManager)) return;
            ((LinearLayoutManager) binding.playlistList.getLayoutManager()).scrollToPositionWithOffset(
                    musicViewModel.getPlaylistScrollPosition(), musicViewModel.getPlaylistScrollOffset());
        });
    }

    private void openFolder(String mode) {
        if (currentSection == SECTION_PLAYLISTS) savePlaylistScrollState();
        Bundle args = new Bundle();
        args.putString("mode", mode);
        Navigation.findNavController(requireView()).navigate(R.id.libraryFolderFragment, args);
    }

    private void openCollection(String type, String value, String title, long playlistId) {
        if ("artist".equals(type) && !AppPreferences.libraryArtistsEnabled(requireContext())) return;
        if ("album".equals(type) && !AppPreferences.libraryAlbumsEnabled(requireContext())) return;
        if (currentSection == SECTION_PLAYLISTS) savePlaylistScrollState();
        Bundle args = new Bundle();
        args.putString("type", type);
        args.putString("value", value);
        args.putString("title", title);
        args.putLong("playlistId", playlistId);
        Navigation.findNavController(requireView()).navigate(R.id.collectionFragment, args);
    }

    private void showDetails(Song song) {
        StringBuilder text = new StringBuilder("Título\n").append(song.getTitle());
        if (AppPreferences.libraryArtistsEnabled(requireContext())) {
            text.append("\n\nArtista\n").append(song.getArtist());
        }
        if (AppPreferences.libraryAlbumsEnabled(requireContext())) {
            text.append("\n\nÁlbum\n").append(song.getAlbum());
        }
        text.append("\n\nDuración\n").append(formatDuration(song.getDurationMs()))
                .append("\n\nUbicación\n").append(song.getContentUri());
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Detalles de la pista")
                .setMessage(text.toString())
                .setNegativeButton("Cerrar", null)
                .setPositiveButton("Editar", (dialog, which) -> openEditor(song))
                .show();
    }

    private void openEditor(Song song) {
        MediaEditorBottomSheet.newInstance(song.getMediaKey(), false, song.getTitle(), song.getArtist(),
                        song.getAlbum(), song.getArtworkUri())
                .show(getParentFragmentManager(), "edit-song");
    }

    private void requestDelete(List<Song> songs) {
        if (songs == null || songs.isEmpty()) return;
        pendingDelete = new ArrayList<>(songs);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            List<android.net.Uri> uris = new ArrayList<>();
            for (Song song : songs) uris.add(song.getContentUri());
            PendingIntent request = MediaStore.createDeleteRequest(requireContext().getContentResolver(), uris);
            deleteLauncher.launch(new IntentSenderRequest.Builder(request.getIntentSender()).build());
            return;
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(songs.size() == 1 ? "Eliminar canción" : "Eliminar canciones")
                .setMessage("Se borrarán físicamente del dispositivo.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Eliminar", (dialog, which) -> deleteLegacy(songs))
                .show();
    }

    private void deleteLegacy(List<Song> songs) {
        ContentResolver resolver = requireContext().getContentResolver();
        for (Song song : songs) {
            try { resolver.delete(song.getContentUri(), null, null); } catch (Exception ignored) { }
        }
        musicViewModel.cleanupDeleted(new ArrayList<>(songs));
        songAdapter.clearSelection();
    }

    private void showSortMenu(View anchor) {
        PopupMenu popup = new PopupMenu(requireContext(), anchor);
        popup.getMenu().add(0, 1, 0, "Fecha añadida").setCheckable(true);
        popup.getMenu().add(0, 2, 1, "Título").setCheckable(true);
        if (AppPreferences.libraryArtistsEnabled(requireContext())) {
            popup.getMenu().add(0, 3, 2, "Artista").setCheckable(true);
        }
        popup.getMenu().setGroupCheckable(0, true, true);
        int checkedId = "title".equals(sortMode) ? 2 : "artist".equals(sortMode) ? 3 : 1;
        if (popup.getMenu().findItem(checkedId) != null) {
            popup.getMenu().findItem(checkedId).setChecked(true);
        }
        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) sortMode = "date";
            else if (item.getItemId() == 2) sortMode = "title";
            else sortMode = "artist";
            updateSortButtonState();
            renderSongs(currentSource);
            return true;
        });
        popup.show();
    }

    private void updateSortButtonState() {
        if (binding == null) return;
        binding.sortButton.setText(sortModeLabel());
        ListAppearance.Appearance list = ListAppearance.resolve(requireContext());
        binding.sortButton.setTextColor(list.accent);
        binding.sortButton.setIconTint(ColorStateList.valueOf(list.accent));
        binding.sortButton.setContentDescription("Orden actual: " + sortModeLabel());
    }

    private String sortModeLabel() {
        if ("title".equals(sortMode)) return "Título";
        if ("artist".equals(sortMode)) return "Artista";
        return "Fecha añadida";
    }

    private void applyLibraryGroupingUi() {
        boolean artists = AppPreferences.libraryArtistsEnabled(requireContext());
        boolean albums = AppPreferences.libraryAlbumsEnabled(requireContext());
        if (!artists && "artist".equals(sortMode)) {
            sortMode = "date";
            updateSortButtonState();
        }
        binding.searchInputLayout.setHint(artists && albums ? "Buscar por canción, artista o álbum"
                : artists ? "Buscar por canción o artista" : "Buscar por canción o álbum");
        if (currentSection == SECTION_PLAYLISTS) renderPlaylists(valueOrEmptyPlaylists(musicViewModel.getPlaylists().getValue()));
        else renderSongs(currentSource);
    }

    private void renderPermissionState() {
        boolean granted = hasAudioPermission();
        binding.permissionButton.setVisibility(granted ? View.GONE : View.VISIBLE);
        if (!granted) {
            binding.songList.setVisibility(View.GONE);
            binding.playlistList.setVisibility(View.GONE);
            binding.progress.setVisibility(View.GONE);
            binding.emptyState.setVisibility(View.GONE);
        }
    }

    private boolean hasAudioPermission() {
        String permission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                ? Manifest.permission.READ_MEDIA_AUDIO : Manifest.permission.READ_EXTERNAL_STORAGE;
        return ContextCompat.checkSelfPermission(requireContext(), permission) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestAudioPermission() {
        String permission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                ? Manifest.permission.READ_MEDIA_AUDIO : Manifest.permission.READ_EXTERNAL_STORAGE;
        permissionLauncher.launch(permission);
    }

    private String formatDuration(long durationMs) {
        long seconds = durationMs / 1000L;
        return String.format(Locale.getDefault(), "%d:%02d", seconds / 60L, seconds % 60L);
    }

    private List<Song> valueOrEmpty(List<Song> value) { return value == null ? Collections.emptyList() : value; }
    private List<PlaylistEntity> valueOrEmptyPlaylists(List<PlaylistEntity> value) { return value == null ? Collections.emptyList() : value; }

    @Override public void onDestroyView() {
        ListAppearance.removeListener(listAppearanceListener);
        activePlaylistEditor = null;
        playlistTouchHelper = null;
        binding = null;
        super.onDestroyView();
    }
}
