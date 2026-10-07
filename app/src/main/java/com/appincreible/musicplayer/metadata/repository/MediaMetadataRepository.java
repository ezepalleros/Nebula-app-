package com.appincreible.musicplayer.metadata.repository;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.appincreible.musicplayer.database.MediaOverrideEntity;
import com.appincreible.musicplayer.database.MusicDatabase;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MediaMetadataRepository {

    public interface ItemCallback {
        void onLoaded(MediaOverrideEntity entity);
    }

    public interface MapCallback {
        void onLoaded(Map<String, MediaOverrideEntity> items);
    }

    public interface SaveCallback {
        void onSaved(MediaOverrideEntity entity);
    }

    private final MusicDatabase database;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    public MediaMetadataRepository(Context context) {
        database = MusicDatabase.getInstance(context.getApplicationContext());
    }

    public void get(String mediaKey, ItemCallback callback) {
        executor.execute(() -> {
            MediaOverrideEntity entity = database.mediaOverrideDao().get(mediaKey);
            main.post(() -> callback.onLoaded(entity));
        });
    }

    public void getByPrefix(String prefix, MapCallback callback) {
        executor.execute(() -> {
            List<MediaOverrideEntity> entities = database.mediaOverrideDao().getByPrefix(prefix);
            Map<String, MediaOverrideEntity> map = new HashMap<>();
            if (entities != null) {
                for (MediaOverrideEntity entity : entities) map.put(entity.mediaKey, entity);
            }
            Map<String, MediaOverrideEntity> immutable = Collections.unmodifiableMap(map);
            main.post(() -> callback.onLoaded(immutable));
        });
    }

    public void save(MediaOverrideEntity entity, SaveCallback callback) {
        executor.execute(() -> {
            database.mediaOverrideDao().upsert(entity);
            main.post(() -> {
                if (callback != null) callback.onSaved(entity);
            });
        });
    }

    public void delete(String mediaKey, Runnable callback) {
        executor.execute(() -> {
            database.mediaOverrideDao().deleteByKey(mediaKey);
            main.post(() -> {
                if (callback != null) callback.run();
            });
        });
    }

    public void close() {
        executor.shutdownNow();
    }
}
