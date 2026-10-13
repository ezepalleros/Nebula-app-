package com.appincreible.musicplayer.metadata.repository;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import com.appincreible.musicplayer.BuildConfig;
import com.appincreible.musicplayer.metadata.model.ArtworkOption;
import com.appincreible.musicplayer.themes.AppPreferences;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/** Validates and saves chosen covers in app-private storage; never writes to the audio directly. */
public final class ArtworkDownloadRepository {
    public interface Callback { void done(String uri, String error); }
    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final OkHttpClient http;
    private Future<?> task;

    public ArtworkDownloadRepository(Context context) {
        this.context = context.getApplicationContext();
        http = new OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(8, TimeUnit.SECONDS).callTimeout(8, TimeUnit.SECONDS).followSslRedirects(false).build();
    }

    public void download(ArtworkOption option, Callback callback) {
        if (task != null) task.cancel(true);
        task = executor.submit(() -> {
            try {
                int max = "standard".equals(AppPreferences.prefs(context).getString(
                        ArtworkSearchRepository.KEY_QUALITY, "large")) ? 600 : 1000;
                byte[] bytes;
                String preferred = max == 600 && !option.getFallbackUrl().isEmpty()
                        ? option.getFallbackUrl() : option.getImageUrl();
                try {
                    bytes = fetch(preferred);
                } catch (IOException ex) {
                    if (option.getFallbackUrl().isEmpty() || option.getFallbackUrl().equals(preferred)) throw ex;
                    bytes = fetch(option.getFallbackUrl());
                }
                if (Thread.currentThread().isInterrupted()) return;
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
                if (bounds.outWidth < 300 || bounds.outHeight < 300) throw new IOException("La imagen mide menos de 300 px.");
                BitmapFactory.Options settings = new BitmapFactory.Options();
                int largest = Math.max(bounds.outWidth, bounds.outHeight);
                settings.inSampleSize = 1;
                while (largest / (settings.inSampleSize * 2) >= max) settings.inSampleSize *= 2;
                Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, settings);
                if (bitmap == null) throw new IOException("La portada no es una imagen válida.");
                int width = bitmap.getWidth(), height = bitmap.getHeight();
                if (Math.max(width, height) > max) {
                    float factor = (float) max / Math.max(width, height);
                    Bitmap reduced = Bitmap.createScaledBitmap(bitmap, Math.max(1, Math.round(width * factor)),
                            Math.max(1, Math.round(height * factor)), true);
                    if (reduced != bitmap) bitmap.recycle();
                    bitmap = reduced;
                }
                File folder = new File(context.getFilesDir(), "artwork_online");
                if (!folder.exists() && !folder.mkdirs()) throw new IOException("No se pudo crear la carpeta de portadas.");
                File file = File.createTempFile("cover_", ".jpg", folder);
                try {
                    try (FileOutputStream out = new FileOutputStream(file)) {
                        if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)) throw new IOException("No se pudo guardar la imagen.");
                        out.getFD().sync();
                    }
                    if (Thread.currentThread().isInterrupted()) { file.delete(); return; }
                    String result = Uri.fromFile(file).toString();
                    main.post(() -> callback.done(result, null));
                } catch (Exception failure) {
                    file.delete();
                    throw failure;
                } finally { bitmap.recycle(); }
            } catch (Exception ex) {
                String error = ex.getMessage() == null ? "No se pudo descargar la portada." : ex.getMessage();
                main.post(() -> callback.done(null, error));
            }
        });
    }

    private byte[] fetch(String url) throws IOException {
        if (!url.startsWith("https://")) throw new IOException("URL de imagen no segura.");
        Request req = new Request.Builder().url(url).header("User-Agent",
                "NebulaMusicPlayer/" + BuildConfig.VERSION_NAME + " (Android; artwork download)").build();
        try (Response response = http.newCall(req).execute()) {
            if (!response.isSuccessful() || response.body() == null) throw new IOException("Error de descarga HTTP " + response.code());
            if (response.body().contentLength() > 10L * 1024 * 1024) throw new IOException("La imagen es demasiado grande.");
            try (java.io.InputStream in = response.body().byteStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] block = new byte[8192];
                int size, total = 0;
                while ((size = in.read(block)) != -1) {
                    if (Thread.currentThread().isInterrupted()) throw new IOException("Descarga cancelada.");
                    total += size;
                    if (total > 10 * 1024 * 1024) throw new IOException("La imagen es demasiado grande.");
                    out.write(block, 0, size);
                }
                return out.toByteArray();
            }
        }
    }

    public void cancelActive() {
        if (task != null) task.cancel(true);
    }
    public void close() {
        cancelActive();
        executor.shutdownNow();
    }
}
