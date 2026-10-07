package com.appincreible.musicplayer.ui.settings;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.media3.common.Player;

import com.appincreible.musicplayer.databinding.FragmentSettingsBinding;
import com.appincreible.musicplayer.player.audio.AudioPreferences;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** Keeps song and radio next/shuffle/repeat preferences independent from the category screens. */
final class PlaybackOrderSettingsController implements SharedPreferences.OnSharedPreferenceChangeListener {
    private final Context appContext;
    private final FragmentSettingsBinding binding;
    private final SharedPreferences prefs;

    PlaybackOrderSettingsController(Context context, FragmentSettingsBinding binding) {
        appContext = context.getApplicationContext();
        this.binding = binding;
        prefs = AudioPreferences.prefs(appContext);
    }

    void attach() {
        AudioPreferences.ensureSplitPlaybackOrder(appContext);
        refresh();
        binding.songShuffleSwitch.setOnClickListener(v -> showOrderPicker(false));
        binding.radioShuffleSwitch.setOnClickListener(v -> showOrderPicker(true));
        binding.songRepeatButton.setOnClickListener(v -> showRepeatPicker(false));
        binding.radioRepeatButton.setOnClickListener(v -> showRepeatPicker(true));
        prefs.registerOnSharedPreferenceChangeListener(this);
    }

    void detach() {
        prefs.unregisterOnSharedPreferenceChangeListener(this);
        binding.songShuffleSwitch.setOnClickListener(null);
        binding.radioShuffleSwitch.setOnClickListener(null);
        binding.songRepeatButton.setOnClickListener(null);
        binding.radioRepeatButton.setOnClickListener(null);
    }

    private void showOrderPicker(boolean radio) {
        String[] labels = {"Lista", "Aleatorio"};
        int checked = AudioPreferences.shuffleEnabled(appContext, radio) ? 1 : 0;
        new MaterialAlertDialogBuilder(binding.getRoot().getContext())
                .setTitle(radio ? "Orden de siguiente · Radio" : "Orden de siguiente · Canciones")
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    AudioPreferences.setShuffleEnabled(appContext, radio, which == 1);
                    dialog.dismiss();
                    refresh();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void showRepeatPicker(boolean radio) {
        String[] labels = {"Desactivada", "Todas", "Una"};
        int current = AudioPreferences.repeatMode(appContext, radio);
        int checked = current == Player.REPEAT_MODE_ALL ? 1 : current == Player.REPEAT_MODE_ONE ? 2 : 0;
        new MaterialAlertDialogBuilder(binding.getRoot().getContext())
                .setTitle(radio ? "Repetición · Radio" : "Repetición · Canciones")
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    int value = which == 1 ? Player.REPEAT_MODE_ALL : which == 2 ? Player.REPEAT_MODE_ONE : Player.REPEAT_MODE_OFF;
                    AudioPreferences.setRepeatMode(appContext, radio, value);
                    dialog.dismiss();
                    refresh();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void refresh() {
        updateOrderLabel(false, AudioPreferences.shuffleEnabled(appContext, false));
        updateOrderLabel(true, AudioPreferences.shuffleEnabled(appContext, true));
        updateRepeatLabel(false, AudioPreferences.repeatMode(appContext, false));
        updateRepeatLabel(true, AudioPreferences.repeatMode(appContext, true));
    }

    private void updateOrderLabel(boolean radio, boolean enabled) {
        String text = "Orden de siguiente: " + (enabled ? "Aleatorio" : "Lista");
        if (radio) binding.radioShuffleSwitch.setText(text);
        else binding.songShuffleSwitch.setText(text);
    }

    private void updateRepeatLabel(boolean radio, int mode) {
        String value = mode == Player.REPEAT_MODE_ONE ? "Una"
                : mode == Player.REPEAT_MODE_ALL ? "Todas" : "Desactivada";
        if (radio) binding.radioRepeatButton.setText("Repetición: " + value);
        else binding.songRepeatButton.setText("Repetición: " + value);
    }

    @Override public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (AudioPreferences.isPlaybackOrderKey(key)) refresh();
    }
}
