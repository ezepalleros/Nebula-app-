package com.appincreible.musicplayer.ui.games;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.GridLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import androidx.core.widget.ImageViewCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.appincreible.musicplayer.R;
import com.appincreible.musicplayer.data.model.Song;
import com.appincreible.musicplayer.databinding.FragmentIntruderGameBinding;
import com.appincreible.musicplayer.themes.AppPreferences;
import com.appincreible.musicplayer.themes.ListAppearance;
import com.appincreible.musicplayer.themes.UiPalette;
import com.appincreible.musicplayer.ui.music.MusicViewModel;
import com.appincreible.musicplayer.ui.player.PlayerViewModel;
import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Offline game where one fragment belongs to a different song. */
public final class IntruderGameFragment extends Fragment implements ListAppearance.Listener {
    private FragmentIntruderGameBinding binding;
    private MusicViewModel musicViewModel;
    private PlayerViewModel playerViewModel;
    private GuessSongAudioPlayer audioPlayer;
    private final IntruderGameController game = new IntruderGameController();
    private final List<GamePlaylistSupport.PlaylistChoice> playlistChoices = new ArrayList<>();
    private final List<Song> selectedPlaylistSongs = new ArrayList<>();
    private final List<MaterialButton> clipButtons = new ArrayList<>();
    private final ExecutorService plannerExecutor = Executors.newSingleThreadExecutor();
    private Future<?> planFuture;

    private GamePlaylistSupport.PlaylistChoice selectedPlaylist;
    private List<GuessSongAudioPlayer.ClipSpec> currentClips = new ArrayList<>();
    private int mainClipCount = 4;
    private long clipDurationMs = 2_000L;
    private int requestedRounds = GameRules.DEFAULT_ROUNDS;
    private int selectedButtonIndex = -1;
    private int playingButtonIndex = -1;
    private int planToken;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentIntruderGameBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        musicViewModel = new ViewModelProvider(requireActivity()).get(MusicViewModel.class);
        playerViewModel = new ViewModelProvider(requireActivity()).get(PlayerViewModel.class);

        binding.backButton.setOnClickListener(v -> Navigation.findNavController(v).navigateUp());
        binding.roundsGroup.check(R.id.rounds10);
        installSetupSelectors();
        binding.startButton.setOnClickListener(v -> startRequestedGame());
        binding.confirmButton.setOnClickListener(v -> submitAnswer());
        binding.nextRoundButton.setOnClickListener(v -> nextRoundOrResults());
        binding.playAgainButton.setOnClickListener(v -> startGameWithSongs(new ArrayList<>(selectedPlaylistSongs)));
        binding.changePlaylistButton.setOnClickListener(v -> showSetup());

        musicViewModel.getPlaylists().observe(getViewLifecycleOwner(), ignored -> rebuildPlaylistChoices());
        musicViewModel.getSongs().observe(getViewLifecycleOwner(), ignored -> rebuildPlaylistChoices());
        if (musicViewModel.getAllSongs().getValue() == null || musicViewModel.getAllSongs().getValue().isEmpty()) {
            musicViewModel.loadSongs();
        }

        ListAppearance.addListener(this);
        applyAppearance();
        showSetup();
    }

    private void installSetupSelectors() {
        List<Integer> counts = new ArrayList<>();
        for (int i = GameRules.MIN_INTRUDER_MAIN_CLIPS; i <= GameRules.MAX_INTRUDER_MAIN_CLIPS; i++) counts.add(i);
        ArrayAdapter<Integer> countAdapter = new ArrayAdapter<Integer>(requireContext(), android.R.layout.simple_list_item_1, counts) {
            @NonNull @Override public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                ((android.widget.TextView) view).setText(labelForCount(getItem(position) == null ? 3 : getItem(position)));
                return view;
            }
            @Override public View getDropDownView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                View view = super.getDropDownView(position, convertView, parent);
                ((android.widget.TextView) view).setText(labelForCount(getItem(position) == null ? 3 : getItem(position)));
                return view;
            }
        };
        binding.clipCountField.setAdapter(countAdapter);
        binding.clipCountField.setText(labelForCount(mainClipCount), false);
        binding.clipCountField.setOnItemClickListener((parent, view, position, id) -> {
            Object value = parent.getItemAtPosition(position);
            if (value instanceof Integer) {
                mainClipCount = (Integer) value;
                binding.clipCountField.setText(labelForCount(mainClipCount), false);
                binding.clipCountHint.setText(mainClipCount + " pistas principales + 1 intruso = "
                        + (mainClipCount + 1) + " botones.");
            }
        });

        List<DurationChoice> durations = new ArrayList<>();
        for (int i = 0; i < GameRules.CLIP_DURATIONS_MS.length; i++) {
            durations.add(new DurationChoice(i, GameRules.CLIP_DURATIONS_MS[i]));
        }
        ArrayAdapter<DurationChoice> durationAdapter = new ArrayAdapter<>(
                requireContext(), android.R.layout.simple_list_item_1, durations);
        binding.durationField.setAdapter(durationAdapter);
        DurationChoice defaultDuration = durations.get(Math.min(2, durations.size() - 1));
        clipDurationMs = defaultDuration.durationMs;
        binding.durationField.setText(defaultDuration.toString(), false);
        binding.durationField.setOnItemClickListener((parent, view, position, id) -> {
            Object value = parent.getItemAtPosition(position);
            if (value instanceof DurationChoice) {
                clipDurationMs = ((DurationChoice) value).durationMs;
            }
        });
        binding.clipCountHint.setText(mainClipCount + " pistas principales + 1 intruso = "
                + (mainClipCount + 1) + " botones.");
    }

    private void rebuildPlaylistChoices() {
        if (binding == null) return;
        String previousKey = selectedPlaylist == null ? GamePlaylistSupport.ALL_KEY : selectedPlaylist.key;
        playlistChoices.clear();
        playlistChoices.addAll(GamePlaylistSupport.buildChoices(musicViewModel));
        if (playlistChoices.isEmpty()) return;
        ArrayAdapter<GamePlaylistSupport.PlaylistChoice> adapter = new ArrayAdapter<>(
                requireContext(), android.R.layout.simple_list_item_1, playlistChoices);
        binding.playlistField.setAdapter(adapter);
        GamePlaylistSupport.PlaylistChoice chosen = playlistChoices.get(0);
        for (GamePlaylistSupport.PlaylistChoice item : playlistChoices) {
            if (item.key.equals(previousKey)) chosen = item;
        }
        selectedPlaylist = chosen;
        binding.playlistField.setText(chosen.toString(), false);
        binding.playlistField.setOnItemClickListener((parent, view, position, id) -> {
            Object value = parent.getItemAtPosition(position);
            if (value instanceof GamePlaylistSupport.PlaylistChoice) {
                selectedPlaylist = (GamePlaylistSupport.PlaylistChoice) value;
            }
        });
    }

    private void startRequestedGame() {
        if (selectedPlaylist == null) return;
        requestedRounds = GamePlaylistSupport.roundsFromCheckedId(binding.roundsGroup.getCheckedButtonId());
        binding.startButton.setEnabled(false);
        GamePlaylistSupport.loadSongs(musicViewModel, selectedPlaylist, songs -> {
            if (binding == null) return;
            binding.startButton.setEnabled(true);
            startGameWithSongs(songs);
        });
    }

    private void startGameWithSongs(List<Song> source) {
        List<Song> unique = GamePlaylistSupport.uniqueSongs(source);
        if (unique.size() < 2) {
            Toast.makeText(requireContext(), "Esta playlist necesita al menos 2 canciones.", Toast.LENGTH_LONG).show();
            return;
        }
        selectedPlaylistSongs.clear();
        selectedPlaylistSongs.addAll(unique);
        int actual = game.start(unique, requestedRounds, mainClipCount, clipDurationMs);
        if (actual <= 0) {
            Toast.makeText(requireContext(),
                    "No hay suficientes canciones con duración válida para esta configuración.", Toast.LENGTH_LONG).show();
            return;
        }
        if (actual < requestedRounds) {
            Toast.makeText(requireContext(), "Sin repetir canciones, esta playlist alcanza para "
                    + actual + " rondas.", Toast.LENGTH_LONG).show();
        }
        playerViewModel.pause();
        if (!game.beginNextRound(mainClipCount + 1)) return;
        showGame();
        prepareCurrentRound();
    }

    private void prepareCurrentRound() {
        if (binding == null || game.getCurrentRound() == null) return;
        releaseAudioPlayer();
        currentClips = new ArrayList<>();
        selectedButtonIndex = -1;
        playingButtonIndex = -1;
        binding.confirmButton.setEnabled(false);
        binding.preparingProgress.setVisibility(View.VISIBLE);
        binding.roundHint.setText("Preparando fragmentos…");
        binding.listenCountText.setText("Escuchas: 0");
        buildClipButtons(mainClipCount + 1, false);
        binding.roundStatus.setText("Ronda " + (game.getRoundIndex() + 1) + "/" + game.getRoundCount()
                + " · " + formatDuration(clipDurationMs));

        int token = ++planToken;
        IntruderGameController.RoundSongs roundSongs = game.getCurrentRound();
        int intruderIndex = game.getIntruderButtonIndex();
        android.content.Context appContext = requireContext().getApplicationContext();
        if (planFuture != null) planFuture.cancel(true);
        planFuture = plannerExecutor.submit(() -> {
            List<GuessSongAudioPlayer.ClipSpec> planned = IntruderRoundPlanner.plan(
                    appContext, roundSongs, mainClipCount, clipDurationMs, intruderIndex);
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                if (binding == null || token != planToken) return;
                if (planned.isEmpty()) {
                    binding.preparingProgress.setVisibility(View.GONE);
                    binding.roundHint.setText("No se pudieron preparar fragmentos válidos.");
                    Toast.makeText(requireContext(), "No se pudo preparar esta ronda.", Toast.LENGTH_LONG).show();
                    return;
                }
                currentClips = planned;
                if (isResumed()) {
                    ensureAudioPlayer();
                    if (audioPlayer != null) audioPlayer.prepareClips(currentClips);
                }
            });
        });
    }

    private void ensureAudioPlayer() {
        if (audioPlayer != null) return;
        audioPlayer = new GuessSongAudioPlayer(requireContext(), new GuessSongAudioPlayer.Listener() {
            @Override public void onReady() {
                if (binding == null) return;
                binding.preparingProgress.setVisibility(View.GONE);
                binding.roundHint.setText("Escuchá los botones y marcá el fragmento intruso.");
                setClipButtonsEnabled(true);
            }

            @Override public void onError(String message) {
                if (isAdded()) Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            }

            @Override public void onClipPlaybackChanged(int clipIndex, boolean playing) {
                if (binding == null) return;
                playingButtonIndex = playing ? clipIndex : -1;
                renderClipButtonStates();
            }
        });
    }

    private void buildClipButtons(int count, boolean enabled) {
        if (binding == null) return;
        binding.clipButtonsGrid.removeAllViews();
        clipButtons.clear();
        for (int i = 0; i < count; i++) {
            final int index = i;
            MaterialButton button = new MaterialButton(requireContext());
            button.setText(String.valueOf(i + 1));
            button.setTextSize(20f);
            button.setMinHeight(dp(64));
            button.setMinWidth(0);
            button.setEnabled(enabled);
            button.setOnClickListener(v -> {
                if (audioPlayer == null || currentClips.isEmpty()) return;
                selectedButtonIndex = index;
                binding.confirmButton.setEnabled(true);
                game.registerListen();
                binding.listenCountText.setText("Escuchas: " + game.getListenCount());
                audioPlayer.playClip(index);
                renderClipButtonStates();
            });
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = 0;
            lp.height = dp(64);
            lp.rowSpec = GridLayout.spec(i / 3);
            lp.columnSpec = GridLayout.spec(i % 3, 1f);
            lp.setMargins(dp(4), dp(4), dp(4), dp(4));
            button.setLayoutParams(lp);
            clipButtons.add(button);
            binding.clipButtonsGrid.addView(button);
        }
        renderClipButtonStates();
    }

    private void setClipButtonsEnabled(boolean enabled) {
        for (MaterialButton button : clipButtons) button.setEnabled(enabled);
    }

    private void renderClipButtonStates() {
        if (binding == null) return;
        ListAppearance.Appearance appearance = ListAppearance.resolve(requireContext());
        int onAccent = UiPalette.readableTextColor(appearance.accent);
        for (int i = 0; i < clipButtons.size(); i++) {
            MaterialButton button = clipButtons.get(i);
            boolean playing = i == playingButtonIndex;
            boolean selected = i == selectedButtonIndex;
            if (playing) {
                button.setBackgroundTintList(ColorStateList.valueOf(appearance.accent));
                button.setTextColor(onAccent);
                button.setStrokeWidth(dp(2));
                button.setStrokeColor(ColorStateList.valueOf(appearance.accent));
            } else if (selected) {
                button.setBackgroundTintList(ColorStateList.valueOf(
                        ColorUtils.setAlphaComponent(appearance.accent, 42)));
                button.setTextColor(appearance.primaryText);
                button.setStrokeWidth(dp(2));
                button.setStrokeColor(ColorStateList.valueOf(appearance.accent));
            } else {
                button.setBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
                button.setTextColor(appearance.primaryText);
                button.setStrokeWidth(dp(1));
                button.setStrokeColor(ColorStateList.valueOf(
                        ColorUtils.setAlphaComponent(appearance.secondaryText, 150)));
            }
        }
    }

    private void submitAnswer() {
        if (selectedButtonIndex < 0 || game.isRevealed()) return;
        if (audioPlayer != null) audioPlayer.pause();
        IntruderGameController.RoundResult result = game.submit(selectedButtonIndex);
        showReveal(result);
    }

    private void showReveal(IntruderGameController.RoundResult result) {
        IntruderGameController.RoundSongs round = game.getCurrentRound();
        if (binding == null || round == null) return;
        binding.playCard.setVisibility(View.GONE);
        binding.revealCard.setVisibility(View.VISIBLE);
        binding.revealStatus.setText(result.correct ? "¡Correcto!" : "Ese no era el intruso");
        binding.revealPoints.setText(result.correct ? "+" + result.pointsAwarded + " puntos" : "0 puntos");
        bindSong(round.mainSong, binding.mainArtwork, binding.mainTitle, binding.mainArtist);
        bindSong(round.intruderSong, binding.intruderArtwork, binding.intruderTitle, binding.intruderArtist);
        binding.intruderLabel.setText("El intruso · botón " + (game.getIntruderButtonIndex() + 1));
        binding.nextRoundButton.setText(game.isFinished() ? "Ver resultados" : "Siguiente ronda");
    }

    private void bindSong(Song song, android.widget.ImageView artwork,
                          android.widget.TextView title, android.widget.TextView artist) {
        title.setText(song.getTitle());
        artist.setText(song.getArtist());
        Glide.with(artwork)
                .load(song.getArtworkUri().isEmpty() ? null : Uri.parse(song.getArtworkUri()))
                .placeholder(R.drawable.ic_music)
                .error(R.drawable.ic_music)
                .centerCrop()
                .into(artwork);
    }

    private void nextRoundOrResults() {
        if (game.isFinished()) {
            showResults();
            return;
        }
        if (game.beginNextRound(mainClipCount + 1)) {
            binding.revealCard.setVisibility(View.GONE);
            binding.playCard.setVisibility(View.VISIBLE);
            prepareCurrentRound();
        }
    }

    private void showResults() {
        releaseAudioPlayer();
        binding.setupCard.setVisibility(View.GONE);
        binding.playCard.setVisibility(View.GONE);
        binding.revealCard.setVisibility(View.GONE);
        binding.resultsCard.setVisibility(View.VISIBLE);
        String playlistKey = selectedPlaylist == null ? GamePlaylistSupport.ALL_KEY : selectedPlaylist.key;
        AppPreferences.setIntruderBestScore(requireContext(), playlistKey, game.getScore());
        int best = AppPreferences.getIntruderBestScore(requireContext(), playlistKey);
        String average = String.format(Locale.getDefault(),
                "Escuchas promedio por ronda: %.1f", game.getAverageListens());
        GameResultsSupport.bind(binding.resultsScore, binding.resultsHits, binding.resultsAverage,
                binding.resultsBest, game.getScore(), game.getHits(), game.getRoundCount(), average, best);
    }

    private void showSetup() {
        releaseAudioPlayer();
        planToken++;
        if (binding == null) return;
        binding.setupCard.setVisibility(View.VISIBLE);
        binding.playCard.setVisibility(View.GONE);
        binding.revealCard.setVisibility(View.GONE);
        binding.resultsCard.setVisibility(View.GONE);
    }

    private void showGame() {
        binding.setupCard.setVisibility(View.GONE);
        binding.playCard.setVisibility(View.VISIBLE);
        binding.revealCard.setVisibility(View.GONE);
        binding.resultsCard.setVisibility(View.GONE);
    }

    private void applyAppearance() {
        if (binding == null) return;
        ListAppearance.Appearance appearance = ListAppearance.resolve(requireContext());
        binding.setupCard.setCardBackgroundColor(appearance.containerColor);
        binding.playCard.setCardBackgroundColor(appearance.containerColor);
        binding.revealCard.setCardBackgroundColor(appearance.containerColor);
        binding.resultsCard.setCardBackgroundColor(appearance.containerColor);

        binding.title.setTextColor(appearance.primaryText);
        binding.subtitle.setTextColor(appearance.secondaryText);
        ImageViewCompat.setImageTintList(binding.backButton, ColorStateList.valueOf(appearance.primaryText));
        binding.setupTitle.setTextColor(appearance.primaryText);
        binding.clipCountHint.setTextColor(appearance.secondaryText);
        binding.roundsLabel.setTextColor(appearance.primaryText);
        binding.roundStatus.setTextColor(appearance.primaryText);
        binding.roundHint.setTextColor(appearance.secondaryText);
        binding.listenCountText.setTextColor(appearance.secondaryText);
        binding.revealStatus.setTextColor(appearance.primaryText);
        binding.mainLabel.setTextColor(appearance.accent);
        binding.intruderLabel.setTextColor(appearance.accent);
        binding.mainTitle.setTextColor(appearance.primaryText);
        binding.intruderTitle.setTextColor(appearance.primaryText);
        binding.mainArtist.setTextColor(appearance.secondaryText);
        binding.intruderArtist.setTextColor(appearance.secondaryText);
        binding.revealPoints.setTextColor(appearance.primaryText);
        binding.revealDivider.setBackgroundColor(ColorUtils.setAlphaComponent(appearance.secondaryText, 70));
        binding.resultsTitle.setTextColor(appearance.primaryText);
        binding.resultsScore.setTextColor(appearance.primaryText);
        binding.resultsHits.setTextColor(appearance.secondaryText);
        binding.resultsAverage.setTextColor(appearance.secondaryText);
        binding.resultsBest.setTextColor(appearance.secondaryText);

        ColorStateList accent = ColorStateList.valueOf(appearance.accent);
        int onAccent = UiPalette.readableTextColor(appearance.accent);
        binding.startButton.setBackgroundTintList(accent);
        binding.startButton.setTextColor(onAccent);
        binding.confirmButton.setBackgroundTintList(accent);
        binding.confirmButton.setTextColor(onAccent);
        binding.nextRoundButton.setBackgroundTintList(accent);
        binding.nextRoundButton.setTextColor(onAccent);
        binding.playAgainButton.setBackgroundTintList(accent);
        binding.playAgainButton.setTextColor(onAccent);
        binding.preparingProgress.setIndeterminateTintList(accent);
        renderClipButtonStates();
    }

    @Override public void onListAppearanceChanged() { applyAppearance(); }

    @Override public void onResume() {
        super.onResume();
        if (game.getCurrentRound() != null && !game.isRevealed()) {
            if (currentClips.isEmpty()) {
                prepareCurrentRound();
            } else {
                ensureAudioPlayer();
                audioPlayer.prepareClips(currentClips);
            }
        }
    }

    @Override public void onPause() {
        if (planFuture != null) planFuture.cancel(true);
        releaseAudioPlayer();
        super.onPause();
    }

    private void releaseAudioPlayer() {
        playingButtonIndex = -1;
        if (audioPlayer != null) {
            audioPlayer.release();
            audioPlayer = null;
        }
        renderClipButtonStates();
    }

    @Override public void onDestroyView() {
        ListAppearance.removeListener(this);
        planToken++;
        if (planFuture != null) planFuture.cancel(true);
        releaseAudioPlayer();
        binding = null;
        super.onDestroyView();
    }

    @Override public void onDestroy() {
        plannerExecutor.shutdownNow();
        super.onDestroy();
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private static String labelForCount(int count) {
        return count + " principales + 1 intruso";
    }

    private static String formatDuration(long millis) {
        if (millis < 1000L) return String.format(Locale.getDefault(), "%.1f s", millis / 1000f);
        return (millis / 1000L) + " s";
    }

    private static final class DurationChoice {
        final int index;
        final long durationMs;

        DurationChoice(int index, long durationMs) {
            this.index = index;
            this.durationMs = durationMs;
        }

        @NonNull @Override public String toString() { return formatDuration(durationMs); }
    }
}
