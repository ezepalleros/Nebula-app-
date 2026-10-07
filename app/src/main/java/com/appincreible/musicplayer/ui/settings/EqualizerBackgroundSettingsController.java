package com.appincreible.musicplayer.ui.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import com.appincreible.musicplayer.databinding.FragmentSettingsBinding;
import com.appincreible.musicplayer.power.PowerSaverManager;
import com.appincreible.musicplayer.themes.AppPreferences;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;

/** Builds and owns the equalizer-background settings without adding more UI logic to SettingsFragment. */
final class EqualizerBackgroundSettingsController {
    private static final String LOCKED_MESSAGE = "Desactivá el modo súper ahorro para cambiar esto";

    private final Context context;
    private final FragmentSettingsBinding binding;
    private MaterialCardView card;
    private MaterialSwitch enabledSwitch;
    private MaterialButton colorButton;
    private MaterialButton barsButton;
    private TextView sensitivityLabel;
    private SeekBar sensitivitySeek;
    private TextView scrimLabel;
    private SeekBar scrimSeek;
    private TextView lockNote;
    private boolean syncing;

    private final SharedPreferences.OnSharedPreferenceChangeListener listener = (prefs, key) -> {
        if (AppPreferences.isEqualizerPreference(key) || PowerSaverManager.isPowerSaverPreference(key)) sync();
    };

    EqualizerBackgroundSettingsController(Context context, FragmentSettingsBinding binding) {
        this.context = context;
        this.binding = binding;
    }

    void attach() {
        buildCard();
        AppPreferences.prefs(context).registerOnSharedPreferenceChangeListener(listener);
        sync();
    }

    void detach() {
        AppPreferences.prefs(context).unregisterOnSharedPreferenceChangeListener(listener);
    }

    private void buildCard() {
        ViewGroup content = (ViewGroup) binding.getRoot().getChildAt(0);
        card = new MaterialCardView(context);
        card.setRadius(dp(20));
        card.setCardElevation(0f);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.setMargins(dp(14), dp(16), dp(14), 0);
        card.setLayoutParams(cardLp);

        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(14), dp(18), dp(16));
        card.addView(box, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = text("FONDO ECUALIZADOR", 13f, true);
        title.setPadding(0, 0, 0, dp(4));
        box.addView(title);

        enabledSwitch = new MaterialSwitch(context);
        enabledSwitch.setText("Fondo ecualizador");
        enabledSwitch.setMinHeight(dp(48));
        box.addView(enabledSwitch, matchWrap());

        TextView description = text("Barras segmentadas que reaccionan a música y radio. El análisis se apaga cuando la app no está visible.", 13f, false);
        description.setAlpha(.72f);
        description.setPadding(0, 0, 0, dp(8));
        box.addView(description);

        colorButton = optionButton("Color de las barras");
        barsButton = optionButton("Cantidad de barras");
        box.addView(colorButton, matchWrap());
        box.addView(barsButton, matchWrap());

        sensitivityLabel = text("Sensibilidad", 14f, true);
        sensitivityLabel.setPadding(dp(4), dp(8), dp(4), 0);
        box.addView(sensitivityLabel);
        sensitivitySeek = new SeekBar(context);
        sensitivitySeek.setMax(150); // 50..200
        box.addView(sensitivitySeek, matchWrap());

        scrimLabel = text("Atenuación", 14f, true);
        scrimLabel.setPadding(dp(4), dp(8), dp(4), 0);
        box.addView(scrimLabel);
        scrimSeek = new SeekBar(context);
        scrimSeek.setMax(35); // 55..90; minimum guarantees bars stay background-like.
        box.addView(scrimSeek, matchWrap());

        lockNote = text(LOCKED_MESSAGE, 12f, false);
        lockNote.setTextColor(Color.GRAY);
        lockNote.setPadding(dp(4), dp(6), dp(4), 0);
        lockNote.setVisibility(View.GONE);
        box.addView(lockNote);

        // Near the top of Personalización, immediately after the super saver block once it is inserted.
        content.addView(card, Math.min(3, content.getChildCount()));

        enabledSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (!syncing) AppPreferences.prefs(context).edit()
                    .putBoolean(AppPreferences.KEY_EQUALIZER_BACKGROUND_ENABLED, checked).commit();
        });
        colorButton.setOnClickListener(v -> showColorModePicker());
        barsButton.setOnClickListener(v -> showBarsPicker());
        sensitivitySeek.setOnSeekBarChangeListener(seekListener(progress ->
                AppPreferences.prefs(context).edit()
                        .putInt(AppPreferences.KEY_EQUALIZER_SENSITIVITY, 50 + progress).commit()));
        scrimSeek.setOnSeekBarChangeListener(seekListener(progress ->
                AppPreferences.prefs(context).edit()
                        .putInt(AppPreferences.KEY_EQUALIZER_SCRIM, 55 + progress).commit()));
    }

    private void sync() {
        if (enabledSwitch == null) return;
        syncing = true;
        boolean powerSaver = PowerSaverManager.isActive(context);
        boolean requested = AppPreferences.equalizerBackgroundRequested(context);
        enabledSwitch.setChecked(requested);
        sensitivitySeek.setProgress(AppPreferences.getEqualizerSensitivity(context) - 50);
        scrimSeek.setProgress(AppPreferences.getEqualizerScrim(context) - 55);
        sensitivityLabel.setText("Sensibilidad · " + AppPreferences.getEqualizerSensitivity(context) + "%");
        scrimLabel.setText("Atenuación · " + AppPreferences.getEqualizerScrim(context) + "%");
        colorButton.setText("Color de las barras · " + colorName());
        barsButton.setText("Cantidad de barras · " + AppPreferences.getEqualizerBars(context));

        setEnabledRecursive(card, !powerSaver);
        // Keep the explanatory note readable even while the rest of the card is disabled.
        lockNote.setEnabled(true);
        lockNote.setAlpha(1f);
        lockNote.setVisibility(powerSaver ? View.VISIBLE : View.GONE);
        card.setAlpha(powerSaver ? .56f : 1f);
        syncing = false;
    }

    private void showColorModePicker() {
        String[] labels = {"Color de acento", "Color de la portada",
                "Degradado verde · amarillo · rojo", "Elegir color…"};
        String[] values = {AppPreferences.EQUALIZER_COLOR_ACCENT, AppPreferences.EQUALIZER_COLOR_ARTWORK,
                AppPreferences.EQUALIZER_COLOR_GRADIENT, AppPreferences.EQUALIZER_COLOR_CUSTOM};
        String current = AppPreferences.getEqualizerColorMode(context);
        int checked = AppPreferences.EQUALIZER_COLOR_ACCENT.equals(current) ? 0
                : AppPreferences.EQUALIZER_COLOR_ARTWORK.equals(current) ? 1
                : AppPreferences.EQUALIZER_COLOR_GRADIENT.equals(current) ? 2 : 3;
        new MaterialAlertDialogBuilder(context)
                .setTitle("Color del ecualizador")
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    if (AppPreferences.EQUALIZER_COLOR_CUSTOM.equals(values[which])) {
                        int initial = AppPreferences.equalizerSolidColor(context);
                        ModernColorPickerDialog.show(context, "Color del ecualizador", initial, color ->
                                AppPreferences.prefs(context).edit()
                                        .putInt(AppPreferences.KEY_EQUALIZER_CUSTOM_COLOR, color)
                                        .putString(AppPreferences.KEY_EQUALIZER_COLOR_MODE, AppPreferences.EQUALIZER_COLOR_CUSTOM)
                                        .commit());
                    } else {
                        AppPreferences.prefs(context).edit()
                                .putString(AppPreferences.KEY_EQUALIZER_COLOR_MODE, values[which]).commit();
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void showBarsPicker() {
        String[] labels = {"16 barras", "24 barras", "32 barras", "48 barras"};
        int[] values = {16, 24, 32, 48};
        int current = AppPreferences.getEqualizerBars(context);
        int checked = 1;
        for (int i = 0; i < values.length; i++) if (values[i] == current) checked = i;
        new MaterialAlertDialogBuilder(context)
                .setTitle("Cantidad de barras")
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    AppPreferences.prefs(context).edit().putInt(AppPreferences.KEY_EQUALIZER_BARS, values[which]).commit();
                    dialog.dismiss();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private String colorName() {
        String mode = AppPreferences.getEqualizerColorMode(context);
        if (AppPreferences.EQUALIZER_COLOR_ACCENT.equals(mode)) return "Acento";
        if (AppPreferences.EQUALIZER_COLOR_ARTWORK.equals(mode)) return "Portada";
        if (AppPreferences.EQUALIZER_COLOR_GRADIENT.equals(mode)) return "Degradado";
        if (AppPreferences.EQUALIZER_COLOR_CUSTOM.equals(mode)) return "Personalizado";
        return "Personalizado";
    }

    private MaterialButton optionButton(String text) {
        MaterialButton button = new MaterialButton(context, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        button.setText(text);
        button.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        button.setMinHeight(dp(48));
        button.setAllCaps(false);
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
        return button;
    }

    private TextView text(String value, float sp, boolean bold) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(sp);
        if (bold) view.setTypeface(view.getTypeface(), android.graphics.Typeface.BOLD);
        return view;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private interface ProgressConsumer { void accept(int progress); }
    private SeekBar.OnSeekBarChangeListener seekListener(ProgressConsumer consumer) {
        return new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && !syncing) consumer.accept(progress);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        };
    }

    private void setEnabledRecursive(View view, boolean enabled) {
        if (view == lockNote) return;
        view.setEnabled(enabled);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) setEnabledRecursive(group.getChildAt(i), enabled);
        }
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
