package com.appincreible.musicplayer.ui.metadata;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import com.appincreible.musicplayer.R;
import com.appincreible.musicplayer.metadata.model.ArtworkOption;
import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.imageview.ShapeableImageView;

import java.util.List;

/** Candidate preview and selection. Search and image validation stay in repositories. */
final class ArtworkResultsSheet {
    interface Actions {
        void select(ArtworkOption option);
        void pickFile();
    }

    private final Context context;
    private final BottomSheetDialog dialog;
    private final LinearLayout results;
    private final ProgressBar progress;
    private final TextView status;
    private final Actions actions;

    ArtworkResultsSheet(Context context, Actions actions, Runnable onDismiss) {
        this.context = context;
        this.actions = actions;
        dialog = new BottomSheetDialog(context);
        dialog.setOnDismissListener(ignored -> onDismiss.run());
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(20), dp(16), dp(16));
        root.setBackgroundColor(MaterialColors.getColor(root, com.google.android.material.R.attr.colorSurface));

        TextView heading = label("Portadas online", com.google.android.material.R.style.TextAppearance_Material3_TitleLarge);
        root.addView(heading);
        TextView hint = label("Deezer e iTunes · Elegí una portada para la canción",
                com.google.android.material.R.style.TextAppearance_Material3_BodySmall);
        hint.setTextColor(MaterialColors.getColor(hint, com.google.android.material.R.attr.colorOnSurfaceVariant));
        root.addView(hint);

        progress = new ProgressBar(context);
        LinearLayout.LayoutParams progressLp = new LinearLayout.LayoutParams(dp(36), dp(36));
        progressLp.gravity = Gravity.CENTER_HORIZONTAL;
        progressLp.topMargin = dp(18);
        root.addView(progress, progressLp);
        status = label("Buscando portadas…", com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, dp(12), 0, dp(12));
        root.addView(status);

        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(false);
        scroll.setClipToPadding(false);
        results = new LinearLayout(context);
        results.setOrientation(LinearLayout.VERTICAL);
        results.setPadding(0, dp(8), 0, dp(8));
        scroll.addView(results);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                0, 1f);
        root.addView(scroll, slp);

        MaterialButton pick = new MaterialButton(context);
        pick.setText("Elegir imagen del teléfono");
        pick.setOnClickListener(v -> { dialog.dismiss(); actions.pickFile(); });
        root.addView(pick);
        dialog.setContentView(root);
        android.util.DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        // Fits smaller screens and leaves room for system bars.
        int height = Math.round(metrics.heightPixels * 0.78f);
        ViewGroup.LayoutParams lp = root.getLayoutParams();
        if (lp != null) { lp.height = height; root.setLayoutParams(lp); }
    }

    void show() { dialog.show(); }
    void dismiss() { dialog.dismiss(); }
    boolean isShowing() { return dialog.isShowing(); }

    void setResults(List<ArtworkOption> options) {
        if (!isShowing()) return;
        progress.setVisibility(View.GONE);
        status.setVisibility(View.GONE);
        results.removeAllViews();
        for (ArtworkOption result : options) results.addView(candidate(result));
    }

    void setError(String message) {
        if (!isShowing()) return;
        progress.setVisibility(View.GONE);
        status.setVisibility(View.VISIBLE);
        status.setText(message);
        results.removeAllViews();
    }

    void showDownloading() {
        if (!isShowing()) return;
        progress.setVisibility(View.VISIBLE);
        status.setVisibility(View.VISIBLE);
        status.setText("Descargando y verificando la portada…");
        results.setEnabled(false);
        for (int n = 0; n < results.getChildCount(); n++) results.getChildAt(n).setEnabled(false);
    }

    void showDownloadError(String error) {
        if (!isShowing()) return;
        progress.setVisibility(View.GONE);
        status.setVisibility(View.VISIBLE);
        status.setText("No se pudo usar la imagen: " + error);
        results.setEnabled(true);
        for (int n = 0; n < results.getChildCount(); n++) results.getChildAt(n).setEnabled(true);
    }

    private View candidate(ArtworkOption option) {
        MaterialCardView card = new MaterialCardView(context);
        card.setRadius(context.getResources().getDimension(R.dimen.radius_16));
        card.setCardBackgroundColor(MaterialColors.getColor(card,
                com.google.android.material.R.attr.colorSurfaceContainerLow));
        card.setCardElevation(0f);
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(12), dp(12), dp(12), dp(12));
        row.setGravity(Gravity.CENTER_VERTICAL);
        ShapeableImageView cover = new ShapeableImageView(context);
        cover.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        cover.setBackgroundResource(R.drawable.bg_artwork_small);
        row.addView(cover, new LinearLayout.LayoutParams(dp(76), dp(76)));
        Glide.with(cover).load(option.getFallbackUrl().isEmpty() ? option.getImageUrl() : option.getFallbackUrl())
                .placeholder(R.drawable.bg_artwork_small).error(R.drawable.bg_artwork_small)
                .centerCrop().override(dp(120), dp(120)).into(cover);
        LinearLayout info = new LinearLayout(context);
        info.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        infoLp.leftMargin = dp(12);
        row.addView(info, infoLp);
        TextView title = label(option.getTitle(), com.google.android.material.R.style.TextAppearance_Material3_TitleMedium);
        title.setMaxLines(2);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        info.addView(title);
        TextView artist = label(option.getArtist(), com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
        info.addView(artist);
        TextView album = label(option.getAlbum(), com.google.android.material.R.style.TextAppearance_Material3_BodySmall);
        album.setMaxLines(1);
        album.setEllipsize(android.text.TextUtils.TruncateAt.END);
        album.setTextColor(MaterialColors.getColor(album, com.google.android.material.R.attr.colorOnSurfaceVariant));
        info.addView(album);
        TextView source = label(option.getSource() + (option.isRecommended() ? " · Recomendada" : ""),
                com.google.android.material.R.style.TextAppearance_Material3_LabelMedium);
        source.setTextColor(MaterialColors.getColor(source, com.google.android.material.R.attr.colorPrimary));
        info.addView(source);
        card.addView(row);
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(v -> actions.select(option));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        card.setLayoutParams(lp);
        return card;
    }

    private TextView label(String text, int appearance) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextAppearance(appearance);
        return view;
    }
    private int dp(int pixels) { return Math.round(context.getResources().getDisplayMetrics().density * pixels); }
}
