package com.appincreible.musicplayer.ui.radio;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.appincreible.musicplayer.database.MediaOverrideEntity;
import com.appincreible.musicplayer.metadata.repository.MediaMetadataRepository;
import com.appincreible.musicplayer.radio.model.RadioStation;
import com.appincreible.musicplayer.radio.repository.RadioFavoritesRepository;
import com.appincreible.musicplayer.radio.repository.RadioRepository;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class RadioViewModel extends AndroidViewModel {

    private static final long SEARCH_DEBOUNCE_MS = 350L;

    private final RadioRepository radioRepository;
    private final RadioFavoritesRepository favoritesRepository;
    private final MediaMetadataRepository metadataRepository;
    private final Handler searchHandler = new Handler(Looper.getMainLooper());

    private final MutableLiveData<List<RadioStation>> visibleStations = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final MutableLiveData<Set<String>> favoriteIds = new MutableLiveData<>(Collections.emptySet());

    private List<RadioStation> popularStations = new ArrayList<>();
    private List<RadioStation> searchStations = new ArrayList<>();
    private final Map<String, MediaOverrideEntity> overrides = new HashMap<>();
    private final Map<String, String> searchKeyCache = new HashMap<>();
    private String query = "";
    private boolean favoritesOnly;
    private int searchGeneration;

    private final Runnable pendingSearch = () -> performSearch(query, ++searchGeneration);

    public RadioViewModel(@NonNull Application application) {
        super(application);
        radioRepository = new RadioRepository(application);
        favoritesRepository = new RadioFavoritesRepository(application);
        metadataRepository = new MediaMetadataRepository(application);
        favoritesRepository.load(ids -> {
            favoriteIds.setValue(ids);
            applyFilters();
        });
        refreshOverrides();
    }

    public LiveData<List<RadioStation>> getVisibleStations() { return visibleStations; }
    public LiveData<Boolean> getLoading() { return loading; }
    public LiveData<String> getError() { return error; }
    public LiveData<Set<String>> getFavoriteIds() { return favoriteIds; }

    public void loadStations() {
        if (!popularStations.isEmpty()) {
            applyFilters();
            return;
        }
        loading.setValue(true);
        error.setValue(null);
        radioRepository.loadArgentinaStations(new RadioRepository.StationsCallback() {
            @Override
            public void onLoaded(List<RadioStation> stations) {
                popularStations = stations;
                applyOverrides(popularStations);
                rebuildSearchCache(popularStations);
                loading.setValue(false);
                applyFilters();
            }

            @Override
            public void onError(String message) {
                loading.setValue(false);
                error.setValue(message);
                visibleStations.setValue(Collections.emptyList());
            }
        });
    }

    public void retry() {
        popularStations = new ArrayList<>();
        searchStations = new ArrayList<>();
        searchKeyCache.clear();
        loadStations();
    }

    public void setQuery(String value) {
        query = value == null ? "" : value.trim();
        searchHandler.removeCallbacks(pendingSearch);
        radioRepository.cancelSearch();
        if (query.length() < 2) {
            searchStations = new ArrayList<>();
            loading.setValue(false);
            applyFilters();
            return;
        }
        loading.setValue(true);
        searchHandler.postDelayed(pendingSearch, SEARCH_DEBOUNCE_MS);
    }

    private void performSearch(String requestedQuery, int generation) {
        if (requestedQuery == null || requestedQuery.trim().length() < 2) return;
        radioRepository.searchArgentinaStations(requestedQuery, 80, new RadioRepository.StationsCallback() {
            @Override
            public void onLoaded(List<RadioStation> stations) {
                if (generation != searchGeneration || !requestedQuery.equals(query)) return;
                searchStations = stations;
                applyOverrides(searchStations);
                rebuildSearchCache(searchStations);
                loading.setValue(false);
                error.setValue(null);
                applyFilters();
            }

            @Override
            public void onError(String message) {
                if (generation != searchGeneration || !requestedQuery.equals(query)) return;
                loading.setValue(false);
                // Si falla la red, la búsqueda local sigue funcionando sobre las populares.
                searchStations = new ArrayList<>();
                applyFilters();
            }
        });
    }

    public void setFavoritesOnly(boolean enabled) {
        favoritesOnly = enabled;
        applyFilters();
    }

    public void toggleFavorite(RadioStation station) {
        if (station == null || station.getStationUuid().isEmpty()) return;
        Set<String> current = new HashSet<>();
        if (favoriteIds.getValue() != null) current.addAll(favoriteIds.getValue());
        boolean newState = !current.contains(station.getStationUuid());
        favoritesRepository.setFavorite(station.getStationUuid(), newState, ids -> {
            favoriteIds.setValue(ids);
            applyFilters();
        });
    }

    public void refreshOverrides() {
        metadataRepository.getByPrefix("radio:", items -> {
            overrides.clear();
            overrides.putAll(items);
            applyOverrides(popularStations);
            applyOverrides(searchStations);
            rebuildSearchCache(popularStations);
            rebuildSearchCache(searchStations);
            applyFilters();
        });
    }

    public void recordClick(RadioStation station) {
        radioRepository.countClick(station);
    }

    private void applyOverrides(List<RadioStation> stations) {
        for (RadioStation station : stations) {
            station.applyOverride(overrides.get(station.getMediaKey()));
        }
    }

    private void rebuildSearchCache(List<RadioStation> stations) {
        for (RadioStation station : stations) {
            searchKeyCache.put(station.getMediaKey(), compact(station.searchableText()));
        }
    }

    private void applyFilters() {
        Set<String> favorites = favoriteIds.getValue() == null ? Collections.emptySet() : favoriteIds.getValue();
        String compactQuery = compact(query);
        List<RadioStation> source = query.length() >= 2 && !searchStations.isEmpty()
                ? searchStations : popularStations;

        List<RadioStation> result = new ArrayList<>();
        for (RadioStation station : source) {
            if (favoritesOnly && !favorites.contains(station.getStationUuid())) continue;
            if (!compactQuery.isEmpty()) {
                String key = searchKeyCache.get(station.getMediaKey());
                if (key == null) {
                    key = compact(station.searchableText());
                    searchKeyCache.put(station.getMediaKey(), key);
                }
                if (!key.contains(compactQuery)) continue;
            }
            result.add(station);
        }
        visibleStations.setValue(result);
    }

    private String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .trim();
    }

    private String compact(String value) {
        return normalize(value).replaceAll("[^a-z0-9]", "");
    }

    @Override
    protected void onCleared() {
        searchHandler.removeCallbacksAndMessages(null);
        radioRepository.cancelSearch();
        favoritesRepository.close();
        metadataRepository.close();
    }
}
