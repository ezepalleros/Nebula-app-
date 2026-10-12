package com.appincreible.musicplayer.lyrics;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import com.appincreible.musicplayer.data.model.Song;

/** User-initiated, cancellable bulk lookup. Never replaces existing local lyrics. */
public final class LrclibBulkWorker extends Worker {
    public static final String UNIQUE = "lrclib_bulk_lyrics";
    public static final String DONE = "done", TOTAL = "total", FOUND = "found";
    public LrclibBulkWorker(@NonNull Context context, @NonNull WorkerParameters params) { super(context, params); }

    public static void start(Context context) {
        // Búsqueda solicitada expresamente por el usuario: sin condiciones de red, batería o carga.
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(LrclibBulkWorker.class).build();
        WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE, ExistingWorkPolicy.REPLACE, request);
    }
    public static void cancel(Context context) { WorkManager.getInstance(context).cancelUniqueWork(UNIQUE); }

    @NonNull @Override public Result doWork() {
        Context context = getApplicationContext();
        int done = 0, found = 0;
        try {
            LrclibLyricsClient client = new LrclibLyricsClient(context);
            LocalLyricsStore local = new LocalLyricsStore(context);
            Uri collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
            String[] cols = {MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.DURATION};
            try (Cursor cursor = context.getContentResolver().query(collection, cols,
                    MediaStore.Audio.Media.IS_MUSIC + " != 0", null, null)) {
                if (cursor == null) return Result.failure();
                int total = cursor.getCount();
                setProgressAsync(progress(0, total, 0));
                while (cursor.moveToNext()) {
                    if (isStopped()) return Result.success();
                    long id = cursor.getLong(0);
                    Uri uri = ContentUris.withAppendedId(collection, id);
                    Song song = new Song(id, cursor.getString(1), cursor.getString(2), cursor.getString(3), cursor.getLong(4), uri);
                    boolean hasLyrics = false;
                    try { hasLyrics = local.loadImported(song.getMediaKey()) != null
                            || EmbeddedLyricsReader.read(context, uri) != null; }
                    catch (Exception ignored) { /* Other tracks still proceed. */ }
                    if (!hasLyrics && !client.isDownloaded(song.getMediaKey())
                            && !new LrclibLyricsState(context).wasCheckedWithoutResult(song.getMediaKey())) {
                        LrclibLyricsClient.Result lookup = client.lookupBulk(song);
                        if (lookup.status == LrclibLyricsClient.Status.FOUND) found++;
                        if (lookup.status == LrclibLyricsClient.Status.NETWORK_ERROR) {
                            setProgressAsync(progress(done, total, found));
                            return Result.retry();
                        }
                    }
                    done++;
                    setProgressAsync(progress(done, total, found));
                    // Respeta la recomendación de LRCLIB para consultas masivas.
                    try { Thread.sleep(350L); } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt(); return Result.success();
                    }
                }
            }
            return Result.success(progress(done, done, found));
        } catch (Exception e) { return Result.failure(progress(done, done, found)); }
    }
    private static Data progress(int done, int total, int found) {
        return new Data.Builder().putInt(DONE, done).putInt(TOTAL, total).putInt(FOUND, found).build();
    }
}
