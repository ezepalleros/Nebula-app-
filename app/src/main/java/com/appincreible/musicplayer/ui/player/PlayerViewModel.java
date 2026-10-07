package com.appincreible.musicplayer.ui.player;

import android.app.Application;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;

import com.appincreible.musicplayer.data.model.Song;
import com.appincreible.musicplayer.player.controller.PlayerController;
import com.appincreible.musicplayer.radio.model.RadioStation;
import com.appincreible.musicplayer.radio.playback.RadioPlaybackInfo;

import java.util.ArrayList;
import java.util.List;

public class PlayerViewModel extends AndroidViewModel implements PlayerController.Callback {

    private final PlayerController playerController;
    private final MutableLiveData<String> title = new MutableLiveData<>("Elegí algo para escuchar");
    private final MutableLiveData<String> artist = new MutableLiveData<>("Tu música, a tu manera.");
    private final MutableLiveData<String> album = new MutableLiveData<>("");
    private final MutableLiveData<String> artworkUri = new MutableLiveData<>("");
    private final MutableLiveData<String> mediaKey = new MutableLiveData<>("");
    private final MutableLiveData<Boolean> playing = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> hasMedia = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> controllerReady = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> shuffleEnabled = new MutableLiveData<>(false);
    private final MutableLiveData<Integer> repeatMode = new MutableLiveData<>(Player.REPEAT_MODE_OFF);
    private final MutableLiveData<Boolean> radioMedia = new MutableLiveData<>(false);
    private final MutableLiveData<Integer> radioConnectionState = new MutableLiveData<>(PlayerController.RADIO_CONNECTION_IDLE);
    private final MutableLiveData<String> radioConnectionMediaId = new MutableLiveData<>("");
    private final MutableLiveData<String> radioConnectionError = new MutableLiveData<>("");
    private final MutableLiveData<Long> position = new MutableLiveData<>(0L);
    private final MutableLiveData<Long> duration = new MutableLiveData<>(0L);
    private final MutableLiveData<List<PlayerController.QueueEntry>> queue = new MutableLiveData<>(new ArrayList<>());
    private final Handler ticker = new Handler(Looper.getMainLooper());
    private boolean uiUpdatesRunning;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!uiUpdatesRunning) return;
            position.setValue(playerController.getCurrentPosition());
            duration.setValue(playerController.getDuration());
            ticker.postDelayed(this, 1_000L);
        }
    };

    public PlayerViewModel(@NonNull Application application) {
        super(application);
        playerController = new PlayerController(application, this);
    }

    public LiveData<String> getTitle() { return title; }
    public LiveData<String> getArtist() { return artist; }
    public LiveData<String> getAlbum() { return album; }
    public LiveData<String> getArtworkUri() { return artworkUri; }
    public LiveData<String> getMediaKey() { return mediaKey; }
    public LiveData<Boolean> getPlaying() { return playing; }
    public LiveData<Boolean> getHasMedia() { return hasMedia; }
    public LiveData<Boolean> getControllerReady() { return controllerReady; }
    public LiveData<Boolean> getShuffleEnabled() { return shuffleEnabled; }
    public LiveData<Integer> getRepeatMode() { return repeatMode; }
    public LiveData<Boolean> getRadioMedia() { return radioMedia; }
    public LiveData<Integer> getRadioConnectionState() { return radioConnectionState; }
    public LiveData<String> getRadioConnectionMediaId() { return radioConnectionMediaId; }
    public LiveData<String> getRadioConnectionError() { return radioConnectionError; }
    public LiveData<Long> getPosition() { return position; }
    public LiveData<Long> getDuration() { return duration; }
    public LiveData<List<PlayerController.QueueEntry>> getQueue() { return queue; }

    /** Starts the UI-only position ticker while Reproduciendo is actually visible. */
    public void startUiUpdates() {
        if (uiUpdatesRunning) return;
        uiUpdatesRunning = true;
        ticker.removeCallbacks(tick);
        ticker.post(tick);
    }

    /** Stops all progress polling when the player screen is not visible. */
    public void stopUiUpdates() {
        uiUpdatesRunning = false;
        ticker.removeCallbacks(tick);
    }

    public void playQueue(List<Song> songs, int startIndex) {
        playerController.playQueue(toSongMediaItems(songs), startIndex);
    }

    /** Replaces only a local-song queue while preserving the current song and position. */
    public void syncSongQueue(List<Song> songs) {
        playerController.syncSongQueue(toSongMediaItems(songs));
    }

    private List<MediaItem> toSongMediaItems(List<Song> songs) {
        List<MediaItem> items = new ArrayList<>();
        if (songs == null) return items;
        for (Song song : songs) {
            MediaMetadata metadata = new MediaMetadata.Builder()
                    .setTitle(song.getTitle()).setArtist(song.getArtist()).setAlbumTitle(song.getAlbum())
                    .setArtworkUri(song.getArtworkUri().isEmpty() ? null : Uri.parse(song.getArtworkUri()))
                    .build();
            items.add(new MediaItem.Builder()
                    .setMediaId(song.getMediaKey()).setUri(song.getContentUri()).setMediaMetadata(metadata).build());
        }
        return items;
    }

    public void playRadioQueue(List<RadioStation> stations, int startIndex) {
        if (stations == null || stations.isEmpty()) return;
        int selectedSourceIndex = Math.max(0, Math.min(startIndex, stations.size() - 1));
        RadioStation selectedStation = stations.get(selectedSourceIndex);
        String selectedId = selectedStation == null ? "" : selectedStation.getMediaKey();
        List<MediaItem> items = new ArrayList<>();
        int selectedQueueIndex = 0;
        for (RadioStation station : stations) {
            if (station == null || station.getStreamUrl().isEmpty()) continue;
            if (station.getMediaKey().equals(selectedId)) selectedQueueIndex = items.size();
            String streamUrl = RadioPlaybackInfo.primaryUrl(station);
            if (streamUrl.isEmpty()) continue;
            MediaMetadata metadata = new MediaMetadata.Builder()
                    .setTitle(station.getName())
                    .setArtist(station.getArtist())
                    .setAlbumTitle(station.getAlbum())
                    .setArtworkUri(station.getFavicon().isEmpty() ? null : Uri.parse(station.getFavicon()))
                    .setExtras(RadioPlaybackInfo.extras(station))
                    .build();
            MediaItem.Builder builder = new MediaItem.Builder()
                    .setMediaId(station.getMediaKey())
                    .setUri(Uri.parse(streamUrl))
                    .setMediaMetadata(metadata);
            String mimeType = RadioPlaybackInfo.mimeType(station, streamUrl);
            if (mimeType != null) builder.setMimeType(mimeType);
            items.add(builder.build());
        }
        if (!items.isEmpty()) playerController.playRadioQueue(items, selectedQueueIndex);
    }

    public void applyCurrentMetadata(String key, String newTitle, String newArtist,
                                     String newAlbum, String newArtwork) {
        String current = mediaKey.getValue();
        if (current != null && current.equals(key)) {
            playerController.updateCurrentMetadata(newTitle, newArtist, newAlbum, newArtwork);
        }
    }

    public void togglePlayPause() { playerController.togglePlayPause(); }
    public void pause() { playerController.pause(); }
    public void previous() { playerController.previous(); }
    public void next() { playerController.next(); }
    public void seekTo(long positionMs) { playerController.seekTo(positionMs); }
    public void jumpToQueueItem(int mediaItemIndex) { playerController.jumpToQueueItem(mediaItemIndex); }
    public void removeQueueItem(int mediaItemIndex) { playerController.removeQueueItem(mediaItemIndex); }
    public void toggleShuffle() { playerController.toggleShuffle(); }
    public void enableShuffle() { playerController.enableShuffle(); }
    public void cycleRepeatMode() { playerController.cycleRepeatMode(); }
    public void retryRadioConnection() { playerController.retryRadioConnection(); }

    @Override
    public void onPlayerChanged(String newTitle, String newArtist, String newAlbum, String newArtwork,
                                String newMediaKey, boolean isPlaying, boolean mediaAvailable,
                                boolean shuffle, int repeat) {
        title.setValue(newTitle);
        artist.setValue(newArtist);
        album.setValue(newAlbum);
        artworkUri.setValue(newArtwork);
        mediaKey.setValue(newMediaKey);
        playing.setValue(isPlaying);
        hasMedia.setValue(mediaAvailable);
        shuffleEnabled.setValue(shuffle);
        repeatMode.setValue(repeat);
        radioMedia.setValue(newMediaKey != null && newMediaKey.startsWith("radio:"));
    }

    @Override
    public void onQueueChanged(List<PlayerController.QueueEntry> items) {
        queue.setValue(items == null ? new ArrayList<>() : new ArrayList<>(items));
    }

    @Override public void onControllerReady() { controllerReady.setValue(true); }

    @Override
    public void onRadioConnectionChanged(String mediaId, int state, String errorMessage) {
        radioConnectionMediaId.setValue(mediaId == null ? "" : mediaId);
        radioConnectionState.setValue(state);
        radioConnectionError.setValue(errorMessage == null ? "" : errorMessage);
    }

    @Override protected void onCleared() {
        ticker.removeCallbacksAndMessages(null);
        playerController.release();
    }
}
