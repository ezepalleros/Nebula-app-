package com.appincreible.musicplayer.ui.games;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.inputmethod.InputMethodManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.core.widget.ImageViewCompat;

import com.appincreible.musicplayer.R;
import com.appincreible.musicplayer.data.model.Song;
import com.appincreible.musicplayer.databinding.FragmentGuessSongBinding;
import com.appincreible.musicplayer.themes.AppPreferences;
import com.appincreible.musicplayer.themes.ListAppearance;
import com.appincreible.musicplayer.themes.UiPalette;
import com.appincreible.musicplayer.ui.music.MusicViewModel;
import com.appincreible.musicplayer.ui.player.PlayerViewModel;
import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class GuessSongFragment extends Fragment implements ListAppearance.Listener {
    private FragmentGuessSongBinding binding;
    private MusicViewModel musicViewModel;
    private PlayerViewModel playerViewModel;
    private GuessSongAudioPlayer audioPlayer;
    private final GuessSongGameController game = new GuessSongGameController();
    private final List<GamePlaylistSupport.PlaylistChoice> playlistChoices = new ArrayList<>();
    private final List<Song> selectedPlaylistSongs = new ArrayList<>();
    private GamePlaylistSupport.PlaylistChoice selectedPlaylist;
    private Song selectedAnswer;
    private boolean autoPlayWhenReady;
    private int requestedRounds = 10;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentGuessSongBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        musicViewModel = new ViewModelProvider(requireActivity()).get(MusicViewModel.class);
        playerViewModel = new ViewModelProvider(requireActivity()).get(PlayerViewModel.class);

        binding.backButton.setOnClickListener(v -> Navigation.findNavController(v).navigateUp());
        binding.roundsGroup.check(R.id.rounds10);
        binding.startButton.setOnClickListener(v -> startRequestedGame());
        binding.playFragmentButton.setOnClickListener(v -> {
            ensureAudioPlayer(false);
            if (audioPlayer != null) audioPlayer.playCurrentFragment();
        });
        binding.skipButton.setOnClickListener(v -> handleOutcome(game.skip()));
        binding.sendButton.setOnClickListener(v -> {
            if (selectedAnswer != null) handleOutcome(game.submit(selectedAnswer));
        });
        binding.nextRoundButton.setOnClickListener(v -> nextRoundOrResults());
        binding.playAgainButton.setOnClickListener(v -> startGameWithSongs(new ArrayList<>(selectedPlaylistSongs)));
        binding.changePlaylistButton.setOnClickListener(v -> showSetup());

        binding.answerField.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                selectedAnswer = null;
                if (binding != null) binding.sendButton.setEnabled(false);
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        binding.answerField.setOnItemClickListener((parent, itemView, position, id) -> {
            Object value = parent.getItemAtPosition(position);
            if (value instanceof SongOption) {
                SongOption option = (SongOption) value;
                binding.answerField.setText(option.toString(), false);
                selectedAnswer = option.song;
                binding.sendButton.setEnabled(true);
                binding.answerField.dismissDropDown();
                binding.answerField.clearFocus();
                InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.hideSoftInputFromWindow(binding.answerField.getWindowToken(), 0);
            }
        });

        musicViewModel.getPlaylists().observe(getViewLifecycleOwner(), ignored -> rebuildPlaylistChoices());
        musicViewModel.getSongs().observe(getViewLifecycleOwner(), ignored -> rebuildPlaylistChoices());
        if (musicViewModel.getAllSongs().getValue() == null || musicViewModel.getAllSongs().getValue().isEmpty()) {
            musicViewModel.loadSongs();
        }

        ListAppearance.addListener(this);
        applyAppearance();
        showSetup();
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
        if (source == null || source.isEmpty()) {
            Toast.makeText(requireContext(), "Esta playlist no tiene canciones disponibles.", Toast.LENGTH_SHORT).show();
            return;
        }
        selectedPlaylistSongs.clear();
        selectedPlaylistSongs.addAll(source);
        int actual = game.start(source, requestedRounds);
        if (actual < requestedRounds) {
            Toast.makeText(requireContext(), "La playlist tiene " + actual + " canciones; la partida será de " + actual + " rondas.", Toast.LENGTH_LONG).show();
        }
        playerViewModel.pause();
        if (!game.beginNextRound()) return;
        showGame();
        prepareRound(true);
    }

    private void prepareRound(boolean autoPlay) {
        Song song = game.getCurrentSong();
        if (song == null) return;
        selectedAnswer = null;
        binding.answerField.setText("");
        binding.sendButton.setEnabled(false);
        installAnswerSuggestions(selectedPlaylistSongs);
        renderRoundHeader();
        ensureAudioPlayer(false);
        if (audioPlayer != null) {
            autoPlayWhenReady = autoPlay;
            binding.playFragmentButton.setEnabled(false);
            audioPlayer.prepareRound(song, game.getClipStartMs(), GuessSongGameController.LEVEL_DURATIONS_MS);
        }
    }

    private void installAnswerSuggestions(List<Song> songs) {
        List<SongOption> options = new ArrayList<>();
        for (Song song : songs) options.add(new SongOption(song));
        binding.answerField.setAdapter(new SongSuggestionAdapter(requireContext(), options));
    }

    private void ensureAudioPlayer(boolean prepareExistingRound) {
        if (audioPlayer != null) return;
        audioPlayer = new GuessSongAudioPlayer(requireContext(), new GuessSongAudioPlayer.Listener() {
            @Override public void onReady() {
                if (binding == null) return;
                binding.playFragmentButton.setEnabled(true);
                if (autoPlayWhenReady) {
                    autoPlayWhenReady = false;
                    audioPlayer.setActiveLevel(game.getLevelIndex());
                    audioPlayer.playCurrentFragment();
                }
            }

            @Override public void onError(String message) {
                if (isAdded()) Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
        if (prepareExistingRound && game.getCurrentSong() != null && !game.isRevealed()) {
            autoPlayWhenReady = false;
            audioPlayer.prepareRound(game.getCurrentSong(), game.getClipStartMs(), GuessSongGameController.LEVEL_DURATIONS_MS);
            audioPlayer.setActiveLevel(game.getLevelIndex());
        }
    }

    private void handleOutcome(GuessSongGameController.RoundResult result) {
        if (result.revealed) {
            showReveal(result.correct, result.pointsAwarded);
            return;
        }
        selectedAnswer = null;
        binding.answerField.setText("");
        binding.sendButton.setEnabled(false);
        renderRoundHeader();
        ensureAudioPlayer(false);
        if (audioPlayer != null) {
            audioPlayer.setActiveLevel(game.getLevelIndex());
            audioPlayer.playCurrentFragment();
        }
        Toast.makeText(requireContext(), "Ahora escuchás " + formatDuration(game.getCurrentClipDurationMs()), Toast.LENGTH_SHORT).show();
    }

    private void renderRoundHeader() {
        if (binding == null) return;
        binding.roundStatus.setText("Ronda " + (game.getRoundIndex() + 1) + "/" + game.getRoundCount()
                + " · " + formatDuration(game.getCurrentClipDurationMs()));
        renderLevelStrip();
    }

    private void renderLevelStrip() {
        if (binding == null) return;
        binding.levelStrip.removeAllViews();
        ListAppearance.Appearance appearance = ListAppearance.resolve(requireContext());
        for (int i = 0; i < GuessSongGameController.LEVEL_DURATIONS_MS.length; i++) {
            View step = new View(requireContext());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(7), 1f);
            if (i > 0) lp.setMarginStart(dp(3));
            step.setLayoutParams(lp);
            GradientDrawable shape = new GradientDrawable();
            shape.setCornerRadius(dp(4));
            int alpha = i <= game.getLevelIndex() ? 255 : 66;
            shape.setColor(ColorUtils.setAlphaComponent(appearance.accent, alpha));
            step.setBackground(shape);
            binding.levelStrip.addView(step);
        }
    }

    private void showReveal(boolean correct, int points) {
        Song song = game.getCurrentSong();
        if (song == null) return;
        if (audioPlayer != null) {
            int revealLevel = GuessSongGameController.LEVEL_DURATIONS_MS.length - 1;
            audioPlayer.setActiveLevel(revealLevel);
            audioPlayer.playCurrentFragment();
        }
        binding.playCard.setVisibility(View.GONE);
        binding.revealCard.setVisibility(View.VISIBLE);
        binding.revealStatus.setText(correct ? "¡Correcto!" : "Era esta canción");
        binding.revealTitle.setText(song.getTitle());
        binding.revealArtist.setText(song.getArtist());
        binding.revealPoints.setText(correct ? "+" + points + " puntos" : "Sin puntos en esta ronda");
        Glide.with(binding.revealArtwork)
                .load(song.getArtworkUri().isEmpty() ? null : Uri.parse(song.getArtworkUri()))
                .placeholder(R.drawable.ic_music)
                .error(R.drawable.ic_music)
                .centerCrop()
                .into(binding.revealArtwork);
        binding.nextRoundButton.setText(game.isFinished() ? "Ver resultados" : "Siguiente ronda");
    }

    private void nextRoundOrResults() {
        if (audioPlayer != null) audioPlayer.pause();
        if (game.isFinished()) {
            showResults();
            return;
        }
        if (game.beginNextRound()) {
            binding.revealCard.setVisibility(View.GONE);
            binding.playCard.setVisibility(View.VISIBLE);
            prepareRound(true);
        }
    }

    private void showResults() {
        releaseAudioPlayer();
        binding.setupCard.setVisibility(View.GONE);
        binding.playCard.setVisibility(View.GONE);
        binding.revealCard.setVisibility(View.GONE);
        binding.resultsCard.setVisibility(View.VISIBLE);
        String playlistKey = selectedPlaylist == null ? GamePlaylistSupport.ALL_KEY : selectedPlaylist.key;
        AppPreferences.setGuessSongBestScore(requireContext(), playlistKey, game.getScore());
        int best = AppPreferences.getGuessSongBestScore(requireContext(), playlistKey);
        String average = game.getHits() == 0
                ? "Nivel promedio de acierto: —"
                : String.format(Locale.getDefault(), "Nivel promedio de acierto: %.1f", game.getAverageHitLevel());
        GameResultsSupport.bind(binding.resultsScore, binding.resultsHits, binding.resultsAverage,
                binding.resultsBest, game.getScore(), game.getHits(), game.getRoundCount(), average, best);
    }

    private void showSetup() {
        releaseAudioPlayer();
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
        binding.roundsLabel.setTextColor(appearance.primaryText);
        binding.roundStatus.setTextColor(appearance.primaryText);
        binding.listenHint.setTextColor(appearance.secondaryText);
        binding.revealStatus.setTextColor(appearance.primaryText);
        binding.revealTitle.setTextColor(appearance.primaryText);
        binding.revealArtist.setTextColor(appearance.secondaryText);
        binding.revealPoints.setTextColor(appearance.secondaryText);
        binding.resultsTitle.setTextColor(appearance.primaryText);
        binding.resultsScore.setTextColor(appearance.primaryText);
        binding.resultsHits.setTextColor(appearance.secondaryText);
        binding.resultsAverage.setTextColor(appearance.secondaryText);
        binding.resultsBest.setTextColor(appearance.secondaryText);

        ColorStateList accent = ColorStateList.valueOf(appearance.accent);
        int onAccent = UiPalette.readableTextColor(appearance.accent);
        binding.startButton.setBackgroundTintList(accent);
        binding.startButton.setTextColor(onAccent);
        binding.playFragmentButton.setBackgroundTintList(accent);
        binding.playFragmentButton.setIconTint(ColorStateList.valueOf(onAccent));
        binding.sendButton.setBackgroundTintList(accent);
        binding.sendButton.setTextColor(onAccent);
        binding.nextRoundButton.setBackgroundTintList(accent);
        binding.nextRoundButton.setTextColor(onAccent);
        binding.playAgainButton.setBackgroundTintList(accent);
        binding.playAgainButton.setTextColor(onAccent);
        renderLevelStrip();
    }

    @Override public void onListAppearanceChanged() { applyAppearance(); }

    @Override public void onResume() {
        super.onResume();
        if (game.getCurrentSong() != null && !game.isRevealed()) ensureAudioPlayer(true);
    }

    @Override public void onPause() {
        releaseAudioPlayer();
        super.onPause();
    }

    private void releaseAudioPlayer() {
        autoPlayWhenReady = false;
        if (audioPlayer != null) {
            audioPlayer.release();
            audioPlayer = null;
        }
    }

    @Override public void onDestroyView() {
        ListAppearance.removeListener(this);
        releaseAudioPlayer();
        binding = null;
        super.onDestroyView();
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private static String formatDuration(long millis) {
        if (millis < 1000L) return String.format(Locale.getDefault(), "%.1f s", millis / 1000f);
        return (millis / 1000L) + " s";
    }

    private static final class SongSuggestionAdapter extends ArrayAdapter<SongOption> {
        private final LayoutInflater inflater;

        SongSuggestionAdapter(@NonNull Context context, @NonNull List<SongOption> items) {
            super(context, R.layout.item_guess_song_suggestion, items);
            inflater = LayoutInflater.from(context);
        }

        @NonNull @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            return bindRow(position, convertView, parent);
        }

        @Override
        public View getDropDownView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            return bindRow(position, convertView, parent);
        }

        private View bindRow(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            View row = convertView;
            RowHolder holder;
            if (row == null) {
                row = inflater.inflate(R.layout.item_guess_song_suggestion, parent, false);
                holder = new RowHolder(row);
                row.setTag(holder);
            } else {
                holder = (RowHolder) row.getTag();
            }

            SongOption option = getItem(position);
            if (option == null) return row;
            Song song = option.song;
            holder.title.setText(song.getTitle());
            holder.artist.setText(song.getArtist());

            ListAppearance.Appearance appearance = ListAppearance.resolve(getContext());
            holder.title.setTextColor(appearance.primaryText);
            holder.artist.setTextColor(appearance.secondaryText);
            row.setBackgroundColor(appearance.containerColor);

            Glide.with(holder.artwork)
                    .load(song.getArtworkUri().isEmpty() ? null : Uri.parse(song.getArtworkUri()))
                    .placeholder(R.drawable.ic_music)
                    .error(R.drawable.ic_music)
                    .centerCrop()
                    .into(holder.artwork);
            return row;
        }

        private static final class RowHolder {
            final ImageView artwork;
            final TextView title;
            final TextView artist;

            RowHolder(View row) {
                artwork = row.findViewById(R.id.suggestionArtwork);
                title = row.findViewById(R.id.suggestionTitle);
                artist = row.findViewById(R.id.suggestionArtist);
            }
        }
    }

    private static final class SongOption {
        final Song song;
        SongOption(Song song) { this.song = song; }
        @NonNull @Override public String toString() { return song.getTitle() + " — " + song.getArtist(); }
    }
}
