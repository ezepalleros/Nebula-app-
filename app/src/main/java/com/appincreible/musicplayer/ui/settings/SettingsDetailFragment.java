package com.appincreible.musicplayer.ui.settings;

import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.text.InputType;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.core.content.ContextCompat;

import com.appincreible.musicplayer.R;
import com.appincreible.musicplayer.databinding.FragmentSettingsBinding;
import com.appincreible.musicplayer.player.audio.AudioPreferences;
import com.appincreible.musicplayer.player.audio.NormalizationAnalysisManager;
import com.appincreible.musicplayer.power.PowerSaverManager;
import com.appincreible.musicplayer.themes.AppPreferences;
import com.appincreible.musicplayer.themes.ListAppearance;
import com.appincreible.musicplayer.ui.common.ModernUiDialogs;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.shape.ShapeAppearanceModel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.Locale;

public class SettingsDetailFragment extends Fragment {

    private FragmentSettingsBinding binding;
    private String category = SettingsFragment.CATEGORY_APPEARANCE;
    private boolean bindingPreferences;
    private boolean previewWebReady;
    private boolean miniPreviewWebReady;
    private final PlayerStylePicker stylePicker = new PlayerStylePicker();
    private PowerSaverSettingsController powerSaverSettings;
    private PlaybackOrderSettingsController playbackOrderSettings;
    private LibrarySourceFoldersController librarySourceFoldersController;
    private SharedPreferences audioPrefs;
    private boolean bindingAudio;
    private TextToSpeech voicePickerTts;
    private final SharedPreferences.OnSharedPreferenceChangeListener audioPreferenceListener = (prefs, key) -> {
        if (binding != null) refreshAudioStatus();
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentSettingsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Bundle args = getArguments();
        if (args != null) category = args.getString(SettingsFragment.ARG_CATEGORY, SettingsFragment.CATEGORY_APPEARANCE);

        SharedPreferences prefs = AppPreferences.prefs(requireContext());
        binding.settingsBackButton.setOnClickListener(v -> androidx.navigation.Navigation.findNavController(v).navigateUp());
        binding.listTransparentSwitch.setChecked(ListAppearance.isTransparent(requireContext()));
        binding.listTransparentSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (!bindingPreferences) {
                ListAppearance.setTransparent(requireContext(), checked);
                applyListAppearance();
            }
        });
        bindSwitches(prefs);
        configureCategoryVisibility();
        if (SettingsFragment.CATEGORY_PLAYER.equals(category)) setupPreviewWebViews();
        refreshSummary();

        if (SettingsFragment.CATEGORY_AUDIO.equals(category)) setupAudioSettings();
        if (SettingsFragment.CATEGORY_AUDIO.equals(category) || SettingsFragment.CATEGORY_RADIO.equals(category)) {
            playbackOrderSettings = new PlaybackOrderSettingsController(requireContext(), binding);
            playbackOrderSettings.attach();
        }
        if (SettingsFragment.CATEGORY_LIBRARY.equals(category)) {
            librarySourceFoldersController = new LibrarySourceFoldersController(requireContext(), binding.librarySourceFoldersContainer);
            librarySourceFoldersController.refresh();
        }

        binding.styleRow.setOnClickListener(v -> showStylePicker());
        binding.equalizerSkinBackgroundRow.setOnClickListener(v -> {
            boolean ring = AppPreferences.STYLE_RING.equals(AppPreferences.getPlayerStyle(requireContext()));
            int initial = ring ? AppPreferences.getRingSkinBackgroundColor(requireContext())
                    : AppPreferences.getEqualizerSkinBackgroundColor(requireContext());
            showColorPickerDialog("Color de fondo", initial, color -> {
                if (ring) AppPreferences.setRingSkinBackgroundColor(requireContext(), color);
                else AppPreferences.setEqualizerSkinBackgroundColor(requireContext(), color);
                refreshSummary();
            });
        });
        binding.equalizerSkinBarsRow.setOnClickListener(v -> {
            boolean ring = AppPreferences.STYLE_RING.equals(AppPreferences.getPlayerStyle(requireContext()));
            int initial = ring ? AppPreferences.getRingSkinColor(requireContext())
                    : AppPreferences.getEqualizerSkinBarsColor(requireContext());
            showColorPickerDialog(ring ? "Color del anillo" : "Color de las barras", initial, color -> {
                if (ring) AppPreferences.setRingSkinColor(requireContext(), color);
                else AppPreferences.setEqualizerSkinBarsColor(requireContext(), color);
                refreshSummary();
            });
        });
        binding.fftIntensitySeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int percent = 50 + progress;
                binding.fftIntensityValue.setText(percent + "%");
                if (!fromUser || bindingPreferences) return;
                String currentStyle = AppPreferences.getPlayerStyle(requireContext());
                if (AppPreferences.STYLE_RING.equals(currentStyle)) {
                    AppPreferences.setRingSkinIntensity(requireContext(), percent);
                } else if (AppPreferences.STYLE_EQUALIZER_2D.equals(currentStyle)) {
                    AppPreferences.setEqualizerSkinIntensity(requireContext(), percent);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });
        binding.accentRow.setOnClickListener(v -> showAccentPicker());
        binding.animationRow.setOnClickListener(v -> showAnimationPicker());
        binding.backgroundRow.setOnClickListener(v -> showBackgroundPicker());
        binding.playerBackgroundColorsRow.setOnClickListener(v ->
                PlayerBackgroundSettingsController.show(this, this::refreshSummary));
        binding.typographyRow.setOnClickListener(v -> showTypographyPicker());
        binding.artworkRow.setOnClickListener(v -> showArtworkPicker());
        binding.progressStyleRow.setOnClickListener(v ->
                PlayerProgressStyleSettingsController.show(this, this::refreshSummary));
        binding.startPageRow.setOnClickListener(v -> showStartPagePicker());
        binding.tabsRow.setOnClickListener(v -> showTabsPicker());
        binding.navigationRow.setOnClickListener(v -> showNavigationEditor());
        binding.themeRow.setOnClickListener(v -> showThemePicker());
        binding.appBackgroundRow.setOnClickListener(v -> showAppBackgroundPicker());
        binding.appBackgroundColorsRow.setOnClickListener(v -> showAppBackgroundColorsPicker());
        binding.libraryGroupingRow.setOnClickListener(v -> showLibraryGroupingPicker());
        binding.hideWhatsAppAudioSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (bindingPreferences) return;
            AppPreferences.setHideWhatsAppAudio(requireContext(), checked);
            if (librarySourceFoldersController != null) librarySourceFoldersController.refresh();
        });

        binding.miniSubtitleSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (bindingPreferences) return;
            prefs.edit().putBoolean(AppPreferences.KEY_SHOW_MINI_SUBTITLE, checked).commit();
        });
        binding.miniPlayerBackgroundSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (bindingPreferences) return;
            prefs.edit().putBoolean(AppPreferences.KEY_MINI_PLAYER_USE_PLAYER_BACKGROUND, checked).commit();
        });
        binding.radioStationCountSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (bindingPreferences) return;
            prefs.edit().putBoolean(AppPreferences.KEY_SHOW_RADIO_STATION_COUNT, checked).commit();
        });
        binding.centerPlayerInfoSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (bindingPreferences) return;
            prefs.edit().putBoolean(AppPreferences.KEY_CENTER_PLAYER_INFO, checked).commit();
            refreshSummary();
        });
        binding.showBottomNavNowPlayingSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (bindingPreferences) return;
            prefs.edit().putBoolean(AppPreferences.KEY_SHOW_BOTTOM_NAV_NOW_PLAYING, checked).commit();
            if (getActivity() instanceof com.appincreible.musicplayer.MainActivity) {
                ((com.appincreible.musicplayer.MainActivity) getActivity()).refreshChrome();
            }
        });

        bindPlayerVisibilitySwitch(binding.showPlayerBackSwitch, prefs, AppPreferences.KEY_SHOW_PLAYER_BACK_BUTTON);
        bindPlayerVisibilitySwitch(binding.showPlayerHeaderLabelSwitch, prefs, AppPreferences.KEY_SHOW_PLAYER_HEADER_LABEL);
        bindPlayerVisibilitySwitch(binding.showPlayerMusicButtonSwitch, prefs, AppPreferences.KEY_SHOW_PLAYER_MUSIC_BUTTON);
        bindPlayerVisibilitySwitch(binding.showPlayerArtworkSwitch, prefs, AppPreferences.KEY_SHOW_PLAYER_ARTWORK);
        bindPlayerVisibilitySwitch(binding.showPlayerProgressLineSwitch, prefs, AppPreferences.KEY_SHOW_PLAYER_PROGRESS_LINE);
        bindPlayerVisibilitySwitch(binding.showPlayerTitleSwitch, prefs, AppPreferences.KEY_SHOW_PLAYER_TITLE);
        bindPlayerVisibilitySwitch(binding.showPlayerArtistSwitch, prefs, AppPreferences.KEY_SHOW_PLAYER_ARTIST);
        bindPlayerVisibilitySwitch(binding.showPlayerAlbumSwitch, prefs, AppPreferences.KEY_SHOW_PLAYER_ALBUM);
        bindPlayerVisibilitySwitch(binding.showPlayerShuffleSwitch, prefs, AppPreferences.KEY_SHOW_PLAYER_SHUFFLE);
        bindPlayerVisibilitySwitch(binding.showPlayerQueueSwitch, prefs, AppPreferences.KEY_SHOW_PLAYER_QUEUE);
        bindPlayerVisibilitySwitch(binding.showPlayerRepeatSwitch, prefs, AppPreferences.KEY_SHOW_PLAYER_REPEAT);
        bindPlayerVisibilitySwitch(binding.showPlayerModeTextSwitch, prefs, AppPreferences.KEY_SHOW_PLAYER_MODE_TEXT);

        powerSaverSettings = new PowerSaverSettingsController(
                requireContext(), binding, binding.listTransparentSwitch,
                SettingsFragment.CATEGORY_BATTERY.equals(category));
        powerSaverSettings.attach();
        applyListAppearance();
    }

    private void configureCategoryVisibility() {
        setAllSettingsViewsGone();
        binding.settingsHeader.setVisibility(View.VISIBLE);
        binding.settingsHeaderTitle.setText(categoryTitle());
        binding.settingsHeaderSubtitle.setText(categorySubtitle());

        switch (category) {
            case SettingsFragment.CATEGORY_APPEARANCE:
                show(binding.themeRow, binding.appBackgroundSectionLabel, binding.appBackgroundPreviewCard,
                        binding.appBackgroundRow, binding.appBackgroundColorsRow, binding.accentRow,
                        binding.typographyRow, binding.animationRow, binding.listTransparentCard);
                break;
            case SettingsFragment.CATEGORY_NAVIGATION:
                show(binding.startPageRow, binding.tabsRow, binding.navigationRow, binding.showBottomNavCard);
                break;
            case SettingsFragment.CATEGORY_PLAYER:
                show(binding.playerPreviewCard, binding.styleRow,
                        binding.equalizerSkinBackgroundRow, binding.equalizerSkinBarsRow, binding.fftIntensityCard,
                        binding.backgroundRow, binding.playerBackgroundColorsRow, binding.playerVisibilityLabel, binding.playerAppearancePreviewCard,
                        binding.playerVisibilityCard, binding.centerPlayerInfoCard, binding.miniSubtitleCard, binding.miniPlayerBackgroundCard);
                break;
            case SettingsFragment.CATEGORY_AUDIO:
                show(binding.playbackOrderCard, binding.songOrderLabel,
                        binding.songShuffleSwitch, binding.songRepeatButton,
                        binding.transitionsLabel, binding.fadeCard, binding.crossfadeCard,
                        binding.volumeLabel, binding.normalizationCard, binding.djSectionLabel, binding.djCard);
                hide(binding.radioOrderLabel, binding.radioShuffleSwitch, binding.radioRepeatButton,
                        binding.radioOrderHelp, binding.orderDivider);
                break;
            case SettingsFragment.CATEGORY_RADIO:
                show(binding.playbackOrderCard, binding.radioOrderLabel, binding.radioShuffleSwitch,
                        binding.radioRepeatButton, binding.radioOrderHelp, binding.radioStationCountCard);
                hide(binding.songOrderLabel, binding.songShuffleSwitch, binding.songRepeatButton,
                        binding.orderDivider);
                break;
            case SettingsFragment.CATEGORY_LIBRARY:
                show(binding.librarySectionLabel, binding.libraryGroupingRow, binding.hideWhatsAppAudioCard,
                        binding.librarySourceFoldersCard);
                break;
            case SettingsFragment.CATEGORY_BATTERY:
                // The controller inserts the battery card below the header.
                break;
        }
        // Equalizer-specific rows are controlled again by refreshSummary().
        if (!SettingsFragment.CATEGORY_PLAYER.equals(category)) {
            binding.equalizerSkinBackgroundRow.setVisibility(View.GONE);
            binding.equalizerSkinBarsRow.setVisibility(View.GONE);
            binding.fftIntensityCard.setVisibility(View.GONE);
        }
    }

    private void setAllSettingsViewsGone() {
        hide(binding.playerPreviewCard, binding.playerSectionLabel, binding.styleRow,
                binding.equalizerSkinBackgroundRow, binding.equalizerSkinBarsRow, binding.fftIntensityCard,
                binding.accentRow, binding.animationRow, binding.backgroundRow, binding.playerBackgroundColorsRow, binding.typographyRow,
                binding.audioSectionLabel, binding.playbackOrderCard,
                binding.transitionsLabel, binding.fadeCard, binding.crossfadeCard,
                binding.volumeLabel, binding.normalizationCard, binding.djSectionLabel, binding.djCard,
                binding.applicationSectionLabel, binding.startPageRow, binding.tabsRow, binding.navigationRow,
                binding.themeRow, binding.appBackgroundSectionLabel, binding.appBackgroundPreviewCard,
                binding.appBackgroundRow, binding.appBackgroundColorsRow, binding.listTransparentCard,
                binding.playerVisibilityLabel, binding.playerAppearancePreviewCard, binding.playerVisibilityCard,
                binding.centerPlayerInfoCard, binding.showBottomNavCard, binding.miniSubtitleCard, binding.miniPlayerBackgroundCard,
                binding.radioStationCountCard,
                binding.librarySectionLabel, binding.libraryGroupingRow, binding.hideWhatsAppAudioCard,
                binding.librarySourceFoldersCard);
    }

    private void show(View... views) { for (View v : views) if (v != null) v.setVisibility(View.VISIBLE); }
    private void hide(View... views) { for (View v : views) if (v != null) v.setVisibility(View.GONE); }

    private String categoryTitle() {
        switch (category) {
            case SettingsFragment.CATEGORY_NAVIGATION: return "Navegación";
            case SettingsFragment.CATEGORY_PLAYER: return "Reproductor";
            case SettingsFragment.CATEGORY_AUDIO: return "Audio";
            case SettingsFragment.CATEGORY_RADIO: return "Radio";
            case SettingsFragment.CATEGORY_LIBRARY: return "Biblioteca";
            case SettingsFragment.CATEGORY_BATTERY: return "Batería";
            default: return "Apariencia";
        }
    }

    private String categorySubtitle() {
        switch (category) {
            case SettingsFragment.CATEGORY_NAVIGATION: return "Qué se muestra y cómo te movés por la app.";
            case SettingsFragment.CATEGORY_PLAYER: return "Solo cambia la pantalla Reproduciendo.";
            case SettingsFragment.CATEGORY_AUDIO: return "Canciones, transiciones, volumen y DJ Loquendo.";
            case SettingsFragment.CATEGORY_RADIO: return "Orden y repetición independientes de Canciones.";
            case SettingsFragment.CATEGORY_LIBRARY: return "Elegí cómo aparecen los agrupamientos de tu música.";
            case SettingsFragment.CATEGORY_BATTERY: return "Ahorro manual y activación automática.";
            default: return "Tema, fondo, color, tipografía y movimiento de toda la app.";
        }
    }

    private void applyListAppearance() {
        if (binding == null) return;
        ListAppearance.Appearance appearance = ListAppearance.resolve(requireContext());
        tintSettingsTree(binding.getRoot(), appearance.containerColor,
                appearance.primaryText, appearance.transparent);
        binding.settingsHeaderTitle.setTextColor(appearance.primaryText);
        binding.settingsHeaderSubtitle.setTextColor(appearance.secondaryText);
        binding.settingsBackButton.setColorFilter(appearance.primaryText);
    }

    private void tintSettingsTree(View view, int cardColor, int textColor, boolean forceText) {
        if (view instanceof MaterialCardView) ((MaterialCardView) view).setCardBackgroundColor(cardColor);
        if (forceText && view instanceof TextView) ((TextView) view).setTextColor(textColor);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                tintSettingsTree(group.getChildAt(i), cardColor, textColor, forceText);
            }
        }
    }

    private void setupAudioSettings() {
        audioPrefs = AudioPreferences.prefs(requireContext());
        audioPrefs.registerOnSharedPreferenceChangeListener(audioPreferenceListener);
        bindingAudio = true;
        binding.fadePauseSwitch.setChecked(AudioPreferences.fadePauseEnabled(requireContext()));
        binding.fadePauseSeek.setProgress(AudioPreferences.fadePauseMs(requireContext()) - 200);
        binding.crossfadeSwitch.setChecked(AudioPreferences.crossfadeEnabled(requireContext()));
        binding.crossfadeSeek.setProgress(AudioPreferences.crossfadeMs(requireContext()) / 100);
        binding.skipSameAlbumSwitch.setChecked(AudioPreferences.skipSameAlbum(requireContext()));
        binding.gaplessSwitch.setChecked(AudioPreferences.gaplessEnabled(requireContext()));
        binding.normalizationSwitch.setChecked(AudioPreferences.normalizationEnabled(requireContext()));
        String target = AudioPreferences.normalizationTarget(requireContext());
        binding.normalizationSoft.setChecked(AudioPreferences.TARGET_SOFT.equals(target));
        binding.normalizationNormal.setChecked(AudioPreferences.TARGET_NORMAL.equals(target));
        binding.normalizationLoud.setChecked(AudioPreferences.TARGET_LOUD.equals(target));
        binding.djLoquendoSwitch.setChecked(AudioPreferences.djEnabled(requireContext()));
        binding.djProbabilitySeek.setProgress(AudioPreferences.djProbability(requireContext()));
        bindingAudio = false;
        refreshAudioStatus();

        binding.fadePauseSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (!bindingAudio) audioPrefs.edit().putBoolean(AudioPreferences.KEY_FADE_PAUSE_ENABLED, checked).commit();
        });
        binding.fadePauseSeek.setOnSeekBarChangeListener(simpleSeek(progress -> {
            int ms = 200 + progress;
            binding.fadePauseValue.setText(ms + " ms");
            audioPrefs.edit().putInt(AudioPreferences.KEY_FADE_PAUSE_MS, ms).commit();
        }));
        binding.crossfadeSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (!bindingAudio) audioPrefs.edit().putBoolean(AudioPreferences.KEY_CROSSFADE_ENABLED, checked).commit();
        });
        binding.crossfadeSeek.setOnSeekBarChangeListener(simpleSeek(progress -> {
            int ms = progress * 100;
            binding.crossfadeValue.setText(String.format(Locale.getDefault(), "%.1f s", ms / 1000f));
            audioPrefs.edit().putInt(AudioPreferences.KEY_CROSSFADE_MS, ms).commit();
        }));
        binding.skipSameAlbumSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (!bindingAudio) audioPrefs.edit().putBoolean(AudioPreferences.KEY_SKIP_SAME_ALBUM, checked).commit();
        });
        binding.gaplessSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (!bindingAudio) audioPrefs.edit().putBoolean(AudioPreferences.KEY_GAPLESS_ENABLED, checked).commit();
        });
        binding.normalizationSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (!bindingAudio) audioPrefs.edit().putBoolean(AudioPreferences.KEY_NORMALIZATION_ENABLED, checked).commit();
        });
        binding.normalizationTargetGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (bindingAudio || checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            String value = id == binding.normalizationSoft.getId() ? AudioPreferences.TARGET_SOFT
                    : id == binding.normalizationLoud.getId() ? AudioPreferences.TARGET_LOUD
                    : AudioPreferences.TARGET_NORMAL;
            audioPrefs.edit().putString(AudioPreferences.KEY_NORMALIZATION_TARGET, value).commit();
        });
        binding.djLoquendoSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (!bindingAudio) audioPrefs.edit().putBoolean(AudioPreferences.KEY_DJ_ENABLED, checked).commit();
        });
        binding.djProbabilitySeek.setOnSeekBarChangeListener(simpleSeek(progress -> {
            audioPrefs.edit().putInt(AudioPreferences.KEY_DJ_PROBABILITY, progress).commit();
            binding.djProbabilityValue.setText("Frecuencia: " + progress + "%");
        }));
        binding.djPhrasesButton.setOnClickListener(v -> showDjPhrasesEditor());
        binding.djVoiceButton.setOnClickListener(v -> showDjVoicePicker());

        binding.analyzeLibraryButton.setOnClickListener(v -> {
            NormalizationAnalysisManager.get(requireContext()).analyzeLibrary();
            refreshAudioStatus();
        });

    }

    private interface SeekValueListener { void onValue(int progress); }
    private SeekBar.OnSeekBarChangeListener simpleSeek(SeekValueListener listener) {
        return new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && !bindingAudio) listener.onValue(progress);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        };
    }

    private void refreshAudioStatus() {
        if (binding == null || audioPrefs == null) return;
        binding.fadePauseValue.setText(AudioPreferences.fadePauseMs(requireContext()) + " ms");
        binding.crossfadeValue.setText(String.format(Locale.getDefault(), "%.1f s", AudioPreferences.crossfadeMs(requireContext()) / 1000f));
        int progress = audioPrefs.getInt(AudioPreferences.KEY_ANALYSIS_PROGRESS, 0);
        boolean analysisRunning = audioPrefs.getBoolean(AudioPreferences.KEY_ANALYSIS_RUNNING, false);
        binding.analysisProgress.setProgress(progress);
        binding.analysisStatus.setText(audioPrefs.getString(AudioPreferences.KEY_ANALYSIS_LABEL,
                analysisRunning ? "Analizando…" : "Sin análisis en curso"));
        binding.analyzeLibraryButton.setEnabled(!analysisRunning);
        int djProbability = AudioPreferences.djProbability(requireContext());
        binding.djProbabilityValue.setText("Frecuencia: " + djProbability + "%");
        binding.djProbabilitySeek.setProgress(djProbability);
        binding.djLoquendoSwitch.setChecked(AudioPreferences.djEnabled(requireContext()));
        String voice = AudioPreferences.djVoiceName(requireContext());
        binding.djVoiceValue.setText(voice == null || voice.isEmpty()
                ? "Voz: automática offline" : "Voz: " + friendlyVoiceName(voice));

    }

    private void showDjPhrasesEditor() {
        EditText field = new EditText(requireContext());
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        field.setMinLines(6);
        field.setGravity(Gravity.TOP | Gravity.START);
        field.setText(AudioPreferences.djPhrases(requireContext()));
        int pad = dp(18);
        LinearLayout wrapper = new LinearLayout(requireContext());
        wrapper.setPadding(pad, dp(4), pad, 0);
        wrapper.addView(field, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Frases del DJ")
                .setMessage("Escribí una frase por línea. El DJ elegirá una al azar cuando toque hablar.")
                .setView(wrapper)
                .setPositiveButton("Guardar", (dialog, which) -> {
                    String value = field.getText().toString().trim();
                    audioPrefs.edit().putString(AudioPreferences.KEY_DJ_PHRASES, value).commit();
                    refreshAudioStatus();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void showDjVoicePicker() {
        if (voicePickerTts != null) {
            voicePickerTts.shutdown();
            voicePickerTts = null;
        }
        Toast.makeText(requireContext(), "Cargando voces offline…", Toast.LENGTH_SHORT).show();
        voicePickerTts = new TextToSpeech(requireContext().getApplicationContext(), status -> {
            if (!isAdded()) return;
            if (status != TextToSpeech.SUCCESS || voicePickerTts == null) {
                Toast.makeText(requireContext(), "No se pudo abrir el motor de voz del teléfono.", Toast.LENGTH_LONG).show();
                return;
            }
            Set<Voice> voices = voicePickerTts.getVoices();
            List<Voice> localVoices = new ArrayList<>();
            if (voices != null) {
                for (Voice voice : voices) {
                    if (voice == null || voice.isNetworkConnectionRequired()) continue;
                    Set<String> features = voice.getFeatures();
                    if (features != null && features.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)) continue;
                    localVoices.add(voice);
                }
            }
            localVoices.sort(Comparator
                    .comparingInt((Voice v) -> v.getLocale() != null && "es".equalsIgnoreCase(v.getLocale().getLanguage()) ? 0 : 1)
                    .thenComparing(v -> v.getLocale() == null ? "" : v.getLocale().getDisplayName())
                    .thenComparing(Voice::getName));

            List<String> labels = new ArrayList<>();
            labels.add("Automática offline (preferir español)");
            for (Voice voice : localVoices) labels.add(voiceLabel(voice));
            String selected = AudioPreferences.djVoiceName(requireContext());
            int checked = 0;
            for (int i = 0; i < localVoices.size(); i++) {
                if (localVoices.get(i).getName().equals(selected)) { checked = i + 1; break; }
            }
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Voz del DJ")
                    .setSingleChoiceItems(labels.toArray(new String[0]), checked, (dialog, which) -> {
                        String name = which == 0 ? "" : localVoices.get(which - 1).getName();
                        audioPrefs.edit().putString(AudioPreferences.KEY_DJ_VOICE, name).commit();
                        dialog.dismiss();
                        refreshAudioStatus();
                    })
                    .setNegativeButton("Cancelar", null)
                    .show();
        });
    }

    private String voiceLabel(Voice voice) {
        Locale locale = voice.getLocale();
        String localeName = locale == null ? "Idioma desconocido" : locale.getDisplayName(locale);
        return localeName + " · " + friendlyVoiceName(voice.getName());
    }

    private String friendlyVoiceName(String name) {
        if (name == null || name.trim().isEmpty()) return "automática offline";
        String clean = name.replace('_', ' ').replace('-', ' ').trim();
        return clean.length() > 34 ? clean.substring(0, 31) + "…" : clean;
    }

    private void bindSwitches(SharedPreferences prefs) {
        bindingPreferences = true;
        binding.miniSubtitleSwitch.setChecked(prefs.getBoolean(AppPreferences.KEY_SHOW_MINI_SUBTITLE, true));
        binding.miniPlayerBackgroundSwitch.setChecked(AppPreferences.miniPlayerUsesPlayerBackground(requireContext()));
        binding.radioStationCountSwitch.setChecked(AppPreferences.showRadioStationCount(requireContext()));
        binding.centerPlayerInfoSwitch.setChecked(prefs.getBoolean(AppPreferences.KEY_CENTER_PLAYER_INFO, false));
        binding.showBottomNavNowPlayingSwitch.setChecked(AppPreferences.showBottomNavInNowPlaying(requireContext()));
        binding.showPlayerBackSwitch.setChecked(AppPreferences.showPlayerBackButton(requireContext()));
        binding.showPlayerHeaderLabelSwitch.setChecked(AppPreferences.showPlayerHeaderLabel(requireContext()));
        binding.showPlayerMusicButtonSwitch.setChecked(AppPreferences.showPlayerMusicButton(requireContext()));
        binding.showPlayerArtworkSwitch.setChecked(AppPreferences.showPlayerArtwork(requireContext()));
        binding.showPlayerProgressLineSwitch.setChecked(AppPreferences.showPlayerProgressLine(requireContext()));
        binding.showPlayerTitleSwitch.setChecked(AppPreferences.showPlayerTitle(requireContext()));
        binding.showPlayerArtistSwitch.setChecked(AppPreferences.showPlayerArtist(requireContext()));
        binding.showPlayerAlbumSwitch.setChecked(AppPreferences.showPlayerAlbum(requireContext()));
        binding.showPlayerShuffleSwitch.setChecked(AppPreferences.showPlayerShuffle(requireContext()));
        binding.showPlayerQueueSwitch.setChecked(AppPreferences.showPlayerQueue(requireContext()));
        binding.showPlayerRepeatSwitch.setChecked(AppPreferences.showPlayerRepeat(requireContext()));
        binding.showPlayerModeTextSwitch.setChecked(AppPreferences.showPlayerModeText(requireContext()));
        binding.hideWhatsAppAudioSwitch.setChecked(AppPreferences.hideWhatsAppAudio(requireContext()));
        bindingPreferences = false;
    }

    private void bindPlayerVisibilitySwitch(com.google.android.material.materialswitch.MaterialSwitch toggle,
                                            SharedPreferences prefs, String key) {
        toggle.setOnCheckedChangeListener((button, checked) -> {
            if (bindingPreferences) return;
            prefs.edit().putBoolean(key, checked).commit();
            refreshSummary();
        });
    }

    private void refreshSummary() {
        if (binding == null) return;
        String style = AppPreferences.getPlayerStyle(requireContext());
        String styleName = styleName(style);
        String animation = AppPreferences.getPlayerAnimation(requireContext());
        int accent = AppPreferences.getPlayerAccentColor(requireContext());

        binding.styleValue.setText(styleName);
        boolean equalizerSkin = AppPreferences.STYLE_EQUALIZER_2D.equals(style);
        boolean ringSkin = AppPreferences.STYLE_RING.equals(style);
        boolean showVisualizerRows = SettingsFragment.CATEGORY_PLAYER.equals(category) && (equalizerSkin || ringSkin);
        binding.equalizerSkinBackgroundRow.setVisibility(showVisualizerRows ? View.VISIBLE : View.GONE);
        binding.equalizerSkinBarsRow.setVisibility(showVisualizerRows ? View.VISIBLE : View.GONE);
        binding.fftIntensityCard.setVisibility(showVisualizerRows ? View.VISIBLE : View.GONE);
        int visualizerBackground = ringSkin ? AppPreferences.getRingSkinBackgroundColor(requireContext())
                : AppPreferences.getEqualizerSkinBackgroundColor(requireContext());
        int visualizerColor = ringSkin ? AppPreferences.getRingSkinColor(requireContext())
                : AppPreferences.getEqualizerSkinBarsColor(requireContext());
        binding.equalizerSkinBackgroundLabel.setText("Color de fondo");
        binding.equalizerSkinBarsLabel.setText(ringSkin ? "Color del anillo" : "Color de las barras");
        binding.equalizerSkinBackgroundPreview.setBackgroundTintList(ColorStateList.valueOf(visualizerBackground));
        binding.equalizerSkinBarsPreview.setBackgroundTintList(ColorStateList.valueOf(visualizerColor));
        binding.equalizerSkinBackgroundValue.setText(String.format(Locale.getDefault(), "#%06X", visualizerBackground & 0xFFFFFF));
        binding.equalizerSkinBarsValue.setText(String.format(Locale.getDefault(), "#%06X", visualizerColor & 0xFFFFFF));
        int visualizerIntensity = ringSkin ? AppPreferences.getRingSkinIntensity(requireContext())
                : AppPreferences.getEqualizerSkinIntensity(requireContext());
        bindingPreferences = true;
        binding.fftIntensitySeek.setProgress(visualizerIntensity - 50);
        bindingPreferences = false;
        binding.fftIntensityValue.setText(visualizerIntensity + "%");
        binding.previewStyleTitle.setText(styleName);
        String styleKind = AppPreferences.STYLE_RING.equals(style) ? "Skin Canvas"
                : AppPreferences.isTwoDimensionalStyle(style) ? "Skin 2D" : "Objeto 3D";
        binding.previewStyleSubtitle.setText(styleKind + " · " + animationName(animation).toLowerCase(Locale.getDefault()));
        binding.animationValue.setText(animationName(animation));
        binding.backgroundValue.setText("Solo Reproduciendo · " + backgroundName(AppPreferences.getPlayerBackground(requireContext())));
        binding.playerBackgroundColorsValue.setText(PlayerBackgroundSettingsController.summary(requireContext()));
        binding.playerBackgroundColorPreview.setBackgroundTintList(ColorStateList.valueOf(
                PlayerBackgroundSettingsController.previewColor(requireContext())));
        binding.typographyValue.setText(typographyName(AppPreferences.getPlayerTypography(requireContext())));
        binding.startPageValue.setText(startPageName(AppPreferences.getStartPage(requireContext())));
        binding.tabsValue.setText(AppPreferences.TABS_BOTTOM.equals(AppPreferences.getMusicTabsPosition(requireContext())) ? "Abajo" : "Arriba");
        binding.themeValue.setText(themeName(AppPreferences.getThemeMode(requireContext())));
        binding.appBackgroundValue.setText("Global · " + backgroundName(AppPreferences.getAppBackground(requireContext())));
        binding.appBackgroundColorsValue.setText(appBackgroundPaletteName());
        binding.appBackgroundColorPreview.setBackgroundTintList(ColorStateList.valueOf(AppPreferences.getAppBackgroundColor1(requireContext())));
        updateAppBackgroundPreview(accent);
        binding.navigationValue.setText(navigationSummary());
        binding.libraryGroupingValue.setText(AppPreferences.libraryGroupingName(requireContext()));
        bindingPreferences = true;
        binding.hideWhatsAppAudioSwitch.setChecked(AppPreferences.hideWhatsAppAudio(requireContext()));
        bindingPreferences = false;

        boolean artistsEnabled = AppPreferences.libraryArtistsEnabled(requireContext());
        boolean albumsEnabled = AppPreferences.libraryAlbumsEnabled(requireContext());
        binding.skipSameAlbumSwitch.setVisibility(
                SettingsFragment.CATEGORY_AUDIO.equals(category) && albumsEnabled ? View.VISIBLE : View.GONE);
        binding.centerPlayerInfoLabel.setText(artistsEnabled ? "Centrar título y artista" : "Centrar título e información");
        binding.miniSubtitleLabel.setText(artistsEnabled ? "Mostrar artista / emisora" : "Mostrar información secundaria / emisora");

        String shape = AppPreferences.getArtworkShape(requireContext());
        String size = AppPreferences.useLargeArtwork(requireContext()) ? "Grande" : "Compacta";
        binding.artworkValue.setText(size + " · " + shapeName(shape));
        boolean artworkVisible = AppPreferences.showPlayerArtwork(requireContext());
        binding.artworkRow.setEnabled(artworkVisible);
        binding.artworkRow.setAlpha(artworkVisible ? 1f : 0.52f);
        float corner = AppPreferences.SHAPE_SQUARE.equals(shape) ? 0f
                : AppPreferences.SHAPE_CIRCLE.equals(shape) ? dp(19) : dp(9);
        binding.artworkShapePreview.setShapeAppearanceModel(ShapeAppearanceModel.builder().setAllCornerSizes(corner).build());
        binding.artworkShapePreview.setBackgroundTintList(ColorStateList.valueOf(accent));
        binding.progressStyleValue.setText(PlayerProgressStyleSettingsController.label(
                requireContext(), AppPreferences.getPlayerProgressStyle(requireContext())));
        boolean progressVisible = AppPreferences.showPlayerProgressLine(requireContext());
        binding.progressStyleRow.setEnabled(progressVisible);
        binding.progressStyleRow.setAlpha(progressVisible ? 1f : 0.52f);
        binding.playerAppearancePreview.applyPreferences();

        binding.accentPreview.setBackgroundTintList(ColorStateList.valueOf(accent));
        binding.accentValue.setText(AppPreferences.useCustomAccent(requireContext())
                ? String.format(Locale.getDefault(), "Personalizado · #%06X", accent & 0xFFFFFF)
                : accentName(AppPreferences.getPlayerAccent(requireContext())));

        binding.previewStyleTitle.setTypeface(AppPreferences.getPlayerTypeface(requireContext(), android.graphics.Typeface.BOLD));
        binding.previewStyleSubtitle.setTypeface(AppPreferences.getPlayerTypeface(requireContext(), android.graphics.Typeface.NORMAL));
        if (SettingsFragment.CATEGORY_PLAYER.equals(category)) updatePreviewWebViews(style, accent);
        applyListAppearance();
    }


    @android.annotation.SuppressLint("SetJavaScriptEnabled")
    private void setupPreviewWebViews() {
        if (binding == null) return;
        setupPreviewWebView(binding.playerPreviewWeb, true);
        setupPreviewWebView(binding.styleMiniPreviewWeb, false);
    }

    @android.annotation.SuppressLint("SetJavaScriptEnabled")
    private void setupPreviewWebView(WebView webView, boolean large) {
        webView.setBackgroundColor(Color.TRANSPARENT);
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(false);
        settings.setDomStorageEnabled(false);
        settings.setBlockNetworkLoads(true);
        webView.setOnLongClickListener(v -> true);
        webView.setHapticFeedbackEnabled(false);
        webView.setVerticalScrollBarEnabled(false);
        webView.setHorizontalScrollBarEnabled(false);
        webView.setOnTouchListener((v, event) -> {
            if (!large && binding != null && event.getActionMasked() == android.view.MotionEvent.ACTION_UP) {
                binding.styleRow.performClick();
            }
            return true;
        });
        webView.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                if (binding == null) return;
                if (large) previewWebReady = true; else miniPreviewWebReady = true;
                int accent = AppPreferences.getPlayerAccentColor(requireContext());
                updateSinglePreview(view, AppPreferences.getPlayerStyle(requireContext()), accent, !large);
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                return !("file".equals(uri.getScheme()) && uri.getPath() != null && uri.getPath().startsWith("/android_asset/"));
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return !url.startsWith("file:///android_asset/");
            }
        });
        webView.loadUrl("file:///android_asset/player3d/index.html");
    }

    private void updatePreviewWebViews(String style, int accent) {
        if (binding == null) return;
        updateSinglePreview(binding.playerPreviewWeb, style, accent, false);
        updateSinglePreview(binding.styleMiniPreviewWeb, style, accent, true);
    }

    private void updateSinglePreview(WebView webView, String style, int accent, boolean compact) {
        if (webView == null) return;
        boolean ready = compact ? miniPreviewWebReady : previewWebReady;
        if (!ready) return;
        int previewAccent = AppPreferences.STYLE_RING.equals(style)
                ? AppPreferences.getRingSkinColor(requireContext()) : accent;
        String hex = String.format(Locale.US, "#%06X", previewAccent & 0xFFFFFF);
        String title = compact ? "Tu" : "Tu canción";
        String artist = AppPreferences.libraryArtistsEnabled(requireContext())
                ? (compact ? "Art" : "Tu artista")
                : (compact ? "Álb" : "Tu álbum");
        webView.evaluateJavascript("Player3D.setSkin(" + org.json.JSONObject.quote(style) + ")", null);
        webView.evaluateJavascript("Player3D.setAccent(" + org.json.JSONObject.quote(hex) + ")", null);
        webView.evaluateJavascript("Player3D.setAnimation(" + (compact ? "0" : String.valueOf(AppPreferences.getAnimationMultiplier(requireContext()))) + ")", null);
        webView.evaluateJavascript("Player3D.setTypography(" + org.json.JSONObject.quote(AppPreferences.getPlayerTypography(requireContext())) + ")", null);
        webView.evaluateJavascript("Player3D.setPlaying(" + (!compact) + ")", null);
        webView.evaluateJavascript("Player3D.setTrack(" + org.json.JSONObject.quote(title) + "," + org.json.JSONObject.quote(artist) + ","
                + org.json.JSONObject.quote("") + ")", null);
        webView.evaluateJavascript("Player3D.setPreviewMode(" + compact + ")", null);
        webView.evaluateJavascript("Player3D.setAppVisible(true)", null);
    }

    private void showStylePicker() {
        stylePicker.show(this, this::refreshSummary);
    }

    private void showAccentPicker() {
        int initial = AppPreferences.getPlayerAccentColor(requireContext());
        ModernColorPickerDialog.show(requireContext(), "Color de acento", initial, color -> {
            AppPreferences.prefs(requireContext()).edit()
                    .putInt(AppPreferences.KEY_CUSTOM_ACCENT_COLOR, color)
                    .putBoolean(AppPreferences.KEY_USE_CUSTOM_ACCENT, true)
                    .commit();
            refreshSummary();
        });
    }

    private void updateAppBackgroundPreview(int accent) {
        if (binding == null) return;
        int nightMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        boolean light = nightMode != Configuration.UI_MODE_NIGHT_YES;
        float motion = PowerSaverManager.isActive(requireContext()) ? 0f : AppPreferences.getAnimationMultiplier(requireContext());
        binding.appBackgroundPreview.setLightMode(light);
        binding.appBackgroundPreview.setScene(AppPreferences.getAppBackground(requireContext()), accent, motion);
        binding.appBackgroundPreview.setPalette(AppPreferences.getAppBackgroundColorMode(requireContext()),
                AppPreferences.getAppBackgroundColor1(requireContext()),
                AppPreferences.getAppBackgroundColor2(requireContext()),
                AppPreferences.getAppBackgroundColor3(requireContext()));
    }

    private void showAnimationPicker() {
        String[] labels = {"Sin animación", "Suave", "Viva"};
        String[] values = {AppPreferences.ANIMATION_OFF, AppPreferences.ANIMATION_SOFT, AppPreferences.ANIMATION_LIVELY};
        int checked = indexOf(values, AppPreferences.getPlayerAnimation(requireContext()));
        new MaterialAlertDialogBuilder(requireContext()).setTitle("Animaciones")
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    AppPreferences.prefs(requireContext()).edit().putString(AppPreferences.KEY_PLAYER_ANIMATION, values[which]).commit();
                    dialog.dismiss(); refreshSummary();
                }).setNegativeButton("Cancelar", null).show();
    }

    private void showBackgroundPicker() {
        String[] labels = {"Aurora", "Ondas", "Grid", "Pulso", "Oscuro", "Sin fondo"};
        String[] values = {AppPreferences.BACKGROUND_AURORA, AppPreferences.BACKGROUND_WAVES,
                AppPreferences.BACKGROUND_GRID, AppPreferences.BACKGROUND_PULSE,
                AppPreferences.BACKGROUND_DARK, AppPreferences.BACKGROUND_NONE};
        int checked = indexOf(values, AppPreferences.getPlayerBackground(requireContext()));
        new MaterialAlertDialogBuilder(requireContext()).setTitle("Fondo del reproductor")
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    AppPreferences.prefs(requireContext()).edit().putString(AppPreferences.KEY_PLAYER_BACKGROUND, values[which]).commit();
                    dialog.dismiss();
                    refreshSummary();
                }).setNegativeButton("Cancelar", null).show();
    }

    private void showAppBackgroundPicker() {
        String[] labels = {"Aurora", "Ondas", "Grid", "Pulso", "Oscuro", "Sin fondo"};
        String[] values = {AppPreferences.BACKGROUND_AURORA, AppPreferences.BACKGROUND_WAVES,
                AppPreferences.BACKGROUND_GRID, AppPreferences.BACKGROUND_PULSE,
                AppPreferences.BACKGROUND_DARK, AppPreferences.BACKGROUND_NONE};
        int checked = indexOf(values, AppPreferences.getAppBackground(requireContext()));
        new MaterialAlertDialogBuilder(requireContext()).setTitle("Fondo de la app")
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    AppPreferences.setAppBackground(requireContext(), values[which]);
                    dialog.dismiss();
                    refreshSummary();
                }).setNegativeButton("Cancelar", null).show();
    }

    private void showAppBackgroundColorsPicker() {
        String[] labels = {"Usar color de acento", "Color sólido", "Degradado de 2 colores",
                "Degradado de 3 colores", "Multicolor animado"};
        String[] values = {AppPreferences.APP_BACKGROUND_COLOR_ACCENT, AppPreferences.APP_BACKGROUND_COLOR_SOLID,
                AppPreferences.APP_BACKGROUND_COLOR_GRADIENT_2, AppPreferences.APP_BACKGROUND_COLOR_GRADIENT_3,
                AppPreferences.APP_BACKGROUND_COLOR_MULTICOLOR};
        int checked = indexOf(values, AppPreferences.getAppBackgroundColorMode(requireContext()));
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Colores del fondo")
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    if (AppPreferences.APP_BACKGROUND_COLOR_ACCENT.equals(values[which])) {
                        AppPreferences.setAppBackgroundPalette(requireContext(), values[which],
                                AppPreferences.getAppBackgroundColor1(requireContext()),
                                AppPreferences.getAppBackgroundColor2(requireContext()),
                                AppPreferences.getAppBackgroundColor3(requireContext()));
                        refreshSummary();
                    } else {
                        showAppBackgroundColorEditor(values[which]);
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void showAppBackgroundColorEditor(String mode) {
        int count = AppPreferences.APP_BACKGROUND_COLOR_SOLID.equals(mode) ? 1
                : AppPreferences.APP_BACKGROUND_COLOR_GRADIENT_2.equals(mode) ? 2 : 3;
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(8), dp(22), 0);

        com.appincreible.musicplayer.themes.view.PlayerBackgroundView preview =
                new com.appincreible.musicplayer.themes.view.PlayerBackgroundView(requireContext());
        preview.setLightMode(false);
        preview.setScene(AppPreferences.getAppBackground(requireContext()),
                AppPreferences.getPlayerAccentColor(requireContext()), .8f);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(140));
        pp.bottomMargin = dp(14);
        root.addView(preview, pp);

        int[] colors = {AppPreferences.getAppBackgroundColor1(requireContext()),
                AppPreferences.getAppBackgroundColor2(requireContext()),
                AppPreferences.getAppBackgroundColor3(requireContext())};
        preview.setPalette(mode, colors[0], colors[1], colors[2]);

        TextView[] values = new TextView[count];
        View[] swatches = new View[count];
        for (int i = 0; i < count; i++) {
            final int index = i;
            LinearLayout row = new LinearLayout(requireContext());
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, dp(5), 0, dp(5));

            View swatch = new View(requireContext());
            swatch.setBackgroundResource(com.appincreible.musicplayer.R.drawable.bg_color_preview);
            swatch.setBackgroundTintList(ColorStateList.valueOf(colors[i]));
            row.addView(swatch, new LinearLayout.LayoutParams(dp(42), dp(42)));
            swatches[i] = swatch;

            LinearLayout textBlock = new LinearLayout(requireContext());
            textBlock.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            tp.leftMargin = dp(14);
            row.addView(textBlock, tp);

            TextView label = new TextView(requireContext());
            label.setText(count == 1 ? "Color" : "Color " + (i + 1));
            label.setTextSize(16f);
            label.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
            textBlock.addView(label);

            TextView value = new TextView(requireContext());
            value.setText(String.format(Locale.getDefault(), "#%06X", colors[i] & 0xFFFFFF));
            value.setTextColor(Color.GRAY);
            textBlock.addView(value);
            values[i] = value;

            com.google.android.material.button.MaterialButton choose = new com.google.android.material.button.MaterialButton(requireContext());
            choose.setText("Elegir");
            choose.setOnClickListener(v -> showColorPickerDialog("Color " + (index + 1), colors[index], picked -> {
                colors[index] = picked;
                swatches[index].setBackgroundTintList(ColorStateList.valueOf(picked));
                values[index].setText(String.format(Locale.getDefault(), "#%06X", picked & 0xFFFFFF));
                if (count == 1) { colors[1] = picked; colors[2] = picked; }
                else if (count == 2) colors[2] = colors[1];
                preview.setPalette(mode, colors[0], colors[1], colors[2]);
            }));
            row.addView(choose, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            root.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(AppPreferences.APP_BACKGROUND_COLOR_MULTICOLOR.equals(mode)
                        ? "Multicolor" : "Colores del fondo")
                .setMessage("Elegí cada color con el picker. También podés ajustar el HEX exacto dentro del selector.")
                .setView(root)
                .setPositiveButton("Guardar", (dialog, which) -> {
                    if (count == 1) { colors[1] = colors[0]; colors[2] = colors[0]; }
                    else if (count == 2) colors[2] = colors[1];
                    AppPreferences.setAppBackgroundPalette(requireContext(), mode, colors[0], colors[1], colors[2]);
                    refreshSummary();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private interface ColorSelectionCallback { void onSelected(int color); }

    private void showColorPickerDialog(String title, int initialColor, ColorSelectionCallback callback) {
        ModernColorPickerDialog.show(requireContext(), title, initialColor, callback::onSelected);
    }

    private String appBackgroundPaletteName() {
        String mode = AppPreferences.getAppBackgroundColorMode(requireContext());
        int c1 = AppPreferences.getAppBackgroundColor1(requireContext());
        int c2 = AppPreferences.getAppBackgroundColor2(requireContext());
        int c3 = AppPreferences.getAppBackgroundColor3(requireContext());
        if (AppPreferences.APP_BACKGROUND_COLOR_SOLID.equals(mode))
            return String.format(Locale.getDefault(), "Sólido · #%06X", c1 & 0xFFFFFF);
        if (AppPreferences.APP_BACKGROUND_COLOR_GRADIENT_2.equals(mode))
            return String.format(Locale.getDefault(), "Degradado · #%06X → #%06X", c1 & 0xFFFFFF, c2 & 0xFFFFFF);
        if (AppPreferences.APP_BACKGROUND_COLOR_GRADIENT_3.equals(mode)) return "Degradado · 3 colores";
        if (AppPreferences.APP_BACKGROUND_COLOR_MULTICOLOR.equals(mode)) return "Multicolor animado";
        return "Color de acento";
    }

    private void showTypographyPicker() {
        ModernUiDialogs.showTypographyPicker(this, this::refreshSummary);
    }

    private void showNavigationEditor() {
        List<String> order = new ArrayList<>(Arrays.asList(AppPreferences.getBottomNavOrder(requireContext()).split(",")));
        ensureNavItem(order, AppPreferences.NAV_MUSIC);
        ensureNavItem(order, AppPreferences.NAV_RADIO);
        ensureNavItem(order, AppPreferences.NAV_GAMES);

        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(6), dp(16), dp(4));
        java.util.Map<String, Boolean> visibleState = new java.util.HashMap<>();
        for (String key : order) visibleState.put(key, isNavVisible(key));

        Runnable rebuild = new Runnable() {
            @Override public void run() {
                root.removeAllViews();
                for (int i = 0; i < order.size(); i++) {
                    final int index = i;
                    String key = order.get(i);
                    LinearLayout row = new LinearLayout(requireContext());
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    row.setPadding(0, dp(5), 0, dp(5));

                    CheckBox check = new CheckBox(requireContext());
                    check.setText(navName(key));
                    check.setChecked(Boolean.TRUE.equals(visibleState.get(key)));
                    check.setOnCheckedChangeListener((button, checked) -> visibleState.put(key, checked));
                    row.addView(check, new LinearLayout.LayoutParams(0, dp(48), 1f));

                    Button up = new Button(requireContext());
                    up.setText("↑");
                    up.setEnabled(index > 0);
                    up.setOnClickListener(v -> {
                        java.util.Collections.swap(order, index, index - 1);
                        run();
                    });
                    row.addView(up, new LinearLayout.LayoutParams(dp(52), dp(48)));

                    Button down = new Button(requireContext());
                    down.setText("↓");
                    down.setEnabled(index < order.size() - 1);
                    down.setOnClickListener(v -> {
                        java.util.Collections.swap(order, index, index + 1);
                        run();
                    });
                    row.addView(down, new LinearLayout.LayoutParams(dp(52), dp(48)));
                    root.addView(row);
                }
                TextView fixed = new TextView(requireContext());
                fixed.setText("Ajustes queda siempre visible al final.");
                fixed.setTextColor(com.google.android.material.color.MaterialColors.getColor(fixed, com.google.android.material.R.attr.colorOnSurfaceVariant));
                fixed.setPadding(0, dp(10), 0, dp(4));
                root.addView(fixed);
            }
        };
        rebuild.run();

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Barra inferior")
                .setView(root)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Guardar", (dialog, which) -> {
                    SharedPreferences.Editor editor = AppPreferences.prefs(requireContext()).edit()
                            .putString(AppPreferences.KEY_BOTTOM_NAV_ORDER, android.text.TextUtils.join(",", order));
                    for (String key : order) {
                        boolean visible = Boolean.TRUE.equals(visibleState.get(key));
                        if (AppPreferences.NAV_MUSIC.equals(key)) editor.putBoolean(AppPreferences.KEY_SHOW_MUSIC_NAV, visible);
                        else if (AppPreferences.NAV_RADIO.equals(key)) editor.putBoolean(AppPreferences.KEY_SHOW_RADIO_NAV, visible);
                        else if (AppPreferences.NAV_GAMES.equals(key)) editor.putBoolean(AppPreferences.KEY_SHOW_GAMES_NAV, visible);
                    }
                    editor.commit();
                    refreshSummary();
                    requireActivity().recreate();
                }).show();
    }

    private void ensureNavItem(List<String> order, String key) { if (!order.contains(key)) order.add(key); }
    private boolean isNavVisible(String key) {
        if (AppPreferences.NAV_MUSIC.equals(key)) return AppPreferences.showMusicNav(requireContext());
        if (AppPreferences.NAV_RADIO.equals(key)) return AppPreferences.showRadioNav(requireContext());
        return AppPreferences.showGamesNav(requireContext());
    }
    private String navName(String key) {
        if (AppPreferences.NAV_RADIO.equals(key)) return "Radio";
        if (AppPreferences.NAV_GAMES.equals(key)) return "Juegos";
        return "Música";
    }
    private String navigationSummary() {
        List<String> visible = new ArrayList<>();
        for (String key : AppPreferences.getBottomNavOrder(requireContext()).split(",")) {
            if (isNavVisible(key)) visible.add(navName(key));
        }
        visible.add("Ajustes");
        return android.text.TextUtils.join(" · ", visible);
    }

    private void showArtworkPicker() {
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(8), dp(22), 0);

        com.google.android.material.materialswitch.MaterialSwitch large =
                new com.google.android.material.materialswitch.MaterialSwitch(requireContext());
        large.setText("Portada grande");
        large.setChecked(AppPreferences.useLargeArtwork(requireContext()));
        root.addView(large, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView shapeLabel = new TextView(requireContext());
        shapeLabel.setText("Forma");
        shapeLabel.setTextAppearance(R.style.TextAppearance_AppIncreible_Caption);
        shapeLabel.setPadding(0, dp(12), 0, dp(6));
        root.addView(shapeLabel);

        com.google.android.material.button.MaterialButtonToggleGroup group =
                new com.google.android.material.button.MaterialButtonToggleGroup(requireContext());
        group.setSingleSelection(true);
        group.setSelectionRequired(true);
        String[] labels = {"Redondeada", "Cuadrada", "Circular"};
        String[] values = {AppPreferences.SHAPE_ROUNDED, AppPreferences.SHAPE_SQUARE, AppPreferences.SHAPE_CIRCLE};
        String current = AppPreferences.getArtworkShape(requireContext());
        for (int i = 0; i < labels.length; i++) {
            com.google.android.material.button.MaterialButton button = new com.google.android.material.button.MaterialButton(requireContext());
            button.setId(View.generateViewId());
            button.setText(labels[i]);
            button.setTag(values[i]);
            group.addView(button, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            if (values[i].equals(current)) group.check(button.getId());
        }
        root.addView(group, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Runnable persist = () -> {
            String shape = AppPreferences.getArtworkShape(requireContext());
            View selected = group.findViewById(group.getCheckedButtonId());
            if (selected != null && selected.getTag() instanceof String) shape = (String) selected.getTag();
            AppPreferences.prefs(requireContext()).edit()
                    .putBoolean(AppPreferences.KEY_LARGE_ARTWORK, large.isChecked())
                    .putString(AppPreferences.KEY_ARTWORK_SHAPE, shape)
                    .commit();
            refreshSummary();
        };
        large.setOnCheckedChangeListener((button, checked) -> persist.run());
        group.addOnButtonCheckedListener((toggleGroup, checkedId, isChecked) -> {
            if (isChecked) persist.run();
        });

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Tamaño y forma de portada")
                .setView(root)
                .setPositiveButton("Listo", null)
                .show();
    }

    private void showStartPagePicker() {
        String[] labels = {"Música", "Radio", "Ajustes"};
        String[] values = {AppPreferences.START_MUSIC, AppPreferences.START_RADIO, AppPreferences.START_SETTINGS};
        int checked = indexOf(values, AppPreferences.getStartPage(requireContext()));
        new MaterialAlertDialogBuilder(requireContext()).setTitle("Pantalla al abrir")
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    AppPreferences.setStartPage(requireContext(), values[which]); dialog.dismiss(); refreshSummary();
                }).setNegativeButton("Cancelar", null).show();
    }

    private void showTabsPicker() {
        String[] labels = {"Arriba", "Abajo"};
        String[] values = {AppPreferences.TABS_TOP, AppPreferences.TABS_BOTTOM};
        int checked = indexOf(values, AppPreferences.getMusicTabsPosition(requireContext()));
        new MaterialAlertDialogBuilder(requireContext()).setTitle("Pestañas de Música")
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    AppPreferences.prefs(requireContext()).edit().putString(AppPreferences.KEY_MUSIC_TABS_POSITION, values[which]).commit();
                    dialog.dismiss(); refreshSummary();
                }).setNegativeButton("Cancelar", null).show();
    }

    private void showLibraryGroupingPicker() {
        final String[] values = {
                AppPreferences.LIBRARY_GROUPING_BOTH,
                AppPreferences.LIBRARY_GROUPING_ARTIST,
                AppPreferences.LIBRARY_GROUPING_ALBUM,
                AppPreferences.LIBRARY_GROUPING_NONE
        };
        final String[] labels = {"Banda y álbum", "Solo banda", "Solo álbum", "Nada"};
        String current = AppPreferences.getLibraryGroupingMode(requireContext());
        int checked = 0;
        for (int i = 0; i < values.length; i++) if (values[i].equals(current)) checked = i;
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Agrupar canciones por")
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    AppPreferences.setLibraryGroupingMode(requireContext(), values[which]);
                    refreshSummary();
                    dialog.dismiss();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void showThemePicker() {
        String[] labels = {"Sistema", "Claro", "Oscuro"};
        String[] values = {AppPreferences.THEME_SYSTEM, AppPreferences.THEME_LIGHT, AppPreferences.THEME_DARK};
        int checked = indexOf(values, AppPreferences.getThemeMode(requireContext()));
        new MaterialAlertDialogBuilder(requireContext()).setTitle("Tema")
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss(); AppPreferences.setThemeMode(requireContext(), values[which]);
                }).setNegativeButton("Cancelar", null).show();
    }

    private int indexOf(String[] values, String value) {
        for (int i=0;i<values.length;i++) if (values[i].equals(value)) return i;
        return 0;
    }
    private String styleName(String s) {
        if (AppPreferences.STYLE_VINYL.equals(s)) return "Vinilo";
        if (AppPreferences.STYLE_CASSETTE.equals(s)) return "Cassette 3D";
        if (AppPreferences.STYLE_WALKMAN.equals(s)) return "Walkman 3D";
        if (AppPreferences.STYLE_RETRO.equals(s)) return "Floppy Disk 3D";
        if (AppPreferences.STYLE_PIXEL.equals(s)) return "Cartucho NES 3D";
        if (AppPreferences.STYLE_GAMEBOY_3D.equals(s)) return "GameBoy 3D";
        if (AppPreferences.STYLE_NEON.equals(s)) return "Neón 2D";
        if (AppPreferences.STYLE_GLASS.equals(s)) return "Glass 2D";
        if (AppPreferences.STYLE_MINIMAL.equals(s)) return "Minimal 2D";
        if (AppPreferences.STYLE_SPOTIFY_2D.equals(s)) return "Spotify 2D";
        if (AppPreferences.STYLE_EQUALIZER_2D.equals(s)) return "Ecualizador 2D";
        if (AppPreferences.STYLE_RING.equals(s)) return "Anillo";
        return "Clásico 2D";
    }
    private String animationName(String a) { return AppPreferences.ANIMATION_OFF.equals(a) ? "Sin animación" : AppPreferences.ANIMATION_LIVELY.equals(a) ? "Viva" : "Suave"; }
    private String shapeName(String s) { return AppPreferences.SHAPE_SQUARE.equals(s) ? "Cuadrada" : AppPreferences.SHAPE_CIRCLE.equals(s) ? "Circular" : "Redondeada"; }
    private String startPageName(String s) { return AppPreferences.START_RADIO.equals(s) ? "Radio" : AppPreferences.START_SETTINGS.equals(s) ? "Ajustes" : "Música"; }
    private String themeName(String s) { return AppPreferences.THEME_LIGHT.equals(s) ? "Claro" : AppPreferences.THEME_DARK.equals(s) ? "Oscuro" : "Sistema"; }
    private String styleDescription(String s) {
        if (AppPreferences.STYLE_VINYL.equals(s)) return "Disco girando con portada en el centro.";
        if (AppPreferences.STYLE_CASSETTE.equals(s)) return "Cassette volumétrico con reels animados.";
        if (AppPreferences.STYLE_WALKMAN.equals(s)) return "Walkman 3D con pantalla integrada.";
        if (AppPreferences.STYLE_RETRO.equals(s)) return "Floppy disk 3D con etiqueta y carátula.";
        if (AppPreferences.STYLE_PIXEL.equals(s)) return "Cartucho NES 3D con label frontal.";
        if (AppPreferences.STYLE_GAMEBOY_3D.equals(s)) return "Consola portátil con volumen y giro.";
        if (AppPreferences.STYLE_NEON.equals(s)) return "Skin 2D con brillo neón y visualizador.";
        if (AppPreferences.STYLE_GLASS.equals(s)) return "Skin 2D translúcida tipo glassmorphism.";
        if (AppPreferences.STYLE_MINIMAL.equals(s)) return "Skin 2D simple, limpia y elegante.";
        if (AppPreferences.STYLE_SPOTIFY_2D.equals(s)) return "Interfaz 2D estilo streaming con carátula dominante.";
        if (AppPreferences.STYLE_EQUALIZER_2D.equals(s)) return "Ecualizador gráfico 2D reactivo al audio, con picos y reflejo.";
        if (AppPreferences.STYLE_RING.equals(s)) return "Portada circular con anillo de espectro reactivo y pulso de graves.";
        return "Skin 2D clásica centrada en la portada.";
    }

    private String accentName(String s) {
        if (AppPreferences.ACCENT_BLUE.equals(s)) return "Azul";
        if (AppPreferences.ACCENT_CYAN.equals(s)) return "Cian";
        if (AppPreferences.ACCENT_TEAL.equals(s)) return "Turquesa";
        if (AppPreferences.ACCENT_GREEN.equals(s)) return "Verde";
        if (AppPreferences.ACCENT_LIME.equals(s)) return "Lima";
        if (AppPreferences.ACCENT_YELLOW.equals(s)) return "Amarillo";
        if (AppPreferences.ACCENT_ORANGE.equals(s)) return "Naranja";
        if (AppPreferences.ACCENT_RED.equals(s)) return "Rojo";
        if (AppPreferences.ACCENT_PINK.equals(s)) return "Rosa";
        return "Violeta";
    }
    private String backgroundName(String s) {
        if (AppPreferences.BACKGROUND_WAVES.equals(s)) return "Ondas";
        if (AppPreferences.BACKGROUND_GRID.equals(s)) return "Grid";
        if (AppPreferences.BACKGROUND_PULSE.equals(s)) return "Pulso";
        if (AppPreferences.BACKGROUND_DARK.equals(s)) return "Oscuro";
        if (AppPreferences.BACKGROUND_NONE.equals(s)) return "Sin fondo";
        return "Aurora";
    }
    private String typographyName(String s) {
        if (AppPreferences.TYPOGRAPHY_ROUNDED.equals(s)) return "Redondeada";
        if (AppPreferences.TYPOGRAPHY_SERIF.equals(s)) return "Serif clásica";
        if (AppPreferences.TYPOGRAPHY_MONO.equals(s)) return "Monoespaciada";
        if (AppPreferences.TYPOGRAPHY_CONDENSED.equals(s)) return "Condensada";
        return "Moderna";
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    @Override public void onResume() { super.onResume(); refreshSummary(); applyListAppearance(); }
    @Override public void onDestroyView() {
        stylePicker.dismiss();
        if (playbackOrderSettings != null) {
            playbackOrderSettings.detach();
            playbackOrderSettings = null;
        }
        if (powerSaverSettings != null) {
            powerSaverSettings.detach();
            powerSaverSettings = null;
        }
        if (librarySourceFoldersController != null) {
            librarySourceFoldersController.destroy();
            librarySourceFoldersController = null;
        }
        if (audioPrefs != null) {
            audioPrefs.unregisterOnSharedPreferenceChangeListener(audioPreferenceListener);
            audioPrefs = null;
        }
        if (voicePickerTts != null) {
            voicePickerTts.stop();
            voicePickerTts.shutdown();
            voicePickerTts = null;
        }
        if (binding != null) {
            try {
                binding.playerPreviewWeb.evaluateJavascript("Player3D.dispose()", null);
                binding.playerPreviewWeb.loadUrl("about:blank");
                binding.playerPreviewWeb.destroy();
            } catch (Exception ignored) { }
            try {
                binding.styleMiniPreviewWeb.evaluateJavascript("Player3D.dispose()", null);
                binding.styleMiniPreviewWeb.loadUrl("about:blank");
                binding.styleMiniPreviewWeb.destroy();
            } catch (Exception ignored) { }
        }
        previewWebReady = false;
        miniPreviewWebReady = false;
        binding = null;
        super.onDestroyView();
    }
}
