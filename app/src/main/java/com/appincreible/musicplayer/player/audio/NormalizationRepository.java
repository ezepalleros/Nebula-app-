package com.appincreible.musicplayer.player.audio;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.media3.common.MediaItem;

import com.appincreible.musicplayer.database.LoudnessAnalysisDao;
import com.appincreible.musicplayer.database.LoudnessAnalysisEntity;
import com.appincreible.musicplayer.database.MusicDatabase;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

final class NormalizationRepository {
    private final Context appContext;
    private final LoudnessAnalysisDao dao;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    NormalizationRepository(Context context) {
        appContext = context.getApplicationContext();
        dao = MusicDatabase.getInstance(appContext).loudnessAnalysisDao();
    }

    void getGainFactorAsync(MediaItem item, String target, Consumer<Float> callback) {
        if (item == null || item.localConfiguration == null || item.localConfiguration.uri == null) {
            callback.accept(1f);
            return;
        }
        final String mediaId = item.mediaId;
        final Uri uri = item.localConfiguration.uri;
        executor.execute(() -> {
            LoudnessAnalysisEntity entity = dao.get(mediaId);
            if (entity == null) entity = analyzeAndStore(mediaId, uri);
            float factor = entity == null ? 1f : gainFactor(entity, target);
            main.post(() -> callback.accept(factor));
        });
    }

    LoudnessAnalysisEntity analyzeAndStore(String mediaId, Uri uri) {
        try {
            ReplayGainReader.Result rg = ReplayGainReader.read(appContext, uri);
            LoudnessAnalysisEntity entity;
            if (rg != null) {
                entity = new LoudnessAnalysisEntity(mediaId, clampDb(rg.gainDb), 0f,
                        rg.peak, "replaygain", System.currentTimeMillis());
            } else {
                PcmLoudnessAnalyzer.Result analyzed = PcmLoudnessAnalyzer.analyze(appContext, uri);
                float gainDb = -14f - analyzed.rmsDb;
                gainDb = limitForPeak(clampDb(gainDb), analyzed.peak);
                entity = new LoudnessAnalysisEntity(mediaId, gainDb, analyzed.rmsDb,
                        analyzed.peak, "analysis", System.currentTimeMillis());
            }
            dao.upsert(entity);
            return entity;
        } catch (Exception ignored) {
            return null;
        }
    }

    LoudnessAnalysisEntity get(String mediaId) { return dao.get(mediaId); }

    static float gainFactor(LoudnessAnalysisEntity entity, String target) {
        float offset = AudioPreferences.TARGET_SOFT.equals(target) ? -4f
                : AudioPreferences.TARGET_LOUD.equals(target) ? 3f : 0f;
        float db = clampDb(entity.gainDb + offset);
        db = limitForPeak(db, entity.peak);
        return (float) Math.pow(10d, db / 20d);
    }

    private static float limitForPeak(float gainDb, float peak) {
        // Without a measured/ReplayGain peak we avoid positive boost: a safe limiter cannot
        // prove that headroom exists. Negative attenuation is still safe.
        if (peak <= 0f) return Math.min(gainDb, 0f);
        float maxDb = (float) (20d * Math.log10(0.944d / Math.max(0.0001d, peak)));
        return Math.min(gainDb, maxDb);
    }

    private static float clampDb(float db) { return Math.max(-18f, Math.min(9f, db)); }

    void close() { executor.shutdownNow(); }
}
