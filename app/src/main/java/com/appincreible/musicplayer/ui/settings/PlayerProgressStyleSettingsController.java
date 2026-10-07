package com.appincreible.musicplayer.ui.settings;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import androidx.fragment.app.Fragment;

import com.appincreible.musicplayer.R;
import com.appincreible.musicplayer.themes.AppPreferences;
import com.appincreible.musicplayer.themes.UiPalette;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.shape.MaterialShapeDrawable;
import com.google.android.material.shape.ShapeAppearanceModel;

/** Presentación del selector de estilo de la barra de progreso. */
final class PlayerProgressStyleSettingsController {
    private PlayerProgressStyleSettingsController() { }

    static void show(@NonNull Fragment fragment, @NonNull Runnable onChanged) {
        Context context = fragment.requireContext();
        UiPalette palette = UiPalette.fallback(context);
        int surface = opaque(palette.surface);
        int primary = UiPalette.readableTextColor(surface);
        int secondaryCandidate = ColorUtils.blendARGB(primary, surface, 0.30f);
        int secondary = ColorUtils.calculateContrast(secondaryCandidate, surface) >= UiPalette.MIN_TEXT_CONTRAST
                ? secondaryCandidate : primary;
        int accent = AppPreferences.getPlayerAccentColor(context);

        BottomSheetDialog dialog = new BottomSheetDialog(context);
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dim(context, R.dimen.space_20), dim(context, R.dimen.space_8),
                dim(context, R.dimen.space_20), dim(context, R.dimen.space_24));
        ShapeAppearanceModel shape = ShapeAppearanceModel.builder()
                .setTopLeftCornerSize(dim(context, R.dimen.radius_24))
                .setTopRightCornerSize(dim(context, R.dimen.radius_24)).build();
        MaterialShapeDrawable background = new MaterialShapeDrawable(shape);
        background.setFillColor(ColorStateList.valueOf(surface));
        root.setBackground(background);

        View handle = new View(context);
        LinearLayout.LayoutParams handleLp = new LinearLayout.LayoutParams(
                dim(context, R.dimen.music_action_size), dim(context, R.dimen.space_4));
        handleLp.gravity = Gravity.CENTER_HORIZONTAL;
        handleLp.bottomMargin = dim(context, R.dimen.space_16);
        handle.setLayoutParams(handleLp);
        handle.setBackgroundTintList(ColorStateList.valueOf(ColorUtils.setAlphaComponent(primary, 90)));
        root.addView(handle);

        TextView title = new TextView(context);
        title.setText("Barra de progreso");
        title.setTextAppearance(R.style.TextAppearance_AppIncreible_Title);
        title.setTextColor(primary);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleLp.bottomMargin = dim(context, R.dimen.space_8);
        title.setLayoutParams(titleLp);
        root.addView(title);

        String current = AppPreferences.getPlayerProgressStyle(context);
        addOption(root, dialog, context, "Clásica", "Línea recta con punto", AppPreferences.PROGRESS_STYLE_CLASSIC,
                current, primary, secondary, accent, onChanged, null);
        addOption(root, dialog, context, "Ondulada", "Trazo suave en forma de onda", AppPreferences.PROGRESS_STYLE_WAVE,
                current, primary, secondary, accent, onChanged, null);
        addOption(root, dialog, context, "Punteada", "Puntos que se completan con el progreso", AppPreferences.PROGRESS_STYLE_DOTS,
                current, primary, secondary, accent, onChanged, null);
        addOption(root, dialog, context, "Cápsula", "Barra gruesa con extremos redondeados", AppPreferences.PROGRESS_STYLE_CAPSULE,
                current, primary, secondary, accent, onChanged, null);
        String emoji = AppPreferences.getPlayerProgressEmoji(context);
        addOption(root, dialog, context, "Emoji " + emoji, "Usá cualquier emoji como indicador", AppPreferences.PROGRESS_STYLE_EMOJI,
                current, primary, secondary, accent, onChanged, () -> showEmojiPicker(fragment, onChanged));

        dialog.setContentView(root);
        dialog.setOnShowListener(ignored -> {
            FrameLayout sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (sheet != null) sheet.setBackground(new ColorDrawable(Color.TRANSPARENT));
        });
        dialog.show();
    }

    static String label(Context context, String style) {
        if (AppPreferences.PROGRESS_STYLE_WAVE.equals(style)) return "Ondulada";
        if (AppPreferences.PROGRESS_STYLE_DOTS.equals(style)) return "Punteada";
        if (AppPreferences.PROGRESS_STYLE_CAPSULE.equals(style)) return "Cápsula";
        if (AppPreferences.PROGRESS_STYLE_EMOJI.equals(style)) {
            return "Emoji " + AppPreferences.getPlayerProgressEmoji(context);
        }
        return "Clásica";
    }

    private static void addOption(LinearLayout root, BottomSheetDialog dialog, Context context,
                                  String title, String subtitle, String value, String current,
                                  int primary, int secondary, int accent, Runnable onChanged,
                                  @Nullable Runnable afterSelect) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setClickable(true);
        row.setFocusable(true);
        TypedValue selectable = new TypedValue();
        if (context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, selectable, true)
                && selectable.resourceId != 0) {
            row.setBackgroundResource(selectable.resourceId);
        }
        row.setPadding(dim(context, R.dimen.space_8), dim(context, R.dimen.space_12),
                dim(context, R.dimen.space_8), dim(context, R.dimen.space_12));

        LinearLayout labels = new LinearLayout(context);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView main = new TextView(context);
        main.setText(title);
        main.setTextAppearance(R.style.TextAppearance_AppIncreible_Body);
        main.setTypeface(main.getTypeface(), Typeface.BOLD);
        main.setTextColor(primary);
        labels.addView(main);

        TextView detail = new TextView(context);
        detail.setText(subtitle);
        detail.setTextAppearance(R.style.TextAppearance_AppIncreible_Caption);
        detail.setTextColor(secondary);
        detail.setPadding(0, dim(context, R.dimen.space_4), 0, 0);
        labels.addView(detail);
        row.addView(labels);

        if (value.equals(current)) {
            TextView check = new TextView(context);
            check.setText("✓");
            check.setTextAppearance(R.style.TextAppearance_AppIncreible_Title);
            check.setTextColor(accent);
            check.setGravity(Gravity.CENTER);
            check.setLayoutParams(new LinearLayout.LayoutParams(
                    dim(context, R.dimen.music_action_size), dim(context, R.dimen.music_action_size)));
            row.addView(check);
        }

        row.setOnClickListener(v -> {
            AppPreferences.setPlayerProgressStyle(context, value);
            onChanged.run();
            dialog.dismiss();
            if (afterSelect != null) afterSelect.run();
        });
        root.addView(row);
    }

    private static void showEmojiPicker(@NonNull Fragment fragment, @NonNull Runnable onChanged) {
        Context context = fragment.requireContext();
        int pad = dim(context, R.dimen.space_20);
        FrameLayout holder = new FrameLayout(context);
        holder.setPadding(pad, dim(context, R.dimen.space_8), pad, 0);

        TextInputLayout inputLayout = new TextInputLayout(context);
        inputLayout.setHint("Emoji");
        inputLayout.setHelperText("Escribí o pegá cualquier emoji");
        inputLayout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        inputLayout.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextInputEditText input = new TextInputEditText(context);
        input.setSingleLine(true);
        input.setText(AppPreferences.getPlayerProgressEmoji(context));
        input.setSelectAllOnFocus(true);
        input.setTextSize(TypedValue.COMPLEX_UNIT_PX, context.getResources().getDimension(R.dimen.text_headline));
        inputLayout.addView(input, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        holder.addView(inputLayout);

        androidx.appcompat.app.AlertDialog emojiDialog = new MaterialAlertDialogBuilder(context)
                .setTitle("Emoji de la barra")
                .setView(holder)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Usar emoji", null)
                .create();
        emojiDialog.setOnShowListener(ignored -> {
            android.widget.Button apply = emojiDialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE);
            Runnable updateEnabled = () -> apply.setEnabled(input.getText() != null
                    && !input.getText().toString().trim().isEmpty());
            updateEnabled.run();
            apply.setOnClickListener(v -> {
                String value = input.getText() == null ? "" : input.getText().toString().trim();
                if (value.isEmpty()) return;
                AppPreferences.setPlayerProgressEmoji(context, value);
                AppPreferences.setPlayerProgressStyle(context, AppPreferences.PROGRESS_STYLE_EMOJI);
                onChanged.run();
                emojiDialog.dismiss();
            });
            input.requestFocus();
        });
        emojiDialog.show();
    }

    private static int dim(Context context, int resId) {
        return context.getResources().getDimensionPixelSize(resId);
    }

    private static int opaque(int color) {
        return Color.rgb(Color.red(color), Color.green(color), Color.blue(color));
    }
}
