package com.appincreible.musicplayer.ui.radio;

import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.appincreible.musicplayer.databinding.FragmentRadioBinding;
import com.appincreible.musicplayer.player.controller.PlayerController;
import com.appincreible.musicplayer.radio.model.RadioStation;
import com.appincreible.musicplayer.themes.AppPreferences;
import com.appincreible.musicplayer.themes.ListAppearance;
import com.appincreible.musicplayer.themes.UiPalette;
import com.appincreible.musicplayer.ui.metadata.MediaEditorBottomSheet;
import com.appincreible.musicplayer.ui.player.PlayerViewModel;

public class RadioFragment extends Fragment {

    private FragmentRadioBinding binding;
    private RadioViewModel radioViewModel;
    private PlayerViewModel playerViewModel;
    private RadioStationAdapter adapter;
    private String catalogError = "";
    private int radioConnectionState = PlayerController.RADIO_CONNECTION_IDLE;
    private String radioConnectionMediaId = "";
    private String radioConnectionError = "";
    private final ListAppearance.Listener listAppearanceListener = () -> {
        if (binding != null) applyAdaptiveRadioSurface();
    };
    private final SharedPreferences.OnSharedPreferenceChangeListener radioPreferenceListener = (prefs, key) -> {
        if (binding != null && AppPreferences.KEY_SHOW_RADIO_STATION_COUNT.equals(key)) {
            updateStationCountVisibility();
            refreshRadioPlaybackUi();
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentRadioBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        radioViewModel = new ViewModelProvider(this).get(RadioViewModel.class);
        playerViewModel = new ViewModelProvider(requireActivity()).get(PlayerViewModel.class);

        adapter = new RadioStationAdapter(new RadioStationAdapter.Listener() {
            @Override public void onStationClick(RadioStation station, int position) {
                radioViewModel.recordClick(station);
                playerViewModel.playRadioQueue(adapter.getItemsSnapshot(), position);
            }
            @Override public void onFavoriteClick(RadioStation station) { radioViewModel.toggleFavorite(station); }
            @Override public void onStationLongPress(RadioStation station) { openEditor(station); }
        });

        binding.stationList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.stationList.setAdapter(adapter);
        binding.stationList.setHasFixedSize(true);
        binding.stationList.setItemAnimator(null);
        ListAppearance.addListener(listAppearanceListener);
        AppPreferences.prefs(requireContext()).registerOnSharedPreferenceChangeListener(radioPreferenceListener);
        applyAdaptiveRadioSurface();
        updateStationCountVisibility();

        binding.searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                radioViewModel.setQuery(s == null ? "" : s.toString());
            }
            @Override public void afterTextChanged(Editable s) { }
        });

        binding.filterAll.setOnClickListener(v -> {
            binding.filterAll.setChecked(true);
            binding.filterFavorites.setChecked(false);
            radioViewModel.setFavoritesOnly(false);
        });
        binding.filterFavorites.setOnClickListener(v -> {
            binding.filterAll.setChecked(false);
            binding.filterFavorites.setChecked(true);
            radioViewModel.setFavoritesOnly(true);
        });
        binding.retryButton.setOnClickListener(v -> {
            if (radioConnectionState == PlayerController.RADIO_CONNECTION_ERROR) {
                playerViewModel.retryRadioConnection();
            } else {
                radioViewModel.retry();
            }
        });

        getParentFragmentManager().setFragmentResultListener(
                MediaEditorBottomSheet.RESULT_KEY, getViewLifecycleOwner(), (requestKey, result) -> {
                    String key = result.getString(MediaEditorBottomSheet.RESULT_MEDIA_KEY, "");
                    if (key.startsWith("radio:")) {
                        radioViewModel.refreshOverrides();
                        if (!result.getBoolean(MediaEditorBottomSheet.RESULT_RESET, false)) {
                            playerViewModel.applyCurrentMetadata(key,
                                    result.getString(MediaEditorBottomSheet.RESULT_TITLE, ""),
                                    result.getString(MediaEditorBottomSheet.RESULT_ARTIST, ""),
                                    result.getString(MediaEditorBottomSheet.RESULT_ALBUM, ""),
                                    result.getString(MediaEditorBottomSheet.RESULT_ARTWORK, ""));
                        }
                    }
                });

        radioViewModel.getVisibleStations().observe(getViewLifecycleOwner(), stations -> {
            adapter.submitList(stations);
            updateStationCount(stations);
            refreshRadioPlaybackUi();
            boolean empty = stations.isEmpty() && !Boolean.TRUE.equals(radioViewModel.getLoading().getValue())
                    && !hasError(radioViewModel.getError().getValue());
            binding.emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        });
        radioViewModel.getFavoriteIds().observe(getViewLifecycleOwner(), adapter::setFavoriteIds);
        radioViewModel.getLoading().observe(getViewLifecycleOwner(), loading -> {
            boolean isLoading = Boolean.TRUE.equals(loading);
            binding.progress.setVisibility(isLoading ? View.VISIBLE : View.GONE);
            if (isLoading) {
                binding.emptyState.setVisibility(View.GONE);
                if (radioConnectionState != PlayerController.RADIO_CONNECTION_ERROR) binding.errorState.setVisibility(View.GONE);
            }
        });
        radioViewModel.getError().observe(getViewLifecycleOwner(), error -> {
            catalogError = error == null ? "" : error;
            refreshRadioPlaybackUi();
        });

        playerViewModel.getRadioConnectionMediaId().observe(getViewLifecycleOwner(), mediaId -> {
            radioConnectionMediaId = mediaId == null ? "" : mediaId;
            refreshRadioPlaybackUi();
        });
        playerViewModel.getRadioConnectionState().observe(getViewLifecycleOwner(), state -> {
            radioConnectionState = state == null ? PlayerController.RADIO_CONNECTION_IDLE : state;
            refreshRadioPlaybackUi();
        });
        playerViewModel.getRadioConnectionError().observe(getViewLifecycleOwner(), error -> {
            radioConnectionError = error == null ? "" : error;
            refreshRadioPlaybackUi();
        });

        radioViewModel.loadStations();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (binding != null) applyAdaptiveRadioSurface();
    }

    private void applyAdaptiveRadioSurface() {
        UiPalette palette = UiPalette.fallback(requireContext());
        int requestedAccent = AppPreferences.getPlayerAccentColor(requireContext());
        int background = ListAppearance.resolveRenderedBackground(requireContext());
        boolean lightBackground = ColorUtils.calculateLuminance(background) > 0.55d;

        int backgroundText = UiPalette.readableTextColor(background);
        int backgroundSecondary = ColorUtils.setAlphaComponent(backgroundText, 184);
        int accent = ColorUtils.calculateContrast(requestedAccent, background) >= 3.0d
                ? requestedAccent : backgroundText;

        int surfaceBase = lightBackground ? Color.WHITE : Color.rgb(12, 14, 20);
        ListAppearance.Appearance list = ListAppearance.resolve(requireContext());

        int searchSurface = ColorUtils.setAlphaComponent(surfaceBase, lightBackground ? 128 : 116);
        int compositedSearch = ColorUtils.compositeColors(searchSurface, background);
        int searchText = UiPalette.readableTextColor(compositedSearch);
        int searchSecondary = ColorUtils.setAlphaComponent(searchText, 176);

        binding.title.setTextColor(backgroundText);
        binding.subtitle.setTextColor(backgroundSecondary);
        binding.stationCount.setTextColor(backgroundSecondary);
        binding.radioListSurface.setCardBackgroundColor(list.containerColor);
        binding.radioListSurface.setStrokeWidth(0);
        binding.emptyTitle.setTextColor(list.primaryText);
        binding.emptySubtitle.setTextColor(list.secondaryText);

        binding.searchLayout.setBoxBackgroundColor(searchSurface);
        binding.searchLayout.setBoxStrokeColor(accent);
        binding.searchLayout.setStartIconTintList(ColorStateList.valueOf(searchSecondary));
        binding.searchLayout.setDefaultHintTextColor(ColorStateList.valueOf(searchSecondary));
        binding.searchLayout.setHintTextColor(ColorStateList.valueOf(searchSecondary));
        binding.searchInput.setTextColor(searchText);
        binding.searchInput.setHintTextColor(searchSecondary);

        ColorStateList chipBackground = checkedStateColors(accent, Color.TRANSPARENT);
        ColorStateList chipText = checkedStateColors(UiPalette.readableTextColor(accent), backgroundText);
        ColorStateList chipStroke = checkedStateColors(accent,
                ColorUtils.setAlphaComponent(backgroundSecondary, 116));
        binding.filterAll.setChipBackgroundColor(chipBackground);
        binding.filterFavorites.setChipBackgroundColor(chipBackground);
        binding.filterAll.setTextColor(chipText);
        binding.filterFavorites.setTextColor(chipText);
        binding.filterAll.setChipStrokeColor(chipStroke);
        binding.filterFavorites.setChipStrokeColor(chipStroke);

        binding.progress.setIndicatorColor(list.accent);
        binding.retryButton.setTextColor(list.primaryText);
        binding.retryButton.setStrokeColor(ColorStateList.valueOf(
                ColorUtils.setAlphaComponent(list.primaryText, 120)));
        adapter.setAdaptiveColors(list.primaryText, list.secondaryText, list.accent);
    }

    private int resolveRadioBackgroundColor(int fallbackSurface, int accent) {
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

    private ColorStateList checkedStateColors(int checked, int unchecked) {
        return new ColorStateList(
                new int[][] { new int[] { android.R.attr.state_checked }, new int[] { -android.R.attr.state_checked } },
                new int[] { checked, unchecked });
    }


    private void updateStationCountVisibility() {
        if (binding == null) return;
        boolean show = AppPreferences.showRadioStationCount(requireContext());
        binding.stationCount.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show && radioViewModel != null) {
            updateStationCount(radioViewModel.getVisibleStations().getValue());
        }
    }

    private void updateStationCount(@Nullable java.util.List<RadioStation> stations) {
        if (binding == null || !AppPreferences.showRadioStationCount(requireContext())) return;
        int count = stations == null ? 0 : stations.size();
        binding.stationCount.setText(count + (count == 1 ? " emisora" : " emisoras"));
    }

    private void refreshRadioPlaybackUi() {
        if (binding == null || adapter == null) return;
        adapter.setConnectionState(radioConnectionMediaId, radioConnectionState);

        boolean playbackError = radioConnectionState == PlayerController.RADIO_CONNECTION_ERROR
                && hasError(radioConnectionError);
        boolean repositoryError = hasError(catalogError);
        binding.errorState.setVisibility(playbackError || repositoryError ? View.VISIBLE : View.GONE);
        if (playbackError) {
            binding.errorText.setText(radioConnectionError);
            binding.retryButton.setText("Reintentar emisora");
            binding.emptyState.setVisibility(View.GONE);
        } else if (repositoryError) {
            binding.errorText.setText(catalogError);
            binding.retryButton.setText("Reintentar");
            binding.emptyState.setVisibility(View.GONE);
        }
    }

    private boolean hasError(String error) {
        return error != null && !error.trim().isEmpty();
    }

    private void openEditor(RadioStation station) {
        MediaEditorBottomSheet.newInstance(station.getMediaKey(), true, station.getName(), station.getArtist(),
                        station.getAlbum(), station.getFavicon())
                .show(getParentFragmentManager(), "edit-radio");
    }

    @Override public void onDestroyView() {
        ListAppearance.removeListener(listAppearanceListener);
        AppPreferences.prefs(requireContext()).unregisterOnSharedPreferenceChangeListener(radioPreferenceListener);
        binding = null;
        super.onDestroyView();
    }
}
