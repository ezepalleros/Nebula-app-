package com.appincreible.musicplayer.metadata.repository;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.Looper;

import com.appincreible.musicplayer.BuildConfig;
import com.appincreible.musicplayer.metadata.model.ArtworkOption;
import com.appincreible.musicplayer.radio.model.RadioStation;
import com.appincreible.musicplayer.radio.repository.RadioRepository;
import com.appincreible.musicplayer.themes.AppPreferences;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/** Searches album artwork only when requested. Radio-logo search remains untouched. */
public final class ArtworkSearchRepository {
    public interface CallbackResult {
        void onLoaded(List<ArtworkOption> options);
        void onError(String message);
    }

    public static final String KEY_DEEZER = "artwork_search_deezer";
    public static final String KEY_ITUNES = "artwork_search_itunes";
    public static final String KEY_WIFI = "artwork_search_wifi";
    public static final String KEY_QUALITY = "artwork_search_quality";

    /** Extend this list when a common placeholder album tag appears. */
    public static final Set<String> GENERIC_ALBUMS;
    static {
        Set<String> albums = new HashSet<>();
        Collections.addAll(albums, "snaptube audio", "youtube", "unknown", "downloads", "desconocido");
        GENERIC_ALBUMS = Collections.unmodifiableSet(albums);
    }
    private static final Pattern NOISE = Pattern.compile("\\(([^)]*)\\)|\\[([^]]*)\\]");
    private static final String[] NOISE_WORDS = {"official video", "official audio", "audio", "lyrics", "letra", "hd", "hq", "remastered"};
    private static final int MAX_CANDIDATES = 12;
    private static final double GOOD_MATCH = 0.86d;
    private static final int CACHE_LIMIT = 48;
    private static final Map<String, List<ArtworkOption>> CACHE = new LinkedHashMap<String, List<ArtworkOption>>(CACHE_LIMIT, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, List<ArtworkOption>> entry) {
            return size() > CACHE_LIMIT;
        }
    };
    private static long lastITunesAt;

    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final OkHttpClient http;
    private final RadioRepository radioRepository;
    private Future<?> currentTask;
    private Call activeCall;
    private int generation;

    public ArtworkSearchRepository(Context context) {
        this.context = context.getApplicationContext();
        String ua = "NebulaMusicPlayer/" + BuildConfig.VERSION_NAME + " (Android; artwork search)";
        http = new OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(8, TimeUnit.SECONDS).callTimeout(8, TimeUnit.SECONDS)
                .followSslRedirects(false)
                .addInterceptor(chain -> chain.proceed(chain.request().newBuilder()
                        .header("User-Agent", ua).header("Accept", "application/json").build()))
                .build();
        radioRepository = new RadioRepository(context);
    }

    public static void clearMemoryCache() {
        synchronized (CACHE) { CACHE.clear(); }
    }

    /** Cancels any previous search, including an in-flight HTTP request. */
    public synchronized void cancel() {
        generation++;
        if (currentTask != null) currentTask.cancel(true);
        if (activeCall != null) activeCall.cancel();
        currentTask = null;
        activeCall = null;
    }

    public void close() { cancel(); executor.shutdownNow(); }

    public void searchSong(String title, String artist, CallbackResult callback) {
        searchSong(title, artist, "", callback);
    }

    public void searchSong(String title, String artist, String album, CallbackResult callback) {
        cancel();
        final int token;
        synchronized (this) { token = generation; }
        boolean deezer = AppPreferences.prefs(context).getBoolean(KEY_DEEZER, true);
        boolean itunes = AppPreferences.prefs(context).getBoolean(KEY_ITUNES, true);
        if (!deezer && !itunes) {
            main.post(() -> callback.onError("Activá Deezer o iTunes en Configuración → Biblioteca."));
            return;
        }
        if (AppPreferences.prefs(context).getBoolean(KEY_WIFI, false) && !onWifi()) {
            main.post(() -> callback.onError("La búsqueda requiere Wi-Fi según tu configuración."));
            return;
        }
        String[] cleaned = cleanInputs(title, artist, album);
        if (cleaned[0].isEmpty()) {
            main.post(() -> callback.onError("Ingresá un título para buscar portadas."));
            return;
        }
        String key = cleaned[0] + "|" + cleaned[1] + "|" + deezer + "|" + itunes;
        List<ArtworkOption> cached;
        synchronized (CACHE) { cached = CACHE.get(key); }
        if (cached != null) {
            main.post(() -> { if (isCurrent(token)) callback.onLoaded(new ArrayList<>(cached)); });
            return;
        }
        currentTask = executor.submit(() -> {
            int attempted = 0;
            List<ArtworkOption> results = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            boolean good = false;
            boolean failed = false;
            try {
                for (int round = 0; round < 3 && !good && results.size() < MAX_CANDIDATES; round++) {
                    checkCancelled(token);
                    String query;
                    if (round == 0 && cleaned[1].isEmpty()) continue;
                    if (round == 0) query = "artist:\"" + cleaned[1] + "\" track:\"" + cleaned[0] + "\"";
                    else if (round == 1) query = (cleaned[0] + " " + cleaned[1]).trim();
                    else query = cleaned[0];
                    if (round == 1 && cleaned[1].isEmpty()) continue; // avoid duplicate title-only request
                    if (deezer) {
                        attempted++;
                        try { good = addDeezer(query, cleaned, round, results, seen, token); }
                        catch (IOException | RuntimeException error) { failed = true; }
                    }
                    if (!good && itunes && results.size() < MAX_CANDIDATES) {
                        // Deliberately one iTunes call per round, never both while a good Deezer match exists.
                        attempted++;
                        try { good = addITunes(round == 0 ? cleaned[0] + " " + cleaned[1] : query,
                                cleaned, round, results, seen, token); }
                        catch (IOException | RuntimeException error) { failed = true; }
                    }
                }
                checkCancelled(token);
                results.sort(Comparator.comparing(ArtworkOption::isRecommended).reversed()
                        .thenComparing(Comparator.comparingDouble(ArtworkOption::getScore).reversed()));
                // Only the top-scoring high-confidence result is preselected.
                boolean topMarked = false;
                for (int i = 0; i < results.size(); i++) {
                    ArtworkOption item = results.get(i);
                    boolean recommend = !topMarked && item.isRecommended();
                    if (recommend) topMarked = true;
                    if (item.isRecommended() != recommend) {
                        results.set(i, new ArtworkOption(item.getLabel(), item.getImageUrl(),
                                item.getFallbackUrl(), item.getTitle(), item.getArtist(), item.getAlbum(),
                                item.getSource(), item.getScore(), false));
                    }
                }
                if (!results.isEmpty()) {
                    synchronized (CACHE) { CACHE.put(key, new ArrayList<>(results)); }
                    main.post(() -> { if (isCurrent(token)) callback.onLoaded(results); });
                } else {
                    final int count = attempted;
                    final String message = "No encontré portadas. Probé en Deezer e iTunes con " + count
                            + " búsquedas" + (failed ? ". Algunas consultas fallaron." : ".");
                    main.post(() -> { if (isCurrent(token)) callback.onError(message); });
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        });
    }

    private boolean addDeezer(String query, String[] requested, int round,
                              List<ArtworkOption> out, Set<String> seen, int token)
            throws IOException, InterruptedException {
        HttpUrl url = new HttpUrl.Builder().scheme("https").host("api.deezer.com")
                .addPathSegment("search").addQueryParameter("q", query)
                .addQueryParameter("limit", "12").build();
        JsonObject json = request(url, token);
        if (json == null) return false;
        JsonArray array = array(json, "data");
        boolean good = false;
        for (JsonElement e : array) {
            checkCancelled(token);
            if (out.size() >= MAX_CANDIDATES) break;
            if (!e.isJsonObject()) continue;
            JsonObject track = e.getAsJsonObject();
            JsonObject artist = object(track, "artist");
            JsonObject album = object(track, "album");
            String image = https(string(album, "cover_xl"));
            if (image.isEmpty()) image = https(string(album, "cover_big"));
            String fallback = https(string(album, "cover_big"));
            String trackTitle = string(track, "title");
            String artistName = string(artist, "name");
            if (image.isEmpty() || trackTitle.isEmpty()) continue;
            double score = score(requested[0], requested[1], trackTitle, artistName);
            boolean recommended = round < 2 && !requested[1].isEmpty() && score >= GOOD_MATCH;
            if (add(out, seen, image, fallback, trackTitle, artistName,
                    string(album, "title"), "Deezer", score, recommended)) good |= recommended;
        }
        return good;
    }

    private boolean addITunes(String query, String[] requested, int round,
                              List<ArtworkOption> out, Set<String> seen, int token)
            throws IOException, InterruptedException {
        // Avoid bursts of calls to iTunes across different user-initiated searches.
        synchronized (ArtworkSearchRepository.class) {
            long wait = 3100L - (android.os.SystemClock.elapsedRealtime() - lastITunesAt);
            if (wait > 0 && lastITunesAt != 0) Thread.sleep(wait);
            lastITunesAt = android.os.SystemClock.elapsedRealtime();
        }
        checkCancelled(token);
        HttpUrl url = new HttpUrl.Builder().scheme("https").host("itunes.apple.com")
                .addPathSegment("search").addQueryParameter("term", query)
                .addQueryParameter("media", "music").addQueryParameter("entity", "song")
                .addQueryParameter("limit", "5").build();
        JsonObject json = request(url, token);
        if (json == null) return false;
        boolean good = false;
        for (JsonElement e : array(json, "results")) {
            checkCancelled(token);
            if (out.size() >= MAX_CANDIDATES) break;
            if (!e.isJsonObject()) continue;
            JsonObject track = e.getAsJsonObject();
            String image = https(string(track, "artworkUrl100"));
            if (image.isEmpty()) continue;
            String high = resizeITunes(image, "1000x1000bb");
            String standard = resizeITunes(image, "600x600bb");
            String name = string(track, "trackName");
            String artist = string(track, "artistName");
            double score = score(requested[0], requested[1], name, artist);
            boolean recommended = round < 2 && !requested[1].isEmpty() && score >= GOOD_MATCH;
            if (add(out, seen, high, standard, name, artist,
                    string(track, "collectionName"), "iTunes", score, recommended)) good |= recommended;
        }
        return good;
    }

    private boolean add(List<ArtworkOption> out, Set<String> seen, String url, String fallback,
                        String title, String artist, String album, String source,
                        double score, boolean recommended) {
        // Album art is often reused by several tracks: use URL as the deduplication key.
        String dedupe = url.replace("1000x1000bb", "{size}").replace("600x600bb", "{size}");
        if (!seen.add(dedupe)) return false;
        out.add(new ArtworkOption(title + " · " + artist, url, fallback, title, artist,
                album, source, score, recommended));
        return true;
    }

    private JsonObject request(HttpUrl url, int token) throws IOException, InterruptedException {
        checkCancelled(token);
        Call call = http.newCall(new Request.Builder().url(url).get().build());
        synchronized (this) { activeCall = call; }
        try (Response response = call.execute()) {
            if (response.code() == 429) throw new IOException("Límite de consultas HTTP 429");
            if (!response.isSuccessful() || response.body() == null) throw new IOException("HTTP " + response.code());
            return JsonParser.parseReader(response.body().charStream()).getAsJsonObject();
        } finally {
            synchronized (this) { if (activeCall == call) activeCall = null; }
        }
    }

    private boolean onWifi() {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        NetworkCapabilities cap = cm.getNetworkCapabilities(cm.getActiveNetwork());
        return cap != null && cap.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
    }

    private synchronized boolean isCurrent(int token) { return generation == token; }
    private void checkCancelled(int token) throws InterruptedException {
        if (Thread.currentThread().isInterrupted() || !isCurrent(token)) throw new InterruptedException();
    }

    private static JsonObject object(JsonObject obj, String name) {
        if (obj == null || !obj.has(name) || !obj.get(name).isJsonObject()) return new JsonObject();
        return obj.getAsJsonObject(name);
    }
    private static JsonArray array(JsonObject obj, String name) {
        if (obj == null || !obj.has(name) || !obj.get(name).isJsonArray()) return new JsonArray();
        return obj.getAsJsonArray(name);
    }
    private static String string(JsonObject obj, String name) {
        return obj != null && obj.has(name) && !obj.get(name).isJsonNull()
                ? obj.get(name).getAsString().trim() : "";
    }
    private static String https(String text) {
        if (text.startsWith("http://")) return "https://" + text.substring(7);
        return text.startsWith("https://") ? text : "";
    }
    public static String resizeITunes(String url, String size) {
        return url.replaceAll("(?i)100x100(?:bb|-\\d+)", size);
    }

    private static String[] cleanInputs(String title, String artist, String album) {
        String t = title == null ? "" : title.trim();
        String a = artist == null ? "" : artist.trim();
        // Split only when the prefix looks like an artist and title is non-empty.
        int sep = t.indexOf(" - ");
        if (sep > 0 && sep < t.length() - 3) {
            String prefix = t.substring(0, sep).trim();
            String suffix = t.substring(sep + 3).trim();
            if (!suffix.isEmpty()) {
                if (a.isEmpty() || isPlaceholder(a)) a = prefix;
                t = suffix;
            }
        }
        Matcher matcher = NOISE.matcher(t);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String segment = normalize(matcher.group(1) != null ? matcher.group(1) : matcher.group(2));
            boolean noise = false;
            for (String word : NOISE_WORDS) if (segment.contains(word)) { noise = true; break; }
            matcher.appendReplacement(buffer, noise ? "" : Matcher.quoteReplacement(matcher.group()));
        }
        matcher.appendTail(buffer);
        String al = normalize(album);
        if (al.isEmpty() || al.equals(normalize(a)) || GENERIC_ALBUMS.contains(al)) al = "";
        return new String[]{normalize(buffer.toString()), normalize(a), al};
    }
    private static boolean isPlaceholder(String value) {
        return GENERIC_ALBUMS.contains(normalize(value)) || normalize(value).equals("wavegroup");
    }
    private static String normalize(String text) {
        if (text == null) return "";
        String s = Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").replaceAll("[^a-z0-9]+", " ").trim();
        return s.replaceAll(" +", " ");
    }
    private static double score(String title, String artist, String hitTitle, String hitArtist) {
        double t = similarity(title, normalize(hitTitle));
        double a = artist.isEmpty() ? 0 : similarity(artist, normalize(hitArtist));
        return title.isEmpty() ? 0 : (artist.isEmpty() ? t * 0.75 : t * 0.76 + a * 0.24);
    }
    private static double similarity(String a, String b) {
        if (a.equals(b)) return 1d;
        if (a.isEmpty() || b.isEmpty()) return 0d;
        Set<String> aa = new HashSet<>(), bb = new HashSet<>();
        Collections.addAll(aa, a.split(" "));
        Collections.addAll(bb, b.split(" "));
        Set<String> intersection = new HashSet<>(aa);
        intersection.retainAll(bb);
        Set<String> union = new HashSet<>(aa);
        union.addAll(bb);
        double jaccard = union.isEmpty() ? 0d : (double) intersection.size() / union.size();
        int distance = editDistance(a, b);
        return 0.55d * jaccard + 0.45d * (1d - (double) distance / Math.max(a.length(), b.length()));
    }
    private static int editDistance(String a, String b) {
        int[] prev = new int[b.length() + 1], curr = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            curr[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1),
                        prev[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1));
            }
            int[] temp = prev; prev = curr; curr = temp;
        }
        return prev[b.length()];
    }

    public void searchRadio(String stationName, CallbackResult callback) {
        radioRepository.searchArgentinaStations(stationName, 16, new RadioRepository.StationsCallback() {
            @Override public void onLoaded(List<RadioStation> stations) {
                List<ArtworkOption> options = new ArrayList<>();
                Set<String> seen = new HashSet<>();
                for (RadioStation station : stations) {
                    String favicon = station.getOriginalFavicon();
                    if (favicon.isEmpty() || !seen.add(favicon)) continue;
                    options.add(new ArtworkOption(station.getName(), favicon));
                }
                if (options.isEmpty()) callback.onError("No encontré logos online para esa radio.");
                else callback.onLoaded(options);
            }
            @Override public void onError(String message) { callback.onError(message); }
        });
    }
}
