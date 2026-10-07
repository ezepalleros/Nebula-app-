package com.appincreible.musicplayer.player.audio;

import androidx.media3.common.C;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.common.audio.BaseAudioProcessor;
import androidx.media3.common.util.UnstableApi;

import java.nio.ByteBuffer;

@UnstableApi
public final class GainAudioProcessor extends BaseAudioProcessor {
    private final EqualizerAudioAnalyzer equalizerAnalyzer;
    private float envelopeGain = 1f;
    private float targetEnvelopeGain = 1f;
    private long rampFramesRemaining;
    private float normalizationGain = 1f;
    private float targetNormalizationGain = 1f;
    private long normalizationRampFramesRemaining;
    private float masterGain = 1f;

    public GainAudioProcessor() {
        this(EqualizerSpectrum.SOURCE_A);
    }

    GainAudioProcessor(int spectrumSource) {
        equalizerAnalyzer = new EqualizerAudioAnalyzer(spectrumSource);
    }

    @Override
    protected AudioProcessor.AudioFormat onConfigure(AudioProcessor.AudioFormat inputAudioFormat)
            throws AudioProcessor.UnhandledAudioFormatException {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT
                && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
            throw new AudioProcessor.UnhandledAudioFormatException(inputAudioFormat);
        }
        equalizerAnalyzer.configure(inputAudioFormat);
        return inputAudioFormat;
    }

    public synchronized void setEnvelopeImmediately(float gain) {
        envelopeGain = sanitizeGain(gain);
        targetEnvelopeGain = envelopeGain;
        rampFramesRemaining = 0L;
    }

    public synchronized void rampEnvelopeTo(float target, long durationMs) {
        targetEnvelopeGain = sanitizeGain(target);
        if (durationMs <= 0 || inputAudioFormat.sampleRate <= 0) {
            envelopeGain = targetEnvelopeGain;
            rampFramesRemaining = 0L;
            return;
        }
        rampFramesRemaining = Math.max(1L, (inputAudioFormat.sampleRate * durationMs) / 1000L);
    }

    public synchronized void setNormalizationGain(float gain) {
        targetNormalizationGain = Math.max(0f, Math.min(4f, gain));
        if (inputAudioFormat.sampleRate <= 0) {
            normalizationGain = targetNormalizationGain;
            normalizationRampFramesRemaining = 0L;
        } else {
            // Avoid a gain discontinuity when analysis finishes or the target changes.
            normalizationRampFramesRemaining = Math.max(1L, inputAudioFormat.sampleRate / 4L);
        }
    }

    public synchronized void setMasterGain(float gain) {
        masterGain = Math.max(0f, Math.min(1f, gain));
    }

    public synchronized float getEnvelopeGain() { return envelopeGain; }
    public synchronized float getEffectiveGain() { return envelopeGain * normalizationGain * masterGain; }

    @Override
    public void queueInput(ByteBuffer inputBuffer) {
        if (!inputBuffer.hasRemaining()) return;
        equalizerAnalyzer.capture(inputBuffer, getEffectiveGain());
        ByteBuffer output = replaceOutputBuffer(inputBuffer.remaining());
        int channels = Math.max(1, inputAudioFormat.channelCount);

        synchronized (this) {
            if (inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT) {
                while (inputBuffer.remaining() >= 4 * channels) {
                    float frameGain = nextFrameGain();
                    float totalGain = frameGain * nextNormalizationGain() * masterGain;
                    for (int ch = 0; ch < channels; ch++) {
                        float sample = inputBuffer.getFloat();
                        float out = clampFloat(sample * totalGain);
                        output.putFloat(out);
                    }
                }
                while (inputBuffer.remaining() >= 4) output.putFloat(inputBuffer.getFloat());
            } else {
                while (inputBuffer.remaining() >= 2 * channels) {
                    float frameGain = nextFrameGain();
                    float totalGain = frameGain * nextNormalizationGain() * masterGain;
                    for (int ch = 0; ch < channels; ch++) {
                        short sample = inputBuffer.getShort();
                        int scaled = Math.round(sample * totalGain);
                        if (scaled > Short.MAX_VALUE) scaled = Short.MAX_VALUE;
                        else if (scaled < Short.MIN_VALUE) scaled = Short.MIN_VALUE;
                        output.putShort((short) scaled);
                    }
                }
                while (inputBuffer.remaining() >= 2) output.putShort(inputBuffer.getShort());
            }
        }
        output.flip();
    }

    private float nextFrameGain() {
        if (rampFramesRemaining <= 0L) return envelopeGain;
        float delta = (targetEnvelopeGain - envelopeGain) / rampFramesRemaining;
        envelopeGain += delta;
        rampFramesRemaining--;
        if (rampFramesRemaining == 0L) envelopeGain = targetEnvelopeGain;
        return envelopeGain;
    }


    private float nextNormalizationGain() {
        if (normalizationRampFramesRemaining <= 0L) return normalizationGain;
        float delta = (targetNormalizationGain - normalizationGain) / normalizationRampFramesRemaining;
        normalizationGain += delta;
        normalizationRampFramesRemaining--;
        if (normalizationRampFramesRemaining == 0L) normalizationGain = targetNormalizationGain;
        return normalizationGain;
    }

    private static float sanitizeGain(float gain) {
        if (Float.isNaN(gain) || Float.isInfinite(gain)) return 1f;
        return Math.max(0f, Math.min(1f, gain));
    }

    private static float clampFloat(float value) {
        return Math.max(-1f, Math.min(1f, value));
    }
}
