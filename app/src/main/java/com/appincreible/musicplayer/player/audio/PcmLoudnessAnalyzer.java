package com.appincreible.musicplayer.player.audio;

import android.content.Context;
import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

final class PcmLoudnessAnalyzer {
    static final class Result {
        final float rmsDb;
        final float peak;
        Result(float rmsDb, float peak) { this.rmsDb = rmsDb; this.peak = peak; }
    }

    private PcmLoudnessAnalyzer() { }

    static Result analyze(Context context, Uri uri) throws Exception {
        MediaExtractor extractor = new MediaExtractor();
        MediaCodec codec = null;
        try {
            extractor.setDataSource(context, uri, null);
            int track = -1;
            MediaFormat inputFormat = null;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat f = extractor.getTrackFormat(i);
                String mime = f.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) { track = i; inputFormat = f; break; }
            }
            if (track < 0 || inputFormat == null) throw new IllegalArgumentException("No audio track");
            extractor.selectTrack(track);
            String mime = inputFormat.getString(MediaFormat.KEY_MIME);
            codec = MediaCodec.createDecoderByType(mime);
            codec.configure(inputFormat, null, null, 0);
            codec.start();

            boolean inputDone = false, outputDone = false;
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            double sumSquares = 0d;
            long sampleCount = 0L;
            float peak = 0f;
            int pcmEncoding = AudioFormat.ENCODING_PCM_16BIT;

            while (!outputDone) {
                if (!inputDone) {
                    int inIndex = codec.dequeueInputBuffer(10_000);
                    if (inIndex >= 0) {
                        ByteBuffer input = codec.getInputBuffer(inIndex);
                        if (input != null) {
                            int size = extractor.readSampleData(input, 0);
                            if (size < 0) {
                                codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                                inputDone = true;
                            } else {
                                codec.queueInputBuffer(inIndex, 0, size, extractor.getSampleTime(), 0);
                                extractor.advance();
                            }
                        }
                    }
                }

                int outIndex = codec.dequeueOutputBuffer(info, 10_000);
                if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat out = codec.getOutputFormat();
                    if (out.containsKey(MediaFormat.KEY_PCM_ENCODING)) pcmEncoding = out.getInteger(MediaFormat.KEY_PCM_ENCODING);
                } else if (outIndex >= 0) {
                    ByteBuffer output = codec.getOutputBuffer(outIndex);
                    if (output != null && info.size > 0) {
                        int oldPos = output.position(), oldLimit = output.limit();
                        output.position(info.offset); output.limit(info.offset + info.size);
                        output.order(ByteOrder.nativeOrder());
                        if (pcmEncoding == AudioFormat.ENCODING_PCM_FLOAT) {
                            while (output.remaining() >= 4) {
                                float s = Math.max(-1f, Math.min(1f, output.getFloat()));
                                float a = Math.abs(s); if (a > peak) peak = a;
                                sumSquares += s * s; sampleCount++;
                            }
                        } else {
                            while (output.remaining() >= 2) {
                                float s = output.getShort() / 32768f;
                                float a = Math.abs(s); if (a > peak) peak = a;
                                sumSquares += s * s; sampleCount++;
                            }
                        }
                        output.position(oldPos); output.limit(oldLimit);
                    }
                    outputDone = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                    codec.releaseOutputBuffer(outIndex, false);
                }
            }
            if (sampleCount == 0L) throw new IllegalStateException("No PCM samples");
            double rms = Math.sqrt(sumSquares / sampleCount);
            float rmsDb = (float) (20d * Math.log10(Math.max(1e-9, rms)));
            return new Result(rmsDb, Math.max(0.0001f, peak));
        } finally {
            try { extractor.release(); } catch (Exception ignored) { }
            if (codec != null) {
                try { codec.stop(); } catch (Exception ignored) { }
                try { codec.release(); } catch (Exception ignored) { }
            }
        }
    }
}
