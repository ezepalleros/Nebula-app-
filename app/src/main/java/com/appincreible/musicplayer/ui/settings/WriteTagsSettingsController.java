package com.appincreible.musicplayer.ui.settings;

import android.content.Context;

import com.appincreible.musicplayer.themes.AppPreferences;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;

/** Enable file writes only after the user explicitly confirms the risk. */
final class WriteTagsSettingsController {
    private WriteTagsSettingsController() { }

    static void attach(Context context, MaterialCardView card, MaterialSwitch toggle) {
        final boolean[] updating = {false};
        final Runnable syncFromPrefs = () -> {
            updating[0] = true;
            toggle.setChecked(AppPreferences.writeTagsToFiles(context));
            updating[0] = false;
        };
        syncFromPrefs.run();

        toggle.setOnCheckedChangeListener((button, checked) -> {
            if (updating[0]) return;
            if (!checked) {
                AppPreferences.prefs(context).edit()
                        .putBoolean(AppPreferences.KEY_WRITE_TAGS_TO_FILES, false).apply();
                return;
            }
            // Keep the preference disabled until confirmation; avoid listener recursion.
            syncFromPrefs.run();
            new MaterialAlertDialogBuilder(context)
                    .setTitle("Modificar archivos de música")
                    .setMessage("Al activar esta opción, las próximas ediciones también cambiarán los tags "
                            + "de los archivos MP3 y M4A, incluida la portada. Otras aplicaciones "
                            + "podrán ver esos cambios. Se conservará una copia de respaldo "
                            + "para intentar restaurar el archivo original.")
                    .setNegativeButton("Cancelar", (dialog, which) -> syncFromPrefs.run())
                    .setPositiveButton("Activar", (dialog, which) -> {
                        AppPreferences.prefs(context).edit()
                                .putBoolean(AppPreferences.KEY_WRITE_TAGS_TO_FILES, true).apply();
                        syncFromPrefs.run();
                    })
                    .setOnCancelListener(dialog -> syncFromPrefs.run())
                    .show();
        });
        card.setOnClickListener(v -> toggle.setChecked(!toggle.isChecked()));
    }
}
