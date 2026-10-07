package com.appincreible.musicplayer.metadata.repository;

import android.content.Context;

import androidx.annotation.NonNull;

import com.appincreible.musicplayer.BuildConfig;
import com.appincreible.musicplayer.metadata.model.ArtworkOption;
import com.appincreible.musicplayer.metadata.network.MusicBrainzApi;
import com.appincreible.musicplayer.radio.model.RadioStation;
import com.appincreible.musicplayer.radio.repository.RadioRepository;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ArtworkSearchRepository {

    public interface CallbackResult {
        void onLoaded(List<ArtworkOption> options);
        void onError(String message);
    }

    private final MusicBrainzApi musicBrainzApi;
    private final RadioRepository radioRepository;

    public ArtworkSearchRepository(Context context) {
        String userAgent = "AppIncreibleMusicPlayer/" + BuildConfig.VERSION_NAME
                + " (Android metadata artwork search)";
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(12, TimeUnit.SECONDS)
                .addInterceptor(chain -> chain.proceed(chain.request().newBuilder()
                        .header("User-Agent", userAgent)
                        .header("Accept", "application/json")
                        .build()))
                .build();
        musicBrainzApi = new Retrofit.Builder()
                .baseUrl("https://musicbrainz.org/")
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(MusicBrainzApi.class);
        radioRepository = new RadioRepository(context);
    }

    public void searchSong(String title, String artist, CallbackResult callback) {
        String cleanTitle = escape(title);
        String cleanArtist = escape(artist);
        String query = cleanTitle;
        if (!cleanArtist.isEmpty()) query += " AND artist:\"" + cleanArtist + "\"";
        musicBrainzApi.searchReleaseGroups(query, "json", 10)
                .enqueue(new Callback<MusicBrainzApi.ReleaseGroupSearchResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<MusicBrainzApi.ReleaseGroupSearchResponse> call,
                                           @NonNull Response<MusicBrainzApi.ReleaseGroupSearchResponse> response) {
                        if (!response.isSuccessful() || response.body() == null
                                || response.body().releaseGroups == null) {
                            callback.onError("No encontré portadas para esa búsqueda.");
                            return;
                        }
                        List<ArtworkOption> options = new ArrayList<>();
                        for (MusicBrainzApi.ReleaseGroup group : response.body().releaseGroups) {
                            if (group == null || group.id == null || group.id.isEmpty()) continue;
                            String label = group.title == null ? "Resultado" : group.title;
                            if (group.artistCredit != null && !group.artistCredit.isEmpty()
                                    && group.artistCredit.get(0) != null
                                    && group.artistCredit.get(0).name != null) {
                                label += " · " + group.artistCredit.get(0).name;
                            }
                            String image = "https://coverartarchive.org/release-group/"
                                    + group.id + "/front-500";
                            options.add(new ArtworkOption(label, image));
                        }
                        if (options.isEmpty()) callback.onError("No encontré portadas para esa búsqueda.");
                        else callback.onLoaded(options);
                    }

                    @Override
                    public void onFailure(@NonNull Call<MusicBrainzApi.ReleaseGroupSearchResponse> call,
                                          @NonNull Throwable throwable) {
                        callback.onError("No pude buscar portadas ahora.");
                    }
                });
    }

    public void searchRadio(String stationName, CallbackResult callback) {
        radioRepository.searchArgentinaStations(stationName, 16, new RadioRepository.StationsCallback() {
            @Override
            public void onLoaded(List<RadioStation> stations) {
                List<ArtworkOption> options = new ArrayList<>();
                Set<String> seen = new HashSet<>();
                for (RadioStation station : stations) {
                    String favicon = station.getOriginalFavicon();
                    if (favicon.isEmpty() || !seen.add(favicon)) continue;
                    options.add(new ArtworkOption(station.getName(), favicon));
                }
                if (options.isEmpty()) callback.onError("No encontré logos online para esa radio.");
                else callback.onLoaded(options);
            }

            @Override public void onError(String message) { callback.onError(message); }
        });
    }

    private String escape(String value) {
        if (value == null) return "";
        return value.replace("\\", " ").replace("\"", " ").trim();
    }
}
