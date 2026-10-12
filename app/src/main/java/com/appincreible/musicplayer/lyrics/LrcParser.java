package com.appincreible.musicplayer.lyrics;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure parser: accepts external .lrc text as well as LRC stored inside metadata tags. */
public final class LrcParser {
    private static final Pattern TIMESTAMP = Pattern.compile("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?\\]");
    private static final Pattern WORD_TIME = Pattern.compile("<\\d{1,3}:\\d{2}(?:[.:]\\d{1,3})?>");
    private static final Pattern META = Pattern.compile("^\\[(?:ar|ti|al|by|re|ve|length):.*]$", Pattern.CASE_INSENSITIVE);
    private static final Pattern OFFSET = Pattern.compile("^\\[offset:([+-]?\\d+)\\]$", Pattern.CASE_INSENSITIVE);
    private static final int MAX_BYTES = 2 * 1024 * 1024;

    private LrcParser() { }

    public static String decode(byte[] bytes) {
        int offset = bytes.length >= 3 && (bytes[0] & 255) == 239
                && (bytes[1] & 255) == 187 && (bytes[2] & 255) == 191 ? 3 : 0;
        ByteBuffer data = ByteBuffer.wrap(bytes, offset, bytes.length - offset);
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(data).toString();
        } catch (CharacterCodingException badUtf8) {
            return Charset.forName("windows-1252").decode(ByteBuffer.wrap(bytes, offset, bytes.length - offset)).toString();
        }
    }

    public static String read(InputStream stream) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = stream.read(buffer)) != -1) {
            if (result.size() + count > MAX_BYTES) throw new IOException("Archivo LRC demasiado grande");
            result.write(buffer, 0, count);
        }
        return decode(result.toByteArray());
    }

    public static LrcLyrics parse(String source) {
        List<LrcLyrics.Line> timed = new ArrayList<>();
        List<LrcLyrics.Line> plain = new ArrayList<>();
        if (source == null || source.isEmpty()) return new LrcLyrics(plain, false);
        long offset = 0;
        String[] rawLines = source.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        // Read offset first, including when it is declared at the end of the file.
        for (String raw : rawLines) {
            Matcher match = OFFSET.matcher(raw.trim());
            if (match.matches()) {
                try { offset = Long.parseLong(match.group(1)); } catch (NumberFormatException ignored) { }
            }
        }
        for (String raw : rawLines) {
            String line = raw.trim();
            if (line.isEmpty() || META.matcher(line).matches() || OFFSET.matcher(line).matches()) continue;
            Matcher timestamp = TIMESTAMP.matcher(line);
            List<Long> positions = new ArrayList<>();
            while (timestamp.find()) {
                try {
                    long min = Long.parseLong(timestamp.group(1));
                    long sec = Long.parseLong(timestamp.group(2));
                    if (sec >= 60) continue;
                    String part = timestamp.group(3);
                    long ms = part == null ? 0 : Integer.parseInt(part) * (part.length() == 1 ? 100 : part.length() == 2 ? 10 : 1);
                    positions.add(Math.max(0, min * 60000 + sec * 1000 + ms + offset));
                } catch (NumberFormatException ignored) { }
            }
            String text = WORD_TIME.matcher(TIMESTAMP.matcher(line).replaceAll("")).replaceAll("").trim();
            if (text.startsWith("[" ) && text.endsWith("]")) continue;
            if (!positions.isEmpty()) {
                for (Long pos : positions) timed.add(new LrcLyrics.Line(pos, text));
            } else if (!text.isEmpty()) {
                plain.add(new LrcLyrics.Line(-1, text));
            }
        }
        if (!timed.isEmpty()) {
            timed.sort(Comparator.comparingLong(item -> item.timeMs));
            return new LrcLyrics(timed, true);
        }
        return new LrcLyrics(plain, false);
    }
}
