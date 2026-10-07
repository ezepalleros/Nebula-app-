package com.appincreible.musicplayer.ui.music;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.appincreible.musicplayer.R;
import com.appincreible.musicplayer.data.model.Song;
import com.appincreible.musicplayer.databinding.ItemSongBinding;
import com.appincreible.musicplayer.themes.AppPreferences;
import com.appincreible.musicplayer.themes.AppTypography;
import com.appincreible.musicplayer.themes.UiPalette;
import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SongAdapter extends ListAdapter<Song, SongAdapter.SongViewHolder> {

    public interface Listener {
        void onSongClick(int position, Song song);
        void onSongLongPress(Song song);
        void onSongMenu(Song song, View anchor);
        void onSelectionChanged(int count);
    }

    private static final DiffUtil.ItemCallback<Song> DIFF = new DiffUtil.ItemCallback<Song>() {
        @Override public boolean areItemsTheSame(@NonNull Song oldItem, @NonNull Song newItem) {
            return oldItem.getId() == newItem.getId();
        }
        @Override public boolean areContentsTheSame(@NonNull Song oldItem, @NonNull Song newItem) {
            return oldItem.getTitle().equals(newItem.getTitle())
                    && oldItem.getArtist().equals(newItem.getArtist())
                    && oldItem.getAlbum().equals(newItem.getAlbum())
                    && oldItem.getArtworkUri().equals(newItem.getArtworkUri());
        }
    };

    private final Listener listener;
    private final Set<Long> selectedIds = new HashSet<>();
    private boolean selectionMode;
    private String currentMediaKey = "";
    private boolean currentPlaying;
    private int adaptiveSurface = Color.TRANSPARENT;
    private int adaptivePrimary = Color.WHITE;
    private int adaptiveSecondary = Color.LTGRAY;
    private int adaptiveAccent = Color.WHITE;
    private boolean hasAdaptiveColors;
    private boolean selectionOnly;

    public SongAdapter(Listener listener) {
        super(DIFF);
        this.listener = listener;
        setHasStableIds(true);
    }

    @Override public long getItemId(int position) { return getItem(position).getId(); }

    @NonNull @Override
    public SongViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new SongViewHolder(ItemSongBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override public void onBindViewHolder(@NonNull SongViewHolder holder, int position) {
        holder.bind(getItem(position), position == getItemCount() - 1);
    }

    public boolean isSelectionMode() { return selectionMode; }

    public void setSelectionOnly(boolean selectionOnly) {
        if (this.selectionOnly == selectionOnly) return;
        this.selectionOnly = selectionOnly;
        if (!selectionOnly) clearSelection(); else notifyDataSetChanged();
    }

    public void setAdaptiveColors(int surface, int primary, int secondary, int accent) {
        if (hasAdaptiveColors && adaptiveSurface == surface && adaptivePrimary == primary
                && adaptiveSecondary == secondary && adaptiveAccent == accent) return;
        adaptiveSurface = surface;
        adaptivePrimary = primary;
        adaptiveSecondary = secondary;
        adaptiveAccent = accent;
        hasAdaptiveColors = true;
        notifyDataSetChanged();
    }

    public void setNowPlaying(String mediaKey, boolean playing) {
        String next = mediaKey == null ? "" : mediaKey;
        boolean changed = !next.equals(currentMediaKey) || playing != currentPlaying;
        if (!changed) return;
        int oldPosition = positionForMediaKey(currentMediaKey);
        currentMediaKey = next;
        currentPlaying = playing;
        int newPosition = positionForMediaKey(currentMediaKey);
        if (oldPosition >= 0) notifyItemChanged(oldPosition);
        if (newPosition >= 0 && newPosition != oldPosition) notifyItemChanged(newPosition);
    }

    private int positionForMediaKey(String mediaKey) {
        if (mediaKey == null || mediaKey.isEmpty()) return -1;
        List<Song> list = getCurrentList();
        for (int i = 0; i < list.size(); i++) {
            if (mediaKey.equals(list.get(i).getMediaKey())) return i;
        }
        return -1;
    }

    public void startSelection(Song song) {
        selectionMode = true;
        selectedIds.add(song.getId());
        notifyDataSetChanged();
        listener.onSelectionChanged(selectedIds.size());
    }

    public void toggleSelection(Song song) {
        if (selectedIds.contains(song.getId())) selectedIds.remove(song.getId()); else selectedIds.add(song.getId());
        if (selectedIds.isEmpty()) selectionMode = false;
        notifyDataSetChanged();
        listener.onSelectionChanged(selectedIds.size());
    }

    public void selectAll() {
        selectionMode = true;
        selectedIds.clear();
        for (Song song : getCurrentList()) selectedIds.add(song.getId());
        notifyDataSetChanged();
        listener.onSelectionChanged(selectedIds.size());
    }

    public void clearSelection() {
        selectedIds.clear();
        selectionMode = false;
        notifyDataSetChanged();
        listener.onSelectionChanged(0);
    }

    public List<Song> getSelectedSongs() {
        List<Song> result = new ArrayList<>();
        for (Song song : getCurrentList()) if (selectedIds.contains(song.getId())) result.add(song);
        return result;
    }

    @Override
    public void onViewRecycled(@NonNull SongViewHolder holder) {
        Glide.with(holder.binding.artwork).clear(holder.binding.artwork);
        holder.binding.artwork.setImageDrawable(null);
        super.onViewRecycled(holder);
    }

    class SongViewHolder extends RecyclerView.ViewHolder {
        private final ItemSongBinding binding;
        private Song current;

        SongViewHolder(ItemSongBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            binding.getRoot().setOnClickListener(v -> {
                int position = getBindingAdapterPosition();
                if (position == RecyclerView.NO_POSITION || current == null) return;
                if (selectionOnly) {
                    if (selectionMode) toggleSelection(current); else startSelection(current);
                } else if (selectionMode) toggleSelection(current); else listener.onSongClick(position, current);
            });
            binding.getRoot().setOnLongClickListener(v -> {
                if (current == null) return false;
                if (selectionOnly) {
                    if (selectionMode) toggleSelection(current); else startSelection(current);
                } else if (selectionMode) toggleSelection(current); else listener.onSongLongPress(current);
                return true;
            });
            binding.selectionCheck.setOnClickListener(v -> { if (current != null) toggleSelection(current); });
            binding.moreButton.setOnClickListener(v -> { if (current != null && !selectionMode) listener.onSongMenu(current, v); });
        }

        void bind(Song song, boolean last) {
            current = song;
            boolean active = song.getMediaKey().equals(currentMediaKey);
            int accent = hasAdaptiveColors ? adaptiveAccent
                    : AppPreferences.getPlayerAccentColor(binding.getRoot().getContext());
            UiPalette palette = UiPalette.fallback(binding.getRoot().getContext());
            int surface = hasAdaptiveColors ? adaptiveSurface : palette.surface;
            int primary = hasAdaptiveColors ? adaptivePrimary : palette.onSurface;
            int secondary = hasAdaptiveColors ? adaptiveSecondary
                    : ColorUtils.setAlphaComponent(primary, 184);
            int activeBg = ColorUtils.setAlphaComponent(accent, 20);
            int opaqueSurface = Color.rgb(Color.red(surface), Color.green(surface), Color.blue(surface));
            int activeText = ColorUtils.calculateContrast(accent, opaqueSurface) >= UiPalette.MIN_TEXT_CONTRAST
                    ? accent : primary;

            binding.getRoot().setCardBackgroundColor(active ? activeBg : Color.TRANSPARENT);
            binding.playingBar.setVisibility(active ? View.VISIBLE : View.GONE);
            binding.playingBar.setBackgroundTintList(ColorStateList.valueOf(accent));

            binding.songTitle.setText(song.getTitle());
            binding.songTitle.setTypeface(AppTypography.get(binding.getRoot().getContext(), Typeface.NORMAL));
            binding.songTitle.setTextColor(active ? activeText : primary);
            binding.songArtist.setText(song.getArtist());
            binding.songArtist.setTextColor(secondary);
            binding.songArtist.setTypeface(AppTypography.get(binding.getRoot().getContext(), Typeface.NORMAL));
            binding.moreButton.setImageTintList(ColorStateList.valueOf(secondary));
            binding.divider.setBackgroundTintList(ColorStateList.valueOf(ColorUtils.setAlphaComponent(secondary, 76)));
            binding.selectionCheck.setVisibility((selectionMode || selectionOnly) ? View.VISIBLE : View.GONE);
            binding.selectionCheck.setChecked(selectedIds.contains(song.getId()));
            binding.moreButton.setVisibility((selectionMode || selectionOnly) ? View.GONE : View.VISIBLE);
            binding.divider.setVisibility(last || active ? View.GONE : View.VISIBLE);

            Glide.with(binding.artwork).clear(binding.artwork);
            binding.artwork.setImageDrawable(null);
            if (song.getArtworkUri().isEmpty()) {
                binding.artwork.setImageResource(R.drawable.ic_music);
            } else {
                Glide.with(binding.artwork)
                        .load(song.getArtworkUri())
                        .placeholder(R.drawable.ic_music)
                        .error(R.drawable.ic_music)
                        .dontAnimate()
                        .centerCrop()
                        .into(binding.artwork);
            }
        }
    }
}
