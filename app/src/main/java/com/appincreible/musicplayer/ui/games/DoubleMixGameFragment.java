package com.appincreible.musicplayer.ui.games;

import android.content.Context;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.ImageViewCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.appincreible.musicplayer.R;
import com.appincreible.musicplayer.data.model.Song;
import com.appincreible.musicplayer.databinding.FragmentDoubleMixGameBinding;
import com.appincreible.musicplayer.themes.ListAppearance;
import com.appincreible.musicplayer.themes.UiPalette;
import com.appincreible.musicplayer.ui.music.MusicViewModel;
import com.appincreible.musicplayer.ui.player.PlayerViewModel;
import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;

/** Offline game: two local songs play at the same time and the user must identify both. */
public final class DoubleMixGameFragment extends Fragment implements ListAppearance.Listener {
    private FragmentDoubleMixGameBinding binding;
    private MusicViewModel musicViewModel;
    private PlayerViewModel playerViewModel;
    private DoubleMixAudioPlayer audioPlayer;
    private final DoubleMixGameController game = new DoubleMixGameController();
    private final List<GamePlaylistSupport.PlaylistChoice> playlistChoices = new ArrayList<>();
    private final List<Song> selectedPlaylistSongs = new ArrayList<>();
    private final List<SongChoice> answerChoices = new ArrayList<>();
    private GamePlaylistSupport.PlaylistChoice selectedPlaylist;
    private Song firstAnswer;
    private Song secondAnswer;
    private int requestedRounds = 10;
    private float secondVolume = 0.70f;
    private boolean autoPlayWhenReady;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentDoubleMixGameBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        musicViewModel = new ViewModelProvider(requireActivity()).get(MusicViewModel.class);
        playerViewModel = new ViewModelProvider(requireActivity()).get(PlayerViewModel.class);

        binding.backButton.setOnClickListener(v -> Navigation.findNavController(v).navigateUp());
        binding.roundsGroup.check(R.id.rounds10);
        binding.secondVolumeSeek.setProgress(50);
        updateDifficultyLabel();
        binding.secondVolumeSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                secondVolume = (20 + progress) / 100f;
                updateDifficultyLabel();
                if (audioPlayer != null) audioPlayer.setSecondVolume(secondVolume);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });

        binding.startButton.setOnClickListener(v -> startRequestedGame());
        binding.playMixButton.setOnClickListener(v -> playCurrentMix());
        binding.confirmButton.setOnClickListener(v -> submitAnswer());
        binding.nextRoundButton.setOnClickListener(v -> nextRoundOrResults());
        binding.playAgainButton.setOnClickListener(v -> startGameWithSongs(new ArrayList<>(selectedPlaylistSongs)));
        binding.changePlaylistButton.setOnClickListener(v -> showSetup());

        installAnswerField(binding.answerOneField, true);
        installAnswerField(binding.answerTwoField, false);

        musicViewModel.getPlaylists().observe(getViewLifecycleOwner(), ignored -> rebuildPlaylistChoices());
        musicViewModel.getSongs().observe(getViewLifecycleOwner(), ignored -> rebuildPlaylistChoices());
        if (musicViewModel.getAllSongs().getValue() == null || musicViewModel.getAllSongs().getValue().isEmpty()) {
            musicViewModel.loadSongs();
        }

        ListAppearance.addListener(this);
        applyAppearance();
        showSetup();
    }

    private void installAnswerField(com.google.android.material.textfield.MaterialAutoCompleteTextView field,
                                    boolean first) {
        field.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (first) firstAnswer = null;
                else secondAnswer = null;
                updateConfirmEnabled();
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        field.setOnItemClickListener((parent, itemView, position, id) -> {
            Object item = parent.getItemAtPosition(position);
            if (!(item instanceof SongChoice)) return;
            SongChoice choice = (SongChoice) item;
            field.setText(choice.toString(), false);
            if (first) firstAnswer = choice.song;
            else secondAnswer = choice.song;
            updateConfirmEnabled();
            field.dismissDropDown();
            field.clearFocus();
            InputMethodManager imm = (InputMethodManager) requireContext()
                    .getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(field.getWindowToken(), 0);
        });
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
        binding.playlistField.setOnItemClickListener((parent, itemView, position, id) -> {
            Object item = parent.getItemAtPosition(position);
            if (item instanceof GamePlaylistSupport.PlaylistChoice) {
                selectedPlaylist = (GamePlaylistSupport.PlaylistChoice) item;
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
        int actualRounds = game.start(unique, requestedRounds);
        if (actualRounds <= 0) return;
        rebuildAnswerChoices(unique);
        playerViewModel.pause();
        if (!game.beginNextRound()) return;
        showGame();
        prepareCurrentRound(true);
    }

    private void rebuildAnswerChoices(List<Song> songs) {
        answerChoices.clear();
        for (Song song : songs) answerChoices.add(new SongChoice(song));
        SongSuggestionAdapter adapterOne = new SongSuggestionAdapter(requireContext(), answerChoices);
        SongSuggestionAdapter adapterTwo = new SongSuggestionAdapter(requireContext(), answerChoices);
        binding.answerOneField.setAdapter(adapterOne);
        binding.answerTwoField.setAdapter(adapterTwo);
    }

    private void prepareCurrentRound(boolean autoPlay) {
        DoubleMixGameController.Round round = game.getCurrentRound();
        if (round == null || binding == null) return;
        firstAnswer = null;
        secondAnswer = null;
        binding.answerOneField.setText("", false);
        binding.answerTwoField.setText("", false);
        updateConfirmEnabled();
        binding.roundStatus.setText("Ronda " + (game.getRoundIndex() + 1) + "/" + game.getRoundCount());
        releaseAudioPlayer();
        autoPlayWhenReady = autoPlay;
        audioPlayer = new DoubleMixAudioPlayer(requireContext(), new DoubleMixAudioPlayer.Listener() {
            @Override public void onReady() {
                if (binding == null || audioPlayer == null) return;
                binding.playMixButton.setEnabled(true);
                if (autoPlayWhenReady) {
                    autoPlayWhenReady = false;
                    audioPlayer.playFromStart();
                }
            }

            @Override public void onError(String message) {
                if (isAdded()) Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
            }
        });
        binding.playMixButton.setEnabled(false);
        audioPlayer.prepare(round.first, round.firstStartMs, round.second, round.secondStartMs,
                DoubleMixGameController.CLIP_DURATION_MS, secondVolume);
    }

    private void playCurrentMix() {
        if (audioPlayer != null) audioPlayer.playFromStart();
    }

    private void updateConfirmEnabled() {
        if (binding != null) binding.confirmButton.setEnabled(firstAnswer != null && secondAnswer != null);
    }

    private void submitAnswer() {
        if (firstAnswer == null || secondAnswer == null) return;
        if (audioPlayer != null) audioPlayer.pause();
        DoubleMixGameController.Result result = game.submit(firstAnswer, secondAnswer);
        DoubleMixGameController.Round round = game.getCurrentRound();
        if (round == null) return;
        binding.playCard.setVisibility(View.GONE);
        binding.revealCard.setVisibility(View.VISIBLE);
        binding.revealStatus.setText(result.correctCount == 2
                ? "¡Reconociste las dos!"
                : result.correctCount == 1 ? "Reconociste una" : "No era ninguna de esas");
        binding.revealFirst.setText("1 · " + round.first.getTitle() + " — " + round.first.getArtist());
        binding.revealSecond.setText("2 · " + round.second.getTitle() + " — " + round.second.getArtist());
        binding.revealPoints.setText(result.points > 0 ? "+" + result.points + " puntos" : "Sin puntos");
        binding.nextRoundButton.setText(game.isFinished() ? "Ver resultados" : "Siguiente ronda");
    }

    private void nextRoundOrResults() {
        if (game.isFinished()) {
            showResults();
            return;
        }
        if (game.beginNextRound()) {
            binding.revealCard.setVisibility(View.GONE);
            binding.playCard.setVisibility(View.VISIBLE);
            prepareCurrentRound(true);
        }
    }

    private void showResults() {
        releaseAudioPlayer();
        binding.setupCard.setVisibility(View.GONE);
        binding.playCard.setVisibility(View.GONE);
        binding.revealCard.setVisibility(View.GONE);
        binding.resultsCard.setVisibility(View.VISIBLE);
        binding.resultsScore.setText(game.getScore() + " puntos");
        binding.resultsHits.setText(game.getFullHits() + " de " + game.getRoundCount() + " rondas perfectas");
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

    private void updateDifficultyLabel() {
        if (binding == null) return;
        int percent = Math.round(secondVolume * 100f);
        String difficulty = percent <= 40 ? "Difícil" : percent <= 70 ? "Normal" : "Fácil";
        binding.difficultyLabel.setText("Volumen de la segunda canción · " + percent + "% · " + difficulty);
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
        binding.setupTitle.setTextColor(appearance.primaryText);
        binding.roundsLabel.setTextColor(appearance.primaryText);
        binding.difficultyLabel.setTextColor(appearance.primaryText);
        binding.difficultyHint.setTextColor(appearance.secondaryText);
        binding.roundStatus.setTextColor(appearance.primaryText);
        binding.mixHint.setTextColor(appearance.secondaryText);
        binding.revealStatus.setTextColor(appearance.primaryText);
        binding.revealFirst.setTextColor(appearance.primaryText);
        binding.revealSecond.setTextColor(appearance.primaryText);
        binding.revealPoints.setTextColor(appearance.secondaryText);
        binding.resultsTitle.setTextColor(appearance.primaryText);
        binding.resultsScore.setTextColor(appearance.primaryText);
        binding.resultsHits.setTextColor(appearance.secondaryText);
        ImageViewCompat.setImageTintList(binding.backButton, ColorStateList.valueOf(appearance.primaryText));
        ColorStateList accent = ColorStateList.valueOf(appearance.accent);
        int onAccent = UiPalette.readableTextColor(appearance.accent);
        binding.startButton.setBackgroundTintList(accent);
        binding.startButton.setTextColor(onAccent);
        binding.playMixButton.setBackgroundTintList(accent);
        binding.playMixButton.setIconTint(ColorStateList.valueOf(onAccent));
        binding.confirmButton.setBackgroundTintList(accent);
        binding.confirmButton.setTextColor(onAccent);
        binding.nextRoundButton.setBackgroundTintList(accent);
        binding.nextRoundButton.setTextColor(onAccent);
        binding.playAgainButton.setBackgroundTintList(accent);
        binding.playAgainButton.setTextColor(onAccent);
    }

    @Override public void onListAppearanceChanged() { applyAppearance(); }

    @Override public void onPause() {
        if (audioPlayer != null) audioPlayer.pause();
        super.onPause();
    }

    @Override public void onDestroyView() {
        ListAppearance.removeListener(this);
        releaseAudioPlayer();
        binding = null;
        super.onDestroyView();
    }

    private void releaseAudioPlayer() {
        if (audioPlayer == null) return;
        audioPlayer.release();
        audioPlayer = null;
        autoPlayWhenReady = false;
    }


    private static final class SongSuggestionAdapter extends ArrayAdapter<SongChoice> {
        private final LayoutInflater inflater;

        SongSuggestionAdapter(@NonNull Context context, @NonNull List<SongChoice> items) {
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

            SongChoice option = getItem(position);
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

    private static final class SongChoice {
        final Song song;
        SongChoice(Song song) { this.song = song; }
        @NonNull @Override public String toString() { return song.getTitle() + " — " + song.getArtist(); }
    }
}
