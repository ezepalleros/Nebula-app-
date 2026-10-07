package com.appincreible.musicplayer.ui.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.appincreible.musicplayer.R;
import com.appincreible.musicplayer.databinding.FragmentSettingsBinding;
import com.appincreible.musicplayer.player.audio.AudioPreferences;
import com.appincreible.musicplayer.power.PowerSaverManager;
import com.appincreible.musicplayer.themes.AppPreferences;
import com.appincreible.musicplayer.themes.ListAppearance;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.ArrayList;
import java.util.List;

/** Owns all UI/state wiring for the super battery saver settings block. */
final class PowerSaverSettingsController {

    private static final String LOCKED_MESSAGE = "Desactivá el modo súper ahorro para cambiar esto";

    private final Context context;
    private final FragmentSettingsBinding binding;
    private final MaterialSwitch listTransparentSwitch;
    private final boolean showControls;
    private final List<View> lockedViews = new ArrayList<>();
    private final List<TextView> lockNotes = new ArrayList<>();

    private MaterialSwitch enabledSwitch;
    private MaterialSwitch autoBatterySwitch;
    private MaterialSwitch autoSystemSwitch;
    private MaterialSwitch forceDarkSwitch;
    private TextView status;
    private TextView thresholdLabel;
    private SeekBar thresholdSeek;
    private boolean bindingState;

    private final SharedPreferences.OnSharedPreferenceChangeListener listener = (prefs, key) -> {
        if (PowerSaverManager.isPowerSaverPreference(key)) sync();
    };

    PowerSaverSettingsController(Context context,
                                 FragmentSettingsBinding binding,
                                 MaterialSwitch listTransparentSwitch,
                                 boolean showControls) {
        this.context = context;
        this.binding = binding;
        this.listTransparentSwitch = listTransparentSwitch;
        this.showControls = showControls;
    }

    void attach() {
        PowerSaverManager.startMonitoring(context);
        if (showControls) addPowerSaverCard();
        collectLockedViews();
        AppPreferences.prefs(context).registerOnSharedPreferenceChangeListener(listener);
        sync();
    }

    void detach() {
        AppPreferences.prefs(context).unregisterOnSharedPreferenceChangeListener(listener);
    }

    private void addPowerSaverCard() {
        ViewGroup content = (ViewGroup) binding.getRoot().getChildAt(0);

        MaterialCardView card = new MaterialCardView(context);
        card.setCardElevation(0f);
        card.setRadius(dp(20));
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.setMargins(dp(14), dp(16), dp(14), 0);
        card.setLayoutParams(cardLp);

        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(14), dp(18), dp(14));
        card.addView(box, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        enabledSwitch = switchView("Modo súper ahorro de batería");
        box.addView(enabledSwitch);

        status = bodyText("Desactivado");
        status.setPadding(0, 0, 0, dp(8));
        box.addView(status);

        autoBatterySwitch = switchView("Activar automáticamente con batería baja");
        box.addView(autoBatterySwitch);

        thresholdLabel = bodyText("Umbral: 20%");
        thresholdLabel.setPadding(0, dp(4), 0, 0);
        box.addView(thresholdLabel);

        thresholdSeek = new SeekBar(context);
        thresholdSeek.setMax(45); // 5% .. 50%
        box.addView(thresholdSeek, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        autoSystemSwitch = switchView("Activar cuando Android active Ahorro de energía");
        box.addView(autoSystemSwitch);

        forceDarkSwitch = switchView("Usar fondo oscuro sólido en este modo");
        box.addView(forceDarkSwitch);

        TextView explanation = bodyText("Al activarlo se congelan los renders, se desactivan transiciones y análisis, y las opciones de mayor consumo quedan bloqueadas temporalmente.");
        explanation.setPadding(0, dp(4), 0, 0);
        box.addView(explanation);

        // After the large player preview and before the REPRODUCTOR section.
        content.addView(card, Math.min(2, content.getChildCount()));

        enabledSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (!bindingState) PowerSaverManager.setManualEnabled(context, checked);
        });
        autoBatterySwitch.setOnCheckedChangeListener((button, checked) -> {
            if (!bindingState) PowerSaverManager.setAutoBatteryEnabled(context, checked);
        });
        autoSystemSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (!bindingState) PowerSaverManager.setAutoSystemEnabled(context, checked);
        });
        forceDarkSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (!bindingState) PowerSaverManager.setForceDarkBackground(context, checked);
        });
        thresholdSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int threshold = 5 + progress;
                thresholdLabel.setText("Umbral: " + threshold + "%");
                if (fromUser && !bindingState) PowerSaverManager.setBatteryThreshold(context, threshold);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });
    }

    private void collectLockedViews() {
        lock(binding.styleRow);
        lock(binding.animationRow);
        lock(binding.backgroundRow);
        lock(topCard(binding.crossfadeSwitch));
        lock(topCard(binding.normalizationSwitch));
        lock(topCard(binding.djLoquendoSwitch));
        lock(binding.appBackgroundRow);
        lock(binding.appBackgroundColorsRow);
        lock(binding.listTransparentCard);
    }

    private void lock(@Nullable View view) {
        if (view == null || lockedViews.contains(view)) return;
        lockedViews.add(view);
        TextView note = new TextView(context);
        note.setText(LOCKED_MESSAGE);
        note.setTextSize(12f);
        note.setTextColor(Color.GRAY);
        note.setPadding(dp(20), dp(2), dp(20), dp(4));
        note.setVisibility(View.GONE);
        ViewParentHelper.insertAfter(view, note);
        lockNotes.add(note);
    }

    private void sync() {
        bindingState = true;
        boolean active = PowerSaverManager.isActive(context);
        if (showControls && enabledSwitch != null) {
            enabledSwitch.setChecked(PowerSaverManager.isManualEnabled(context));
            autoBatterySwitch.setChecked(PowerSaverManager.isAutoBatteryEnabled(context));
            autoSystemSwitch.setChecked(PowerSaverManager.isAutoSystemEnabled(context));
            forceDarkSwitch.setChecked(PowerSaverManager.forceDarkBackground(context));
            int threshold = PowerSaverManager.batteryThreshold(context);
            thresholdSeek.setProgress(threshold - 5);
            thresholdSeek.setEnabled(autoBatterySwitch.isChecked());
            thresholdLabel.setAlpha(autoBatterySwitch.isChecked() ? 1f : 0.5f);
            thresholdSeek.setAlpha(autoBatterySwitch.isChecked() ? 1f : 0.5f);

            if (active) {
                if (!PowerSaverManager.isManualEnabled(context)) {
                    status.setText("Activo automáticamente · batería " + PowerSaverManager.batteryPercent(context)
                            + "%" + (PowerSaverManager.systemPowerSaveMode(context) ? " · ahorro del sistema" : ""));
                } else {
                    status.setText("Activo");
                }
            } else {
                status.setText("Desactivado");
            }
        }

        if (!active) restoreVisibleControlState();
        for (int i = 0; i < lockedViews.size(); i++) {
            View locked = lockedViews.get(i);
            setEnabledRecursive(locked, !active);
            TextView note = lockNotes.get(i);
            note.setVisibility(active && locked.getVisibility() == View.VISIBLE ? View.VISIBLE : View.GONE);
        }
        updatePreviewActivity(active);
        bindingState = false;
    }

    private void restoreVisibleControlState() {
        SharedPreferences audio = AudioPreferences.prefs(context);
        binding.crossfadeSwitch.setChecked(audio.getBoolean(AudioPreferences.KEY_CROSSFADE_ENABLED, false));
        binding.skipSameAlbumSwitch.setChecked(audio.getBoolean(AudioPreferences.KEY_SKIP_SAME_ALBUM, true));
        binding.gaplessSwitch.setChecked(audio.getBoolean(AudioPreferences.KEY_GAPLESS_ENABLED, true));
        binding.normalizationSwitch.setChecked(audio.getBoolean(AudioPreferences.KEY_NORMALIZATION_ENABLED, false));
        binding.djLoquendoSwitch.setChecked(audio.getBoolean(AudioPreferences.KEY_DJ_ENABLED, false));
        listTransparentSwitch.setChecked(AppPreferences.prefs(context)
                .getBoolean(ListAppearance.KEY_LIST_TRANSPARENT, false));
    }

    private void updatePreviewActivity(boolean active) {
        try {
            if (active) {
                binding.playerPreviewWeb.evaluateJavascript("Player3D.setAnimation(0);Player3D.setAppVisible(false)", null);
                binding.styleMiniPreviewWeb.evaluateJavascript("Player3D.setAnimation(0);Player3D.setAppVisible(false)", null);
            } else {
                float animation = AppPreferences.getAnimationMultiplier(context);
                binding.playerPreviewWeb.evaluateJavascript("Player3D.setAnimation(" + animation + ");Player3D.setAppVisible(true)", null);
                binding.styleMiniPreviewWeb.evaluateJavascript("Player3D.setAnimation(0);Player3D.setAppVisible(true)", null);
            }
        } catch (Exception ignored) { }
    }

    private MaterialSwitch switchView(String text) {
        MaterialSwitch view = new MaterialSwitch(context);
        view.setText(text);
        view.setTextAppearance(R.style.TextAppearance_AppIncreible_Body);
        view.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return view;
    }

    private TextView bodyText(String text) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextAppearance(R.style.TextAppearance_AppIncreible_Caption);
        return view;
    }

    private View topCard(View child) {
        View current = child;
        while (current != null && !(current instanceof MaterialCardView)) {
            if (!(current.getParent() instanceof View)) break;
            current = (View) current.getParent();
        }
        return current;
    }

    private void setEnabledRecursive(View view, boolean enabled) {
        view.setEnabled(enabled);
        view.setAlpha(enabled ? 1f : 0.45f);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) setEnabledRecursive(group.getChildAt(i), enabled);
        }
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static final class ViewParentHelper {
        static void insertAfter(View anchor, View note) {
            if (!(anchor.getParent() instanceof ViewGroup)) return;
            ViewGroup parent = (ViewGroup) anchor.getParent();
            int index = parent.indexOfChild(anchor);
            parent.addView(note, Math.min(index + 1, parent.getChildCount()));
        }
    }
}
