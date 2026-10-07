package com.appincreible.musicplayer.player.audio;

import android.content.Context;

import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.appincreible.musicplayer.power.PowerSaverManager;

/** Schedules the optional library analysis as battery-aware persistent work. */
public final class NormalizationAnalysisManager {
    public static final String UNIQUE_WORK = "loudness-library-analysis";
    private static volatile NormalizationAnalysisManager instance;
    private final Context appContext;

    private NormalizationAnalysisManager(Context context) {
        appContext = context.getApplicationContext();
    }

    public static NormalizationAnalysisManager get(Context context) {
        if (instance == null) {
            synchronized (NormalizationAnalysisManager.class) {
                if (instance == null) instance = new NormalizationAnalysisManager(context);
            }
        }
        return instance;
    }

    public void analyzeLibrary() {
        if (PowerSaverManager.isActive(appContext)) {
            AudioPreferences.prefs(appContext).edit()
                    .putBoolean(AudioPreferences.KEY_ANALYSIS_RUNNING, false)
                    .putString(AudioPreferences.KEY_ANALYSIS_LABEL, "Modo súper ahorro: análisis desactivado")
                    .apply();
            return;
        }
        AudioPreferences.prefs(appContext).edit()
                .putBoolean(AudioPreferences.KEY_ANALYSIS_RUNNING, true)
                .putInt(AudioPreferences.KEY_ANALYSIS_PROGRESS, 0)
                .putString(AudioPreferences.KEY_ANALYSIS_LABEL, "Preparando biblioteca…")
                .apply();

        Constraints constraints = new Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build();
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(NormalizationAnalysisWorker.class)
                .setConstraints(constraints)
                .build();
        WorkManager.getInstance(appContext).enqueueUniqueWork(
                UNIQUE_WORK, ExistingWorkPolicy.KEEP, request);
    }
    public void cancelForPowerSaver() {
        WorkManager.getInstance(appContext).cancelUniqueWork(UNIQUE_WORK);
        AudioPreferences.prefs(appContext).edit()
                .putBoolean(AudioPreferences.KEY_ANALYSIS_RUNNING, false)
                .putString(AudioPreferences.KEY_ANALYSIS_LABEL, "Modo súper ahorro: análisis desactivado")
                .apply();
    }

}
