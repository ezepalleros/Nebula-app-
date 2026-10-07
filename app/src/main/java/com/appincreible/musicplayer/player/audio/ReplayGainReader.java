package com.appincreible.musicplayer.player.audio;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;

import androidx.annotation.Nullable;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

final class ReplayGainReader {
    static final class Result {
        final float gainDb;
        final float peak;
        Result(float gainDb, float peak) { this.gainDb = gainDb; this.peak = peak; }
    }

    private ReplayGainReader() { }

    static @Nullable Result read(Context context, Uri uri) {
        ContentResolver resolver = context.getContentResolver();
        try (InputStream raw = resolver.openInputStream(uri)) {
            if (raw == null) return null;
            BufferedInputStream in = new BufferedInputStream(raw, 64 * 1024);
            in.mark(16);
            byte[] magic = new byte[4];
            int read = in.read(magic);
            in.reset();
            if (read >= 3 && magic[0] == 'I' && magic[1] == 'D' && magic[2] == '3') return readId3(in);
            if (read == 4 && magic[0] == 'f' && magic[1] == 'L' && magic[2] == 'a' && magic[3] == 'C') return readFlac(in);
        } catch (Exception ignored) { }
        return null;
    }

    private static @Nullable Result readId3(InputStream input) throws IOException {
        DataInputStream in = new DataInputStream(input);
        byte[] header = new byte[10];
        in.readFully(header);
        int version = header[3] & 0xFF;
        int size = synchsafe(header, 6);
        if (size <= 0 || size > 2_000_000) return null;
        byte[] tag = new byte[size];
        in.readFully(tag);
        int offset = 0;
        Float trackGain = null, albumGain = null, peak = null;
        while (offset + 10 <= tag.length) {
            String id = new String(tag, offset, 4, StandardCharsets.ISO_8859_1);
            if (id.trim().isEmpty() || id.charAt(0) == 0) break;
            int frameSize = version >= 4 ? synchsafe(tag, offset + 4) : int32be(tag, offset + 4);
            if (frameSize <= 0 || offset + 10 + frameSize > tag.length) break;
            if ("TXXX".equals(id)) {
                String[] pair = decodeTxxx(tag, offset + 10, frameSize);
                if (pair != null) {
                    String key = pair[0].trim().toUpperCase(Locale.US);
                    if ("REPLAYGAIN_TRACK_GAIN".equals(key)) trackGain = parseDb(pair[1]);
                    else if ("REPLAYGAIN_ALBUM_GAIN".equals(key)) albumGain = parseDb(pair[1]);
                    else if ("REPLAYGAIN_TRACK_PEAK".equals(key)) peak = parseFloat(pair[1]);
                }
            }
            offset += 10 + frameSize;
        }
        Float gain = trackGain != null ? trackGain : albumGain;
        return gain == null ? null : new Result(gain, peak == null ? 0f : Math.max(0.0001f, peak));
    }

    private static String[] decodeTxxx(byte[] data, int offset, int length) {
        if (length < 2) return null;
        int enc = data[offset] & 0xFF;
        Charset charset = enc == 1 || enc == 2 ? StandardCharsets.UTF_16 : enc == 3 ? StandardCharsets.UTF_8 : StandardCharsets.ISO_8859_1;
        String all = new String(data, offset + 1, length - 1, charset);
        int zero = all.indexOf('\0');
        if (zero < 0) return null;
        return new String[]{all.substring(0, zero), all.substring(zero + 1).replace("\0", "").trim()};
    }

    private static @Nullable Result readFlac(InputStream input) throws IOException {
        DataInputStream in = new DataInputStream(input);
        byte[] signature = new byte[4]; in.readFully(signature);
        boolean last = false;
        Float trackGain = null, albumGain = null, peak = null;
        while (!last) {
            int first = in.readUnsignedByte();
            last = (first & 0x80) != 0;
            int type = first & 0x7F;
            int len = (in.readUnsignedByte() << 16) | (in.readUnsignedByte() << 8) | in.readUnsignedByte();
            if (len < 0 || len > 4_000_000) return null;
            byte[] block = new byte[len]; in.readFully(block);
            if (type != 4) continue;
            ByteBuffer bb = ByteBuffer.wrap(block).order(ByteOrder.LITTLE_ENDIAN);
            if (bb.remaining() < 8) continue;
            int vendorLen = bb.getInt();
            if (vendorLen < 0 || vendorLen > bb.remaining()) continue;
            bb.position(bb.position() + vendorLen);
            if (bb.remaining() < 4) continue;
            int count = bb.getInt();
            for (int i = 0; i < count && bb.remaining() >= 4; i++) {
                int l = bb.getInt();
                if (l < 0 || l > bb.remaining()) break;
                byte[] comment = new byte[l]; bb.get(comment);
                String s = new String(comment, StandardCharsets.UTF_8);
                int eq = s.indexOf('=');
                if (eq <= 0) continue;
                String key = s.substring(0, eq).trim().toUpperCase(Locale.US);
                String value = s.substring(eq + 1).trim();
                if ("REPLAYGAIN_TRACK_GAIN".equals(key)) trackGain = parseDb(value);
                else if ("REPLAYGAIN_ALBUM_GAIN".equals(key)) albumGain = parseDb(value);
                else if ("REPLAYGAIN_TRACK_PEAK".equals(key)) peak = parseFloat(value);
            }
        }
        Float gain = trackGain != null ? trackGain : albumGain;
        return gain == null ? null : new Result(gain, peak == null ? 0f : Math.max(0.0001f, peak));
    }

    private static int synchsafe(byte[] b, int o) {
        return ((b[o] & 0x7F) << 21) | ((b[o+1] & 0x7F) << 14) | ((b[o+2] & 0x7F) << 7) | (b[o+3] & 0x7F);
    }
    private static int int32be(byte[] b, int o) {
        return ((b[o] & 0xFF) << 24) | ((b[o+1] & 0xFF) << 16) | ((b[o+2] & 0xFF) << 8) | (b[o+3] & 0xFF);
    }
    private static Float parseDb(String value) {
        if (value == null) return null;
        try { return Float.parseFloat(value.toLowerCase(Locale.US).replace("db", "").trim()); }
        catch (Exception e) { return null; }
    }
    private static Float parseFloat(String value) {
        try { return Float.parseFloat(value.trim()); } catch (Exception e) { return null; }
    }
}
