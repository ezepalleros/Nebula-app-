package com.appincreible.musicplayer.lyrics;

import android.content.ContentUris;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.work.WorkManager;
import com.appincreible.musicplayer.R;
import com.appincreible.musicplayer.data.model.Song;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Búsqueda manual visible: no depende de la cola de WorkManager. */
public final class LrclibScanActivity extends AppCompatActivity {
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private LinearLayout entries;
    private TextView summary;
    private ProgressBar bar;
    private MaterialButton cancel;
    private int total, reviewed, existing, downloaded, missing;

    @Override protected void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        int pad = dp(16);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        root.setBackgroundColor(getColor(R.color.ui_surface_dark));
        TextView title = label("Búsqueda de letras · LRCLIB", 22, R.color.ui_text_primary_dark);
        root.addView(title);
        TextView legend = label("● Amarillo: ya tenía letra    ● Rojo: sin letra    ● Verde: descargada", 13, R.color.ui_text_secondary_dark);
        root.addView(legend);
        summary = label("Preparando biblioteca…", 15, R.color.ui_text_primary_dark);
        root.addView(summary);
        bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setIndeterminate(true);
        root.addView(bar, new LinearLayout.LayoutParams(-1, dp(10)));
        cancel = new MaterialButton(this);
        cancel.setText("Cancelar búsqueda");
        cancel.setOnClickListener(v -> stopScan());
        root.addView(cancel);
        ScrollView scroll = new ScrollView(this);
        entries = new LinearLayout(this);
        entries.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(entries);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        // Cancela una búsqueda de la implementación anterior que pudiera quedar en cola.
        WorkManager.getInstance(getApplicationContext()).cancelUniqueWork(LrclibBulkWorker.UNIQUE);
        executor.execute(this::scan);
    }

    private void scan() {
        LrclibLyricsClient client = new LrclibLyricsClient(getApplicationContext());
        LocalLyricsStore local = new LocalLyricsStore(getApplicationContext());
        Uri collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        String[] columns = {MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.DURATION};
        try (Cursor cursor = getContentResolver().query(collection, columns,
                MediaStore.Audio.Media.IS_MUSIC + " != 0", null, MediaStore.Audio.Media.TITLE + " COLLATE NOCASE ASC")) {
            if (cursor == null) { finishScan("No se pudo leer la biblioteca."); return; }
            total = cursor.getCount();
            runOnUiThread(() -> { bar.setIndeterminate(false); bar.setMax(Math.max(1, total)); refresh(); });
            while (!cancelled.get() && cursor.moveToNext()) {
                long id = cursor.getLong(0);
                Uri uri = ContentUris.withAppendedId(collection, id);
                Song song = new Song(id, cursor.getString(1), cursor.getString(2), cursor.getString(3), cursor.getLong(4), uri);
                String status;
                int color;
                try {
                    LrcLyrics cached = local.loadImported(song.getMediaKey());
                    LrcLyrics embedded = EmbeddedLyricsReader.read(this, uri);
                    // La letra sincronizada existente se conserva. La letra plana de LRCLIB
                    // puede mejorarse con una versión sincronizada de la misma canción.
                    boolean downloadedPlain = client.isDownloaded(song.getMediaKey())
                            && cached != null && !cached.synchronizedLines;
                    if ((cached != null && !downloadedPlain) || (embedded != null && embedded.synchronizedLines)) {
                        existing++;
                        color = R.color.settings_category_radio;
                        status = "Ya tenía letra";
                    } else {
                        LrclibLyricsClient.Result result = client.lookupBulk(song);
                        if (result.status == LrclibLyricsClient.Status.FOUND) {
                            downloaded++;
                            color = R.color.settings_category_battery;
                            status = result.lyrics != null && result.lyrics.synchronizedLines
                                    ? "Descargada sincronizada" : "Descargada sin tiempos";
                        } else if (cached != null || embedded != null) {
                            existing++;
                            color = R.color.settings_category_radio;
                            status = "Con letra sin tiempos";
                        } else {
                            missing++;
                            color = -1;
                            status = result.status == LrclibLyricsClient.Status.NETWORK_ERROR
                                    ? "No se pudo buscar (red; reintentar)"
                                    : result.status == LrclibLyricsClient.Status.INSTRUMENTAL
                                    ? "Instrumental" : "Sin letra";
                        }
                    }
                } catch (Exception e) {
                    missing++;
                    color = -1;
                    status = "No se pudo buscar";
                }
                reviewed++;
                final String line = song.getTitle() + " · " + song.getArtist() + " — " + status;
                final int textColor = color;
                runOnUiThread(() -> {
                    addEntry(line, textColor);
                    bar.setProgress(reviewed);
                    refresh();
                });
                if (cancelled.get()) break;
                try { Thread.sleep(350); } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); break;
                }
            }
            finishScan(cancelled.get() ? "Búsqueda cancelada" : "Búsqueda terminada");
        } catch (Exception error) { finishScan("No se pudo leer la biblioteca: " + error.getClass().getSimpleName()); }
    }

    private void addEntry(String value, int color) {
        MaterialCardView card = new MaterialCardView(this);
        card.setCardBackgroundColor(getColor(R.color.ui_surface_container_dark));
        card.setRadius(dp(12));
        TextView text = label(value, 14, color);
        if (color == -1) text.setTextColor(com.google.android.material.color.MaterialColors.getColor(
                text, com.google.android.material.R.attr.colorError));
        text.setPadding(dp(12), dp(10), dp(12), dp(10));
        card.addView(text);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(6);
        entries.addView(card, params);
    }

    private void refresh() {
        summary.setText("Revisadas " + reviewed + "/" + total + " · Con letra " + existing
                + " · Descargadas " + downloaded + " · Sin letra " + missing);
    }

    private void finishScan(String result) {
        runOnUiThread(() -> {
            summary.setText(result + " · " + reviewed + "/" + total + " · " + downloaded + " nuevas");
            cancel.setText("Volver");
            cancel.setOnClickListener(v -> finish());
        });
    }

    private void stopScan() {
        cancelled.set(true);
        cancel.setEnabled(false);
        summary.setText("Cancelando después de la consulta actual…");
    }

    @Override protected void onDestroy() {
        cancelled.set(true);
        executor.shutdownNow();
        super.onDestroy();
    }

    private TextView label(String text, int sp, int color) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(sp);
        view.setTextColor(getColor(color == -1 ? R.color.ui_text_primary_dark : color));
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setPadding(0, dp(8), 0, dp(8));
        return view;
    }
    private int dp(int n) { return Math.round(getResources().getDisplayMetrics().density * n); }
}
