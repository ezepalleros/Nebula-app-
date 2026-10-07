package com.appincreible.musicplayer.ui.settings;

import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.fragment.app.Fragment;
import androidx.media3.common.Player;
import androidx.navigation.Navigation;

import com.appincreible.musicplayer.R;
import com.appincreible.musicplayer.databinding.FragmentSettingsHomeBinding;
import com.appincreible.musicplayer.player.audio.AudioPreferences;
import com.appincreible.musicplayer.power.PowerSaverManager;
import com.appincreible.musicplayer.themes.AppPreferences;
import com.appincreible.musicplayer.themes.ListAppearance;
import com.google.android.material.card.MaterialCardView;

/** Settings landing screen: category navigation only. */
public class SettingsFragment extends Fragment implements ListAppearance.Listener {

    public static final String ARG_CATEGORY = "settings_category";
    public static final String CATEGORY_APPEARANCE = "appearance";
    public static final String CATEGORY_NAVIGATION = "navigation";
    public static final String CATEGORY_PLAYER = "player";
    public static final String CATEGORY_AUDIO = "audio";
    public static final String CATEGORY_RADIO = "radio";
    public static final String CATEGORY_LIBRARY = "library";
    public static final String CATEGORY_BATTERY = "battery";

    private FragmentSettingsHomeBinding binding;
    private SharedPreferences appPrefs;
    private SharedPreferences audioPrefs;

    private final SharedPreferences.OnSharedPreferenceChangeListener appListener = (prefs, key) -> {
        if (binding != null) refresh();
    };
    private final SharedPreferences.OnSharedPreferenceChangeListener audioListener = (prefs, key) -> {
        if (binding != null) refresh();
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentSettingsHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        PowerSaverManager.startMonitoring(requireContext());
        appPrefs = AppPreferences.prefs(requireContext());
        audioPrefs = AudioPreferences.prefs(requireContext());
        appPrefs.registerOnSharedPreferenceChangeListener(appListener);
        audioPrefs.registerOnSharedPreferenceChangeListener(audioListener);
        ListAppearance.addListener(this);
        buildCategories();
        refresh();
    }

    private void buildCategories() {
        binding.categoriesContainer.removeAllViews();
        addCategory(CATEGORY_APPEARANCE, "Apariencia", R.drawable.ic_settings);
        addCategory(CATEGORY_NAVIGATION, "Navegación", R.drawable.ic_home);
        addCategory(CATEGORY_PLAYER, "Reproductor", R.drawable.ic_music);
        addCategory(CATEGORY_AUDIO, "Audio", R.drawable.ic_notification_music);
        addCategory(CATEGORY_RADIO, "Radio", R.drawable.ic_radio);
        addCategory(CATEGORY_LIBRARY, "Biblioteca", R.drawable.ic_library);
        addCategory(CATEGORY_BATTERY, "Batería", R.drawable.ic_battery_saver);
    }

    private void addCategory(String category, String title, int iconRes) {
        View row = getLayoutInflater().inflate(R.layout.item_settings_category, binding.categoriesContainer, false);
        row.setTag(category);
        ImageView icon = row.findViewById(R.id.categoryIcon);
        TextView titleView = row.findViewById(R.id.categoryTitle);
        icon.setImageResource(iconRes);
        titleView.setText(title);
        row.setOnClickListener(v -> openCategory(category));
        binding.categoriesContainer.addView(row);
    }

    private void openCategory(String category) {
        Bundle args = new Bundle();
        args.putString(ARG_CATEGORY, category);
        Navigation.findNavController(requireView()).navigate(R.id.settingsDetailFragment, args);
    }

    private void refresh() {
        if (binding == null) return;
        for (int i = 0; i < binding.categoriesContainer.getChildCount(); i++) {
            View row = binding.categoriesContainer.getChildAt(i);
            Object tag = row.getTag();
            if (!(tag instanceof String)) continue;
            TextView summary = row.findViewById(R.id.categorySummary);
            summary.setText(summaryFor((String) tag));
        }
        applyAppearance();
    }

    private String summaryFor(String category) {
        switch (category) {
            case CATEGORY_APPEARANCE:
                return themeName(AppPreferences.getThemeMode(requireContext())) + " · "
                        + typographyName(AppPreferences.getPlayerTypography(requireContext()));
            case CATEGORY_NAVIGATION:
                return startPageName(AppPreferences.getStartPage(requireContext())) + " · Pestañas "
                        + (AppPreferences.TABS_BOTTOM.equals(AppPreferences.getMusicTabsPosition(requireContext())) ? "abajo" : "arriba");
            case CATEGORY_PLAYER:
                return styleName(AppPreferences.getPlayerStyle(requireContext())) + " · "
                        + (AppPreferences.useLargeArtwork(requireContext()) ? "Portada grande" : "Portada compacta");
            case CATEGORY_AUDIO:
                return orderName(AudioPreferences.shuffleEnabled(requireContext(), false)) + " · "
                        + repeatName(AudioPreferences.repeatMode(requireContext(), false));
            case CATEGORY_RADIO:
                return orderName(AudioPreferences.shuffleEnabled(requireContext(), true)) + " · "
                        + repeatName(AudioPreferences.repeatMode(requireContext(), true));
            case CATEGORY_LIBRARY:
                return AppPreferences.libraryGroupingName(requireContext());
            case CATEGORY_BATTERY:
                return powerSaverSummary();
            default:
                return "";
        }
    }

    private String powerSaverSummary() {
        if (PowerSaverManager.isActive(requireContext())) {
            if (PowerSaverManager.isManualEnabled(requireContext())) return "Activo";
            if (PowerSaverManager.systemPowerSaveMode(requireContext())) return "Activo · ahorro de Android";
            return "Activo · batería " + PowerSaverManager.batteryPercent(requireContext()) + "%";
        }
        if (PowerSaverManager.isAutoBatteryEnabled(requireContext())) {
            return "Desactivado · automático al " + PowerSaverManager.batteryThreshold(requireContext()) + "%";
        }
        return "Desactivado";
    }

    private void applyAppearance() {
        ListAppearance.Appearance appearance = ListAppearance.resolve(requireContext());
        binding.title.setTextColor(appearance.primaryText);
        binding.subtitle.setTextColor(appearance.secondaryText);
        for (int i = 0; i < binding.categoriesContainer.getChildCount(); i++) {
            View row = binding.categoriesContainer.getChildAt(i);
            Object tag = row.getTag();
            if (!(tag instanceof String)) continue;
            int categoryColor = ContextCompat.getColor(requireContext(), categoryColorRes((String) tag));
            MaterialCardView card = row.findViewById(R.id.categoryCard);
            ImageView icon = row.findViewById(R.id.categoryIcon);
            TextView title = row.findViewById(R.id.categoryTitle);
            TextView summary = row.findViewById(R.id.categorySummary);
            TextView arrow = row.findViewById(R.id.categoryArrow);
            card.setCardBackgroundColor(appearance.containerColor);
            card.setStrokeColor(ColorUtils.setAlphaComponent(categoryColor, appearance.transparent ? 210 : 120));
            icon.setImageTintList(ColorStateList.valueOf(categoryColor));
            icon.setBackgroundTintList(ColorStateList.valueOf(ColorUtils.setAlphaComponent(categoryColor, 36)));
            title.setTextColor(appearance.primaryText);
            summary.setTextColor(appearance.secondaryText);
            arrow.setTextColor(categoryColor);
        }
    }

    private int categoryColorRes(String category) {
        switch (category) {
            case CATEGORY_NAVIGATION: return R.color.settings_category_navigation;
            case CATEGORY_PLAYER: return R.color.settings_category_player;
            case CATEGORY_AUDIO: return R.color.settings_category_audio;
            case CATEGORY_RADIO: return R.color.settings_category_radio;
            case CATEGORY_LIBRARY: return R.color.settings_category_library;
            case CATEGORY_BATTERY: return R.color.settings_category_battery;
            case CATEGORY_APPEARANCE:
            default: return R.color.settings_category_appearance;
        }
    }

    private String orderName(boolean shuffle) { return shuffle ? "Aleatorio" : "Lista"; }
    private String repeatName(int mode) {
        return mode == Player.REPEAT_MODE_ONE ? "Repetir una"
                : mode == Player.REPEAT_MODE_ALL ? "Repetir todas" : "Repetición desactivada";
    }
    private String themeName(String value) {
        return AppPreferences.THEME_LIGHT.equals(value) ? "Tema claro"
                : AppPreferences.THEME_DARK.equals(value) ? "Tema oscuro" : "Tema del sistema";
    }
    private String typographyName(String value) {
        if (AppPreferences.TYPOGRAPHY_ROUNDED.equals(value)) return "Redondeada";
        if (AppPreferences.TYPOGRAPHY_SERIF.equals(value)) return "Serif clásica";
        if (AppPreferences.TYPOGRAPHY_MONO.equals(value)) return "Monoespaciada";
        if (AppPreferences.TYPOGRAPHY_CONDENSED.equals(value)) return "Condensada";
        return "Moderna";
    }
    private String startPageName(String value) {
        if (AppPreferences.START_RADIO.equals(value)) return "Radio";
        if (AppPreferences.START_DOWNLOADS.equals(value)) return "Descargas";
        if (AppPreferences.START_SETTINGS.equals(value)) return "Ajustes";
        return "Música";
    }
    private String styleName(String value) {
        if (AppPreferences.STYLE_VINYL.equals(value)) return "Vinilo";
        if (AppPreferences.STYLE_PIXEL.equals(value)) return "Cartucho NES";
        if (AppPreferences.STYLE_CASSETTE.equals(value)) return "Cassette";
        if (AppPreferences.STYLE_WALKMAN.equals(value)) return "Walkman";
        if (AppPreferences.STYLE_RETRO.equals(value)) return "Floppy retro";
        if (AppPreferences.STYLE_NEON.equals(value)) return "Neón";
        if (AppPreferences.STYLE_GLASS.equals(value)) return "Glass";
        if (AppPreferences.STYLE_MINIMAL.equals(value)) return "Minimal";
        if (AppPreferences.STYLE_GAMEBOY_3D.equals(value)) return "Game Boy 3D";
        if (AppPreferences.STYLE_SPOTIFY_2D.equals(value)) return "Streaming 2D";
        if (AppPreferences.STYLE_EQUALIZER_2D.equals(value)) return "Ecualizador 2D";
        return "Clásico";
    }

    @Override public void onListAppearanceChanged() { if (binding != null) refresh(); }

    @Override public void onResume() {
        super.onResume();
        refresh();
    }

    @Override public void onDestroyView() {
        ListAppearance.removeListener(this);
        if (appPrefs != null) appPrefs.unregisterOnSharedPreferenceChangeListener(appListener);
        if (audioPrefs != null) audioPrefs.unregisterOnSharedPreferenceChangeListener(audioListener);
        appPrefs = null;
        audioPrefs = null;
        binding = null;
        super.onDestroyView();
    }
}
