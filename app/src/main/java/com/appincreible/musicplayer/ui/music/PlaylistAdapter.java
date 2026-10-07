package com.appincreible.musicplayer.ui.music;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.RecyclerView;

import com.appincreible.musicplayer.R;
import com.appincreible.musicplayer.databinding.ItemSongBinding;
import com.appincreible.musicplayer.themes.AppPreferences;
import com.appincreible.musicplayer.themes.AppTypography;
import com.appincreible.musicplayer.themes.UiPalette;
import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Playlist/library entries deliberately reuse the exact same visual row as songs. */
public class PlaylistAdapter extends RecyclerView.Adapter<PlaylistAdapter.Holder> {
    public interface Listener {
        void onEntryClick(LibraryEntry entry);
        void onEntryMenu(LibraryEntry entry, View anchor);
        void onDragRequested(@NonNull RecyclerView.ViewHolder holder);
    }

    private final Listener listener;
    private final List<LibraryEntry> items = new ArrayList<>();
    private boolean hasAdaptiveColors;
    private int primaryText;
    private int secondaryText;
    private int accent;

    public PlaylistAdapter(Listener listener) {
        this.listener = listener;
        setHasStableIds(true);
    }

    public void submitList(List<LibraryEntry> newItems) {
        items.clear();
        if (newItems != null) items.addAll(newItems);
        notifyDataSetChanged();
    }

    public List<LibraryEntry> getCurrentList() { return Collections.unmodifiableList(items); }

    public List<String> getStableIds() {
        List<String> ids = new ArrayList<>(items.size());
        for (LibraryEntry item : items) ids.add(item.stableId);
        return ids;
    }

    public boolean moveItem(int from, int to) {
        if (from < 0 || to < 0 || from >= items.size() || to >= items.size() || from == to) return false;
        Collections.swap(items, from, to);
        notifyItemMoved(from, to);
        return true;
    }

    public void refreshArtwork(String stableId) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).stableId.equals(stableId)) {
                notifyItemChanged(i);
                return;
            }
        }
    }

    @Override public long getItemId(int position) { return items.get(position).stableId.hashCode(); }
    @Override public int getItemCount() { return items.size(); }

    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemSongBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
        holder.bind(items.get(position), position == items.size() - 1);
    }

    public void setAdaptiveColors(int primary, int secondary, int accent) {
        if (hasAdaptiveColors && primaryText == primary && secondaryText == secondary && this.accent == accent) return;
        primaryText = primary;
        secondaryText = secondary;
        this.accent = accent;
        hasAdaptiveColors = true;
        notifyDataSetChanged();
    }

    class Holder extends RecyclerView.ViewHolder {
        final ItemSongBinding binding;
        LibraryEntry current;

        Holder(ItemSongBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            binding.getRoot().setOnClickListener(v -> {
                if (current != null) listener.onEntryClick(current);
            });
            binding.moreButton.setOnClickListener(v -> {
                if (current != null) listener.onEntryMenu(current, v);
            });
            binding.dragHandle.setOnLongClickListener(v -> {
                listener.onDragRequested(this);
                return true;
            });
        }

        void bind(LibraryEntry item, boolean last) {
            current = item;
            AppTypography.applyToViewTree(binding.getRoot());
            UiPalette fallback = UiPalette.fallback(binding.getRoot().getContext());
            int primary = hasAdaptiveColors ? primaryText : fallback.onSurface;
            int secondary = hasAdaptiveColors ? secondaryText : ColorUtils.setAlphaComponent(primary, 184);
            int rowAccent = hasAdaptiveColors ? accent : fallback.accent;

            binding.getRoot().setCardBackgroundColor(Color.TRANSPARENT);
            binding.playingBar.setVisibility(View.GONE);
            binding.selectionCheck.setVisibility(View.GONE);
            binding.songTitle.setText(item.title);
            binding.songArtist.setText(item.subtitle);
            binding.songTitle.setTextColor(primary);
            binding.songArtist.setTextColor(secondary);
            binding.moreButton.setImageTintList(ColorStateList.valueOf(secondary));
            binding.moreButton.setVisibility(item.type == LibraryEntry.TYPE_HIDDEN_ROOT ? View.INVISIBLE : View.VISIBLE);
            binding.dragHandle.setVisibility(View.VISIBLE);
            binding.dragHandle.setImageTintList(ColorStateList.valueOf(secondary));
            binding.divider.setVisibility(last ? View.GONE : View.VISIBLE);
            binding.divider.setBackgroundTintList(ColorStateList.valueOf(ColorUtils.setAlphaComponent(secondary, 76)));

            String customArtwork = AppPreferences.getPlaylistArtworkUri(binding.getRoot().getContext(), item.stableId);
            if (!customArtwork.isEmpty()) {
                binding.artwork.setPadding(0, 0, 0, 0);
                binding.artwork.setScaleType(ImageView.ScaleType.CENTER_CROP);
                binding.artwork.setImageTintList(null);
                binding.artwork.setBackgroundColor(Color.TRANSPARENT);
                Glide.with(binding.artwork)
                        .load(Uri.parse(customArtwork))
                        .placeholder(R.drawable.ic_music)
                        .error(R.drawable.ic_music)
                        .centerCrop()
                        .into(binding.artwork);
            } else {
                Glide.with(binding.artwork).clear(binding.artwork);
                binding.artwork.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                int padding = Math.round(12 * binding.getRoot().getResources().getDisplayMetrics().density);
                binding.artwork.setPadding(padding, padding, padding, padding);
                binding.artwork.setImageResource(item.type == LibraryEntry.TYPE_USER_PLAYLIST ? R.drawable.ic_music : R.drawable.ic_library);
                binding.artwork.setImageTintList(ColorStateList.valueOf(rowAccent));
                binding.artwork.setBackgroundColor(ColorUtils.setAlphaComponent(rowAccent, 24));
            }
        }
    }
}
