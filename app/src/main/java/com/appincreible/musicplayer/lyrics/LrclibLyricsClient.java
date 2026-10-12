package com.appincreible.musicplayer.lyrics;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import androidx.annotation.NonNull;
import com.appincreible.musicplayer.data.model.Song;
import com.appincreible.musicplayer.power.PowerSaverManager;
import com.appincreible.musicplayer.themes.AppPreferences;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/** Búsqueda LRCLIB: primero coincidencia exacta y luego alternativas verificadas. Solo hilo de fondo. */
public final class LrclibLyricsClient {
    public static final String KEY_AUTO = "lyrics_lrclib_auto";
    public static final String KEY_WIFI_ONLY = "lyrics_lrclib_wifi_only";
    public static final String KEY_AUTO_MODE = "lyrics_lrclib_auto_mode";
    public static final String MODE_VIEWER = "viewer";
    public static final String MODE_LIBRARY = "library";
    public enum Status { FOUND, INSTRUMENTAL, NOT_FOUND, NETWORK_ERROR, DISABLED, ALREADY_CHECKED }
    public static final class Result {
        public final Status status;
        public final LrcLyrics lyrics;
        public Result(Status status, LrcLyrics lyrics) { this.status = status; this.lyrics = lyrics; }
    }
    private final Context context;
    private final LocalLyricsStore store;
    private final LrclibLyricsState state;
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(35, TimeUnit.SECONDS).build();
    private static final String BASE = "https://lrclib.net/api/";

    public LrclibLyricsClient(@NonNull Context context) {
        this.context = context.getApplicationContext();
        store = new LocalLyricsStore(this.context);
        state = new LrclibLyricsState(this.context);
    }

    public Result lookup(@NonNull Song song, boolean manual) {
        if (!manual && (!AppPreferences.prefs(context).getBoolean(KEY_AUTO, false)
                || PowerSaverManager.isActive(context))) return new Result(Status.DISABLED, null);
        if (!manual && state.wasCheckedWithoutResult(song.getMediaKey())) return new Result(Status.ALREADY_CHECKED, null);
        if (!networkAllowed(manual)) return new Result(Status.NETWORK_ERROR, null);
        return search(song);
    }

    /** Búsqueda masiva explícita: reevalúa negativos de versiones anteriores con búsqueda flexible. */
    public Result lookupBulk(@NonNull Song song) {
        if (!networkAllowed(true)) return new Result(Status.NETWORK_ERROR, null);
        return search(song);
    }

    /** Opciones para selección humana: NO relaja los filtros de la búsqueda masiva. */
    public static final class Choice {
        public final long id;
        public final String title, artist, album;
        public final int durationSeconds;
        public final boolean synced, instrumental;
        Choice(JsonObject object) {
            id = (long) number(object, "id", -1);
            title = value(object, "trackName");
            artist = value(object, "artistName");
            album = value(object, "albumName");
            durationSeconds = (int) Math.round(number(object, "duration", 0));
            synced = !value(object, "syncedLyrics").trim().isEmpty();
            instrumental = bool(object, "instrumental");
        }
        public String displayLabel() {
            String time = String.format(Locale.ROOT, "%d:%02d", durationSeconds / 60, durationSeconds % 60);
            return title + " · " + artist + "\n" + time + " · "
                    + (instrumental ? "Instrumental" : synced ? "Sincronizada" : "Sin tiempos")
                    + (album.isEmpty() ? "" : " · " + album);
        }
    }

    /** Busca solamente por título; el usuario elige. Ejecutar fuera del hilo principal. */
    public List<Choice> searchChoices(@NonNull Song song) throws IOException, InterruptedException {
        if (!networkAllowed(true)) throw new IOException("Sin conexión");
        String title = clean(song.getTitle());
        List<Choice> options = new ArrayList<>();
        if (title.isEmpty()) return options;
        java.util.HashSet<Long> seen = new java.util.HashSet<>();
        java.util.ArrayList<String> queries = new java.util.ArrayList<>();
        queries.add(title);
        // Una segunda búsqueda elimina versiones/featurings entre paréntesis.
        String simplified = title.replaceAll("(?i)\\s*[\\[(](?:remaster(?:ed)?|official|audio|video|lyrics|feat\\.?|ft\\.).*?[\\])]", "").trim();
        if (!simplified.isEmpty() && !simplified.equalsIgnoreCase(title)) queries.add(simplified);
        for (String query : queries) {
            HttpUrl url = HttpUrl.get(BASE + "search").newBuilder()
                    .addQueryParameter("track_name", query).build();
            JsonElement results = fetch(url);
            if (results == null || !results.isJsonArray()) continue;
            for (JsonElement row : results.getAsJsonArray()) {
                if (!row.isJsonObject()) continue;
                JsonObject data = row.getAsJsonObject();
                Choice choice = new Choice(data);
                if (choice.id < 0 || !seen.add(choice.id)) continue;
                if (!choice.instrumental && value(data, "syncedLyrics").trim().isEmpty()
                        && value(data, "plainLyrics").trim().isEmpty()) continue;
                options.add(choice);
            }
        }
        options.sort((a, b) -> {
            if (a.synced != b.synced) return a.synced ? -1 : 1;
            long da = Math.abs(a.durationSeconds - song.getDurationMs() / 1000L);
            long db = Math.abs(b.durationSeconds - song.getDurationMs() / 1000L);
            return Long.compare(da, db);
        });
        return options;
    }

    /** Recupera el registro exacto elegido por el usuario y lo guarda en la caché interna. */
    public Result selectChoice(@NonNull Song song, @NonNull Choice choice) {
        try {
            JsonElement response = fetch(HttpUrl.get(BASE + "get/" + choice.id));
            if (response == null || !response.isJsonObject()) return new Result(Status.NOT_FOUND, null);
            JsonObject selected = response.getAsJsonObject();
            if (bool(selected, "instrumental")) {
                state.markInstrumental(song.getMediaKey());
                return new Result(Status.INSTRUMENTAL, null);
            }
            String synced = value(selected, "syncedLyrics");
            String text = !synced.trim().isEmpty() ? synced : value(selected, "plainLyrics");
            LrcLyrics parsed = LrcParser.parse(text);
            if (parsed.lines.isEmpty()) return new Result(Status.NOT_FOUND, null);
            store.save(song.getMediaKey(), text);
            state.markDownloaded(song.getMediaKey());
            return new Result(Status.FOUND, parsed);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Result(Status.NETWORK_ERROR, null);
        } catch (Exception e) { return new Result(Status.NETWORK_ERROR, null); }
    }

    private Result search(Song song) {
        try {
            String title = clean(song.getTitle());
            String artist = clean(song.getArtist());
            if (title.isEmpty() || artist.isEmpty()) return new Result(Status.NOT_FOUND, null);
            double seconds = song.getDurationMs() / 1000.0;
            List<JsonObject> candidates = new ArrayList<>();
            boolean hadTemporaryError = false;
            // Sin álbum: muchos tags locales tienen nombres distintos a LRCLIB.
            HttpUrl exact = HttpUrl.get(BASE + "get").newBuilder()
                    .addQueryParameter("track_name", title).addQueryParameter("artist_name", artist)
                    .addQueryParameter("duration", String.valueOf(Math.round(seconds))).build();
            try { JsonElement result = fetch(exact); if (result != null && result.isJsonObject()) candidates.add(result.getAsJsonObject()); }
            catch (IOException temporary) { hadTemporaryError = true; }
            // /api/search encuentra versiones ausentes en /api/get y permite preferir letras sincronizadas.
            HttpUrl broad = HttpUrl.get(BASE + "search").newBuilder()
                    .addQueryParameter("track_name", title).addQueryParameter("artist_name", artist).build();
            try {
                JsonElement result = fetch(broad);
                if (result != null && result.isJsonArray()) for (JsonElement row : result.getAsJsonArray())
                    if (row.isJsonObject()) candidates.add(row.getAsJsonObject());
            } catch (IOException temporary) { hadTemporaryError = true; }
            // Segundo intento sin calificadores habituales, pero siempre validando artista y duración.
            String simpleTitle = title.replaceAll("(?i)\\s*[\\[(](?:remaster(?:ed)?|official|audio|video|lyrics|feat\\.?|ft\\.).*?[\\])]", "").trim();
            if (!simpleTitle.equalsIgnoreCase(title) && !simpleTitle.isEmpty()) {
                HttpUrl alternate = HttpUrl.get(BASE + "search").newBuilder()
                        .addQueryParameter("track_name", simpleTitle).addQueryParameter("artist_name", artist).build();
                try {
                    JsonElement result = fetch(alternate);
                    if (result != null && result.isJsonArray()) for (JsonElement row : result.getAsJsonArray())
                        if (row.isJsonObject()) candidates.add(row.getAsJsonObject());
                } catch (IOException temporary) { hadTemporaryError = true; }
            }
            JsonObject best = null;
            double bestScore = -1;
            for (JsonObject candidate : candidates) {
                double score = score(candidate, song, simpleTitle);
                if (score > bestScore) { bestScore = score; best = candidate; }
            }
            if (best != null && bestScore >= 0) {
                if (bool(best, "instrumental")) {
                    state.markInstrumental(song.getMediaKey());
                    return new Result(Status.INSTRUMENTAL, null);
                }
                String synced = value(best, "syncedLyrics");
                String plain = value(best, "plainLyrics");
                String text = !synced.trim().isEmpty() ? synced : plain;
                LrcLyrics parsed = LrcParser.parse(text);
                if (!parsed.lines.isEmpty()) {
                    store.save(song.getMediaKey(), text);
                    state.markDownloaded(song.getMediaKey());
                    return new Result(Status.FOUND, parsed);
                }
            }
            // Nunca registrar un negativo si alguna petición falló: se podrá volver a intentar.
            if (hadTemporaryError) return new Result(Status.NETWORK_ERROR, null);
            state.markNoResult(song.getMediaKey());
            return new Result(Status.NOT_FOUND, null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Result(Status.NETWORK_ERROR, null);
        } catch (Exception e) { return new Result(Status.NETWORK_ERROR, null); }
    }

    /** Devuelve null para 404. Respeta Retry-After y reintenta errores temporales. */
    private JsonElement fetch(HttpUrl url) throws IOException, InterruptedException {
        for (int attempt = 0; attempt < 3; attempt++) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
            Request req = new Request.Builder().url(url)
                    .header("User-Agent", "AppIncreibleMusicPlayer/1.0 (Android; com.appincreible.musicplayer)").get().build();
            try (Response response = client.newCall(req).execute()) {
                if (response.code() == 404) return null;
                int code = response.code();
                if (code == 429 || code == 502 || code == 503 || code == 504) {
                    if (attempt == 2) throw new IOException("LRCLIB temporal HTTP " + code);
                    long delay = (attempt + 1) * 1200L;
                    String retryAfter = response.header("Retry-After");
                    if (retryAfter != null) try { delay = Math.max(delay, Math.min(30000L, Long.parseLong(retryAfter.trim()) * 1000L)); }
                    catch (NumberFormatException ignored) { delay = 30000L; }
                    Thread.sleep(delay);
                    continue;
                }
                if (!response.isSuccessful() || response.body() == null) throw new IOException("HTTP " + code);
                return JsonParser.parseString(response.body().string());
            } catch (IOException transientError) {
                if (attempt == 2) throw transientError;
                Thread.sleep(800L * (attempt + 1));
            }
        }
        throw new IOException("Sin respuesta");
    }

    private static double score(JsonObject obj, Song song, String simpleTitle) {
        String remoteTitle = normalize(value(obj, "trackName"));
        String title = normalize(song.getTitle());
        String basic = normalize(simpleTitle);
        String remoteArtist = normalize(value(obj, "artistName"));
        String artist = normalize(song.getArtist());
        if (remoteTitle.isEmpty() || remoteArtist.isEmpty() || artist.isEmpty()) return -1;
        if (!remoteTitle.equals(title) && !remoteTitle.equals(basic)) return -1;
        if (!remoteArtist.equals(artist) && !remoteArtist.contains(artist) && !artist.contains(remoteArtist)) return -1;
        double duration = number(obj, "duration", -1);
        double delta = Math.abs(duration - song.getDurationMs() / 1000.0);
        if (duration <= 0 || delta > 3.0) return -1;
        boolean synced = !value(obj, "syncedLyrics").trim().isEmpty();
        boolean plain = !value(obj, "plainLyrics").trim().isEmpty();
        if (!synced && !plain && !bool(obj, "instrumental")) return -1;
        return (synced ? 1000 : plain ? 100 : 10) - delta * 10
                + (remoteArtist.equals(artist) ? 30 : 0)
                + (normalize(value(obj, "albumName")).equals(normalize(song.getAlbum())) ? 5 : 0);
    }
    private static String normalize(String text) {
        return Normalizer.normalize(clean(text), Normalizer.Form.NFD).replaceAll("\\p{M}+", "")
                .replaceAll("[^\\p{L}\\p{N}]+", " ").trim().toLowerCase(Locale.ROOT);
    }
    private static String clean(String text) { return text == null ? "" : text.trim(); }
    private static String value(JsonObject json, String key) {
        return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsString() : "";
    }
    private static boolean bool(JsonObject json, String key) {
        return json.has(key) && !json.get(key).isJsonNull() && json.get(key).getAsBoolean();
    }
    private static double number(JsonObject json, String key, double fallback) {
        try { return json.get(key).getAsDouble(); } catch (Exception ignored) { return fallback; }
    }
    public boolean isDownloaded(String mediaKey) { return state.isDownloaded(mediaKey); }
    public boolean isInstrumental(String mediaKey) { return state.isInstrumental(mediaKey); }
    public void clearDownloadedRecords() { state.clear(); }
    private boolean networkAllowed(boolean manual) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        android.net.Network network = cm.getActiveNetwork();
        NetworkCapabilities caps = network == null ? null : cm.getNetworkCapabilities(network);
        if (caps == null || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return false;
        return manual || !AppPreferences.prefs(context).getBoolean(KEY_WIFI_ONLY, true)
                || caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
    }
}
