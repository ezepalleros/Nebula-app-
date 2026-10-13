package com.appincreible.musicplayer.ui.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.appincreible.musicplayer.R;
import com.appincreible.musicplayer.metadata.repository.ArtworkSearchRepository;
import com.appincreible.musicplayer.themes.AppPreferences;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;

/** Online artwork preferences, in the Biblioteca category only. */
final class ArtworkOnlineSettingsController {
    private ArtworkOnlineSettingsController() { }

    static void attach(Context context, View header) {
        SharedPreferences preferences = AppPreferences.prefs(context);
        LinearLayout parent = (LinearLayout) header.getParent();
        MaterialCardView card = new MaterialCardView(context);
        card.setRadius(context.getResources().getDimension(R.dimen.radius_16));
        card.setCardElevation(0);
        card.setCardBackgroundColor(MaterialColors.getColor(card,
                com.google.android.material.R.attr.colorSurfaceContainerLow));
        LinearLayout body = new LinearLayout(context);
        body.setOrientation(LinearLayout.VERTICAL);
        int pad = context.getResources().getDimensionPixelSize(R.dimen.space_16);
        body.setPadding(pad, pad, pad, pad);

        TextView heading = text(context, "PORTADAS ONLINE", com.google.android.material.R.style.TextAppearance_Material3_TitleMedium);
        body.addView(heading);
        toggle(context, body, preferences, "Usar Deezer", "Buscar portadas en Deezer.",
                ArtworkSearchRepository.KEY_DEEZER, true);
        toggle(context, body, preferences, "Usar iTunes", "Buscar portadas también en iTunes.",
                ArtworkSearchRepository.KEY_ITUNES, true);
        toggle(context, body, preferences, "Solo con Wi-Fi", "No buscar con datos móviles.",
                ArtworkSearchRepository.KEY_WIFI, false);
        LinearLayout qualityRow = new LinearLayout(context);
        qualityRow.setOrientation(LinearLayout.VERTICAL);
        qualityRow.setPadding(0, pad, 0, pad);
        TextView qualityLabel = text(context, "Calidad de la imagen", com.google.android.material.R.style.TextAppearance_Material3_BodyLarge);
        TextView qualityValue = text(context, "", com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
        qualityValue.setTextColor(MaterialColors.getColor(qualityValue,
                com.google.android.material.R.attr.colorPrimary));
        Runnable refresh = () -> qualityValue.setText("standard".equals(preferences.getString(
                ArtworkSearchRepository.KEY_QUALITY, "large")) ? "Estándar · hasta 600 px" : "Grande · hasta 1000 px");
        refresh.run();
        qualityRow.addView(qualityLabel);
        qualityRow.addView(qualityValue);
        qualityRow.setClickable(true);
        qualityRow.setOnClickListener(v -> new MaterialAlertDialogBuilder(context)
                .setTitle("Calidad de la portada")
                .setSingleChoiceItems(new String[]{"Grande · hasta 1000 px", "Estándar · hasta 600 px"},
                        "standard".equals(preferences.getString(ArtworkSearchRepository.KEY_QUALITY, "large")) ? 1 : 0,
                        (dialog, which) -> {
                            preferences.edit().putString(ArtworkSearchRepository.KEY_QUALITY,
                                    which == 0 ? "large" : "standard").apply();
                            refresh.run();
                            dialog.dismiss();
                        }).setNegativeButton("Cancelar", null).show());
        body.addView(qualityRow);

        MaterialButton clear = new MaterialButton(context);
        clear.setText("Borrar caché de búsquedas");
        clear.setOnClickListener(v -> {
            ArtworkSearchRepository.clearMemoryCache();
            android.widget.Toast.makeText(context, "Caché de búsquedas borrada", android.widget.Toast.LENGTH_SHORT).show();
        });
        body.addView(clear);
        TextView help = text(context, "Se envían el título y el artista a Deezer e iTunes.",
                com.google.android.material.R.style.TextAppearance_Material3_BodySmall);
        help.setTextColor(MaterialColors.getColor(help,
                com.google.android.material.R.attr.colorOnSurfaceVariant));
        body.addView(help);

        card.addView(body);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(pad, pad, pad, pad);
        parent.addView(card, lp);
    }

    private static void toggle(Context context, LinearLayout body, SharedPreferences prefs,
                               String name, String desc, String key, boolean initial) {
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView title = text(context, name, com.google.android.material.R.style.TextAppearance_Material3_BodyLarge);
        TextView help = text(context, desc, com.google.android.material.R.style.TextAppearance_Material3_BodySmall);
        help.setTextColor(MaterialColors.getColor(help, com.google.android.material.R.attr.colorOnSurfaceVariant));
        texts.addView(title);
        texts.addView(help);
        row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));
        MaterialSwitch toggle = new MaterialSwitch(context);
        toggle.setChecked(prefs.getBoolean(key, initial));
        toggle.setOnCheckedChangeListener((button, enabled) -> prefs.edit().putBoolean(key, enabled).apply());
        row.addView(toggle);
        row.setPadding(0, context.getResources().getDimensionPixelSize(R.dimen.space_12), 0,
                context.getResources().getDimensionPixelSize(R.dimen.space_12));
        row.setOnClickListener(v -> toggle.setChecked(!toggle.isChecked()));
        body.addView(row);
    }

    private static TextView text(Context context, String value, int style) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextAppearance(style);
        return view;
    }
}
