package com.appincreible.musicplayer.ui.games;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.core.widget.ImageViewCompat;
import android.content.res.ColorStateList;

import com.appincreible.musicplayer.R;
import com.appincreible.musicplayer.databinding.FragmentGamesBinding;
import com.appincreible.musicplayer.themes.ListAppearance;

public final class GamesFragment extends Fragment implements ListAppearance.Listener {
    private FragmentGamesBinding binding;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentGamesBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding.guessSongCard.setOnClickListener(v ->
                Navigation.findNavController(v).navigate(R.id.guessSongFragment));
        binding.intruderCard.setOnClickListener(v ->
                Navigation.findNavController(v).navigate(R.id.intruderGameFragment));
        ListAppearance.addListener(this);
        applyAppearance();
    }

    private void applyAppearance() {
        if (binding == null) return;
        ListAppearance.Appearance appearance = ListAppearance.resolve(requireContext());
        binding.guessSongCard.setCardBackgroundColor(appearance.containerColor);
        binding.intruderCard.setCardBackgroundColor(appearance.containerColor);
        binding.gamesTitle.setTextColor(appearance.primaryText);
        binding.gamesSubtitle.setTextColor(appearance.secondaryText);
        binding.gameTitle.setTextColor(appearance.primaryText);
        binding.gameDescription.setTextColor(appearance.secondaryText);
        binding.gameArrow.setTextColor(appearance.accent);
        binding.intruderTitle.setTextColor(appearance.primaryText);
        binding.intruderDescription.setTextColor(appearance.secondaryText);
        binding.intruderArrow.setTextColor(appearance.accent);
        ImageViewCompat.setImageTintList(binding.gameIcon, ColorStateList.valueOf(appearance.accent));
        ImageViewCompat.setImageTintList(binding.intruderIcon, ColorStateList.valueOf(appearance.accent));
    }

    @Override public void onListAppearanceChanged() { applyAppearance(); }

    @Override public void onDestroyView() {
        ListAppearance.removeListener(this);
        binding = null;
        super.onDestroyView();
    }
}
