package com.appincreible.musicplayer.ui.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.fragment.app.Fragment;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import com.appincreible.musicplayer.lyrics.LrclibBulkWorker;
import com.appincreible.musicplayer.lyrics.LrclibLyricsClient;
import com.appincreible.musicplayer.themes.AppPreferences;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.List;

/** Sección independiente de letras, sin añadir lógica a SettingsFragment. */
public final class LyricsDownloadSettingsController {
    private LyricsDownloadSettingsController() { }
    public static void attach(Fragment fragment, View header) {
        Context context = fragment.requireContext();
        SharedPreferences prefs = AppPreferences.prefs(context);
        LinearLayout root = (LinearLayout) header.getParent();
        MaterialCardView card = new MaterialCardView(context);
        card.setRadius(dp(context, 18));
        card.setCardBackgroundColor(com.google.android.material.color.MaterialColors.getColor(card, com.google.android.material.R.attr.colorSurfaceContainerLow));
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(context, 18), dp(context, 12), dp(context, 18), dp(context, 14));
        TextView heading = new TextView(context);
        heading.setText("LETRAS · LRCLIB");
        heading.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium);
        column.addView(heading);

        MaterialSwitch auto = new MaterialSwitch(context);
        auto.setText("Buscar letras automáticamente");
        auto.setChecked(prefs.getBoolean(LrclibLyricsClient.KEY_AUTO, false));
        column.addView(auto);
        MaterialSwitch wifi = new MaterialSwitch(context);
        wifi.setText("Solo con Wi-Fi");
        wifi.setChecked(prefs.getBoolean(LrclibLyricsClient.KEY_WIFI_ONLY, true));
        wifi.setOnCheckedChangeListener((v, value) -> prefs.edit().putBoolean(LrclibLyricsClient.KEY_WIFI_ONLY, value).apply());
        column.addView(wifi);
        setDependentEnabled(wifi, auto.isChecked());
        auto.setOnCheckedChangeListener((v, value) -> {
            prefs.edit().putBoolean(LrclibLyricsClient.KEY_AUTO, value).apply();
            setDependentEnabled(wifi, value);
        });

        TextView automaticHint = hint(context, "Busca al abrir una canción sin letra; solo usa Wi-Fi si lo elegís.");
        column.addView(automaticHint);

        MaterialSwitch manualChoice = new MaterialSwitch(context);
        manualChoice.setText("Mostrar resultados al buscar");
        manualChoice.setChecked(prefs.getBoolean("lyrics_manual_choose_results_first", false));
        manualChoice.setOnCheckedChangeListener((v, enabled) -> prefs.edit()
                .putBoolean("lyrics_manual_choose_results_first", enabled).apply());
        column.addView(manualChoice);
        column.addView(hint(context, "Apagado: elige la mejor opción. Encendido: vos elegís."));

        MaterialButton search = new MaterialButton(context);
        search.setText("Buscar letras faltantes");
        column.addView(search);
        column.addView(hint(context, "Revisa las canciones sin letra, con progreso y cancelar."));
        search.setOnClickListener(v -> new MaterialAlertDialogBuilder(context)
                .setTitle("Buscar letras de toda la biblioteca")
                .setMessage("Se abrirá el progreso por canción. La búsqueda manual funciona con Wi-Fi o datos móviles.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Buscar", (d, which) -> fragment.startActivity(
                        new android.content.Intent(context, com.appincreible.musicplayer.lyrics.LrclibScanActivity.class)))
                .show());
        card.addView(column);
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(-1, -2);
        layout.setMargins(dp(context, 14), dp(context, 16), dp(context, 14), dp(context, 12));
        root.addView(card, layout);
    }
    private static TextView hint(Context context, String text) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall);
        view.setPadding(0, 0, 0, dp(context, 10));
        return view;
    }
    private static void setDependentEnabled(View view, boolean enabled) {
        view.setEnabled(enabled);
        view.setAlpha(enabled ? 1f : 0.45f);
    }
    private static int dp(Context c, int size) { return Math.round(size * c.getResources().getDisplayMetrics().density); }
}
