package com.appincreible.musicplayer.ui.settings;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import com.appincreible.musicplayer.R;
import com.appincreible.musicplayer.themes.view.HsvColorPickerView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Locale;

/** Shared color selector used by every color preference in Settings. */
final class ModernColorPickerDialog {

    interface Callback { void onSelected(int color); }

    private ModernColorPickerDialog() { }

    static void show(Context context, String title, int initialColor, Callback callback) {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dimen(context, R.dimen.space_20), dimen(context, R.dimen.space_8),
                dimen(context, R.dimen.space_20), 0);

        HsvColorPickerView picker = new HsvColorPickerView(context);
        picker.setColor(initialColor);
        root.addView(picker, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dimen(context, R.dimen.music_row_height) * 5 / 2));

        TextView hueLabel = new TextView(context);
        hueLabel.setText("Tono");
        hueLabel.setPadding(0, dimen(context, R.dimen.space_8), 0, 0);
        root.addView(hueLabel);

        SeekBar hue = new SeekBar(context);
        hue.setMax(360);
        hue.setProgress(Math.round(picker.getHue()));
        root.addView(hue, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout valueRow = new LinearLayout(context);
        valueRow.setGravity(Gravity.CENTER_VERTICAL);
        valueRow.setOrientation(LinearLayout.HORIZONTAL);
        valueRow.setPadding(0, dimen(context, R.dimen.space_4), 0, 0);
        root.addView(valueRow);

        View preview = new View(context);
        preview.setBackgroundResource(R.drawable.bg_color_preview);
        preview.setBackgroundTintList(ColorStateList.valueOf(initialColor));
        int previewSize = dimen(context, R.dimen.music_action_size);
        valueRow.addView(preview, new LinearLayout.LayoutParams(previewSize, previewSize));

        EditText hex = new EditText(context);
        hex.setSingleLine(true);
        hex.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        hex.setText(String.format(Locale.getDefault(), "#%06X", initialColor & 0xFFFFFF));
        LinearLayout.LayoutParams hexParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        hexParams.setMarginStart(dimen(context, R.dimen.space_12));
        valueRow.addView(hex, hexParams);

        final boolean[] internalChange = {false};
        picker.setOnColorChangedListener(color -> {
            preview.setBackgroundTintList(ColorStateList.valueOf(color));
            internalChange[0] = true;
            hex.setText(String.format(Locale.getDefault(), "#%06X", color & 0xFFFFFF));
            hex.setSelection(hex.length());
            internalChange[0] = false;
        });
        hue.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) picker.setHue(progress);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });
        hex.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (internalChange[0]) return;
                try {
                    int color = Color.parseColor(s.toString().trim());
                    picker.setColor(color);
                    hue.setProgress(Math.round(picker.getHue()));
                    preview.setBackgroundTintList(ColorStateList.valueOf(color));
                } catch (IllegalArgumentException ignored) { }
            }
            @Override public void afterTextChanged(android.text.Editable s) { }
        });

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setView(root)
                .setPositiveButton("Usar color", null)
                .setNegativeButton("Cancelar", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog
                .getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    try {
                        int color = Color.parseColor(hex.getText().toString().trim());
                        callback.onSelected(color);
                        dialog.dismiss();
                    } catch (IllegalArgumentException ex) {
                        hex.setError("Usá un color válido, por ejemplo #6C4DFF");
                    }
                }));
        dialog.show();
    }

    private static int dimen(Context context, int id) {
        return context.getResources().getDimensionPixelSize(id);
    }
}
