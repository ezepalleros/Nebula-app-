package com.appincreible.musicplayer.ui.settings;

import android.content.Context;
import android.text.InputType;
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

/** Opciones exclusivas del Teléfono clásico. No modifica SettingsFragment. */
final class PhoneSkinSettingsController {
    private final Context context;
    private final View styleRow;
    private final Runnable onChange;
    private final MaterialCardView card;
    private final TextView[] values = new TextView[5];
    private final int spacing;

    PhoneSkinSettingsController(Context context, View styleRow, Runnable onChange) {
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
        heading.setText("TELÉFONO CLÁSICO");
        heading.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium);
        heading.setPadding(0, 0, 0, spacing / 2);
        content.addView(heading);
        addRow(content, 0, "Tema de pantalla", this::pickTheme);
        addRow(content, 1, "Título de la pantalla", this::editTitle);
        addRow(content, 2, "Aspecto", this::pickAppearance);
        addRow(content, 3, "Efecto LCD", this::pickLcd);
        addRow(content, 4, "Contenido de la tarjeta", this::pickCard);
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
        // Sección contigua al selector, sin tocar la disposición de los otros skins.
        parent.addView(card, index + 1, params);
        refresh();
    }

    void refresh() {
        card.setVisibility(AppPreferences.STYLE_PHONE_2D.equals(AppPreferences.getPlayerStyle(context))
                ? View.VISIBLE : View.GONE);
        String theme = AppPreferences.getPhoneTheme(context);
        values[0].setText(AppPreferences.PHONE_GREEN.equals(theme) ? "Verde"
                : AppPreferences.PHONE_GRAY.equals(theme) ? "Gris" : "Cielo azul");
        values[1].setText(AppPreferences.getPhoneTitle(context));
        values[2].setText(AppPreferences.PHONE_SHARP.equals(AppPreferences.getPhoneAppearance(context)) ? "Nítido" : "Pixelado");
        values[3].setText(AppPreferences.isPhoneLcdEnabled(context) ? "Activado" : "Desactivado");
        values[4].setText(AppPreferences.PHONE_BARS.equals(AppPreferences.getPhoneCard(context))
                ? "Visualización de barras" : "Portada y texto");
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

    private void choose(String title, String[] labels, String[] options, int selected, String key) {
        new MaterialAlertDialogBuilder(context).setTitle(title)
                .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                    saveString(key, options[which]);
                    dialog.dismiss();
                }).setNegativeButton("Cancelar", null).show();
    }

    private void pickTheme() {
        String theme = AppPreferences.getPhoneTheme(context);
        choose("Tema de pantalla", new String[]{"Cielo azul", "Verde", "Gris"},
                new String[]{AppPreferences.PHONE_SKY, AppPreferences.PHONE_GREEN, AppPreferences.PHONE_GRAY},
                AppPreferences.PHONE_GREEN.equals(theme) ? 1 : AppPreferences.PHONE_GRAY.equals(theme) ? 2 : 0,
                AppPreferences.KEY_PHONE_THEME);
    }

    private void editTitle() {
        EditText input = new EditText(context);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(45)});
        input.setText(AppPreferences.getPhoneTitle(context));
        input.setSelectAllOnFocus(true);
        LinearLayout root = new LinearLayout(context);
        root.setPadding(spacing, spacing / 2, spacing, 0);
        root.addView(input, new LinearLayout.LayoutParams(-1, -2));
        new MaterialAlertDialogBuilder(context).setTitle("Título de la pantalla").setView(root)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Guardar", (dialog, which) -> saveString(AppPreferences.KEY_PHONE_TITLE,
                        input.getText().toString().trim().isEmpty()
                                ? AppPreferences.PHONE_DEFAULT_TITLE : input.getText().toString().trim())).show();
    }

    private void pickAppearance() {
        choose("Aspecto", new String[]{"Pixelado", "Nítido"},
                new String[]{AppPreferences.PHONE_PIXEL, AppPreferences.PHONE_SHARP},
                AppPreferences.PHONE_SHARP.equals(AppPreferences.getPhoneAppearance(context)) ? 1 : 0,
                AppPreferences.KEY_PHONE_APPEARANCE);
    }

    private void pickLcd() {
        new MaterialAlertDialogBuilder(context).setTitle("Efecto LCD")
                .setSingleChoiceItems(new String[]{"Activado", "Desactivado"},
                        AppPreferences.isPhoneLcdEnabled(context) ? 0 : 1,
                        (dialog, which) -> {
                            AppPreferences.prefs(context).edit()
                                    .putBoolean(AppPreferences.KEY_PHONE_LCD, which == 0).apply();
                            refresh();
                            onChange.run();
                            dialog.dismiss();
                        }).setNegativeButton("Cancelar", null).show();
    }

    private void pickCard() {
        choose("Contenido de la tarjeta", new String[]{"Portada y texto", "Visualización de barras"},
                new String[]{AppPreferences.PHONE_COVER, AppPreferences.PHONE_BARS},
                AppPreferences.PHONE_BARS.equals(AppPreferences.getPhoneCard(context)) ? 1 : 0,
                AppPreferences.KEY_PHONE_CARD);
    }
}
