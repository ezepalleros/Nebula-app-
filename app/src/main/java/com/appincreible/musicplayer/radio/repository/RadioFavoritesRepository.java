package com.appincreible.musicplayer.radio.repository;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.appincreible.musicplayer.database.FavoriteRadioDao;
import com.appincreible.musicplayer.database.FavoriteRadioEntity;
import com.appincreible.musicplayer.database.MusicDatabase;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RadioFavoritesRepository {

    public interface Callback {
        void onResult(Set<String> favoriteIds);
    }

    private final FavoriteRadioDao dao;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public RadioFavoritesRepository(Context context) {
        dao = MusicDatabase.getInstance(context).favoriteRadioDao();
    }

    public void load(Callback callback) {
        executor.execute(() -> {
            List<String> ids = dao.getAllIds();
            mainHandler.post(() -> callback.onResult(new HashSet<>(ids)));
        });
    }

    public void setFavorite(String stationUuid, boolean favorite, Callback callback) {
        if (stationUuid == null || stationUuid.trim().isEmpty()) return;
        executor.execute(() -> {
            if (favorite) {
                dao.insert(new FavoriteRadioEntity(stationUuid, System.currentTimeMillis()));
            } else {
                dao.delete(stationUuid);
            }
            List<String> ids = dao.getAllIds();
            mainHandler.post(() -> callback.onResult(new HashSet<>(ids)));
        });
    }

    public void close() {
        executor.shutdownNow();
    }
}
