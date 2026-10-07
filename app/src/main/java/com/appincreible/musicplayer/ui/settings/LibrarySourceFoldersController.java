package com.appincreible.musicplayer.ui.settings;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.appincreible.musicplayer.R;
import com.appincreible.musicplayer.themes.AppPreferences;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class LibrarySourceFoldersController {

    private final Context appContext;
    private final LinearLayout container;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean destroyed;

    LibrarySourceFoldersController(Context context, LinearLayout container) {
        this.appContext = context.getApplicationContext();
        this.container = container;
    }

    void refresh() {
        executor.execute(() -> {
            List<FolderItem> folders = loadFolders();
            mainHandler.post(() -> {
                if (!destroyed) render(folders);
            });
        });
    }

    void destroy() {
        destroyed = true;
        executor.shutdownNow();
        mainHandler.removeCallbacksAndMessages(null);
    }

    private List<FolderItem> loadFolders() {
        Map<String, FolderItem> unique = new LinkedHashMap<>();
        ContentResolver resolver = appContext.getContentResolver();
        String pathColumn = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                ? MediaStore.Audio.Media.RELATIVE_PATH : MediaStore.Audio.Media.DATA;
        String[] projection = { pathColumn };
        String selection = MediaStore.Audio.Media.IS_MUSIC + " != 0";
        boolean hideWhatsApp = AppPreferences.hideWhatsAppAudio(appContext);

        try (Cursor cursor = resolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection, selection, null, MediaStore.Audio.Media.DATE_ADDED + " DESC")) {
            if (cursor == null) return new ArrayList<>();
            int pathIndex = cursor.getColumnIndexOrThrow(pathColumn);
            while (cursor.moveToNext()) {
                String raw = cursor.getString(pathIndex);
                if (hideWhatsApp && isWhatsAppAudioPath(raw)) continue;
                String key = folderKey(raw);
                if (key.isEmpty() || unique.containsKey(key)) continue;
                String display = folderDisplay(raw);
                unique.put(key, new FolderItem(key, folderName(display), display));
            }
        }
        return new ArrayList<>(unique.values());
    }

    private void render(List<FolderItem> folders) {
        container.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(container.getContext());
        Set<String> disabled = AppPreferences.getDisabledMusicSourceFolders(container.getContext());

        if (folders.isEmpty()) {
            View row = inflater.inflate(R.layout.item_library_source_folder, container, false);
            TextView title = row.findViewById(R.id.folderTitle);
            TextView path = row.findViewById(R.id.folderPath);
            MaterialSwitch toggle = row.findViewById(R.id.folderEnabledSwitch);
            title.setText("No se encontraron carpetas");
            path.setText("MediaStore no devolvió carpetas de música.");
            toggle.setVisibility(View.GONE);
            container.addView(row);
            return;
        }

        for (FolderItem folder : folders) {
            View row = inflater.inflate(R.layout.item_library_source_folder, container, false);
            TextView title = row.findViewById(R.id.folderTitle);
            TextView path = row.findViewById(R.id.folderPath);
            MaterialSwitch toggle = row.findViewById(R.id.folderEnabledSwitch);
            title.setText(folder.name);
            path.setText(folder.path);
            toggle.setChecked(!disabled.contains(folder.key));
            toggle.setOnCheckedChangeListener((button, checked) ->
                    AppPreferences.setMusicSourceFolderEnabled(container.getContext(), folder.key, checked));
            container.addView(row);
        }
    }

    private String folderKey(String rawPath) {
        if (rawPath == null || rawPath.trim().isEmpty()) return "";
        String normalized = rawPath.replace('\\', '/').trim();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            int slash = normalized.lastIndexOf('/');
            if (slash > 0) normalized = normalized.substring(0, slash);
        }
        while (normalized.endsWith("/")) normalized = normalized.substring(0, normalized.length() - 1);
        return normalized.toLowerCase(Locale.ROOT);
    }

    private String folderDisplay(String rawPath) {
        if (rawPath == null || rawPath.trim().isEmpty()) return "Carpeta desconocida";
        String display = rawPath.replace('\\', '/').trim();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            int slash = display.lastIndexOf('/');
            if (slash > 0) display = display.substring(0, slash);
        }
        while (display.endsWith("/")) display = display.substring(0, display.length() - 1);
        return display;
    }

    private String folderName(String displayPath) {
        int slash = displayPath.lastIndexOf('/');
        return slash >= 0 && slash < displayPath.length() - 1
                ? displayPath.substring(slash + 1) : displayPath;
    }

    private boolean isWhatsAppAudioPath(String rawPath) {
        if (rawPath == null || rawPath.trim().isEmpty()) return false;
        String path = rawPath.replace('\\', '/').toLowerCase(Locale.ROOT);
        return path.contains("whatsapp/media/whatsapp audio/")
                || path.contains("whatsapp/media/whatsapp voice notes/")
                || path.contains("whatsapp business/media/whatsapp business audio/")
                || path.contains("whatsapp business/media/whatsapp business voice notes/");
    }

    private static final class FolderItem {
        final String key;
        final String name;
        final String path;
        FolderItem(String key, String name, String path) {
            this.key = key;
            this.name = name;
            this.path = path;
        }
    }
}
