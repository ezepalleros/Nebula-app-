package com.appincreible.musicplayer.player.audio;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.appincreible.musicplayer.database.LoudnessAnalysisDao;
import com.appincreible.musicplayer.database.MusicDatabase;
import com.appincreible.musicplayer.power.PowerSaverManager;

import java.util.ArrayList;
import java.util.List;

/** Battery-aware persistent worker for the optional full-library loudness analysis. */
public final class NormalizationAnalysisWorker extends Worker {

    private static final class Entry {
        final String mediaId;
        final Uri uri;
        Entry(String mediaId, Uri uri) { this.mediaId = mediaId; this.uri = uri; }
    }

    public NormalizationAnalysisWorker(@NonNull Context appContext,
                                       @NonNull WorkerParameters workerParams) {
        super(appContext, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        if (PowerSaverManager.isActive(context)) {
            AudioPreferences.prefs(context).edit()
                    .putBoolean(AudioPreferences.KEY_ANALYSIS_RUNNING, false)
                    .putString(AudioPreferences.KEY_ANALYSIS_LABEL, "Modo súper ahorro: análisis desactivado")
                    .apply();
            return Result.success();
        }
        NormalizationRepository repository = new NormalizationRepository(context);
        AudioPreferences.prefs(context).edit()
                .putBoolean(AudioPreferences.KEY_ANALYSIS_RUNNING, true)
                .putString(AudioPreferences.KEY_ANALYSIS_LABEL, "Preparando biblioteca…")
                .apply();
        try {
            List<Entry> songs = querySongs(context);
            LoudnessAnalysisDao dao = MusicDatabase.getInstance(context).loudnessAnalysisDao();
            int total = songs.size();
            int done = 0;
            for (Entry song : songs) {
                if (PowerSaverManager.isActive(context)) {
                    AudioPreferences.prefs(context).edit()
                            .putString(AudioPreferences.KEY_ANALYSIS_LABEL, "Modo súper ahorro: análisis desactivado")
                            .apply();
                    return Result.success();
                }
                if (isStopped()) return Result.retry();
                if (dao.get(song.mediaId) == null) repository.analyzeAndStore(song.mediaId, song.uri);
                done++;
                int progress = total == 0 ? 100 : Math.round(done * 100f / total);
                AudioPreferences.prefs(context).edit()
                        .putInt(AudioPreferences.KEY_ANALYSIS_PROGRESS, progress)
                        .putString(AudioPreferences.KEY_ANALYSIS_LABEL, "Analizando " + done + " de " + total)
                        .apply();
            }
            AudioPreferences.prefs(context).edit()
                    .putInt(AudioPreferences.KEY_ANALYSIS_PROGRESS, 100)
                    .putString(AudioPreferences.KEY_ANALYSIS_LABEL, "Biblioteca analizada")
                    .apply();
            return Result.success();
        } catch (Exception ignored) {
            AudioPreferences.prefs(context).edit()
                    .putString(AudioPreferences.KEY_ANALYSIS_LABEL, "No se pudo completar el análisis")
                    .apply();
            return Result.failure();
        } finally {
            repository.close();
            AudioPreferences.prefs(context).edit()
                    .putBoolean(AudioPreferences.KEY_ANALYSIS_RUNNING, false)
                    .apply();
        }
    }

    private static List<Entry> querySongs(Context context) {
        List<Entry> result = new ArrayList<>();
        ContentResolver resolver = context.getContentResolver();
        Uri collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        String[] projection = {MediaStore.Audio.Media._ID};
        String selection = MediaStore.Audio.Media.IS_MUSIC + " != 0";
        try (Cursor cursor = resolver.query(collection, projection, selection, null, null)) {
            if (cursor == null) return result;
            int idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
            while (cursor.moveToNext()) {
                long id = cursor.getLong(idCol);
                result.add(new Entry("song:" + id, ContentUris.withAppendedId(collection, id)));
            }
        }
        return result;
    }
}
