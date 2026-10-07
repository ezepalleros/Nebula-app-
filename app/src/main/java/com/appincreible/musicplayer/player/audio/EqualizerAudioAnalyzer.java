package com.appincreible.musicplayer.player.audio;

import androidx.media3.common.C;
import androidx.media3.common.audio.AudioProcessor;

import java.nio.ByteBuffer;

/**
 * Read-only FFT helper attached to the existing GainAudioProcessor. It never moves the input
 * ByteBuffer position and is completely idle when the global equalizer background is inactive.
 */
final class EqualizerAudioAnalyzer {
    private static final int FFT_SIZE = 512;
    private static final long ANALYSIS_INTERVAL_NS = 50_000_000L; // match the ~20 fps visualizers; avoid wasted FFT work
    private static final float[] COS = new float[FFT_SIZE / 2];
    private static final float[] SIN = new float[FFT_SIZE / 2];
    private static final float[] WINDOW = new float[FFT_SIZE];

    static {
        for (int i = 0; i < FFT_SIZE / 2; i++) {
            double angle = -2.0 * Math.PI * i / FFT_SIZE;
            COS[i] = (float) Math.cos(angle);
            SIN[i] = (float) Math.sin(angle);
        }
        for (int i = 0; i < FFT_SIZE; i++) {
            WINDOW[i] = (float) (0.5 - 0.5 * Math.cos((2.0 * Math.PI * i) / (FFT_SIZE - 1)));
        }
    }

    private final int source;
    private final float[] ring = new float[FFT_SIZE];
    private final float[] real = new float[FFT_SIZE];
    private final float[] imaginary = new float[FFT_SIZE];
    private final float[] bandLevels = new float[EqualizerSpectrum.BAND_COUNT];
    private final int[] binToBand = new int[FFT_SIZE / 2];
    private int sampleRate;
    private int channels;
    private int encoding;
    private int ringWrite;
    private int ringCount;
    private long lastAnalysisNs;

    EqualizerAudioAnalyzer(int source) {
        this.source = source;
    }

    void configure(AudioProcessor.AudioFormat format) {
        sampleRate = format.sampleRate;
        channels = Math.max(1, format.channelCount);
        encoding = format.encoding;
        ringWrite = 0;
        ringCount = 0;
        lastAnalysisNs = 0L;
        buildBandMap(sampleRate);
    }

    void capture(ByteBuffer input, float effectiveGain) {
        if (!EqualizerSpectrum.isAnalysisEnabled() || input == null || !input.hasRemaining()) return;
        if (encoding != C.ENCODING_PCM_16BIT && encoding != C.ENCODING_PCM_FLOAT) return;

        int bytesPerSample = encoding == C.ENCODING_PCM_FLOAT ? 4 : 2;
        int frameBytes = channels * bytesPerSample;
        int start = input.position();
        int end = input.limit() - frameBytes + 1;
        for (int frame = start; frame < end; frame += frameBytes) {
            float mono = 0f;
            for (int ch = 0; ch < channels; ch++) {
                int offset = frame + ch * bytesPerSample;
                mono += encoding == C.ENCODING_PCM_FLOAT
                        ? clamp(input.getFloat(offset), -1f, 1f)
                        : input.getShort(offset) / 32768f;
            }
            ring[ringWrite] = mono / channels;
            ringWrite = (ringWrite + 1) & (FFT_SIZE - 1);
            if (ringCount < FFT_SIZE) ringCount++;
        }

        long now = System.nanoTime();
        if (ringCount == FFT_SIZE && now - lastAnalysisNs >= ANALYSIS_INTERVAL_NS) {
            lastAnalysisNs = now;
            analyze(now, effectiveGain);
        }
    }

    private void analyze(long nowNs, float effectiveGain) {
        int oldest = ringWrite;
        for (int i = 0; i < FFT_SIZE; i++) {
            real[i] = ring[(oldest + i) & (FFT_SIZE - 1)] * WINDOW[i];
            imaginary[i] = 0f;
        }
        fftInPlace(real, imaginary);
        for (int i = 0; i < bandLevels.length; i++) bandLevels[i] = 0f;

        for (int bin = 1; bin < FFT_SIZE / 2; bin++) {
            int band = binToBand[bin];
            if (band < 0) continue;
            float re = real[bin];
            float im = imaginary[bin];
            float magnitude = (float) Math.sqrt(re * re + im * im) / (FFT_SIZE * 0.5f);
            float compressed = (float) (Math.log1p(magnitude * 40f) / Math.log(41f));
            if (compressed > bandLevels[band]) bandLevels[band] = Math.min(1f, compressed);
        }

        float previous = 0f;
        for (int i = 0; i < bandLevels.length; i++) {
            if (bandLevels[i] <= 0f) bandLevels[i] = previous * 0.82f;
            previous = Math.max(previous * 0.72f, bandLevels[i]);
        }
        EqualizerSpectrum.publish(source, bandLevels, effectiveGain, nowNs);
    }

    private void buildBandMap(int rate) {
        double minHz = 55.0;
        double maxHz = Math.min(18_000.0, Math.max(1_000.0, rate * 0.48));
        double logSpan = Math.log(maxHz / minHz);
        for (int bin = 0; bin < binToBand.length; bin++) {
            double hz = bin * (double) rate / FFT_SIZE;
            if (hz < minHz || hz > maxHz) {
                binToBand[bin] = -1;
                continue;
            }
            int band = (int) (Math.log(hz / minHz) / logSpan * EqualizerSpectrum.BAND_COUNT);
            binToBand[bin] = Math.max(0, Math.min(EqualizerSpectrum.BAND_COUNT - 1, band));
        }
    }

    private static void fftInPlace(float[] re, float[] im) {
        int n = re.length;
        int j = 0;
        for (int i = 1; i < n; i++) {
            int bit = n >> 1;
            while ((j & bit) != 0) {
                j ^= bit;
                bit >>= 1;
            }
            j ^= bit;
            if (i < j) {
                float tr = re[i]; re[i] = re[j]; re[j] = tr;
                float ti = im[i]; im[i] = im[j]; im[j] = ti;
            }
        }
        for (int len = 2; len <= n; len <<= 1) {
            int half = len >> 1;
            int step = n / len;
            for (int base = 0; base < n; base += len) {
                for (int k = 0; k < half; k++) {
                    int twiddle = k * step;
                    float wr = COS[twiddle];
                    float wi = SIN[twiddle];
                    int even = base + k;
                    int odd = even + half;
                    float or = re[odd] * wr - im[odd] * wi;
                    float oi = re[odd] * wi + im[odd] * wr;
                    re[odd] = re[even] - or;
                    im[odd] = im[even] - oi;
                    re[even] += or;
                    im[even] += oi;
                }
            }
        }
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
