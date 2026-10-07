package com.appincreible.musicplayer.ui.settings;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import com.appincreible.musicplayer.R;
import com.appincreible.musicplayer.themes.AppPreferences;
import com.appincreible.musicplayer.themes.view.PlayerBackgroundView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Locale;

final class PlayerBackgroundSettingsController {

    private PlayerBackgroundSettingsController() { }

    static void show(Fragment fragment, Runnable onChanged) {
        Context context = fragment.requireContext();
        String[] labels = {"Color de la portada", "Un color", "Degradado de 2 colores", "Degradado de 3 colores", "Multicolor arcoíris"};
        String[] values = {AppPreferences.PLAYER_BACKGROUND_COLOR_ARTWORK,
                AppPreferences.APP_BACKGROUND_COLOR_SOLID,
                AppPreferences.APP_BACKGROUND_COLOR_GRADIENT_2,
                AppPreferences.APP_BACKGROUND_COLOR_GRADIENT_3,
                AppPreferences.PLAYER_BACKGROUND_COLOR_RAINBOW};
        int checked = indexOf(values, AppPreferences.getPlayerBackgroundColorMode(context));

        new MaterialAlertDialogBuilder(context)
                .setTitle("Colores del fondo del reproductor")
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    String mode = values[which];
                    if (AppPreferences.PLAYER_BACKGROUND_COLOR_RAINBOW.equals(mode)
                            || AppPreferences.PLAYER_BACKGROUND_COLOR_ARTWORK.equals(mode)) {
                        AppPreferences.setPlayerBackgroundPalette(context, mode,
                                AppPreferences.getPlayerBackgroundColor1(context),
                                AppPreferences.getPlayerBackgroundColor2(context),
                                AppPreferences.getPlayerBackgroundColor3(context));
                        onChanged.run();
                    } else {
                        showColorEditor(fragment, mode, onChanged);
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    static String summary(Context context) {
        String mode = AppPreferences.getPlayerBackgroundColorMode(context);
        int c1 = AppPreferences.getPlayerBackgroundColor1(context);
        int c2 = AppPreferences.getPlayerBackgroundColor2(context);
        if (AppPreferences.PLAYER_BACKGROUND_COLOR_ARTWORK.equals(mode)) return "Color de la portada";
        if (AppPreferences.APP_BACKGROUND_COLOR_SOLID.equals(mode))
            return String.format(Locale.getDefault(), "Color · #%06X", c1 & 0xFFFFFF);
        if (AppPreferences.APP_BACKGROUND_COLOR_GRADIENT_2.equals(mode))
            return String.format(Locale.getDefault(), "Degradado · #%06X → #%06X", c1 & 0xFFFFFF, c2 & 0xFFFFFF);
        if (AppPreferences.APP_BACKGROUND_COLOR_GRADIENT_3.equals(mode)) return "Degradado · 3 colores";
        if (AppPreferences.PLAYER_BACKGROUND_COLOR_RAINBOW.equals(mode)) return "Multicolor arcoíris";
        return "Color de acento";
    }

    static int previewColor(Context context) {
        String mode = AppPreferences.getPlayerBackgroundColorMode(context);
        if (AppPreferences.PLAYER_BACKGROUND_COLOR_RAINBOW.equals(mode)
                || AppPreferences.PLAYER_BACKGROUND_COLOR_ARTWORK.equals(mode)) {
            return AppPreferences.getPlayerAccentColor(context);
        }
        return AppPreferences.getPlayerBackgroundColor1(context);
    }

    private static void showColorEditor(Fragment fragment, String mode, Runnable onChanged) {
        Context context = fragment.requireContext();
        int count = AppPreferences.APP_BACKGROUND_COLOR_SOLID.equals(mode) ? 1
                : AppPreferences.APP_BACKGROUND_COLOR_GRADIENT_2.equals(mode) ? 2 : 3;
        int[] colors = {AppPreferences.getPlayerBackgroundColor1(context),
                AppPreferences.getPlayerBackgroundColor2(context),
                AppPreferences.getPlayerBackgroundColor3(context)};

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        int horizontal = dimen(context, R.dimen.space_20);
        root.setPadding(horizontal, dimen(context, R.dimen.space_8), horizontal, 0);

        PlayerBackgroundView preview = new PlayerBackgroundView(context);
        preview.setLightMode(false);
        preview.setScene(AppPreferences.getPlayerBackground(context),
                AppPreferences.getPlayerAccentColor(context), 0f);
        preview.setPalette(mode, colors[0], colors[1], colors[2]);
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dimen(context, R.dimen.music_row_height) * 2);
        previewParams.bottomMargin = dimen(context, R.dimen.space_12);
        root.addView(preview, previewParams);

        for (int i = 0; i < count; i++) {
            final int index = i;
            LinearLayout row = new LinearLayout(context);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, dimen(context, R.dimen.space_4), 0, dimen(context, R.dimen.space_4));

            View swatch = new View(context);
            swatch.setBackgroundResource(R.drawable.bg_color_preview);
            swatch.setBackgroundTintList(ColorStateList.valueOf(colors[i]));
            row.addView(swatch, new LinearLayout.LayoutParams(
                    dimen(context, R.dimen.music_action_size), dimen(context, R.dimen.music_action_size)));

            TextView value = new TextView(context);
            value.setTextAppearance(R.style.TextAppearance_AppIncreible_Body);
            value.setText(String.format(Locale.getDefault(), "Color %d · #%06X", index + 1, colors[index] & 0xFFFFFF));
            LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            valueParams.setMarginStart(dimen(context, R.dimen.space_12));
            row.addView(value, valueParams);

            MaterialButton choose = new MaterialButton(context);
            choose.setText("Elegir");
            choose.setOnClickListener(v -> ModernColorPickerDialog.show(context, "Color " + (index + 1), colors[index], picked -> {
                colors[index] = picked;
                if (count == 1) {
                    colors[1] = picked;
                    colors[2] = picked;
                } else if (count == 2) {
                    colors[2] = colors[1];
                }
                swatch.setBackgroundTintList(ColorStateList.valueOf(picked));
                value.setText(String.format(Locale.getDefault(), "Color %d · #%06X", index + 1, picked & 0xFFFFFF));
                preview.setPalette(mode, colors[0], colors[1], colors[2]);
            }));
            row.addView(choose);
            root.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        new MaterialAlertDialogBuilder(context)
                .setTitle("Colores del fondo")
                .setView(root)
                .setPositiveButton("Guardar", (dialog, which) -> {
                    if (count == 1) {
                        colors[1] = colors[0];
                        colors[2] = colors[0];
                    } else if (count == 2) {
                        colors[2] = colors[1];
                    }
                    AppPreferences.setPlayerBackgroundPalette(context, mode, colors[0], colors[1], colors[2]);
                    onChanged.run();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private static int indexOf(String[] values, String value) {
        for (int i = 0; i < values.length; i++) if (values[i].equals(value)) return i;
        return -1;
    }

    private static int dimen(Context context, int id) {
        return context.getResources().getDimensionPixelSize(id);
    }

}
