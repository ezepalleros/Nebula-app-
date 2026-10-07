package com.appincreible.musicplayer.ui.common;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.fragment.app.Fragment;

import com.appincreible.musicplayer.R;
import com.appincreible.musicplayer.data.model.Song;
import com.appincreible.musicplayer.power.PowerSaverManager;
import com.appincreible.musicplayer.themes.AppPreferences;
import com.appincreible.musicplayer.themes.AppTypography;
import com.appincreible.musicplayer.themes.UiPalette;
import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.shape.MaterialShapeDrawable;
import com.google.android.material.shape.ShapeAppearanceModel;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

/** Shared modern dialogs/sheets. Contains presentation only; callers keep all business logic. */
public final class ModernUiDialogs {

    public interface SongActions {
        void addToPlaylist();
        void toggleFavorite();
        void toggleHidden();
        void details();
        void album();
        void artist();
        void editMetadata();
        default void removeFromPlaylist() { }
        void deleteFromPhone();
    }

    public interface TextResult { void onResult(@NonNull String value); }

    public interface PlaylistActions {
        void editName();
        void changeArtwork();
        void play();
        void shuffle();
        void delete();
    }

    public interface PlaylistEditorResult {
        void onSave(@NonNull String name, @NonNull String artworkUri);
    }

    public static final class PlaylistEditorHandle {
        private final androidx.appcompat.app.AlertDialog dialog;
        private final ShapeableImageView artwork;
        private String artworkUri;

        PlaylistEditorHandle(androidx.appcompat.app.AlertDialog dialog, ShapeableImageView artwork, String artworkUri) {
            this.dialog = dialog;
            this.artwork = artwork;
            this.artworkUri = artworkUri == null ? "" : artworkUri;
        }

        public boolean isShowing() { return dialog.isShowing(); }
        public String getArtworkUri() { return artworkUri; }
        public void setArtworkUri(String uri) {
            artworkUri = uri == null ? "" : uri;
            if (artworkUri.isEmpty()) {
                Glide.with(artwork).clear(artwork);
                artwork.setImageResource(R.drawable.ic_music);
                artwork.setImageTintList(ColorStateList.valueOf(resolveColor(artwork.getContext(), com.google.android.material.R.attr.colorPrimary, 0xFF8B5CF6)));
            } else {
                artwork.setImageTintList(null);
                Glide.with(artwork).load(Uri.parse(artworkUri)).placeholder(R.drawable.ic_music).error(R.drawable.ic_music).centerCrop().into(artwork);
            }
        }
    }

    private ModernUiDialogs() { }

    public static void showSongActions(@NonNull Fragment fragment,
                                       @NonNull Song song,
                                       boolean hidden,
                                       boolean favorite,
                                       @NonNull SongActions actions) {
        showSongActions(fragment, song, hidden, favorite, false, actions);
    }

    public static void showSongActions(@NonNull Fragment fragment,
                                       @NonNull Song song,
                                       boolean hidden,
                                       boolean favorite,
                                       boolean canRemoveFromPlaylist,
                                       @NonNull SongActions actions) {
        Context context = fragment.requireContext();
        UiPalette palette = UiPalette.fallback(context);
        int surface = opaque(palette.surface);
        int primary = UiPalette.readableTextColor(surface);
        int secondaryCandidate = ColorUtils.blendARGB(primary, surface, 0.28f);
        int secondary = ColorUtils.calculateContrast(secondaryCandidate, surface) >= UiPalette.MIN_TEXT_CONTRAST
                ? secondaryCandidate : primary;
        int accent = ensureTextContrast(AppPreferences.getPlayerAccentColor(context), surface);

        BottomSheetDialog dialog = new BottomSheetDialog(context);
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(context, 20), dp(context, 10), dp(context, 20), dp(context, 24));

        ShapeAppearanceModel shape = ShapeAppearanceModel.builder()
                .setTopLeftCornerSize(dp(context, 28))
                .setTopRightCornerSize(dp(context, 28))
                .build();
        MaterialShapeDrawable background = new MaterialShapeDrawable(shape);
        background.setFillColor(ColorStateList.valueOf(surface));
        root.setBackground(background);

        View handle = new View(context);
        LinearLayout.LayoutParams handleLp = new LinearLayout.LayoutParams(dp(context, 38), dp(context, 4));
        handleLp.gravity = Gravity.CENTER_HORIZONTAL;
        handleLp.bottomMargin = dp(context, 16);
        handle.setLayoutParams(handleLp);
        handle.setBackgroundTintList(ColorStateList.valueOf(ColorUtils.setAlphaComponent(primary, 90)));
        root.addView(handle);

        LinearLayout header = new LinearLayout(context);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setPadding(0, 0, 0, dp(context, 12));

        ShapeableImageView artwork = new ShapeableImageView(context);
        LinearLayout.LayoutParams artLp = new LinearLayout.LayoutParams(dp(context, 58), dp(context, 58));
        artLp.setMarginEnd(dp(context, 14));
        artwork.setLayoutParams(artLp);
        artwork.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        artwork.setBackgroundResource(R.drawable.bg_artwork_small);
        artwork.setShapeAppearanceModel(ShapeAppearanceModel.builder()
                .setAllCornerSizes(dp(context, 12)).build());
        Glide.with(artwork)
                .load(song.getArtworkUri().isEmpty() ? null : song.getArtworkUri())
                .placeholder(R.drawable.ic_music)
                .error(R.drawable.ic_music)
                .centerCrop()
                .into(artwork);
        header.addView(artwork);

        LinearLayout labels = new LinearLayout(context);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView title = text(context, song.getTitle(), 17f, primary, Typeface.BOLD);
        title.setMaxLines(2);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        TextView artist = text(context, song.getArtist(), 14f, secondary, Typeface.NORMAL);
        artist.setMaxLines(1);
        artist.setEllipsize(android.text.TextUtils.TruncateAt.END);
        labels.addView(title);
        labels.addView(artist);
        header.addView(labels);
        root.addView(header);

        addAction(root, context, "Añadir a playlist", android.R.drawable.ic_input_add, primary, actions::addToPlaylist, dialog);
        addAction(root, context, favorite ? "Quitar de favoritos" : "Añadir a favoritos",
                favorite ? R.drawable.ic_star_filled : R.drawable.ic_star, primary, actions::toggleFavorite, dialog);
        addAction(root, context, hidden ? "Mostrar de nuevo" : "Ocultar",
                android.R.drawable.ic_menu_close_clear_cancel, primary, actions::toggleHidden, dialog);
        addAction(root, context, "Detalles de la pista", android.R.drawable.ic_menu_info_details, primary, actions::details, dialog);
        if (AppPreferences.libraryAlbumsEnabled(context)) {
            addAction(root, context, "Álbum", R.drawable.ic_library, primary, actions::album, dialog);
        }
        if (AppPreferences.libraryArtistsEnabled(context)) {
            addAction(root, context, "Artista", android.R.drawable.ic_menu_myplaces, primary, actions::artist, dialog);
        }
        addAction(root, context, "Editar información y portada", android.R.drawable.ic_menu_edit, primary, actions::editMetadata, dialog);
        if (canRemoveFromPlaylist) {
            addAction(root, context, "Quitar de esta playlist", android.R.drawable.ic_menu_close_clear_cancel,
                    primary, actions::removeFromPlaylist, dialog);
        }

        View divider = new View(context);
        LinearLayout.LayoutParams dividerLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 1));
        dividerLp.topMargin = dp(context, 8);
        dividerLp.bottomMargin = dp(context, 8);
        divider.setLayoutParams(dividerLp);
        divider.setBackgroundColor(ColorUtils.setAlphaComponent(primary, 28));
        root.addView(divider);

        int error = ensureTextContrast(resolveColor(context, com.google.android.material.R.attr.colorError, 0xFFBA1A1A), surface);
        addAction(root, context, "Eliminar del teléfono", android.R.drawable.ic_menu_delete, error, actions::deleteFromPhone, dialog);

        dialog.setContentView(root);
        dialog.setOnShowListener(ignored -> {
            FrameLayout sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (sheet != null) sheet.setBackground(new ColorDrawable(Color.TRANSPARENT));
            applyMotionPolicy(dialog.getWindow(), context);
        });
        dialog.show();
    }

    public static void showPlaylistActions(@NonNull Fragment fragment,
                                           @NonNull String title,
                                           boolean editable,
                                           @NonNull PlaylistActions actions) {
        Context context = fragment.requireContext();
        UiPalette palette = UiPalette.fallback(context);
        int surface = opaque(palette.surface);
        int primary = UiPalette.readableTextColor(surface);
        int accent = ensureTextContrast(AppPreferences.getPlayerAccentColor(context), surface);
        int error = ensureTextContrast(resolveColor(context, com.google.android.material.R.attr.colorError, 0xFFBA1A1A), surface);

        BottomSheetDialog dialog = new BottomSheetDialog(context);
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(context, 20), dp(context, 10), dp(context, 20), dp(context, 24));
        ShapeAppearanceModel shape = ShapeAppearanceModel.builder()
                .setTopLeftCornerSize(dp(context, 28)).setTopRightCornerSize(dp(context, 28)).build();
        MaterialShapeDrawable background = new MaterialShapeDrawable(shape);
        background.setFillColor(ColorStateList.valueOf(surface));
        root.setBackground(background);

        View handle = new View(context);
        LinearLayout.LayoutParams handleLp = new LinearLayout.LayoutParams(dp(context, 38), dp(context, 4));
        handleLp.gravity = Gravity.CENTER_HORIZONTAL;
        handleLp.bottomMargin = dp(context, 14);
        handle.setLayoutParams(handleLp);
        handle.setBackgroundTintList(ColorStateList.valueOf(ColorUtils.setAlphaComponent(primary, 90)));
        root.addView(handle);

        TextView heading = text(context, title, 18f, primary, Typeface.BOLD);
        heading.setMaxLines(2);
        heading.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams headingLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        headingLp.setMargins(dp(context, 8), 0, dp(context, 8), dp(context, 10));
        heading.setLayoutParams(headingLp);
        root.addView(heading);

        if (editable) addAction(root, context, "Editar nombre", android.R.drawable.ic_menu_edit, primary, actions::editName, dialog);
        addAction(root, context, "Cambiar portada", android.R.drawable.ic_menu_gallery, primary, actions::changeArtwork, dialog);
        addAction(root, context, "Reproducir", R.drawable.ic_play_arrow, primary, actions::play, dialog);
        addAction(root, context, "Reproducir aleatorio", R.drawable.ic_shuffle, accent, actions::shuffle, dialog);

        if (editable) {
            View divider = new View(context);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 1));
            lp.topMargin = dp(context, 8);
            lp.bottomMargin = dp(context, 8);
            divider.setLayoutParams(lp);
            divider.setBackgroundColor(ColorUtils.setAlphaComponent(primary, 28));
            root.addView(divider);
            addAction(root, context, "Eliminar playlist", android.R.drawable.ic_menu_delete, error, actions::delete, dialog);
        }

        dialog.setContentView(root);
        dialog.setOnShowListener(ignored -> {
            FrameLayout sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (sheet != null) sheet.setBackground(new ColorDrawable(Color.TRANSPARENT));
            applyMotionPolicy(dialog.getWindow(), context);
        });
        dialog.show();
    }

    public static PlaylistEditorHandle showPlaylistEditor(@NonNull Fragment fragment,
                                                           @NonNull String dialogTitle,
                                                           @NonNull String initialName,
                                                           @NonNull String initialArtworkUri,
                                                           @NonNull String saveLabel,
                                                           @NonNull Runnable chooseArtwork,
                                                           @NonNull PlaylistEditorResult callback) {
        Context context = fragment.requireContext();
        int pad = dp(context, 24);
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(pad, dp(context, 8), pad, 0);

        MaterialCardView artCard = new MaterialCardView(context);
        LinearLayout.LayoutParams artCardLp = new LinearLayout.LayoutParams(dp(context, 112), dp(context, 112));
        artCardLp.bottomMargin = dp(context, 18);
        artCard.setLayoutParams(artCardLp);
        artCard.setRadius(dp(context, 22));
        artCard.setCardElevation(dp(context, 2));
        artCard.setClickable(true);
        artCard.setFocusable(true);
        ShapeableImageView artwork = new ShapeableImageView(context);
        artwork.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        artwork.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        artwork.setContentDescription("Elegir portada");
        artCard.addView(artwork);
        content.addView(artCard);

        TextInputLayout inputLayout = new TextInputLayout(context);
        inputLayout.setHint("Nombre de la playlist");
        inputLayout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        inputLayout.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextInputEditText input = new TextInputEditText(context);
        input.setSingleLine(true);
        input.setText(initialName);
        input.setSelectAllOnFocus(initialName.length() > 0);
        input.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        inputLayout.addView(input, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        content.addView(inputLayout);

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setTitle(dialogTitle)
                .setView(content)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton(saveLabel, null)
                .create();
        PlaylistEditorHandle handle = new PlaylistEditorHandle(dialog, artwork, initialArtworkUri);
        handle.setArtworkUri(initialArtworkUri);
        artCard.setOnClickListener(v -> chooseArtwork.run());

        dialog.setOnShowListener(ignored -> {
            android.widget.Button save = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE);
            int buttonAccent = AppPreferences.getPlayerAccentColor(context);
            save.setBackgroundTintList(ColorStateList.valueOf(buttonAccent));
            save.setTextColor(UiPalette.readableTextColor(buttonAccent));
            save.setMinHeight(dp(context, 48));
            Runnable updateEnabled = () -> save.setEnabled(input.getText() != null && !input.getText().toString().trim().isEmpty());
            updateEnabled.run();
            save.setOnClickListener(v -> {
                String name = input.getText() == null ? "" : input.getText().toString().trim();
                if (name.isEmpty()) return;
                callback.onSave(name, handle.getArtworkUri());
                dialog.dismiss();
            });
            input.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) { updateEnabled.run(); }
                @Override public void afterTextChanged(Editable s) { }
            });
            input.requestFocus();
            Window window = dialog.getWindow();
            if (window != null) {
                applyMotionPolicy(window, context);
                window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            }
            input.postDelayed(() -> {
                InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
            }, 120L);
        });
        dialog.show();
        return handle;
    }

    public static void showCreatePlaylist(@NonNull Fragment fragment, @NonNull TextResult callback) {
        Context context = fragment.requireContext();
        int pad = dp(context, 24);
        FrameLayout holder = new FrameLayout(context);
        holder.setPadding(pad, dp(context, 8), pad, 0);

        TextInputLayout inputLayout = new TextInputLayout(context);
        inputLayout.setHint("Nombre de la playlist");
        inputLayout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        inputLayout.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextInputEditText input = new TextInputEditText(context);
        input.setSingleLine(true);
        input.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        // TextInputLayout extends LinearLayout and expects LinearLayout.LayoutParams for its EditText child.
        // Using generic ViewGroup.LayoutParams crashes inside Material's updateInputLayoutMargins().
        inputLayout.addView(input, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        holder.addView(inputLayout);

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setTitle("Nueva playlist")
                .setView(holder)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Crear", null)
                .create();

        dialog.setOnShowListener(ignored -> {
            android.widget.Button create = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE);
            create.setEnabled(false);
            create.setOnClickListener(v -> {
                String value = input.getText() == null ? "" : input.getText().toString().trim();
                if (value.isEmpty()) return;
                callback.onResult(value);
                dialog.dismiss();
            });
            input.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    create.setEnabled(s != null && !s.toString().trim().isEmpty());
                }
                @Override public void afterTextChanged(Editable s) { }
            });
            input.requestFocus();
            Window window = dialog.getWindow();
            if (window != null) {
                applyMotionPolicy(window, context);
                window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            }
            input.postDelayed(() -> {
                InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
            }, 120L);
        });
        dialog.show();
    }

    public static void showTypographyPicker(@NonNull Fragment fragment, @NonNull Runnable onApplied) {
        Context context = fragment.requireContext();
        String[] labels = {"Moderna", "Redondeada", "Serif clásica", "Monoespaciada", "Condensada"};
        String[] values = {AppPreferences.TYPOGRAPHY_MODERN, AppPreferences.TYPOGRAPHY_ROUNDED,
                AppPreferences.TYPOGRAPHY_SERIF, AppPreferences.TYPOGRAPHY_MONO,
                AppPreferences.TYPOGRAPHY_CONDENSED};
        String selected = AppPreferences.getPlayerTypography(context);
        UiPalette palette = UiPalette.fallback(context);
        int surface = opaque(palette.surface);
        int primary = UiPalette.readableTextColor(surface);
        int accent = ensureTextContrast(AppPreferences.getPlayerAccentColor(context), surface);

        LinearLayout list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(context, 12), dp(context, 4), dp(context, 12), dp(context, 8));
        ScrollView scroll = new ScrollView(context);
        scroll.addView(list);

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setTitle("Tipografía")
                .setView(scroll)
                .setNegativeButton("Cancelar", null)
                .create();

        for (int i = 0; i < values.length; i++) {
            final String value = values[i];
            MaterialCardView card = new MaterialCardView(context);
            LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cardLp.bottomMargin = dp(context, 8);
            card.setLayoutParams(cardLp);
            card.setRadius(dp(context, 16));
            card.setCardBackgroundColor(Color.TRANSPARENT);
            card.setStrokeWidth(selected.equals(value) ? dp(context, 2) : dp(context, 1));
            card.setStrokeColor(selected.equals(value) ? accent : ColorUtils.setAlphaComponent(primary, 48));
            card.setClickable(true);
            card.setFocusable(true);

            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(context, 16), dp(context, 12), dp(context, 12), dp(context, 12));
            LinearLayout copy = new LinearLayout(context);
            copy.setOrientation(LinearLayout.VERTICAL);
            copy.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView label = text(context, labels[i], 15f, primary, Typeface.BOLD);
            TextView sample = text(context, "Aa Bb Cc · Tu música, a tu manera", 17f, primary, Typeface.NORMAL);
            label.setTypeface(AppTypography.preview(context, value, Typeface.BOLD));
            sample.setTypeface(AppTypography.preview(context, value, Typeface.NORMAL));
            copy.addView(label);
            copy.addView(sample);
            row.addView(copy);
            TextView check = text(context, selected.equals(value) ? "✓" : "", 22f, accent, Typeface.BOLD);
            check.setGravity(Gravity.CENTER);
            check.setMinWidth(dp(context, 36));
            row.addView(check);
            card.addView(row);
            card.setOnClickListener(v -> {
                AppPreferences.prefs(context).edit().putString(AppPreferences.KEY_PLAYER_TYPOGRAPHY, value).commit();
                View decor = fragment.requireActivity().getWindow().getDecorView();
                AppTypography.refresh(decor);
                onApplied.run();
                dialog.dismiss();
            });
            list.addView(card);
        }

        dialog.setOnShowListener(ignored -> applyMotionPolicy(dialog.getWindow(), context));
        dialog.show();
    }

    private static void addAction(LinearLayout root, Context context, String label,
                                  @DrawableRes int icon, int color, Runnable action,
                                  BottomSheetDialog dialog) {
        MaterialButton button = new MaterialButton(context);
        button.setBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
        button.setText(label);
        button.setTextColor(color);
        button.setTextSize(16f);
        button.setAllCaps(false);
        button.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        button.setIconResource(icon);
        button.setIconTint(ColorStateList.valueOf(color));
        button.setIconSize(dp(context, 22));
        button.setIconPadding(dp(context, 16));
        button.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
        button.setMinHeight(dp(context, 52));
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setMaxLines(2);
        button.setEllipsize(android.text.TextUtils.TruncateAt.END);
        button.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        button.setOnClickListener(v -> {
            // Wait until this sheet is fully gone before opening another dialog/sheet.
            // Some OEM window managers (notably Samsung) can drop the next window if
            // it is shown while the current BottomSheetDialog is still animating out.
            dialog.setOnDismissListener(ignored ->
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(action));
            dialog.dismiss();
        });
        root.addView(button);
    }

    private static TextView text(Context context, String value, float sizeSp, int color, int style) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        view.setTypeface(AppTypography.get(context, style));
        return view;
    }

    private static int ensureTextContrast(int desired, int background) {
        int bg = opaque(background);
        int candidate = opaque(desired);
        if (ColorUtils.calculateContrast(candidate, bg) >= UiPalette.MIN_TEXT_CONTRAST) return candidate;
        int target = UiPalette.readableTextColor(bg);
        for (int i = 1; i <= 10; i++) {
            candidate = ColorUtils.blendARGB(opaque(desired), target, i / 10f);
            if (ColorUtils.calculateContrast(candidate, bg) >= UiPalette.MIN_TEXT_CONTRAST) return candidate;
        }
        return target;
    }

    private static int resolveColor(Context context, int attr, int fallback) {
        android.util.TypedValue out = new android.util.TypedValue();
        if (context.getTheme().resolveAttribute(attr, out, true)) {
            if (out.resourceId != 0) return androidx.core.content.ContextCompat.getColor(context, out.resourceId);
            return out.data;
        }
        return fallback;
    }

    private static int opaque(int color) {
        return Color.rgb(Color.red(color), Color.green(color), Color.blue(color));
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static void applyMotionPolicy(Window window, Context context) {
        if (window != null && PowerSaverManager.isActive(context)) window.setWindowAnimations(0);
    }
}
