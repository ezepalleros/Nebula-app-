package com.appincreible.musicplayer.player.audio;

/**
 * Allocation-free bridge between the audio processors and the UI equalizer background.
 * Audio threads publish up to two physical player spectra; the UI reads one mixed snapshot.
 */
public final class EqualizerSpectrum {
    public static final int BAND_COUNT = 48;
    public static final int SOURCE_A = 0;
    public static final int SOURCE_B = 1;

    private static final long SOURCE_STALE_NS = 220_000_000L;
    private static final Object LOCK = new Object();
    private static final float[][] bands = new float[2][BAND_COUNT];
    private static final float[] weights = new float[2];
    private static final long[] updatedAtNs = new long[2];
    private static volatile boolean analysisEnabled;

    private EqualizerSpectrum() { }

    public static void setAnalysisEnabled(boolean enabled) {
        analysisEnabled = enabled;
        if (!enabled) clear();
    }

    public static boolean isAnalysisEnabled() {
        return analysisEnabled;
    }

    static void publish(int source, float[] values, float weight, long nowNs) {
        if (!analysisEnabled || source < 0 || source >= 2 || values == null) return;
        synchronized (LOCK) {
            System.arraycopy(values, 0, bands[source], 0, Math.min(BAND_COUNT, values.length));
            weights[source] = clamp(weight, 0f, 1.5f);
            updatedAtNs[source] = nowNs;
        }
    }

    /** Fills {@code out} with a gain-weighted mix of both physical players. */
    public static boolean snapshot(float[] out) {
        if (out == null || out.length < BAND_COUNT || !analysisEnabled) return false;
        long now = System.nanoTime();
        synchronized (LOCK) {
            float wA = now - updatedAtNs[SOURCE_A] <= SOURCE_STALE_NS ? weights[SOURCE_A] : 0f;
            float wB = now - updatedAtNs[SOURCE_B] <= SOURCE_STALE_NS ? weights[SOURCE_B] : 0f;
            float total = wA + wB;
            if (total <= 0.0001f) {
                for (int i = 0; i < BAND_COUNT; i++) out[i] = 0f;
                return false;
            }
            float inv = 1f / total;
            for (int i = 0; i < BAND_COUNT; i++) {
                out[i] = clamp((bands[SOURCE_A][i] * wA + bands[SOURCE_B][i] * wB) * inv, 0f, 1f);
            }
            return true;
        }
    }

    public static void clear() {
        synchronized (LOCK) {
            for (int source = 0; source < 2; source++) {
                for (int i = 0; i < BAND_COUNT; i++) bands[source][i] = 0f;
                weights[source] = 0f;
                updatedAtNs[source] = 0L;
            }
        }
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
