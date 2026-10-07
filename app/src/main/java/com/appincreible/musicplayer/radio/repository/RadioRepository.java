package com.appincreible.musicplayer.radio.repository;

import android.content.Context;

import androidx.annotation.NonNull;

import com.appincreible.musicplayer.BuildConfig;
import com.appincreible.musicplayer.radio.model.RadioStation;
import com.appincreible.musicplayer.radio.network.RadioBrowserApi;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class RadioRepository {

    private static final int CATALOG_LIMIT = 100000;

    public interface StationsCallback {
        void onLoaded(List<RadioStation> stations);
        void onError(String message);
    }

    private static final String[] SERVERS = {
            "https://de1.api.radio-browser.info/",
            "https://nl1.api.radio-browser.info/"
    };

    private final OkHttpClient client;
    private Call<List<RadioStation>> activeSearch;

    public RadioRepository(Context context) {
        String userAgent = "AppIncreibleMusicPlayer/" + BuildConfig.VERSION_NAME
                + " (Android music player; contact: local-app)";
        client = new OkHttpClient.Builder()
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(12, TimeUnit.SECONDS)
                .addInterceptor(chain -> chain.proceed(chain.request().newBuilder()
                        .header("User-Agent", userAgent)
                        .header("Accept", "application/json")
                        .build()))
                .build();
    }

    public void loadArgentinaStations(StationsCallback callback) {
        requestPopularFromServer(0, callback);
    }

    public void searchArgentinaStations(String query, int limit, StationsCallback callback) {
        cancelSearch();
        searchFromServer(0, query == null ? "" : query.trim(), Math.max(10, limit), callback);
    }

    public void cancelSearch() {
        if (activeSearch != null) {
            activeSearch.cancel();
            activeSearch = null;
        }
    }

    private void requestPopularFromServer(int serverIndex, StationsCallback callback) {
        if (serverIndex >= SERVERS.length) {
            callback.onError("No pude conectar con el catálogo de radios. Revisá tu conexión e intentá de nuevo.");
            return;
        }
        RadioBrowserApi api = createApi(SERVERS[serverIndex]);
        api.getArgentinaStations(true, "votes", true, CATALOG_LIMIT)
                .enqueue(callbackFor(serverIndex, callback, false, "", CATALOG_LIMIT));
    }

    private void searchFromServer(int serverIndex, String query, int limit, StationsCallback callback) {
        if (serverIndex >= SERVERS.length) {
            callback.onError("No pude completar la búsqueda online.");
            return;
        }
        RadioBrowserApi api = createApi(SERVERS[serverIndex]);
        int effectiveLimit = Math.max(CATALOG_LIMIT, limit);
        activeSearch = api.searchArgentinaStations("AR", query, true, "votes", true, effectiveLimit);
        activeSearch.enqueue(callbackFor(serverIndex, callback, true, query, effectiveLimit));
    }

    private Callback<List<RadioStation>> callbackFor(int serverIndex, StationsCallback callback,
                                                      boolean search, String query, int limit) {
        return new Callback<List<RadioStation>>() {
            @Override
            public void onResponse(@NonNull Call<List<RadioStation>> call,
                                   @NonNull Response<List<RadioStation>> response) {
                if (call.isCanceled()) return;
                if (!response.isSuccessful() || response.body() == null) {
                    retry(serverIndex, callback, search, query, limit);
                    return;
                }
                List<RadioStation> sanitized = sanitize(response.body());
                if (sanitized.isEmpty() && !search) {
                    retry(serverIndex, callback, false, query, limit);
                } else {
                    callback.onLoaded(sanitized);
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<RadioStation>> call, @NonNull Throwable throwable) {
                if (call.isCanceled()) return;
                retry(serverIndex, callback, search, query, limit);
            }
        };
    }

    private void retry(int serverIndex, StationsCallback callback, boolean search, String query, int limit) {
        if (search) searchFromServer(serverIndex + 1, query, limit, callback);
        else requestPopularFromServer(serverIndex + 1, callback);
    }

    private List<RadioStation> sanitize(List<RadioStation> source) {
        Map<String, RadioStation> unique = new LinkedHashMap<>();
        for (RadioStation station : source) {
            if (station == null || station.getStreamUrl().isEmpty()) continue;
            String streamUrl = station.getStreamUrl().toLowerCase();
            if (!streamUrl.startsWith("http://") && !streamUrl.startsWith("https://")) continue;
            String key = station.getStationUuid().isEmpty() ? station.getStreamUrl() : station.getStationUuid();
            unique.put(key, station);
        }
        return new ArrayList<>(unique.values());
    }

    public void countClick(RadioStation station) {
        if (station == null || station.getStationUuid().isEmpty()) return;
        createApi(SERVERS[0]).countClick(station.getStationUuid()).enqueue(new Callback<ResponseBody>() {
            @Override public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) { }
            @Override public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable throwable) { }
        });
    }

    private RadioBrowserApi createApi(String baseUrl) {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        return retrofit.create(RadioBrowserApi.class);
    }
}
