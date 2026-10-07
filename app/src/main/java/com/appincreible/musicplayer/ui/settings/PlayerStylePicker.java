package com.appincreible.musicplayer.ui.settings;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.net.Uri;
import android.view.View;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.appincreible.musicplayer.themes.AppPreferences;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Locale;

/** Keeps the style-picker WebView/dialog single-instance and lifecycle-safe. */
final class PlayerStylePicker {

    private AlertDialog dialog;

    @SuppressLint("SetJavaScriptEnabled")
    void show(Fragment fragment, Runnable onApplied) {
        if (!fragment.isAdded() || (dialog != null && dialog.isShowing())) return;
        Context context = fragment.requireContext();
        WebView web = new WebView(context);
        web.setBackgroundColor(Color.TRANSPARENT);
        web.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(false);
        settings.setDomStorageEnabled(false);
        settings.setBlockNetworkLoads(true);

        boolean[] selectionHandled = {false};
        web.setWebViewClient(new WebViewClient() {
            private boolean handle(Uri uri) {
                if (!"app".equals(uri.getScheme()) || !"select".equals(uri.getHost())) return false;
                if (selectionHandled[0]) return true;
                String skin = uri.getQueryParameter("skin");
                if (!isAllowedSkin(skin) || !fragment.isAdded()) return true;
                selectionHandled[0] = true;
                AppPreferences.setPlayerStyle(context, skin);
                dismiss();
                if (fragment.isAdded()) onApplied.run();
                return true;
            }

            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (handle(uri)) return true;
                return !("file".equals(uri.getScheme()) && uri.getPath() != null
                        && uri.getPath().startsWith("/android_asset/"));
            }

            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handle(Uri.parse(url)) || !url.startsWith("file:///android_asset/");
            }
        });

        int accent = AppPreferences.getPlayerAccentColor(context);
        String hex = String.format(Locale.US, "%06X", accent & 0xFFFFFF);
        String current = AppPreferences.getPlayerStyle(context);
        web.loadUrl("file:///android_asset/player3d/picker.html?selected="
                + Uri.encode(current) + "&accent=" + hex);

        dialog = new MaterialAlertDialogBuilder(context)
                .setTitle("Estilo del reproductor")
                .setView(web)
                .setNegativeButton("Cancelar", null)
                .create();
        dialog.setOnDismissListener(d -> {
            web.loadUrl("about:blank");
            web.stopLoading();
            web.destroy();
            dialog = null;
        });
        dialog.show();
    }

    void dismiss() {
        if (dialog != null) dialog.dismiss();
    }

    private static boolean isAllowedSkin(String skin) {
        return AppPreferences.STYLE_PIXEL.equals(skin)
                || AppPreferences.STYLE_CASSETTE.equals(skin)
                || AppPreferences.STYLE_GAMEBOY_3D.equals(skin)
                || AppPreferences.STYLE_WALKMAN.equals(skin)
                || AppPreferences.STYLE_RETRO.equals(skin)
                || AppPreferences.STYLE_VINYL.equals(skin)
                || AppPreferences.STYLE_CLASSIC.equals(skin)
                || AppPreferences.STYLE_NEON.equals(skin)
                || AppPreferences.STYLE_GLASS.equals(skin)
                || AppPreferences.STYLE_MINIMAL.equals(skin)
                || AppPreferences.STYLE_SPOTIFY_2D.equals(skin)
                || AppPreferences.STYLE_EQUALIZER_2D.equals(skin)
                || AppPreferences.STYLE_RING.equals(skin);
    }
}
