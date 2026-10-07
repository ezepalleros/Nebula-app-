package com.appincreible.musicplayer.ui.radio;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.appincreible.musicplayer.R;
import com.appincreible.musicplayer.databinding.ItemRadioStationBinding;
import com.appincreible.musicplayer.radio.model.RadioStation;
import com.appincreible.musicplayer.player.controller.PlayerController;
import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RadioStationAdapter extends ListAdapter<RadioStation, RadioStationAdapter.StationViewHolder> {

    public interface Listener {
        void onStationClick(RadioStation station, int position);
        void onFavoriteClick(RadioStation station);
        void onStationLongPress(RadioStation station);
    }

    private static final DiffUtil.ItemCallback<RadioStation> DIFF = new DiffUtil.ItemCallback<RadioStation>() {
        @Override public boolean areItemsTheSame(@NonNull RadioStation oldItem, @NonNull RadioStation newItem) {
            return oldItem.getMediaKey().equals(newItem.getMediaKey());
        }
        @Override public boolean areContentsTheSame(@NonNull RadioStation oldItem, @NonNull RadioStation newItem) {
            return oldItem.getName().equals(newItem.getName())
                    && oldItem.getSubtitle().equals(newItem.getSubtitle())
                    && oldItem.getFavicon().equals(newItem.getFavicon());
        }
    };

    private final Set<String> favoriteIds = new HashSet<>();
    private final Listener listener;
    private int adaptivePrimary = Color.WHITE;
    private int adaptiveSecondary = Color.LTGRAY;
    private int adaptiveAccent = Color.WHITE;
    private boolean hasAdaptiveColors;
    private String connectionMediaKey = "";
    private int connectionState = PlayerController.RADIO_CONNECTION_IDLE;

    public RadioStationAdapter(Listener listener) {
        super(DIFF);
        this.listener = listener;
        setHasStableIds(true);
    }

    public List<RadioStation> getItemsSnapshot() { return new ArrayList<>(getCurrentList()); }

    public void setAdaptiveColors(int primary, int secondary, int accent) {
        if (hasAdaptiveColors && adaptivePrimary == primary
                && adaptiveSecondary == secondary && adaptiveAccent == accent) return;
        adaptivePrimary = primary;
        adaptiveSecondary = secondary;
        adaptiveAccent = accent;
        hasAdaptiveColors = true;
        notifyDataSetChanged();
    }

    public void setConnectionState(String mediaKey, int state) {
        String oldKey = connectionMediaKey;
        connectionMediaKey = mediaKey == null ? "" : mediaKey;
        connectionState = state;
        notifyConnectionRow(oldKey);
        if (!connectionMediaKey.equals(oldKey)) notifyConnectionRow(connectionMediaKey);
    }

    private void notifyConnectionRow(String mediaKey) {
        if (mediaKey == null || mediaKey.isEmpty()) return;
        for (int i = 0; i < getItemCount(); i++) {
            if (mediaKey.equals(getItem(i).getMediaKey())) {
                notifyItemChanged(i);
                return;
            }
        }
    }

    public void setFavoriteIds(Set<String> ids) {
        favoriteIds.clear();
        if (ids != null) favoriteIds.addAll(ids);
        notifyItemRangeChanged(0, getItemCount(), "favorite");
    }

    @Override public long getItemId(int position) { return getItem(position).getMediaKey().hashCode(); }

    @NonNull
    @Override
    public StationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new StationViewHolder(ItemRadioStationBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override public void onBindViewHolder(@NonNull StationViewHolder holder, int position) { holder.bind(getItem(position), position == getItemCount() - 1); }

    @Override
    public void onBindViewHolder(@NonNull StationViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (!payloads.isEmpty()) holder.bindFavorite(getItem(position));
        else super.onBindViewHolder(holder, position, payloads);
    }

    class StationViewHolder extends RecyclerView.ViewHolder {
        private final ItemRadioStationBinding binding;
        private RadioStation current;

        StationViewHolder(ItemRadioStationBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            binding.getRoot().setOnClickListener(v -> {
                int position = getBindingAdapterPosition();
                if (current != null && position != RecyclerView.NO_POSITION) listener.onStationClick(current, position);
            });
            binding.getRoot().setOnLongClickListener(v -> {
                if (current != null) listener.onStationLongPress(current);
                return true;
            });
            binding.favoriteButton.setOnClickListener(v -> {
                if (current != null) listener.onFavoriteClick(current);
            });
        }

        void bind(RadioStation station, boolean last) {
            current = station;
            int primary = hasAdaptiveColors ? adaptivePrimary : binding.stationName.getCurrentTextColor();
            int secondary = hasAdaptiveColors ? adaptiveSecondary : binding.stationInfo.getCurrentTextColor();

            binding.getRoot().setCardBackgroundColor(Color.TRANSPARENT);
            binding.stationName.setText(station.getName());
            binding.stationName.setTextColor(primary);
            if (station.getMediaKey().equals(connectionMediaKey)
                    && connectionState == PlayerController.RADIO_CONNECTION_CONNECTING) {
                binding.stationInfo.setText("Conectando…");
            } else if (station.getMediaKey().equals(connectionMediaKey)
                    && connectionState == PlayerController.RADIO_CONNECTION_ERROR) {
                binding.stationInfo.setText("Error de conexión");
            } else {
                binding.stationInfo.setText(station.getSubtitle());
            }
            binding.stationInfo.setTextColor(secondary);
            binding.divider.setBackgroundTintList(ColorStateList.valueOf(
                    ColorUtils.setAlphaComponent(secondary, 76)));
            binding.divider.setVisibility(last ? android.view.View.GONE : android.view.View.VISIBLE);
            bindFavorite(station);
            Glide.with(binding.stationLogo)
                    .load(station.getFavicon())
                    .placeholder(R.drawable.ic_radio_station)
                    .error(R.drawable.ic_radio_station)
                    .fitCenter()
                    .into(binding.stationLogo);
        }

        void bindFavorite(RadioStation station) {
            boolean favorite = favoriteIds.contains(station.getStationUuid());
            binding.favoriteButton.setImageResource(favorite ? R.drawable.ic_star_filled : R.drawable.ic_star);
            int tint = hasAdaptiveColors
                    ? (favorite ? adaptiveAccent : adaptiveSecondary)
                    : binding.stationInfo.getCurrentTextColor();
            binding.favoriteButton.setImageTintList(ColorStateList.valueOf(tint));
            binding.favoriteButton.setContentDescription(favorite ? "Quitar de favoritas" : "Agregar a favoritas");
        }
    }
}
