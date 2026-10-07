package com.appincreible.musicplayer.ui.player;

import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.core.graphics.ColorUtils;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.appincreible.musicplayer.R;
import com.appincreible.musicplayer.databinding.ItemSongBinding;
import com.appincreible.musicplayer.player.controller.PlayerController;
import com.appincreible.musicplayer.power.PowerSaverManager;
import com.appincreible.musicplayer.themes.AppPreferences;
import com.appincreible.musicplayer.themes.UiPalette;
import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.shape.MaterialShapeDrawable;
import com.google.android.material.shape.ShapeAppearanceModel;

import java.util.ArrayList;
import java.util.List;

/** Live song queue. Radio deliberately never opens this sheet. */
public final class QueueBottomSheet extends BottomSheetDialogFragment {
    private static final int PAGE_SIZE = 40;
    private static final int INLINE_PREVIEW_SIZE = 2;

    private PlayerViewModel viewModel;
    private QueueAdapter adapter;
    private TextView emptyView;

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        dialog.setOnShowListener(ignored -> {
            FrameLayout sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (sheet != null) {
                sheet.setBackground(new ColorDrawable(Color.TRANSPARENT));
                ViewGroup.LayoutParams params = sheet.getLayoutParams();
                params.height = ViewGroup.LayoutParams.MATCH_PARENT;
                sheet.setLayoutParams(params);
                BottomSheetBehavior<FrameLayout> behavior = dialog.getBehavior();
                behavior.setFitToContents(false);
                behavior.setExpandedOffset(dim(R.dimen.space_24));
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                behavior.setSkipCollapsed(true);
            }
            disableAnimationsForPowerSaver(dialog.getWindow());
        });
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        Context context = requireContext();
        UiPalette palette = UiPalette.fallback(context);
        int surface = opaque(palette.surface);
        int text = contrastSafeText(surface, palette.onSurface);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dim(R.dimen.space_20), dim(R.dimen.space_12),
                dim(R.dimen.space_20), dim(R.dimen.space_20));
        ShapeAppearanceModel shape = ShapeAppearanceModel.builder()
                .setTopLeftCornerSize(dim(R.dimen.radius_24))
                .setTopRightCornerSize(dim(R.dimen.radius_24))
                .build();
        MaterialShapeDrawable background = new MaterialShapeDrawable(shape);
        background.setFillColor(ColorStateList.valueOf(surface));
        root.setBackground(background);

        TextView title = new TextView(context);
        title.setText("Cola de reproducción");
        title.setTextAppearance(R.style.TextAppearance_AppIncreible_Title);
        title.setTextColor(text);
        title.setPadding(dim(R.dimen.space_4), 0, dim(R.dimen.space_4), dim(R.dimen.space_12));
        root.addView(title);

        emptyView = new TextView(context);
        emptyView.setText("No hay canciones en la cola.");
        emptyView.setTextAppearance(R.style.TextAppearance_AppIncreible_Body);
        emptyView.setTextColor(text);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setPadding(0, dim(R.dimen.space_24), 0, dim(R.dimen.space_24));
        emptyView.setVisibility(View.GONE);
        root.addView(emptyView);

        RecyclerView list = new RecyclerView(context);
        list.setLayoutManager(new LinearLayoutManager(context));
        list.setItemAnimator(null);
        adapter = new QueueAdapter(this::openRemoveMenu, entry -> viewModel.jumpToQueueItem(entry.mediaItemIndex));
        adapter.setExpanded(true);
        list.setAdapter(adapter);
        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        list.setLayoutParams(listParams);
        list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (dy >= 0 && !recyclerView.canScrollVertically(1)) adapter.loadMore();
            }
        });
        root.addView(list);
        return root;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(PlayerViewModel.class);
        viewModel.getQueue().observe(getViewLifecycleOwner(), items -> {
            List<PlayerController.QueueEntry> safe = items == null ? new ArrayList<>() : items;
            adapter.submit(safe);
            emptyView.setVisibility(safe.isEmpty() ? View.VISIBLE : View.GONE);
        });
    }

    private void openRemoveMenu(PlayerController.QueueEntry entry) {
        showRemoveMenu(this, viewModel, entry);
    }

    static void showRemoveMenu(@NonNull Fragment host, @NonNull PlayerViewModel viewModel,
                               @NonNull PlayerController.QueueEntry entry) {
        Context context = host.requireContext();
        UiPalette palette = UiPalette.fallback(context);
        int surface = opaque(palette.surface);
        int text = contrastSafeText(surface, palette.onSurface);

        BottomSheetDialog dialog = new BottomSheetDialog(context);
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dim(host, R.dimen.space_20), dim(host, R.dimen.space_16),
                dim(host, R.dimen.space_20), dim(host, R.dimen.space_20));
        MaterialShapeDrawable background = new MaterialShapeDrawable(
                ShapeAppearanceModel.builder()
                        .setTopLeftCornerSize(dim(host, R.dimen.radius_24))
                        .setTopRightCornerSize(dim(host, R.dimen.radius_24))
                        .build());
        background.setFillColor(ColorStateList.valueOf(surface));
        root.setBackground(background);

        TextView title = new TextView(context);
        title.setText(entry.title);
        title.setMaxLines(2);
        title.setTextAppearance(R.style.TextAppearance_AppIncreible_Title);
        title.setTextColor(text);
        title.setPadding(dim(host, R.dimen.space_4), 0, dim(host, R.dimen.space_4),
                dim(host, R.dimen.space_12));
        root.addView(title);

        MaterialButton remove = new MaterialButton(context);
        remove.setText("Quitar de la cola");
        remove.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        remove.setTextColor(text);
        remove.setIconResource(android.R.drawable.ic_menu_close_clear_cancel);
        remove.setIconTint(ColorStateList.valueOf(text));
        remove.setBackgroundTintList(ColorStateList.valueOf(surface));
        remove.setOnClickListener(v -> {
            viewModel.removeQueueItem(entry.mediaItemIndex);
            dialog.dismiss();
        });
        root.addView(remove, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        dialog.setContentView(root);
        dialog.setOnShowListener(ignored -> {
            FrameLayout sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (sheet != null) sheet.setBackground(new ColorDrawable(Color.TRANSPARENT));
            Window window = dialog.getWindow();
            if (window != null && PowerSaverManager.isActive(context)) window.setWindowAnimations(0);
        });
        dialog.show();
    }

    private static int dim(@NonNull Fragment host, int resId) {
        return host.getResources().getDimensionPixelSize(resId);
    }

    private void disableAnimationsForPowerSaver(Window window) {
        if (window != null && PowerSaverManager.isActive(requireContext())) window.setWindowAnimations(0);
    }

    private int dim(int resId) {
        return getResources().getDimensionPixelSize(resId);
    }

    private static int contrastSafeText(int surface, int preferred) {
        int background = opaque(surface);
        int text = opaque(preferred);
        return ColorUtils.calculateContrast(text, background) >= UiPalette.MIN_TEXT_CONTRAST
                ? text : UiPalette.readableTextColor(background);
    }

    private static int opaque(int color) {
        return Color.rgb(Color.red(color), Color.green(color), Color.blue(color));
    }

    static final class QueueAdapter extends RecyclerView.Adapter<QueueAdapter.Holder> {
        interface RemoveListener { void onRemove(PlayerController.QueueEntry entry); }
        interface ClickListener { void onClick(PlayerController.QueueEntry entry); }

        private final List<PlayerController.QueueEntry> all = new ArrayList<>();
        private final RemoveListener removeListener;
        private final ClickListener clickListener;
        private int visibleCount;
        private boolean expanded;

        QueueAdapter(RemoveListener removeListener, ClickListener clickListener) {
            this.removeListener = removeListener;
            this.clickListener = clickListener;
        }

        void submit(List<PlayerController.QueueEntry> items) {
            all.clear();
            all.addAll(items);
            visibleCount = expanded ? Math.min(PAGE_SIZE, all.size()) : previewCount();
            notifyDataSetChanged();
        }

        void setExpanded(boolean expanded) {
            if (this.expanded == expanded) return;
            this.expanded = expanded;
            visibleCount = expanded ? Math.min(PAGE_SIZE, all.size()) : previewCount();
            notifyDataSetChanged();
        }

        private int previewStart() {
            for (int i = 0; i < all.size(); i++) {
                if (all.get(i).current) return i + 1;
            }
            return Math.min(1, all.size());
        }

        private int previewCount() {
            int remaining = Math.max(0, all.size() - previewStart());
            return Math.min(INLINE_PREVIEW_SIZE, remaining);
        }

        private PlayerController.QueueEntry displayedEntry(int position) {
            return all.get(expanded ? position : previewStart() + position);
        }

        void loadMore() {
            if (!expanded || visibleCount >= all.size()) return;
            int old = visibleCount;
            visibleCount = Math.min(all.size(), visibleCount + PAGE_SIZE);
            notifyItemRangeInserted(old, visibleCount - old);
        }

        @NonNull
        @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(ItemSongBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
            holder.bind(displayedEntry(position), expanded, position);
        }

        @Override public int getItemCount() { return visibleCount; }

        final class Holder extends RecyclerView.ViewHolder {
            private final ItemSongBinding row;

            Holder(ItemSongBinding row) {
                super(row.getRoot());
                this.row = row;
                row.selectionCheck.setVisibility(View.GONE);
                row.dragHandle.setVisibility(View.GONE);
            }

            void bind(PlayerController.QueueEntry entry, boolean expanded, int displayPosition) {
                Context context = row.getRoot().getContext();
                ViewGroup.LayoutParams rootParams = row.getRoot().getLayoutParams();
                int rowHeight = context.getResources().getDimensionPixelSize(
                        expanded ? R.dimen.music_row_height : R.dimen.music_action_size);
                if (rootParams == null) {
                    rootParams = new RecyclerView.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, rowHeight);
                } else {
                    rootParams.height = rowHeight;
                }
                row.getRoot().setLayoutParams(rootParams);

                int artworkSize = context.getResources().getDimensionPixelSize(
                        expanded ? R.dimen.music_artwork_size : R.dimen.bottom_nav_icon_size);
                ViewGroup.LayoutParams artworkParams = row.artwork.getLayoutParams();
                artworkParams.width = artworkSize;
                artworkParams.height = artworkSize;
                row.artwork.setLayoutParams(artworkParams);
                row.moreButton.setVisibility(expanded ? View.VISIBLE : View.GONE);
                row.getRoot().setTranslationY(!expanded && displayPosition > 0
                        ? -context.getResources().getDimensionPixelSize(R.dimen.space_8) : 0f);
                float previewAlpha = context.getResources().getFraction(
                        R.fraction.player_queue_preview_alpha, 1, 1);
                row.getRoot().setAlpha(expanded ? 1f
                        : (displayPosition == 0 ? previewAlpha : previewAlpha * previewAlpha));

                UiPalette palette = UiPalette.fallback(context);
                int surface = opaque(palette.surface);
                int normalText = contrastSafeText(surface, palette.onSurface);
                int accentText = contrastSafeText(surface,
                        AppPreferences.getPlayerAccentColor(row.getRoot().getContext()));

                row.songTitle.setText(entry.title);
                row.songArtist.setText(entry.artist);
                row.songTitle.setTextColor(entry.current ? accentText : normalText);
                row.songArtist.setTextColor(normalText);
                row.playingBar.setVisibility(entry.current ? View.VISIBLE : View.GONE);
                row.moreButton.setVisibility(expanded ? View.VISIBLE : View.GONE);
                row.moreButton.setColorFilter(normalText);
                row.getRoot().setContentDescription(entry.current
                        ? "Sonando ahora: " + entry.title : entry.title);

                if (entry.artworkUri.isEmpty()) {
                    Glide.with(row.artwork).load((Object) null)
                            .placeholder(R.drawable.ic_music).error(R.drawable.ic_music).into(row.artwork);
                } else {
                    Glide.with(row.artwork).load(Uri.parse(entry.artworkUri))
                            .placeholder(R.drawable.ic_music).error(R.drawable.ic_music).centerCrop().into(row.artwork);
                }
                row.getRoot().setOnClickListener(v -> clickListener.onClick(entry));
                row.moreButton.setOnClickListener(v -> removeListener.onRemove(entry));
            }
        }
    }
}
