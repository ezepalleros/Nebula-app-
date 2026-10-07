package com.appincreible.musicplayer.ui.games;

import android.content.Context;
import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;

import com.appincreible.musicplayer.data.model.Song;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Reusable, best-effort offline PCM probe for avoiding digital silence at clip starts. */
final class GameSilenceProbe implements AutoCloseable {
    private static final long PROBE_US = 180_000L;
    private static final long DEQUEUE_TIMEOUT_US = 6_000L;
    private static final double SILENCE_RMS = 0.0065;

    private final MediaExtractor extractor;
    private final MediaCodec codec;
    private int pcmEncoding = AudioFormat.ENCODING_PCM_16BIT;
    private boolean closed;

    private GameSilenceProbe(MediaExtractor extractor, MediaCodec codec) {
        this.extractor = extractor;
        this.codec = codec;
    }

    static GameSilenceProbe open(Context context, Song song) {
        if (context == null || song == null || song.getContentUri() == null) return null;
        MediaExtractor extractor = new MediaExtractor();
        MediaCodec codec = null;
        try {
            extractor.setDataSource(context, song.getContentUri(), null);
            int track = findAudioTrack(extractor);
            if (track < 0) {
                extractor.release();
                return null;
            }
            extractor.selectTrack(track);
            MediaFormat format = extractor.getTrackFormat(track);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime == null) {
                extractor.release();
                return null;
            }
            codec = MediaCodec.createDecoderByType(mime);
            codec.configure(format, null, null, 0);
            codec.start();
            return new GameSilenceProbe(extractor, codec);
        } catch (Exception ignored) {
            try { extractor.release(); } catch (Exception ignoredRelease) { }
            if (codec != null) {
                try { codec.release(); } catch (Exception ignoredRelease) { }
            }
            return null;
        }
    }

    /** Returns false when probing is unavailable, so unsupported files are never rejected. */
    boolean isLikelySilent(long startMs) {
        if (closed) return false;
        try {
            long startUs = Math.max(0L, startMs) * 1000L;
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
            codec.flush();

            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean inputEnded = false;
            long inspectedUntilUs = startUs;
            double energy = 0.0;
            long samples = 0L;

            for (int loops = 0; loops < 80 && inspectedUntilUs - startUs < PROBE_US; loops++) {
                if (Thread.currentThread().isInterrupted()) return false;
                if (!inputEnded) {
                    int inputIndex = codec.dequeueInputBuffer(DEQUEUE_TIMEOUT_US);
                    if (inputIndex >= 0) {
                        ByteBuffer input = codec.getInputBuffer(inputIndex);
                        if (input != null) {
                            int size = extractor.readSampleData(input, 0);
                            long timeUs = extractor.getSampleTime();
                            if (size < 0 || timeUs < 0) {
                                codec.queueInputBuffer(inputIndex, 0, 0, 0,
                                        MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                                inputEnded = true;
                            } else {
                                codec.queueInputBuffer(inputIndex, 0, size, timeUs, 0);
                                extractor.advance();
                            }
                        }
                    }
                }

                int outputIndex = codec.dequeueOutputBuffer(info, DEQUEUE_TIMEOUT_US);
                if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat out = codec.getOutputFormat();
                    if (out.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                        pcmEncoding = out.getInteger(MediaFormat.KEY_PCM_ENCODING);
                    }
                    continue;
                }
                if (outputIndex < 0) continue;

                ByteBuffer output = codec.getOutputBuffer(outputIndex);
                if (output != null && info.size > 0 && info.presentationTimeUs >= startUs) {
                    output.position(info.offset);
                    output.limit(info.offset + info.size);
                    output.order(ByteOrder.LITTLE_ENDIAN);
                    if (pcmEncoding == AudioFormat.ENCODING_PCM_FLOAT) {
                        while (output.remaining() >= 4) {
                            float value = output.getFloat();
                            energy += value * value;
                            samples++;
                        }
                    } else {
                        while (output.remaining() >= 2) {
                            double value = output.getShort() / 32768.0;
                            energy += value * value;
                            samples++;
                        }
                    }
                    inspectedUntilUs = Math.max(inspectedUntilUs, info.presentationTimeUs);
                }
                boolean eos = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                codec.releaseOutputBuffer(outputIndex, false);
                if (eos) break;
            }

            if (samples == 0L) return false;
            return Math.sqrt(energy / samples) < SILENCE_RMS;
        } catch (Exception ignored) {
            return false;
        }
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        try { extractor.release(); } catch (Exception ignored) { }
        try { codec.stop(); } catch (Exception ignored) { }
        try { codec.release(); } catch (Exception ignored) { }
    }

    private static int findAudioTrack(MediaExtractor extractor) {
        for (int i = 0; i < extractor.getTrackCount(); i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime != null && mime.startsWith("audio/")) return i;
        }
        return -1;
    }
}
