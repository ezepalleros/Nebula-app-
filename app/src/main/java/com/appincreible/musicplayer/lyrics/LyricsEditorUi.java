package com.appincreible.musicplayer.lyrics;

import android.content.Context;
import android.graphics.Color;
import android.content.SharedPreferences;
import com.appincreible.musicplayer.themes.AppPreferences;
import com.appincreible.musicplayer.power.PowerSaverManager;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Typeface;
import com.appincreible.musicplayer.themes.UiPalette;
import android.net.Uri;
import android.content.ContentUris;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import com.appincreible.musicplayer.data.model.Song;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Visor/editor autónomo; solo consulta la posición del reproductor mediante callbacks. */
public final class LyricsEditorUi {
    public interface Playback {
        long position();
        void pauseToggle();
        void rewindThreeSeconds();
    }
    private final Context context;
    private final ManualLyricsRepository repository;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Playback playback;
    private FrameLayout stage;
    private FrameLayout overlay;
    private View seekBoundary;
    private LinearLayout viewerColumn;
    private TextView content;
    private ScrollView lyricsScroll;
    private String lastStaticScrollKey;
    private TextView originLabel;
    private String songKey;
    private Song song;
    private final LrclibLyricsClient lrclib;
    private LrcLyrics lyrics;
    private String lyricsOrigin = "";
    private int lastLine = -2;
    private boolean fullscreenHost = false;
    private boolean immersiveLimitedHost = false;
    private final java.util.Map<View, Integer> artworkVisibility = new java.util.LinkedHashMap<>();
    private SharedPreferences.OnSharedPreferenceChangeListener lyricsPrefListener;

    public LyricsEditorUi(@NonNull Context context, @NonNull Playback playback) {
        this.context = context;
        this.playback = playback;
        repository = new ManualLyricsRepository(context);
        lrclib = new LrclibLyricsClient(context);
    }

    private java.util.function.Consumer<Boolean> visibilityListener;
    public void setVisibilityListener(java.util.function.Consumer<Boolean> listener) {
        visibilityListener = listener;
    }

    public void setHost(@NonNull FrameLayout host) {
        if (stage == host || overlay == null) return;
        restoreArtworkChildren();
        if (overlay.getParent() instanceof android.view.ViewGroup)
            ((android.view.ViewGroup) overlay.getParent()).removeView(overlay);
        stage = host;
        stage.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
        updateOverlayBounds();
        overlay.setElevation(dp(48));
        overlay.bringToFront();
    }

    // Spotify y otros skins inmersivos usan un FrameLayout a pantalla completa.
    // En modo normal el visor se limita a la parte superior, sin invadir controles.
    public void setImmersiveLimitedHost(boolean limited) {
        immersiveLimitedHost = limited;
        updateOverlayBounds();
    }

    public void setSeekBoundary(@Nullable View seekBar) {
        seekBoundary = seekBar;
        updateOverlayBounds();
    }

    private void updateOverlayBounds() {
        if (overlay == null || stage == null) return;
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -1);
        if (!fullscreenHost && immersiveLimitedHost) {
            // Usar altura real del host, nunca toda la pantalla. Spotify dibuja
            // el seek dentro del canvas, en la parte inferior.
            int hostHeight = stage.getHeight() > 0 ? stage.getHeight()
                    : context.getResources().getDisplayMetrics().heightPixels;
            int hostWidth = stage.getWidth() > 0 ? stage.getWidth()
                    : context.getResources().getDisplayMetrics().widthPixels;
            lp.gravity = Gravity.TOP;
            lp.topMargin = systemTopInset() + dp(12);
            // Mismas proporciones que spotifyMetrics() en models.js: la letra ocupa la zona
            // de la portada y termina donde ella termina, antes del título y el artista.
            // Antes usaba el 57 % de la pantalla, que invadía el título en este skin.
            float coverBottom = hostHeight * 0.13f + Math.min(hostWidth * 0.88f, hostHeight * 0.49f);
            lp.height = Math.max(dp(160), Math.round(coverBottom) - lp.topMargin);
        }
        // No ocupar el área táctil de la barra de progreso en modo normal.
        // Medir posiciones reales, no proporciones fijas: los skins inmersivos
        // colocan el progreso a distintas alturas.
        if (!fullscreenHost && seekBoundary != null && seekBoundary.isShown()
                && seekBoundary.getHeight() > 0 && stage.getHeight() > 0) {
            int[] hostAt = new int[2];
            int[] seekAt = new int[2];
            stage.getLocationOnScreen(hostAt);
            seekBoundary.getLocationOnScreen(seekAt);
            int top = lp.topMargin;
            int maximum = seekAt[1] - hostAt[1] - top - dp(12);
            if (maximum > dp(80)) {
                lp.height = lp.height == -1 ? maximum : Math.min(lp.height, maximum);
            }
        }
        // En el host nativo el overlay ocupa solo la zona ya disponible;
        // no desplazar ni cubrir la barra de progreso.
        overlay.setLayoutParams(lp);
    }

    public void setFullscreenHost(boolean fullscreen) {
        fullscreenHost = fullscreen;
        updateOverlayBounds();
        // La raíz de Reproduciendo se dibuja detrás de la barra de estado.
        // Reservar ese espacio: los botones no deben quedar bajo los iconos del sistema.
        if (viewerColumn != null) {
            viewerColumn.setPadding(dp(12), fullscreen ? systemTopInset() + dp(8) : dp(4),
                    dp(12), fullscreen ? systemBottomInset() + dp(8) : dp(6));
        }
        if (overlay != null) {
            overlay.setElevation(fullscreen ? dp(64) : dp(12));
            if (fullscreen) overlay.bringToFront();
        }
    }

    private int systemTopInset() {
        int resource = context.getResources().getIdentifier("status_bar_height", "dimen", "android");
        return resource > 0 ? context.getResources().getDimensionPixelSize(resource) : dp(28);
    }

    private int systemBottomInset() {
        int resource = context.getResources().getIdentifier("navigation_bar_height", "dimen", "android");
        return resource > 0 ? context.getResources().getDimensionPixelSize(resource) : dp(24);
    }

    public void attach(@NonNull FrameLayout stage) {
        this.stage = stage;
        overlay = new FrameLayout(context);
        overlay.setElevation(dp(12));
        overlay.setVisibility(View.GONE);
        overlay.setBackgroundColor(0xE9151220);
        stage.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout column = new LinearLayout(context);
        viewerColumn = column;
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(12), dp(4), dp(12), dp(6));
        overlay.addView(column, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout bar = new LinearLayout(context);
        bar.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        column.addView(bar, new LinearLayout.LayoutParams(-1, dp(52)));
        // Los controles de edición no ocupan espacio de lectura ni tapan la portada.
        // Un único acceso discreto a las acciones de letras cuando el visor está abierto.
        smallAction(bar, "✎", this::showLyricsActions);
        smallAction(bar, "×", this::hide);
        originLabel = new TextView(context);
        originLabel.setTextSize(10);
        originLabel.setTextColor(0xFFE2DCEA);
        originLabel.setGravity(Gravity.CENTER);
        column.addView(originLabel);
        lyricsScroll = new ScrollView(context);
        lyricsScroll.setFillViewport(true);
        lyricsScroll.setVerticalScrollBarEnabled(false);
        column.addView(lyricsScroll, new LinearLayout.LayoutParams(-1, 0, 1));
        content = new TextView(context);
        content.setTextColor(Color.WHITE);
        content.setTextSize(17);
        content.setGravity(Gravity.CENTER);
        content.setLineSpacing(dp(10), 1.08f);
        content.setPadding(dp(8), dp(18), dp(8), dp(18));
        // El texto debe medir toda su altura: con altura fija al viewport no hay recorrido.
        lyricsScroll.addView(content, new ScrollView.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT));
        // Toque corto sobre la letra = cerrar; arrastrar sigue desplazando.
        // Detectar el gesto en el ScrollView sin consumir eventos de scroll/fling.
        final float[] touchStart = new float[2];
        final long[] touchTime = new long[1];
        final boolean[] dragged = new boolean[1];
        final int touchSlop = android.view.ViewConfiguration.get(context).getScaledTouchSlop();
        lyricsScroll.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    touchStart[0] = event.getX();
                    touchStart[1] = event.getY();
                    touchTime[0] = event.getEventTime();
                    dragged[0] = false;
                    // No bloquear la detección de arrastres horizontales del skin
                    // hasta saber que el usuario está desplazando la letra.
                    break;
                case android.view.MotionEvent.ACTION_MOVE:
                    if (Math.abs(event.getX() - touchStart[0]) > touchSlop
                            || Math.abs(event.getY() - touchStart[1]) > touchSlop)
                        dragged[0] = true;
                    if (dragged[0] && Math.abs(event.getY() - touchStart[1])
                            > Math.abs(event.getX() - touchStart[0]))
                        v.getParent().requestDisallowInterceptTouchEvent(true);
                    break;
                case android.view.MotionEvent.ACTION_UP:
                    v.getParent().requestDisallowInterceptTouchEvent(false);
                    if (!dragged[0] && event.getEventTime() - touchTime[0]
                            <= android.view.ViewConfiguration.getTapTimeout() + 150L) {
                        v.performClick();
                        hide();
                        return true;
                    }
                    break;
                case android.view.MotionEvent.ACTION_CANCEL:
                    v.getParent().requestDisallowInterceptTouchEvent(false);
                    break;
            }
            return false;
        });
        lyricsPrefListener = (p, key) -> { if (key != null && key.startsWith("lyrics_view_")) {
            if (overlay != null) overlay.post(() -> { applyViewerOptions(); refresh(); });
        }};
        AppPreferences.prefs(context).registerOnSharedPreferenceChangeListener(lyricsPrefListener);
        // No capturar los toques del texto: ScrollView debe manejar el arrastre,
        // especialmente cuando las letras son estáticas y el usuario desplaza a mano.
        content.setClickable(false);
    }

    private void smallAction(LinearLayout bar, String text, Runnable action) {
        TextView label = new TextView(context);
        label.setText(text);
        label.setTextColor(0xFFF6F2FF);
        label.setTextSize(22);
        label.setGravity(Gravity.CENTER);
        label.setMinWidth(dp(52));
        label.setMinHeight(dp(48));
        label.setPadding(dp(10), dp(7), dp(10), dp(7));
        bar.addView(label, new LinearLayout.LayoutParams(dp(52), dp(48)));
        label.setOnClickListener(v -> action.run());
    }

    private void restoreOriginal() {
        if (songKey == null) return;
        final String key = songKey;
        new MaterialAlertDialogBuilder(context).setTitle("¿Volver a la letra original?")
                .setMessage("Se borra la letra guardada en la app y se vuelve a leer la del archivo o la descargada.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Volver", (d, w) -> io.execute(() -> {
                    try { repository.delete(key); } catch (Exception ignored) { }
                    if (stage != null) stage.post(() -> { if (key.equals(songKey)) reloadCurrent(); });
                })).show();
    }

    private void reloadCurrent() {
        final String key = songKey;
        if (key == null) return;
        lyrics = null; lyricsOrigin = ""; lastLine = -2; lastStaticScrollKey = null;
        refresh();
        loadAsync(key);
    }

    private void showLyricsActions() {
        new MaterialAlertDialogBuilder(context)
                .setTitle("Opciones de letra")
                .setItems(new String[]{"Buscar letra", "Editar letra", "Volver a la letra original"}, (dialog, which) -> {
                    if (which == 0) lookupManual();
                    else if (which == 1) edit();
                    else restoreOriginal();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    public void setCurrentSong(@Nullable Song current) {
        song = current;
        setSong(current == null ? null : current.getMediaKey());
    }

    private void lookupManual() {
        Song requested = song;
        if (requested == null) return;
        // Preferencia exclusiva de la búsqueda manual: al activarla, presentar
        // primero las alternativas por título sin aceptar una coincidencia automática.
        boolean chooseFirst = com.appincreible.musicplayer.themes.AppPreferences.prefs(context)
                .getBoolean("lyrics_manual_choose_results_first", false);
        if (chooseFirst) {
            Runnable choose = () -> showManualChoices(requested);
            if (lyrics != null && !lyrics.lines.isEmpty()) {
                new MaterialAlertDialogBuilder(context)
                        .setTitle("¿Reemplazar la letra existente?")
                        .setMessage("Podés elegir otra letra de LRCLIB para esta canción.")
                        .setNegativeButton("Cancelar", null)
                        .setPositiveButton("Ver resultados", (dialog, which) -> choose.run())
                        .show();
            } else choose.run();
            return;
        }
        Runnable task = () -> {
            toast("Buscando letra en LRCLIB…");
            io.execute(() -> {
                LrclibLyricsClient.Result result = lrclib.lookup(requested, true);
                if (stage == null) return;
                stage.post(() -> {
                    if (!requested.getMediaKey().equals(songKey)) return;
                    if (result.status == LrclibLyricsClient.Status.FOUND) {
                        lyrics = result.lyrics;
                        lyricsOrigin = "Letra: LRCLIB";
                        refresh();
                    } else if (result.status == LrclibLyricsClient.Status.INSTRUMENTAL) {
                        lyrics = null; lyricsOrigin = "LRCLIB"; content.setText("Canción instrumental");
                    } else if (result.status == LrclibLyricsClient.Status.NOT_FOUND) {
                        showManualChoices(requested);
                    } else toast("No se pudo buscar");
                });
            });
        };
        if (lyrics != null && !lyrics.lines.isEmpty())
            new MaterialAlertDialogBuilder(context).setTitle("¿Reemplazar la letra existente?")
                    .setMessage("La búsqueda puede reemplazar la letra guardada en la app.")
                    .setNegativeButton("Cancelar", null)
                    .setPositiveButton("Buscar", (dialog, which) -> task.run()).show();
        else task.run();
    }

    /** Si la coincidencia automática falla, ofrecer opciones por título sin descargarlas todavía. */
    private void showManualChoices(@NonNull Song requested) {
        toast("Buscando otras versiones por título…");
        io.execute(() -> {
            java.util.List<LrclibLyricsClient.Choice> choices;
            try { choices = lrclib.searchChoices(requested); }
            catch (Exception e) {
                if (stage != null) stage.post(() -> toast("No se pudo buscar"));
                return;
            }
            if (stage == null) return;
            stage.post(() -> {
                if (!requested.getMediaKey().equals(songKey)) return;
                if (choices.isEmpty()) {
                    toast("No se encontraron otras versiones en LRCLIB");
                    return;
                }
                showChoicesDialog(requested, choices);
            });
        });
    }

    /** Resultados separados: evita el texto corrido del setItems de Android. */
    private void showChoicesDialog(@NonNull Song requested,
                                   @NonNull java.util.List<LrclibLyricsClient.Choice> choices) {
        UiPalette palette = UiPalette.fallback(context);
        LinearLayout list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(12), dp(8), dp(12), dp(8));
        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(false);
        scroll.addView(list);
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setTitle("Elegí la letra correcta")
                .setView(scroll)
                .setNegativeButton("Cancelar", null)
                .create();
        for (LrclibLyricsClient.Choice choice : choices) {
            LinearLayout card = new LinearLayout(context);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(16), dp(14), dp(16), dp(14));
            GradientDrawable shape = new GradientDrawable();
            shape.setColor(palette.surface);
            shape.setCornerRadius(dp(16));
            shape.setStroke(dp(1), palette.accent);
            card.setBackground(shape);
            card.setClickable(true);
            card.setFocusable(true);
            card.setContentDescription(choice.displayLabel());
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2);
            cardParams.bottomMargin = dp(10);
            list.addView(card, cardParams);

            TextView title = new TextView(context);
            title.setText(choice.title.isEmpty() ? "Sin título" : choice.title);
            title.setTextSize(17);
            title.setTypeface(null, Typeface.BOLD);
            title.setTextColor(palette.onSurface);
            title.setMaxLines(3);
            card.addView(title);

            TextView artist = new TextView(context);
            artist.setText(choice.artist.isEmpty() ? "Artista desconocido" : choice.artist);
            artist.setTextSize(14);
            artist.setTextColor(palette.onSurface);
            LinearLayout.LayoutParams artistParams = new LinearLayout.LayoutParams(-1, -2);
            artistParams.topMargin = dp(5);
            card.addView(artist, artistParams);

            String duration = String.format(java.util.Locale.ROOT, "%d:%02d",
                    choice.durationSeconds / 60, choice.durationSeconds % 60);
            TextView details = new TextView(context);
            details.setText(duration + "   ·   " + (choice.instrumental ? "Instrumental"
                    : choice.synced ? "Sincronizada" : "Sin tiempos"));
            details.setTextColor(palette.onSurface);
            details.setTextSize(13);
            LinearLayout.LayoutParams detailsParams = new LinearLayout.LayoutParams(-1, -2);
            detailsParams.topMargin = dp(8);
            card.addView(details, detailsParams);

            if (!choice.album.isEmpty()) {
                TextView album = new TextView(context);
                album.setText("Álbum: " + choice.album);
                album.setTextColor(palette.onSurface);
                album.setTextSize(12);
                album.setMaxLines(2);
                LinearLayout.LayoutParams albumParams = new LinearLayout.LayoutParams(-1, -2);
                albumParams.topMargin = dp(5);
                card.addView(album, albumParams);
            }
            card.setOnClickListener(v -> {
                dialog.dismiss();
                confirmChoice(requested, choice);
            });
        }
        dialog.show();
        android.view.Window window = dialog.getWindow();
        if (window != null) window.setLayout(
                (int) (context.getResources().getDisplayMetrics().widthPixels * 0.92f),
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private void confirmChoice(@NonNull Song requested, @NonNull LrclibLyricsClient.Choice choice) {
        new MaterialAlertDialogBuilder(context)
                .setTitle("¿Usar esta versión?")
                .setMessage(choice.displayLabel() + "\n\nLa letra se guardará solo para esta canción y reemplazará su caché interna.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Guardar letra", (dialog, which) -> {
                    toast("Descargando letra elegida…");
                    io.execute(() -> {
                        LrclibLyricsClient.Result result = lrclib.selectChoice(requested, choice);
                        if (stage == null) return;
                        stage.post(() -> {
                            if (!requested.getMediaKey().equals(songKey)) return;
                            if (result.status == LrclibLyricsClient.Status.FOUND) {
                                lyrics = result.lyrics;
                                lyricsOrigin = "Letra: LRCLIB (elegida)";
                                refresh();
                                toast("Letra guardada");
                            } else if (result.status == LrclibLyricsClient.Status.INSTRUMENTAL) {
                                lyrics = null;
                                lyricsOrigin = "Instrumental: LRCLIB";
                                refresh();
                            } else toast("No se pudo descargar la versión elegida");
                        });
                    });
                }).show();
    }

    public void setSong(@Nullable String key) {
        if (key == null) song = null;
        if (java.util.Objects.equals(songKey, key)) return;
        songKey = key;
        lastStaticScrollKey = null;
        lyrics = null;
        lyricsOrigin = "";
        lastLine = -2;
        hide();
        if (key == null) return;
        loadAsync(key);
    }

    private void loadAsync(final String key) {
        io.execute(() -> {
            LrcLyrics loaded = null;
            String origin = "";
            try {
                loaded = repository.load(key);
                if (loaded != null) origin = "Letra guardada en la app";
                if (loaded == null && key.startsWith("song:")) {
                    Uri audioUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                            Long.parseLong(key.substring(5)));
                    loaded = EmbeddedLyricsReader.read(context, audioUri);
                    if (loaded != null) origin = "Letra incrustada en el archivo";
                }
            } catch (Exception ignored) { }
            if (loaded == null && song != null && key.equals(song.getMediaKey())
                    && com.appincreible.musicplayer.themes.AppPreferences.prefs(context)
                        .getBoolean(LrclibLyricsClient.KEY_AUTO, false)
                    && !com.appincreible.musicplayer.power.PowerSaverManager.isActive(context)
                    && LrclibLyricsClient.MODE_VIEWER.equals(
                        com.appincreible.musicplayer.themes.AppPreferences.prefs(context)
                        .getString(LrclibLyricsClient.KEY_AUTO_MODE, LrclibLyricsClient.MODE_VIEWER))) {
                LrclibLyricsClient.Result remote = lrclib.lookup(song, false);
                if (remote.status == LrclibLyricsClient.Status.FOUND) {
                    loaded = remote.lyrics; origin = "Letra: LRCLIB";
                } else if (remote.status == LrclibLyricsClient.Status.INSTRUMENTAL) {
                    origin = "Instrumental: LRCLIB";
                }
            }
            final LrcLyrics result = loaded;
            final String source = origin;
            if (stage != null) stage.post(() -> {
                if (key.equals(songKey)) { lyrics = result; lyricsOrigin = source; refresh(); }
            });
        });
    }

    // Keep the original visibility only once. Never restore the artwork during a
    // refresh: doing so briefly exposes it again after seeking or Glide callbacks.
    private void restoreArtworkChildren() {
        for (java.util.Map.Entry<View, Integer> entry : artworkVisibility.entrySet())
            if (entry.getKey().getParent() != null) entry.getKey().setVisibility(entry.getValue());
        artworkVisibility.clear();
    }

    public boolean isTransparentVisible() {
        return isVisible() && "transparent".equals(AppPreferences.prefs(context)
                .getString("lyrics_view_background", "artwork"));
    }

    public void enforceTransparentArtworkHidden() {
        if (fullscreenHost || !isTransparentVisible() || stage == null) return;
        // The canvas/WebView contains visualizers as well as artwork. Keep it alive;
        // its artwork is disabled separately through Player2D.setVisibility().
        for (int i = 0; i < stage.getChildCount(); i++) {
            View child = stage.getChildAt(i);
            // El host puede ser la raíz del reproductor inmersivo. No ocultar
            // contenedores: incluyen el WebView de Spotify, progreso y controles.
            // Solo las imágenes nativas independientes representan la portada.
            if (child == overlay || !(child instanceof android.widget.ImageView)) continue;
            if (!artworkVisibility.containsKey(child))
                artworkVisibility.put(child, child.getVisibility());
            child.setVisibility(View.INVISIBLE);
        }
    }

    private void updateArtworkChildren() {
        if (fullscreenHost) restoreArtworkChildren();
        else if (isTransparentVisible()) enforceTransparentArtworkHidden();
        else restoreArtworkChildren();
    }

    public void ensureFullscreenOnTop() {
        if (fullscreenHost && overlay != null && isVisible()) {
            overlay.bringToFront();
            overlay.setElevation(dp(64));
        }
    }

    public boolean isVisible() { return overlay != null && overlay.getVisibility() == View.VISIBLE; }
    public void show() {
        if (songKey == null || overlay == null) return;
        // Nunca redimensionar el host. Cambiar su altura desplaza el canvas y
        // modifica las coordenadas de seek incluso después de cerrar Letras.
        updateOverlayBounds();
        // Recalcular tras el layout para que seekBar y overlay usen coordenadas
        // definitivas incluso después de cambiar de skin.
        overlay.post(() -> { if (isVisible() && !fullscreenHost) updateOverlayBounds(); });
        overlay.bringToFront();
        overlay.setElevation(fullscreenHost ? dp(64) : dp(12));
        overlay.setVisibility(View.VISIBLE);
        // El WebView inmersivo (Spotify) puede reorganizarse al recibir un seek.
        // Reafirmar el orden después de la pasada de layout.
        if (fullscreenHost) overlay.post(() -> {
            if (isVisible() && fullscreenHost) overlay.bringToFront();
        });
        applyViewerOptions(); refresh();
        updateArtworkChildren();
        if (visibilityListener != null) visibilityListener.accept(true);
    }
    public void hide() {
        restoreArtworkChildren();
        if (overlay != null) overlay.setVisibility(View.GONE);
        // Al cerrar solo se quita el overlay: el layout original nunca cambió.
        if (visibilityListener != null) visibilityListener.accept(false);
    }
    public void toggle() { if (isVisible()) hide(); else show(); }
    public void tick(long positionMs) {
        if (!isVisible() || lyrics == null || !lyrics.synchronizedLines || content == null) return;
        int active = lyrics.activeLineAt(positionMs, 0);
        SharedPreferences viewPrefs = AppPreferences.prefs(context);
        String mode = viewPrefs.getString("lyrics_view_mode", "full");
        if ("cover".equals(mode)) return;
        if (active == lastLine) return;
        // Solo reconstruir el texto cuando cambia la línea, nunca cada frame.
        lastLine = active;
        android.text.SpannableStringBuilder builder = new android.text.SpannableStringBuilder();
        for (int i = 0; i < lyrics.lines.size(); i++) {
            if ("single".equals(mode) && i != active) continue;
            int start = builder.length();
            builder.append(lyrics.lines.get(i).text).append('\n');
            builder.setSpan(new android.text.style.ForegroundColorSpan(i == active ? currentHighlightColor() : dimTextColor()),
                    start, builder.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (i == active) builder.setSpan(new android.text.style.StyleSpan(Typeface.BOLD),
                    start, builder.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        content.setText(builder);
        if (active >= 0 && !"single".equals(mode)) {
            int offset = 0;
            for (int i = 0; i < active; i++) offset += lyrics.lines.get(i).text.length() + 1;
            scheduleCenter(Math.min(offset, content.getText().length()), active, songKey);
        }
    }

    /** Ejecuta la acción cuando el ScrollView ya terminó de medirse y colocarse. */
    private void afterNextLayout(final Runnable action) {
        if (lyricsScroll == null) return;
        lyricsScroll.getViewTreeObserver().addOnGlobalLayoutListener(
                new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override public void onGlobalLayout() {
                        android.view.ViewTreeObserver current =
                                lyricsScroll == null ? null : lyricsScroll.getViewTreeObserver();
                        if (current != null && current.isAlive()) current.removeOnGlobalLayoutListener(this);
                        action.run();
                    }
                });
        // Garantiza una pasada de layout aunque la vista ya estuviera estable.
        lyricsScroll.requestLayout();
    }

    /**
     * Centra la línea activa en el visor. Espera a que el visor tenga tamaño y relleno
     * definitivos: antes se intentaba desplazar sin haber medido, y el scroll quedaba
     * limitado al rango viejo, dejando la línea activa abajo, fuera de la vista.
     */
    private void scheduleCenter(final int offset, final int line, final String song) {
        if (lyricsScroll == null || content == null) return;
        final Runnable center = new Runnable() {
            @Override public void run() {
                if (!isVisible() || content == null || lyricsScroll == null
                        || !java.util.Objects.equals(song, songKey) || line != lastLine) return;
                android.text.Layout measured = content.getLayout();
                if (lyricsScroll.getHeight() <= 0 || measured == null) return;
                int visualLine = measured.getLineForOffset(Math.min(offset, content.length()));
                int lineHalf = Math.max(1,
                        (measured.getLineBottom(visualLine) - measured.getLineTop(visualLine)) / 2);
                // Relleno arriba y abajo para que la primera y la última línea también lleguen al centro.
                int edge = Math.max(dp(18), lyricsScroll.getHeight() / 2 - lineHalf);
                if (content.getPaddingTop() != edge || content.getPaddingBottom() != edge) {
                    content.setPadding(dp(8), edge, dp(8), edge);
                    afterNextLayout(this); // medir con el relleno nuevo antes de desplazar
                    return;
                }
                centerActiveLine(offset);
            }
        };
        if (lyricsScroll.getHeight() <= 0 || lyricsScroll.isLayoutRequested()) afterNextLayout(center);
        else center.run();
    }

    private void centerActiveLine(int offset) {
        if (!isVisible() || content == null || lyricsScroll == null) return;
        android.text.Layout layout = content.getLayout();
        if (layout == null) return;
        int line = layout.getLineForOffset(Math.min(offset, content.length()));
        int middle = (layout.getLineTop(line) + layout.getLineBottom(line)) / 2;
        // El texto empieza después del relleno superior. Sin sumarlo, la línea activa
        // quedaba media pantalla más abajo de lo debido y no se veía.
        int y = Math.max(0, content.getTop() + content.getPaddingTop() + middle - lyricsScroll.getHeight() / 2);
        if (PowerSaverManager.isActive(context)) lyricsScroll.scrollTo(0, y);
        else lyricsScroll.smoothScrollTo(0, y);
    }

    private void refresh() {
        if (content == null) return;
        applyViewerOptions();
        if (originLabel != null) {
            boolean noTimes = lyrics != null && !lyrics.lines.isEmpty() && !lyrics.synchronizedLines;
            // Los avisos sobre letras guardadas dentro de la app no aportan al visor.
            // Se conserva la indicación de LRCLIB y el aviso de letra sin tiempos.
            boolean internalLyrics = lyricsOrigin != null && lyricsOrigin.equals("Letra guardada en la app");
            originLabel.setVisibility(internalLyrics ? View.GONE : View.VISIBLE);
            originLabel.setText(internalLyrics ? "" :
                    (noTimes ? lyricsOrigin + " · sin tiempos (no se resalta)" : lyricsOrigin));
        }
        if ("cover".equals(AppPreferences.prefs(context).getString("lyrics_view_mode", "full"))) {
            content.setText(""); return;
        }
        if (lyrics == null || lyrics.lines.isEmpty()) {
            content.setText("No hay letra para esta canción. Tocá Buscar letra para consultar LRCLIB.");
            return;
        }
        if (lyrics.synchronizedLines) { lastLine = -2; tick(playback.position()); }
        else {
            // Sin marcas temporales no hay línea activa ni seguimiento automático.
            // Mostrar todo el texto con un único color legible y scroll manual.
            StringBuilder text = new StringBuilder();
            for (LrcLyrics.Line line : lyrics.lines) text.append(line.text).append('\n');
            content.setTextColor(0xFFFFFFFF);
            content.setPadding(dp(8), dp(18), dp(8), dp(18));
            content.setText(text.toString());
            lyricsScroll.setSmoothScrollingEnabled(false);
            // Reiniciar solo al cargar otra letra; no forzar la posición en cada
            // cambio de opciones ni en refrescos mientras se está desplazando.
            if (!java.util.Objects.equals(lastStaticScrollKey, songKey)) {
                lastStaticScrollKey = songKey;
                lyricsScroll.post(() -> lyricsScroll.scrollTo(0, 0));
            }
        }
    }
    private int currentHighlightColor() {
        SharedPreferences p = AppPreferences.prefs(context);
        int option = p.getInt("lyrics_view_color", 0);
        if (option == 1) return 0xFFFFD76A;
        if (option == 2) return 0xFFB9A1FF;
        if (option == 3) return 0xFF7CE8CE;
        return 0xFFFFFFFF;
    }
    private int dimTextColor() {
        int opacity = AppPreferences.prefs(context).getInt("lyrics_view_dim", 65);
        int alpha = Math.max(115, Math.round(255 * (100 - opacity) / 100f));
        return (alpha << 24) | 0xFFFFFF;
    }
    private void applyViewerOptions() {
        if (content == null || overlay == null) return;
        SharedPreferences p = AppPreferences.prefs(context);
        content.setTextSize(p.getInt("lyrics_view_font", 22));
        String font = p.getString("lyrics_view_typeface", "default");
        if ("serif".equals(font)) content.setTypeface(Typeface.SERIF);
        else if ("mono".equals(font)) content.setTypeface(Typeface.MONOSPACE);
        else if ("light".equals(font)) content.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        else content.setTypeface(Typeface.DEFAULT);
        // En letras sin tiempos nunca centrar verticalmente: la primera estrofa
        // debe quedar arriba y el usuario debe poder llegar a la última.
        boolean centered = p.getBoolean("lyrics_view_center", true);
        content.setGravity((centered ? Gravity.CENTER_HORIZONTAL : Gravity.START) | Gravity.TOP);
        content.setLineSpacing(dp(10), 1.05f);
        boolean saver = PowerSaverManager.isActive(context);
        boolean onlyCover = "cover".equals(p.getString("lyrics_view_mode", "full"));
        String background = p.getString("lyrics_view_background", "artwork");
        boolean transparent = "transparent".equals(background);
        overlay.setBackgroundColor(fullscreenHost ? 0xFF13101C : onlyCover || transparent ? Color.TRANSPARENT :
                "solid".equals(background) ? 0xFF13101C : (saver ? 0xF013101C : 0xDD13101C));
        // Sobre skins con movimiento, la sombra mantiene el texto legible sin dibujar un panel.
        if (transparent) content.setShadowLayer(dp(3), 0, dp(1), 0xFF000000);
        else content.setShadowLayer(0, 0, 0, Color.TRANSPARENT);
        if (isVisible()) updateArtworkChildren();
        if (lyrics != null && !lyrics.synchronizedLines) content.setTextColor(Color.WHITE);
    }

    private void edit() {
        if (songKey == null) return;
        EditText editor = new EditText(context);
        editor.setMinLines(8);
        editor.setGravity(Gravity.TOP);
        editor.setHint("Una línea por renglón. Conservá las marcas [mm:ss.xx] para mantener la sincronización");
        editor.setText(lyrics == null ? "" : toEditableText(lyrics));
        new MaterialAlertDialogBuilder(context).setTitle("Editar letra")
                .setView(editor).setNegativeButton("Cancelar", null)
                .setPositiveButton("Guardar", (d, w) -> save(editor.getText().toString())).show();
    }
    /** Texto editable que conserva las marcas de tiempo, para no perder la sincronización al guardar. */
    private static String toEditableText(LrcLyrics source) {
        StringBuilder out = new StringBuilder();
        for (LrcLyrics.Line line : source.lines) {
            if (source.synchronizedLines && line.timeMs >= 0) {
                long t = line.timeMs;
                out.append(String.format(java.util.Locale.US, "[%02d:%02d.%02d]",
                        t / 60000, (t / 1000) % 60, (t % 1000) / 10));
            }
            out.append(line.text).append('\n');
        }
        return out.toString();
    }
    private void save(String text) {
        if (songKey == null) return;
        Runnable action = () -> {
            final String key = songKey;
            io.execute(() -> {
                try {
                    LrcLyrics result = repository.saveText(key, text, true);
                    if (stage != null) stage.post(() -> { if (key.equals(songKey)) { lyrics = result; lyricsOrigin = "Letra guardada en la app"; refresh(); toast("Letra guardada"); } });
                } catch (IOException e) { if (stage != null) stage.post(() -> toast("No se pudo guardar la letra")); }
            });
        };
        if (lyrics != null && !lyrics.lines.isEmpty()) {
            boolean losesTiming = lyrics.synchronizedLines && !LrcParser.parse(text).synchronizedLines;
            MaterialAlertDialogBuilder confirm = new MaterialAlertDialogBuilder(context)
                    .setTitle("¿Reemplazar letra existente?")
                    .setNegativeButton("Cancelar", null).setPositiveButton("Reemplazar", (d, w) -> action.run());
            if (losesTiming) confirm.setMessage("La letra nueva no tiene marcas de tiempo: se perderá el resaltado sincronizado.");
            confirm.show();
        } else action.run();
    }
    private void synchronize() {
        if (songKey == null || lyrics == null || lyrics.lines.isEmpty()) { toast("Primero escribí una letra"); return; }
        StringBuilder plain = new StringBuilder();
        for (LrcLyrics.Line line : lyrics.lines) plain.append(line.text).append('\n');
        ManualLyricsSession session = new ManualLyricsSession(plain.toString());
        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(16), dp(12), dp(16), dp(10));
        TextView line = new TextView(context);
        line.setTextSize(22);
        line.setGravity(Gravity.CENTER);
        panel.addView(line, new LinearLayout.LayoutParams(-1, dp(130)));
        Button mark = new Button(context);
        mark.setText("MARCAR INICIO DE LÍNEA");
        panel.addView(mark);
        LinearLayout actions = new LinearLayout(context);
        panel.addView(actions);
        button(actions, "Pausar / seguir", playback::pauseToggle);
        button(actions, "−3 s", playback::rewindThreeSeconds);
        Button undo = new Button(context);
        undo.setText("Deshacer");
        panel.addView(undo);
        final androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setTitle("Sincronizar tocando").setView(panel)
                .setNegativeButton("Cancelar", null).setPositiveButton("Terminar", null).create();
        Runnable update = () -> line.setText(session.isFinished() ? "Todas las líneas marcadas" :
                (session.nextIndex() + 1) + "/" + session.lineCount() + "\n" + session.currentLine());
        mark.setOnClickListener(v -> { if (!session.mark(playback.position())) toast("Tiempo anterior a la marca previa"); update.run(); });
        undo.setOnClickListener(v -> { session.undo(); update.run(); });
        dialog.setOnShowListener(d -> dialog.getButton(-1).setOnClickListener(v -> {
            if (!session.isFinished()) { toast("Faltan líneas por marcar"); return; }
            String result = session.toLrc();
            dialog.dismiss();
            save(result);
        }));
        update.run(); dialog.show();
    }
    private void button(LinearLayout row, String label, Runnable action) {
        Button b = new Button(context);
        b.setAllCaps(false);
        b.setText(label);
        row.addView(b, new LinearLayout.LayoutParams(0, dp(48), 1));
        b.setOnClickListener(v -> action.run());
    }
    private int dp(float value) { return (int) (value * context.getResources().getDisplayMetrics().density + .5f); }
    private void toast(String msg) { Toast.makeText(context, msg, Toast.LENGTH_SHORT).show(); }
    public void importFrom(@NonNull Uri uri) {
        if (songKey == null) return;
        Runnable importAction = () -> {
            String key = songKey;
            io.execute(() -> {
                try {
                    LrcLyrics result = repository.importLrc(key, uri, true);
                    if (stage != null) stage.post(() -> { if (key.equals(songKey)) { lyrics = result; lyricsOrigin = "Letra guardada en la app"; show(); toast("LRC importado"); } });
                } catch (IOException e) { if (stage != null) stage.post(() -> toast("Error al importar LRC")); }
            });
        };
        if (lyrics != null && !lyrics.lines.isEmpty())
            new MaterialAlertDialogBuilder(context).setTitle("¿Reemplazar letra existente?")
                .setNegativeButton("Cancelar", null).setPositiveButton("Reemplazar", (d, w) -> importAction.run()).show();
        else importAction.run();
    }
    public void exportTo(@NonNull Uri uri) {
        if (songKey == null) return;
        String key = songKey;
        io.execute(() -> {
            try { repository.exportLrc(key, uri); if (stage != null) stage.post(() -> toast("LRC exportado")); }
            catch (IOException e) { if (stage != null) stage.post(() -> toast("Error al exportar LRC")); }
        });
    }
    public void release() {
        restoreArtworkChildren();
        if (lyricsPrefListener != null) AppPreferences.prefs(context).unregisterOnSharedPreferenceChangeListener(lyricsPrefListener);
        io.shutdown(); stage = null; overlay = null; content = null;
    }
}
