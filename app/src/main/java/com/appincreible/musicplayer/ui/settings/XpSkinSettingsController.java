package com.appincreible.musicplayer.ui.settings;

import android.content.Context;
import android.graphics.Color;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.appincreible.musicplayer.R;
import com.appincreible.musicplayer.themes.AppPreferences;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Locale;

/** XP-only settings beside the player style selector; no SettingsFragment additions. */
final class XpSkinSettingsController {
    private final Context context;
    private final View styleRow;
    private final Runnable onChange;
    private final MaterialCardView card;
    private final TextView[] values = new TextView[5];
    private final int spacing;

    XpSkinSettingsController(Context context, View styleRow, Runnable onChange) {
        this.context = context;
        this.styleRow = styleRow;
        this.onChange = onChange;
        spacing = context.getResources().getDimensionPixelSize(R.dimen.space_16);
        card = new MaterialCardView(context);
        card.setRadius(context.getResources().getDimension(R.dimen.radius_16));
        card.setCardElevation(0);
        card.setCardBackgroundColor(MaterialColors.getColor(card,
                com.google.android.material.R.attr.colorSurfaceContainerLow));
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(spacing, spacing, spacing, spacing);
        TextView heading = new TextView(context);
        heading.setText("REPRODUCTOR XP");
        heading.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium);
        heading.setPadding(0, 0, 0, spacing / 2);
        content.addView(heading);
        addRow(content, 0, "Tema de ventana", this::pickTheme);
        addRow(content, 1, "Fondo de escritorio", this::pickDesktopColor);
        addRow(content, 2, "Título de la ventana", this::editTitle);
        addRow(content, 3, "Aspecto", this::pickAppearance);
        addRow(content, 4, "Pantalla", this::pickDisplay);
        card.addView(content);
    }

    void attach() {
        ViewGroup parent = (ViewGroup) styleRow.getParent();
        int index = parent.indexOfChild(styleRow);
        ViewGroup.LayoutParams original = styleRow.getLayoutParams();
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (original instanceof LinearLayout.LayoutParams) {
            LinearLayout.LayoutParams source = (LinearLayout.LayoutParams) original;
            params.leftMargin = source.leftMargin;
            params.rightMargin = source.rightMargin;
            params.topMargin = spacing / 2;
            params.bottomMargin = spacing / 2;
        }
        parent.addView(card, index + 1, params);
        refresh();
    }

    void refresh() {
        card.setVisibility(AppPreferences.STYLE_XP_2D.equals(AppPreferences.getPlayerStyle(context))
                ? View.VISIBLE : View.GONE);
        String theme = AppPreferences.getXpTheme(context);
        values[0].setText(AppPreferences.XP_OLIVE.equals(theme) ? "Oliva"
                : AppPreferences.XP_SILVER.equals(theme) ? "Plata" : "Azul");
        values[1].setText(String.format(Locale.getDefault(), "#%06X",
                AppPreferences.getXpDesktopColor(context) & 0xFFFFFF));
        values[2].setText(AppPreferences.getXpTitle(context));
        values[3].setText(AppPreferences.XP_SHARP.equals(AppPreferences.getXpAppearance(context)) ? "Nítido" : "Pixelado");
        values[4].setText(AppPreferences.XP_BARS.equals(AppPreferences.getXpDisplay(context))
                ? "Visualización de barras" : "Portada");
    }

    private void addRow(LinearLayout root, int index, String name, Runnable action) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, spacing / 2, 0, spacing / 2);
        TextView label = new TextView(context);
        label.setText(name);
        label.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge);
        row.addView(label);
        TextView value = new TextView(context);
        value.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
        value.setTextColor(MaterialColors.getColor(value,
                com.google.android.material.R.attr.colorPrimary));
        row.addView(value);
        values[index] = value;
        row.setClickable(true);
        row.setFocusable(true);
        row.setOnClickListener(v -> action.run());
        root.addView(row);
    }

    private void saveString(String key, String value) {
        AppPreferences.prefs(context).edit().putString(key, value).apply();
        refresh();
        onChange.run();
    }

    private void choose(String title, String[] labels, String[] values, int current, String key) {
        new MaterialAlertDialogBuilder(context).setTitle(title)
                .setSingleChoiceItems(labels, current, (dialog, index) -> {
                    saveString(key, values[index]);
                    dialog.dismiss();
                }).setNegativeButton("Cancelar", null).show();
    }

    private void pickTheme() {
        String theme = AppPreferences.getXpTheme(context);
        choose("Tema de ventana", new String[]{"Azul", "Oliva", "Plata"},
                new String[]{AppPreferences.XP_BLUE, AppPreferences.XP_OLIVE, AppPreferences.XP_SILVER},
                AppPreferences.XP_OLIVE.equals(theme) ? 1 : AppPreferences.XP_SILVER.equals(theme) ? 2 : 0,
                AppPreferences.KEY_XP_THEME);
    }

    private void pickDesktopColor() {
        ModernColorPickerDialog.show(context, "Fondo de escritorio",
                AppPreferences.getXpDesktopColor(context), color -> {
                    AppPreferences.prefs(context).edit().putInt(AppPreferences.KEY_XP_DESKTOP_COLOR, color).apply();
                    refresh();
                    onChange.run();
                });
    }

    private void editTitle() {
        EditText input = new EditText(context);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(70)});
        input.setText(AppPreferences.getXpTitle(context));
        input.setSelectAllOnFocus(true);
        LinearLayout root = new LinearLayout(context);
        root.setPadding(spacing, spacing / 2, spacing, 0);
        root.addView(input, new LinearLayout.LayoutParams(-1, -2));
        new MaterialAlertDialogBuilder(context).setTitle("Título de la ventana").setView(root)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Guardar", (d, w) -> saveString(AppPreferences.KEY_XP_TITLE,
                        input.getText().toString().trim().isEmpty()
                                ? AppPreferences.XP_DEFAULT_TITLE : input.getText().toString().trim())).show();
    }

    private void pickAppearance() {
        choose("Aspecto", new String[]{"Pixelado", "Nítido"},
                new String[]{AppPreferences.XP_PIXEL, AppPreferences.XP_SHARP},
                AppPreferences.XP_SHARP.equals(AppPreferences.getXpAppearance(context)) ? 1 : 0,
                AppPreferences.KEY_XP_APPEARANCE);
    }

    private void pickDisplay() {
        choose("Pantalla", new String[]{"Portada", "Visualización de barras"},
                new String[]{AppPreferences.XP_COVER, AppPreferences.XP_BARS},
                AppPreferences.XP_BARS.equals(AppPreferences.getXpDisplay(context)) ? 1 : 0,
                AppPreferences.KEY_XP_DISPLAY);
    }
}
