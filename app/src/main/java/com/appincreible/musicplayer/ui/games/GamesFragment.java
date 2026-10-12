package com.appincreible.musicplayer.ui.games;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

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
        binding.doubleMixCard.setOnClickListener(v ->
                Navigation.findNavController(v).navigate(R.id.doubleMixGameFragment));
        ListAppearance.addListener(this);
        applyAppearance();
    }

    private void applyAppearance() {
        if (binding == null) return;
        ListAppearance.Appearance appearance = ListAppearance.resolve(requireContext());
        int guessColor = appearance.accent;
        int intruderColor = ColorUtils.blendARGB(appearance.accent, 0xFF22D3EE, 0.38f);
        int white = Color.WHITE;

        binding.guessSongCard.setCardBackgroundColor(appearance.containerColor);
        binding.intruderCard.setCardBackgroundColor(appearance.containerColor);
        binding.doubleMixCard.setCardBackgroundColor(appearance.containerColor);

        binding.gamesTitle.setTextColor(appearance.primaryText);
        binding.gamesSubtitle.setTextColor(appearance.secondaryText);

        binding.gameTitle.setTextColor(appearance.primaryText);
        binding.gameDescription.setTextColor(appearance.secondaryText);
        binding.gameArrowContainer.setBackgroundTintList(ColorStateList.valueOf(ColorUtils.setAlphaComponent(guessColor, 40)));
        binding.gameArrow.setTextColor(guessColor);

        binding.intruderTitle.setTextColor(appearance.primaryText);
        binding.intruderDescription.setTextColor(appearance.secondaryText);
        binding.intruderArrowContainer.setBackgroundTintList(ColorStateList.valueOf(ColorUtils.setAlphaComponent(intruderColor, 40)));
        binding.intruderArrow.setTextColor(intruderColor);

        int doubleMixColor = ContextCompat.getColor(requireContext(), R.color.settings_category_radio);
        binding.doubleMixTitle.setTextColor(appearance.primaryText);
        binding.doubleMixDescription.setTextColor(appearance.secondaryText);
        binding.doubleMixArrowContainer.setBackgroundTintList(ColorStateList.valueOf(ColorUtils.setAlphaComponent(doubleMixColor, 40)));
        binding.doubleMixArrow.setTextColor(doubleMixColor);

        ImageViewCompat.setImageTintList(binding.gameIcon, ColorStateList.valueOf(white));
        ImageViewCompat.setImageTintList(binding.intruderIcon, ColorStateList.valueOf(white));
        ImageViewCompat.setImageTintList(binding.doubleMixIcon, ColorStateList.valueOf(white));
    }

    @Override public void onListAppearanceChanged() { applyAppearance(); }

    @Override public void onDestroyView() {
        ListAppearance.removeListener(this);
        binding = null;
        super.onDestroyView();
    }
}
