package com.appincreible.musicplayer.ui.player;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.ContentResolver;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Base64;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.activity.OnBackPressedCallback;
import androidx.fragment.app.Fragment;
import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.NestedScrollView;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.Player;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.appincreible.musicplayer.R;
import com.appincreible.musicplayer.lyrics.LyricsEditorUi;
import com.appincreible.musicplayer.data.model.Song;
import com.appincreible.musicplayer.MainActivity;
import com.appincreible.musicplayer.power.PowerSaverManager;
import com.appincreible.musicplayer.player.audio.EqualizerSpectrum;
import com.appincreible.musicplayer.player.controller.PlayerController;
import com.appincreible.musicplayer.databinding.FragmentNowPlayingBinding;
import com.appincreible.musicplayer.themes.AppPreferences;
import com.appincreible.musicplayer.themes.AppTypography;
import com.appincreible.musicplayer.themes.UiPalette;
import com.appincreible.musicplayer.ui.metadata.MediaEditorBottomSheet;
import com.appincreible.musicplayer.ui.music.MusicViewModel;
import com.bumptech.glide.Glide;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.shape.ShapeAppearanceModel;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class NowPlayingFragment extends Fragment {

    private FragmentNowPlayingBinding binding;
    private PlayerViewModel viewModel;
    private MusicViewModel musicViewModel;
    private boolean userSeeking;
    private long durationMs;
    private long positionMs;
    private boolean shuffleEnabled;
    private int repeatMode = Player.REPEAT_MODE_OFF;
    private boolean radioMedia;
    private int radioConnectionState = PlayerController.RADIO_CONNECTION_IDLE;
    private boolean playing;
    private boolean immersive2d;
    private boolean rendererTwoDimensional;
    private boolean rendererReady;
    private boolean rendererContentReady;
    private boolean rendererRefreshInPlace;
    private boolean hasMedia;
    private boolean artworkResolved;
    private int rendererGeneration;
    private String activeStyle = "";
    private String rendererStyle = "";
    private String currentTitle = "";
    private String currentArtist = "";
    private String currentArtworkUri = "";
    private String currentArtworkDataUrl = "";
    private SharedPreferences.OnSharedPreferenceChangeListener appearanceListener;
    private ShapeableImageView staticPlayerImage;
    private WebView rendererWeb;
    private LyricsEditorUi lyricsUi;
    private Integer artworkVisibilityBeforeTransparentLyrics;
    private Song currentSong;
    private Song pendingDeleteSong;
    private UiPalette currentPalette;
    private int playerContentColor = Color.WHITE;
    private int playerSecondaryColor = Color.WHITE;
    private int playerAccentColor = Color.WHITE;
    private int playerGradientTop = Color.BLACK;
    private int playerGradientBottom = Color.BLACK;
    private QueueBottomSheet.QueueAdapter inlineQueueAdapter;
    private boolean queueExpanded;
    private int statusBarInsetTop;
    private float artworkSwipeDownX;
    private float artworkSwipeDownY;
    private boolean artworkSwipeCandidate;
    private boolean artworkSwipeTransitionRunning;
    private View pendingArtworkSwipeView;
    private int pendingArtworkSwipeDuration;

    private final ActivityResultLauncher<String[]> importLyricsLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null && lyricsUi != null) lyricsUi.importFrom(uri);
            });
    private final ActivityResultLauncher<String> exportLyricsLauncher = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("text/plain"), uri -> {
                if (uri != null && lyricsUi != null) lyricsUi.exportTo(uri);
            });

    private final ActivityResultLauncher<IntentSenderRequest> deleteCurrentSongLauncher = registerForActivityResult(
            new ActivityResultContracts.StartIntentSenderForResult(), result -> {
                Song deleted = pendingDeleteSong;
                pendingDeleteSong = null;
                if (result.getResultCode() == Activity.RESULT_OK && deleted != null && isAdded()) {
                    skipThenCleanupDeletedSong(deleted);
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentNowPlayingBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(PlayerViewModel.class);
        musicViewModel = new ViewModelProvider(requireActivity()).get(MusicViewModel.class);
        currentPalette = UiPalette.fallback(requireContext());
        currentTitle = value(viewModel.getTitle().getValue());
        currentArtist = value(viewModel.getArtist().getValue());
        currentArtworkUri = value(viewModel.getArtworkUri().getValue());
        playing = Boolean.TRUE.equals(viewModel.getPlaying().getValue());
        hasMedia = Boolean.TRUE.equals(viewModel.getHasMedia().getValue());
        activeStyle = AppPreferences.getPlayerStyle(requireContext());
        installFullscreenInsets();

        lyricsUi = new LyricsEditorUi(requireContext(), new LyricsEditorUi.Playback() {
            @Override public long position() { return positionMs; }
            @Override public void pauseToggle() { viewModel.togglePlayPause(); }
            @Override public void rewindThreeSeconds() { viewModel.seekTo(Math.max(0L, positionMs - 3000L)); }
        });
        lyricsUi.attach(binding.artworkStage);
        lyricsUi.setSeekBoundary(binding.seekBar);
        lyricsUi.setVisibilityListener(visible -> {
            if (binding == null) return;
            if (visible) {
                push2dVisibility();
                enforceLyricsArtworkVisibility();
            } else {
                restoreLyricsArtworkVisibility();
                restoreRendererAfterLyrics();
                // Re-assert after the layout pass: immersive 2D skins may change
                // canvas visibility while the overlay is removed.
                binding.getRoot().post(() -> {
                    if (binding == null || lyricsUi == null || lyricsUi.isVisible()) return;
                    restoreLyricsArtworkVisibility();
                    restoreRendererAfterLyrics();
                });
            }
            if (visible) {
                binding.playerHeader.setVisibility(View.GONE);
                applyPlayerTopInsets(false);
            } else {
                applyPlayerElementVisibility(AppPreferences.STYLE_RING.equals(activeStyle));
            }
        });
        createStaticPlayerImage();
        applyPlayerAppearance();
        loadArtworkForRenderers(currentArtworkUri);

        appearanceListener = (prefs, key) -> {
            if (binding == null) return;
            binding.getRoot().post(() -> {
                if (binding != null) applyPlayerAppearance();
            });
        };
        AppPreferences.prefs(requireContext()).registerOnSharedPreferenceChangeListener(appearanceListener);

        inlineQueueAdapter = new QueueBottomSheet.QueueAdapter(
                entry -> QueueBottomSheet.showRemoveMenu(this, viewModel, entry),
                entry -> {
                    if (queueExpanded) {
                        viewModel.jumpToQueueItem(entry.mediaItemIndex);
                    } else {
                        setQueueExpanded(true);
                    }
                });
        binding.inlineQueueList.setLayoutManager(new LinearLayoutManager(requireContext()) {
            @Override public boolean canScrollVertically() {
                return queueExpanded && super.canScrollVertically();
            }
        });
        binding.inlineQueueList.setItemAnimator(null);
        binding.inlineQueueList.setAdapter(inlineQueueAdapter);
        binding.queueToggleRow.setOnClickListener(v -> setQueueExpanded(!queueExpanded));
        binding.standardPlayerScroll.setOnScrollChangeListener(
                (NestedScrollView.OnScrollChangeListener) (scroll, scrollX, scrollY, oldScrollX, oldScrollY) -> {
                    if (!queueExpanded) {
                        if (scrollY != 0) scroll.scrollTo(0, 0);
                        return;
                    }
                    if (scrollY >= oldScrollY && !scroll.canScrollVertically(1)
                            && inlineQueueAdapter != null) {
                        inlineQueueAdapter.loadMore();
                    }
                });

        binding.backButton.setOnClickListener(v -> navigateBackToSource());
        binding.moreButton.setOnClickListener(v -> openPlayerOptions());
        binding.favoriteButton.setOnClickListener(v -> {
            if (currentSong != null && !radioMedia) musicViewModel.toggleFavorite(currentSong);
        });
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { navigateBackToSource(); }
        });

        viewModel.getTitle().observe(getViewLifecycleOwner(), value -> {
            currentTitle = value(value);
            binding.title.setText(currentTitle);
            pushMetadata();
        });
        viewModel.getArtist().observe(getViewLifecycleOwner(), value -> {
            currentArtist = value(value);
            binding.artist.setText(currentArtist);
            pushMetadata();
        });
        viewModel.getMediaKey().observe(getViewLifecycleOwner(), value -> syncCurrentSong());
        viewModel.getAlbum().observe(getViewLifecycleOwner(), value -> {
            binding.album.setText(value == null ? "" : value);
            applyMetadataVisibility();
        });
        viewModel.getArtworkUri().observe(getViewLifecycleOwner(), this::loadArtworkForRenderers);
        viewModel.getHasMedia().observe(getViewLifecycleOwner(), available -> {
            boolean mediaAvailable = Boolean.TRUE.equals(available);
            boolean availabilityChanged = hasMedia != mediaAvailable;
            hasMedia = mediaAvailable;
            if (!hasMedia) {
                if (availabilityChanged || rendererWeb != null) {
                    destroyRenderer();
                    showStaticArtwork();
                }
            } else if (availabilityChanged || rendererWeb == null) {
                // PlayerController republishes hasMedia=true for play/pause, shuffle and repeat.
                // Do not re-prepare an already-visible renderer for those state-only updates:
                // setPlaying()/setPlaybackModes() update the canvas in place without flashing.
                ensureRendererForCurrentState();
            }
        });
        viewModel.getPlaying().observe(getViewLifecycleOwner(), isPlaying -> {
            playing = Boolean.TRUE.equals(isPlaying);
            binding.playPause.setIconResource(playing ? R.drawable.ic_pause : R.drawable.ic_play_arrow);
            eval3d("Player3D.setPlaying(" + playing + ")");
            eval2d("Player2D.setPlaying(" + playing + ")");
            updateRadioStatusUi();
            updateEqualizerAnalysisState();
        });
        viewModel.getShuffleEnabled().observe(getViewLifecycleOwner(), enabled -> {
            shuffleEnabled = Boolean.TRUE.equals(enabled);
            renderPlaybackModes();
        });
        viewModel.getRadioMedia().observe(getViewLifecycleOwner(), isRadio -> {
            radioMedia = Boolean.TRUE.equals(isRadio);
            syncCurrentSong();
            applyPlayerElementVisibility(AppPreferences.STYLE_RING.equals(activeStyle));
            updateRadioStatusUi();
        });
        viewModel.getRadioConnectionState().observe(getViewLifecycleOwner(), state -> {
            radioConnectionState = state == null ? PlayerController.RADIO_CONNECTION_IDLE : state;
            updateRadioStatusUi();
        });
        viewModel.getRepeatMode().observe(getViewLifecycleOwner(), mode -> {
            repeatMode = mode == null ? Player.REPEAT_MODE_OFF : mode;
            renderPlaybackModes();
        });
        viewModel.getQueue().observe(getViewLifecycleOwner(), items -> {
            if (inlineQueueAdapter == null) return;
            inlineQueueAdapter.submit(items == null ? java.util.Collections.emptyList() : items);
            binding.inlineQueueEmpty.setVisibility(items == null || items.isEmpty() ? View.VISIBLE : View.GONE);
        });
        musicViewModel.getAllSongs().observe(getViewLifecycleOwner(), songs -> syncCurrentSong());
        musicViewModel.getFavoriteSongs().observe(getViewLifecycleOwner(), songs -> syncFavoriteButton());
        if (!Boolean.TRUE.equals(musicViewModel.getLoading().getValue())
                && (musicViewModel.getAllSongs().getValue() == null
                || musicViewModel.getAllSongs().getValue().isEmpty())) {
            musicViewModel.loadSongs();
        }
        viewModel.getDuration().observe(getViewLifecycleOwner(), duration -> {
            durationMs = duration == null ? 0L : duration;
            binding.duration.setText(durationMs > 0 ? formatTime(durationMs) : "LIVE");
            binding.seekBar.setEnabled(durationMs > 0);
            push2dProgress();
        });
        viewModel.getPosition().observe(getViewLifecycleOwner(), position -> {
            positionMs = position == null ? 0L : position;
            binding.currentTime.setText(durationMs > 0 ? formatTime(positionMs) : "EN VIVO");
            if (!userSeeking && durationMs > 0) {
                binding.seekBar.setProgress((int) Math.min(1000L, (positionMs * 1000L) / durationMs));
            }
            push2dProgress();
            if (lyricsUi != null) lyricsUi.tick(positionMs);
        });

        binding.playPause.setOnClickListener(v -> viewModel.togglePlayPause());
        binding.previous.setOnClickListener(v -> viewModel.previous());
        binding.next.setOnClickListener(v -> viewModel.next());
        binding.shuffleButton.setOnClickListener(v -> viewModel.toggleShuffle());
        binding.repeatButton.setOnClickListener(v -> viewModel.cycleRepeatMode());

        View.OnLongClickListener editListener = v -> {
            openEditor();
            return true;
        };
        binding.title.setOnLongClickListener(editListener);
        binding.artist.setOnLongClickListener(editListener);
        binding.album.setOnLongClickListener(editListener);

        getParentFragmentManager().setFragmentResultListener(
                MediaEditorBottomSheet.RESULT_KEY, getViewLifecycleOwner(), (requestKey, result) -> {
                    String key = result.getString(MediaEditorBottomSheet.RESULT_MEDIA_KEY, "");
                    if (!result.getBoolean(MediaEditorBottomSheet.RESULT_RESET, false)) {
                        viewModel.applyCurrentMetadata(
                                key,
                                result.getString(MediaEditorBottomSheet.RESULT_TITLE, ""),
                                result.getString(MediaEditorBottomSheet.RESULT_ARTIST, ""),
                                result.getString(MediaEditorBottomSheet.RESULT_ALBUM, ""),
                                result.getString(MediaEditorBottomSheet.RESULT_ARTWORK, ""));
                    }
                });

        binding.seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) { }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {
                userSeeking = true;
                applySeekThumb(true);
            }
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                if (durationMs > 0) viewModel.seekTo((durationMs * seekBar.getProgress()) / 1000L);
                userSeeking = false;
                applySeekThumb(false);
            }
        });
    }

    private void createStaticPlayerImage() {
        staticPlayerImage = new ShapeableImageView(requireContext());
        staticPlayerImage.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        staticPlayerImage.setVisibility(View.VISIBLE);
        staticPlayerImage.setClickable(true);
        staticPlayerImage.setOnTouchListener(this::handleArtworkSwipe);
        attachStaticArtworkToHost(binding.artworkStage);
    }

    private FrameLayout rendererHost() {
        return immersive2d ? binding.immersiveRendererHost : binding.artworkStage;
    }

    private void attachStaticArtworkToHost(FrameLayout host) {
        if (staticPlayerImage == null || host == null) return;
        if (staticPlayerImage.getParent() instanceof ViewGroup) {
            ViewGroup parent = (ViewGroup) staticPlayerImage.getParent();
            if (parent == host) return;
            parent.removeView(staticPlayerImage);
        }
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        host.addView(staticPlayerImage, 0, params);
        applyArtworkPreferences(AppPreferences.STYLE_RING.equals(activeStyle));
    }

    // La portada nativa también vive en immersiveRendererHost (fuera de artworkStage).
    // No ocultar el WebView ni el host: solo la imagen cuando Letras es transparente.
    private boolean suppressArtworkForLyrics() {
        return lyricsUi != null && lyricsUi.isTransparentVisible();
    }

    private void enforceLyricsArtworkVisibility() {
        if (suppressArtworkForLyrics() && staticPlayerImage != null) {
            if (artworkVisibilityBeforeTransparentLyrics == null)
                artworkVisibilityBeforeTransparentLyrics = staticPlayerImage.getVisibility();
            staticPlayerImage.animate().cancel();
            staticPlayerImage.setVisibility(View.INVISIBLE);
            staticPlayerImage.setAlpha(0f);
            // Image alpha belongs to the drawable, so a later View alpha animation
            // or Glide bitmap update cannot reveal the cover behind the lyrics.
            staticPlayerImage.setImageAlpha(0);
        }
    }

    private void restoreLyricsArtworkVisibility() {
        if (staticPlayerImage != null && !suppressArtworkForLyrics()) {
            // Even when the saved visibility is null, an artwork update may have
            // set imageAlpha(0). Always restore the drawable opacity on close.
            staticPlayerImage.animate().cancel();
            staticPlayerImage.setImageAlpha(255);
            staticPlayerImage.setAlpha(1f);
            if (artworkVisibilityBeforeTransparentLyrics != null) {
                staticPlayerImage.setVisibility(artworkVisibilityBeforeTransparentLyrics);
            }
        }
        artworkVisibilityBeforeTransparentLyrics = null;
    }

    /** Restore the actual drawing surface, not just the fallback ImageView.
     * Pausing playback used to make the artwork reappear because it refreshed the
     * renderer; closing Lyrics must perform that refresh itself. */
    private void restoreRendererAfterLyrics() {
        if (binding == null || lyricsUi == null || lyricsUi.isVisible()) return;
        if (rendererWeb != null && rendererReady && rendererContentReady) {
            rendererWeb.animate().cancel();
            rendererWeb.setVisibility(View.VISIBLE);
            rendererWeb.setAlpha(1f);
            rendererWeb.invalidate();
            if (staticPlayerImage != null) {
                staticPlayerImage.animate().cancel();
                staticPlayerImage.setVisibility(View.GONE);
            }
            if (rendererTwoDimensional) {
                push2dVisibility();
            }
        } else if (staticPlayerImage != null && !AppPreferences.STYLE_RING.equals(activeStyle)
                && AppPreferences.showPlayerArtwork(requireContext())) {
            staticPlayerImage.animate().cancel();
            staticPlayerImage.setImageAlpha(255);
            staticPlayerImage.setAlpha(1f);
            staticPlayerImage.setVisibility(View.VISIBLE);
            staticPlayerImage.invalidate();
        }
    }

    private void showStaticArtwork() {
        if (binding == null || staticPlayerImage == null) return;
        FrameLayout host = rendererHost();
        attachStaticArtworkToHost(host);
        staticPlayerImage.animate().cancel();
        if (!artworkSwipeTransitionRunning) staticPlayerImage.setTranslationX(0f);
        staticPlayerImage.setAlpha(1f);
        if (AppPreferences.STYLE_RING.equals(activeStyle) || !AppPreferences.showPlayerArtwork(requireContext())) {
            staticPlayerImage.setVisibility(View.GONE);
        } else {
            staticPlayerImage.setVisibility(View.VISIBLE);
        }
        if (lyricsUi != null) lyricsUi.enforceTransparentArtworkHidden();
        enforceLyricsArtworkVisibility();
        if (rendererWeb != null) {
            rendererWeb.animate().cancel();
            rendererWeb.setAlpha(0f);
            rendererWeb.setVisibility(View.INVISIBLE);
        }
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private void createRenderer() {
        if (binding == null || !hasMedia || !artworkResolved || PowerSaverManager.isActive(requireContext())
                || (!AppPreferences.showPlayerArtwork(requireContext())
                    && !AppPreferences.isImmersiveTwoDimensionalStyle(activeStyle))
                || AppPreferences.STYLE_EQUALIZER_2D.equals(activeStyle)
                || AppPreferences.STYLE_RING.equals(activeStyle)) return;

        destroyRenderer();
        showStaticArtwork();
        rendererTwoDimensional = AppPreferences.isTwoDimensionalStyle(activeStyle);
        rendererStyle = activeStyle;
        rendererReady = false;
        rendererContentReady = false;
        rendererRefreshInPlace = false;
        final int generation = ++rendererGeneration;

        WebView web = new WebView(requireContext());
        rendererWeb = web;
        configureLocalWebView(web);
        web.setOnTouchListener(this::handleArtworkSwipe);
        web.setAlpha(0f);
        web.setVisibility(View.INVISIBLE);
        web.addJavascriptInterface(new RendererBridge(generation), "AndroidRenderer");
        updateEqualizerAnalysisState();
        web.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                if (binding == null || rendererWeb != view || generation != rendererGeneration) return;
                rendererReady = true;
                prepareCurrentRenderer();
            }

            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (rendererTwoDimensional && handle2dControl(uri)) return true;
                return !isLocalAsset(uri);
            }

            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                Uri uri = Uri.parse(url);
                if (rendererTwoDimensional && handle2dControl(uri)) return true;
                return !url.startsWith("file:///android_asset/");
            }
        });

        FrameLayout host = rendererHost();
        host.addView(web, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        web.loadUrl(rendererTwoDimensional
                ? "file:///android_asset/player3d/player2d.html"
                : "file:///android_asset/player3d/index.html");
    }

    @SuppressLint("SetJavaScriptEnabled")
    private boolean handleArtworkSwipe(View view, MotionEvent event) {
        if (binding == null || viewModel == null || !hasMedia || radioMedia) {
            artworkSwipeCandidate = false;
            return false;
        }
        boolean swipeEnabled = AppPreferences.swipeArtworkToChangeTrack(requireContext());

        // When swipe-to-change is enabled, a 3D renderer must not also receive the same
        // drag gesture. Consuming the WebView touch stream here keeps the model fixed while
        // still allowing this listener to recognize left/right track swipes. If the option
        // is disabled, touches fall through normally and the 3D model remains interactive.
        boolean lock3dModelTouch = swipeEnabled && view == rendererWeb && !rendererTwoDimensional;

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                // Immersive 2D skins also contain controls. Only treat gestures that begin
                // in the artwork area as artwork swipes.
                if (view == rendererWeb && immersive2d && view.getHeight() > 0
                        && event.getY() > view.getHeight() * 0.66f) {
                    artworkSwipeCandidate = false;
                    return false;
                }
                artworkSwipeDownX = event.getX();
                artworkSwipeDownY = event.getY();
                artworkSwipeCandidate = true;
                return lock3dModelTouch;

            case MotionEvent.ACTION_MOVE:
                if (!artworkSwipeCandidate) return lock3dModelTouch;
                float moveX = event.getX() - artworkSwipeDownX;
                float moveY = event.getY() - artworkSwipeDownY;
                if (Math.abs(moveX) >= dp(16) && Math.abs(moveX) > Math.abs(moveY) * 1.2f) {
                    if (view.getParent() != null) view.getParent().requestDisallowInterceptTouchEvent(true);
                }
                return lock3dModelTouch;

            case MotionEvent.ACTION_UP:
                if (!artworkSwipeCandidate) return lock3dModelTouch;
                artworkSwipeCandidate = false;
                if (view.getParent() != null) view.getParent().requestDisallowInterceptTouchEvent(false);
                float dx = event.getX() - artworkSwipeDownX;
                float dy = event.getY() - artworkSwipeDownY;
                if (swipeEnabled && Math.abs(dx) >= dp(64) && Math.abs(dx) > Math.abs(dy) * 1.35f) {
                    runArtworkSwipeTransition(view, dx < 0f);
                    return true;
                }
                // Tap corto sin desplazamiento: abrir/cerrar letras. Los arrastres 3D
                // y el gesto horizontal para cambiar canción conservan su comportamiento.
                if (Math.abs(dx) < dp(12) && Math.abs(dy) < dp(12)
                        && lyricsUi != null && !artworkSwipeTransitionRunning) {
                    if (rendererTwoDimensional && view == rendererWeb) return false; // Canvas distingue botones de portada.
                    toggleLyricsViewer();
                    return true;
                }
                return lock3dModelTouch;

            case MotionEvent.ACTION_CANCEL:
                artworkSwipeCandidate = false;
                if (view.getParent() != null) view.getParent().requestDisallowInterceptTouchEvent(false);
                return lock3dModelTouch;

            default:
                return lock3dModelTouch;
        }
    }

    private void runArtworkSwipeTransition(@NonNull View artworkView, boolean next) {
        if (viewModel == null) return;
        Runnable changeTrack = () -> {
            if (viewModel == null) return;
            if (next) viewModel.next(); else viewModel.previous();
        };
        if (PowerSaverManager.isActive(requireContext())
                || AppPreferences.getAnimationMultiplier(requireContext()) <= 0f) {
            changeTrack.run();
            return;
        }

        int duration = getResources().getInteger(android.R.integer.config_shortAnimTime);
        if (artworkView == rendererWeb) {
            artworkSwipeTransitionRunning = true;
            if (rendererTwoDimensional) {
                String direction = next ? "left" : "right";
                eval2d("Player2D.animateArtworkSwipe(" + JSONObject.quote(direction) + "," + duration + ")");
                artworkView.postDelayed(changeTrack, duration);
            } else {
                // Never translate the entire 3D WebView off-screen. The WebGL canvas can finish
                // loading its replacement texture asynchronously; moving the host view at the same
                // time could leave an empty stage. Keep the old model visible until Player3D
                // confirms the new track artwork through AndroidRenderer.onReady().
                changeTrack.run();
                artworkView.postDelayed(() -> {
                    if (binding != null && artworkSwipeTransitionRunning && !rendererTwoDimensional) {
                        artworkSwipeTransitionRunning = false;
                        rendererRefreshInPlace = false;
                        if (rendererWeb != null) {
                            rendererWeb.animate().cancel();
                            rendererWeb.setTranslationX(0f);
                            rendererWeb.setVisibility(View.VISIBLE);
                            rendererWeb.setAlpha(1f);
                        }
                    }
                }, Math.max(1400L, duration * 7L));
            }
            return;
        }

        float distance = Math.max(1f, artworkView.getWidth());
        float outgoing = next ? -distance : distance;
        artworkSwipeTransitionRunning = true;
        artworkView.animate().cancel();
        artworkView.animate()
                .translationX(outgoing)
                .setDuration(duration)
                .withEndAction(() -> {
                    if (binding == null) {
                        resetPendingArtworkSwipe();
                        return;
                    }
                    // Keep the view just outside the opposite edge until the new artwork/renderer
                    // is actually ready. Starting the incoming animation immediately can be
                    // cancelled by the asynchronous artwork load and leave the cover off-screen.
                    pendingArtworkSwipeView = artworkView;
                    pendingArtworkSwipeDuration = duration;
                    artworkView.setTranslationX(-outgoing);
                    changeTrack.run();
                    artworkView.postDelayed(() -> {
                        if (pendingArtworkSwipeView == artworkView) completePendingArtworkSwipe();
                    }, Math.max(900L, duration * 5L));
                })
                .start();
    }

    private void completePendingArtworkSwipe() {
        View pending = pendingArtworkSwipeView;
        if (pending == null) return;
        int duration = pendingArtworkSwipeDuration > 0
                ? pendingArtworkSwipeDuration
                : getResources().getInteger(android.R.integer.config_shortAnimTime);
        pendingArtworkSwipeView = null;
        pendingArtworkSwipeDuration = 0;
        pending.animate().cancel();
        if (binding == null || PowerSaverManager.isActive(requireContext())
                || AppPreferences.getAnimationMultiplier(requireContext()) <= 0f) {
            pending.setTranslationX(0f);
            artworkSwipeTransitionRunning = false;
            return;
        }
        pending.setVisibility(View.VISIBLE);
        pending.animate()
                .translationX(0f)
                .setDuration(duration)
                .withEndAction(() -> artworkSwipeTransitionRunning = false)
                .start();
    }

    private void resetPendingArtworkSwipe() {
        if (pendingArtworkSwipeView != null) {
            pendingArtworkSwipeView.animate().cancel();
            pendingArtworkSwipeView.setTranslationX(0f);
        }
        pendingArtworkSwipeView = null;
        pendingArtworkSwipeDuration = 0;
        artworkSwipeTransitionRunning = false;
    }

    private void resumeRendererArtworkSwipeWithCurrentArtwork() {
        if (!artworkSwipeTransitionRunning || rendererWeb == null) return;
        if (rendererTwoDimensional) {
            eval2d("Player2D.setArtworkDataUrl(" + JSONObject.quote(currentArtworkDataUrl) + ")");
        } else {
            eval3d("Player3D.setArtworkDataUrl(" + JSONObject.quote(currentArtworkDataUrl) + ")");
        }
    }

    private void configureLocalWebView(WebView web) {
        web.setBackgroundColor(Color.TRANSPARENT);
        web.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(false);
        settings.setDomStorageEnabled(false);
        settings.setBlockNetworkLoads(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        web.setVerticalScrollBarEnabled(false);
        web.setHorizontalScrollBarEnabled(false);
    }

    private final class RendererBridge {
        private final int generation;

        RendererBridge(int generation) {
            this.generation = generation;
        }

        @JavascriptInterface
        public void onReady(String style) {
            if (binding == null) return;
            binding.getRoot().post(() -> {
                if (binding == null || rendererWeb == null || generation != rendererGeneration) return;
                if (!activeStyle.equals(style) || !rendererStyle.equals(style)) return;
                rendererContentReady = true;
                if (pendingArtworkSwipeView == rendererWeb) {
                    // 3D/native renderer: the new artwork is ready, so now bring the renderer in.
                    rendererRefreshInPlace = false;
                    completePendingArtworkSwipe();
                    return;
                }
                boolean finishing2dSwipe = artworkSwipeTransitionRunning && rendererTwoDimensional;
                artworkSwipeTransitionRunning = false;
                if (isResumed()) {
                    if (finishing2dSwipe || rendererRefreshInPlace) {
                        // Track/state refresh: keep the complete renderer on screen until the new
                        // frame is ready. Never expose the native artwork-only fallback in between.
                        rendererRefreshInPlace = false;
                        rendererWeb.animate().cancel();
                        rendererWeb.setVisibility(View.VISIBLE);
                        rendererWeb.setAlpha(1f);
                        if (staticPlayerImage != null) staticPlayerImage.setVisibility(View.GONE);
                    } else {
                        revealRenderer();
                    }
                }
            });
        }
    }

    private void updateEqualizerAnalysisState() {
        if (binding == null) return;
        boolean equalizerSkin = AppPreferences.STYLE_EQUALIZER_2D.equals(activeStyle);
        boolean ringSkin = AppPreferences.STYLE_RING.equals(activeStyle);

        binding.ringVisualizer.setSkinActive(ringSkin);
        binding.ringVisualizer.setPlaybackActive(playing);
        binding.ringVisualizer.setHostVisible(isResumed());

        binding.equalizerSkinBackground.setSkinActive(equalizerSkin);
        binding.equalizerSkinBackground.setPlaybackActive(playing);
        binding.equalizerSkinBackground.setHostVisible(isResumed());

        // EqualizerBackgroundView owns this switch for its own skin. Ring uses the same
        enforceLyricsArtworkVisibility();
        // spectrum source, so only override it while Ring is the active visualizer.
        if (ringSkin) EqualizerSpectrum.setAnalysisEnabled(binding.ringVisualizer.shouldAnalyze());
    }

    private void revealRenderer() {
        if (binding == null || rendererWeb == null || !rendererContentReady
                || PowerSaverManager.isActive(requireContext())) return;
        rendererWeb.setVisibility(View.VISIBLE);
        rendererWeb.animate().cancel();
        rendererWeb.setAlpha(0f);
        rendererWeb.animate().alpha(1f).setDuration(140L).start();
        if (staticPlayerImage != null) {
            staticPlayerImage.animate().cancel();
            if (!AppPreferences.showPlayerArtwork(requireContext())) {
                staticPlayerImage.setVisibility(View.GONE);
                staticPlayerImage.setAlpha(1f);
            } else {
                staticPlayerImage.setVisibility(suppressArtworkForLyrics() ? View.INVISIBLE : View.VISIBLE);
                staticPlayerImage.setAlpha(suppressArtworkForLyrics() ? 0f : 1f);
                if (suppressArtworkForLyrics()) staticPlayerImage.setImageAlpha(0);
                staticPlayerImage.animate().alpha(0f).setDuration(140L).withEndAction(() -> {
                    if (staticPlayerImage != null) {
                        staticPlayerImage.setVisibility(View.GONE);
                        staticPlayerImage.setAlpha(1f);
                    }
                }).start();
            }
        }
    }

    private void ensureRendererForCurrentState() {
        if (binding == null) return;
        if (AppPreferences.STYLE_EQUALIZER_2D.equals(activeStyle)
                || AppPreferences.STYLE_RING.equals(activeStyle)) {
            destroyRenderer();
            showStaticArtwork();
            updateEqualizerAnalysisState();
            return;
        }
        if (!AppPreferences.showPlayerArtwork(requireContext())
                && !AppPreferences.isImmersiveTwoDimensionalStyle(activeStyle)) {
            destroyRenderer();
            showStaticArtwork();
            return;
        }
        if (!isResumed()) {
            showStaticArtwork();
            return;
        }
        if (PowerSaverManager.isActive(requireContext()) || !hasMedia || !artworkResolved) {
            if (PowerSaverManager.isActive(requireContext())) {
                destroyRenderer();
                showStaticArtwork();
            } else if (!canKeepRendererVisibleForRefresh()) {
                showStaticArtwork();
            }
            return;
        }
        boolean shouldBe2d = AppPreferences.isTwoDimensionalStyle(activeStyle);
        if (rendererWeb == null || rendererTwoDimensional != shouldBe2d || !activeStyle.equals(rendererStyle)) {
            createRenderer();
        } else if (rendererReady) {
            prepareCurrentRenderer();
        }
    }

    private boolean canKeepRendererVisibleForRefresh() {
        return rendererWeb != null
                && rendererReady
                && rendererContentReady
                && activeStyle.equals(rendererStyle)
                && rendererWeb.getVisibility() == View.VISIBLE
                && rendererWeb.getAlpha() > 0.5f;
    }

    private void prepareCurrentRenderer() {
        if (!rendererReady || rendererWeb == null || binding == null || !hasMedia || !artworkResolved) return;
        boolean keepCurrentFrame = canKeepRendererVisibleForRefresh();
        rendererContentReady = false;
        rendererRefreshInPlace = keepCurrentFrame;
        if (!artworkSwipeTransitionRunning && !keepCurrentFrame) showStaticArtwork();
        int accent = rendererAccentColor();
        String hex = String.format(Locale.US, "#%06X", accent & 0xFFFFFF);
        String style = activeStyle;
        String typography = AppPreferences.getPlayerTypography(requireContext());
        float animation = AppPreferences.getAnimationMultiplier(requireContext());
        if (rendererTwoDimensional) {
            push2dVisibility();
            pushSpotify2dState();
            pushNeon2dState();
            rendererWeb.evaluateJavascript("Player2D.setProgress(" + positionMs + "," + durationMs + ")", null);
            rendererWeb.evaluateJavascript("Player2D.prepare("
                    + JSONObject.quote(style) + "," + JSONObject.quote(hex) + "," + animation + ","
                    + JSONObject.quote(typography) + "," + playing + ","
                    + JSONObject.quote(currentTitle) + "," + JSONObject.quote(currentArtist) + ","
                    + JSONObject.quote(currentArtworkDataUrl) + ","
                    + JSONObject.quote(AppPreferences.getArtworkShape(requireContext())) + ")", null);
        } else {
            if (keepCurrentFrame || artworkSwipeTransitionRunning) {
                // Track changes must not re-run Player3D.prepare(): prepare() temporarily sets
                // initialized/visible=false while the new texture loads, which exposes an empty
                // stage during next/previous/swipe. setTrack() keeps the current model/frame alive
                // and swaps metadata + artwork only after the new image has finished loading.
                rendererWeb.evaluateJavascript("Player3D.setTrack("
                        + JSONObject.quote(currentTitle) + ","
                        + JSONObject.quote(currentArtist) + ","
                        + JSONObject.quote(currentArtworkDataUrl) + ")", null);
            } else {
                rendererWeb.evaluateJavascript("Player3D.prepare("
                        + JSONObject.quote(style) + "," + JSONObject.quote(hex) + "," + animation + ","
                        + JSONObject.quote(typography) + "," + playing + ","
                        + JSONObject.quote(currentTitle) + "," + JSONObject.quote(currentArtist) + ","
                        + JSONObject.quote(currentArtworkDataUrl) + ")", null);
            }
        }
    }

    private void pushRendererAppearance() {
        if (!rendererReady || rendererWeb == null || binding == null) return;
        int accent = rendererAccentColor();
        String hex = String.format(Locale.US, "#%06X", accent & 0xFFFFFF);
        float animation = AppPreferences.getAnimationMultiplier(requireContext());
        String typography = AppPreferences.getPlayerTypography(requireContext());
        if (rendererTwoDimensional) {
            push2dVisibility();
            eval2d("Player2D.setAccent(" + JSONObject.quote(hex) + ")");
            eval2d("Player2D.setAnimation(" + animation + ")");
            eval2d("Player2D.setTypography(" + JSONObject.quote(typography) + ")");
            eval2d("Player2D.setArtworkShape("
                    + JSONObject.quote(AppPreferences.getArtworkShape(requireContext())) + ")");
            eval2d("Player2D.setPlaying(" + playing + ")");
            pushSpotify2dState();
            pushNeon2dState();
        } else {
            eval3d("Player3D.setAccent(" + JSONObject.quote(hex) + ")");
            eval3d("Player3D.setAnimation(" + animation + ")");
            eval3d("Player3D.setTypography(" + JSONObject.quote(typography) + ")");
            eval3d("Player3D.setPlaying(" + playing + ")");
        }
    }

    private boolean isLocalAsset(Uri uri) {
        return "file".equals(uri.getScheme()) && uri.getPath() != null && uri.getPath().startsWith("/android_asset/");
    }

    private boolean handle2dControl(Uri uri) {
        if (!"app".equals(uri.getScheme()) || !"control".equals(uri.getHost()) || viewModel == null) return false;
        String command = uri.getQueryParameter("cmd");
        if (command == null) return true;
        switch (command) {
            case "playpause": viewModel.togglePlayPause(); break;
            case "previous": viewModel.previous(); break;
            case "next": viewModel.next(); break;
            case "shuffle": viewModel.toggleShuffle(); break;
            case "repeat": viewModel.cycleRepeatMode(); break;
            case "favorite":
                if (!radioMedia && currentSong != null && musicViewModel != null) {
                    musicViewModel.toggleFavorite(currentSong);
                }
                break;
            case "queue":
                if (!radioMedia && AppPreferences.showPlayerQueue(requireContext())
                        && getParentFragmentManager().findFragmentByTag("spotify2d_queue") == null) {
                    new QueueBottomSheet().show(getParentFragmentManager(), "spotify2d_queue");
                }
                break;
            case "edit": openEditor(); break;
            case "lyrics": if (!radioMedia) toggleLyricsViewer(); break;
            case "seek":
                if (durationMs > 0) {
                    try {
                        float ratio = Float.parseFloat(value(uri.getQueryParameter("ratio")));
                        ratio = Math.max(0f, Math.min(1f, ratio));
                        viewModel.seekTo((long) (durationMs * ratio));
                    } catch (NumberFormatException ignored) { }
                }
                break;
            default: break;
        }
        return true;
    }

    private void toggleLyricsViewer() {
        if (binding == null || lyricsUi == null || radioMedia || !hasMedia) return;
        // Los estilos inmersivos ocultan artworkStage: mostrar el visor sobre su propio host.
        if (lyricsUi.isVisible()) {
            lyricsUi.hide();
            return;
        }
        boolean lyricsFullscreen = AppPreferences.prefs(requireContext())
                .getInt("lyrics_view_fullscreen", 0) == 1;
        lyricsUi.setFullscreenHost(lyricsFullscreen);
        // El host inmersivo de Spotify llena la pantalla: usar la raíz con
        // límites propios para que el visor normal no cubra sus controles.
        lyricsUi.setHost(lyricsFullscreen || immersive2d
                ? binding.getRoot() : binding.artworkStage);
        lyricsUi.setImmersiveLimitedHost(!lyricsFullscreen && immersive2d);
        lyricsUi.show();
        // La barra se ubica después del layout; no permitir que el visor
        // transparente intercepte sus toques al abrir Letras.
        binding.getRoot().post(() -> {
            if (binding != null && lyricsUi != null && lyricsUi.isVisible())
                lyricsUi.setSeekBoundary(binding.seekBar);
        });
        // En Spotify y los skins inmersivos, mantener la capa opaca por encima del WebView.
        if (lyricsFullscreen) binding.getRoot().post(() -> {
            if (binding != null && lyricsUi != null && lyricsUi.isVisible())
                lyricsUi.ensureFullscreenOnTop();
        });
    }

    private void push2dVisibility() {
        if (!rendererReady || rendererWeb == null || !rendererTwoDimensional) return;
        eval2d("Player2D.setVisibility("
                + (AppPreferences.showPlayerArtwork(requireContext())
                    && !(lyricsUi != null && lyricsUi.isTransparentVisible())) + ","
                + AppPreferences.showPlayerTitle(requireContext()) + ","
                + AppPreferences.showPlayerArtist(requireContext()) + ","
                + AppPreferences.showPlayerProgressLine(requireContext()) + ","
                + true + ","
                + AppPreferences.showPlayerShuffle(requireContext()) + ","
                + AppPreferences.showPlayerRepeat(requireContext()) + ","
                + AppPreferences.showPlayerModeText(requireContext()) + ")");
        // El canvas redibuja al avanzar/retroceder: no volver a exponer la portada nativa.
        enforceLyricsArtworkVisibility();
    }

    private void pushSpotify2dState() {
        if (binding == null || !rendererTwoDimensional
                || !AppPreferences.STYLE_SPOTIFY_2D.equals(activeStyle)) return;
        boolean favoriteVisible = !radioMedia && currentSong != null;
        boolean favorite = favoriteVisible && musicViewModel != null
                && musicViewModel.isFavorite(currentSong.getId());
        boolean queueVisible = !radioMedia && AppPreferences.showPlayerQueue(requireContext());
        UiPalette palette = currentPalette == null ? UiPalette.fallback(requireContext()) : currentPalette;
        String backgroundHex = String.format(Locale.US, "#%06X", palette.surface & 0xFFFFFF);
        eval2d("Player2D.setPlaybackModes(" + shuffleEnabled + "," + repeatMode + ")");
        eval2d("Player2D.setFavorite(" + favorite + "," + favoriteVisible + ")");
        eval2d("Player2D.setQueueVisible(" + queueVisible + ")");
        eval2d("Player2D.setBackgroundColor(" + JSONObject.quote(backgroundHex) + ")");
    }

    private int rendererAccentColor() {
        if (!AppPreferences.STYLE_NEON.equals(activeStyle)) {
            return AppPreferences.getPlayerAccentColor(requireContext());
        }
        String mode = AppPreferences.getNeonSkinColorMode(requireContext());
        if (AppPreferences.APP_BACKGROUND_COLOR_ACCENT.equals(mode)) {
            return AppPreferences.getPlayerAccentColor(requireContext());
        }
        if (AppPreferences.PLAYER_BACKGROUND_COLOR_ARTWORK.equals(mode)) {
            UiPalette palette = currentPalette == null ? UiPalette.fallback(requireContext()) : currentPalette;
            return palette.accent;
        }
        return AppPreferences.getNeonSkinColor1(requireContext());
    }

    private void pushNeon2dState() {
        if (binding == null || !rendererTwoDimensional
                || !AppPreferences.STYLE_NEON.equals(activeStyle)) return;
        String mode = AppPreferences.getNeonSkinColorMode(requireContext());
        int c1;
        int c2;
        int c3;
        if (AppPreferences.APP_BACKGROUND_COLOR_ACCENT.equals(mode)) {
            c1 = c2 = c3 = AppPreferences.getPlayerAccentColor(requireContext());
        } else if (AppPreferences.PLAYER_BACKGROUND_COLOR_ARTWORK.equals(mode)) {
            UiPalette palette = currentPalette == null ? UiPalette.fallback(requireContext()) : currentPalette;
            c1 = c2 = c3 = palette.accent;
        } else {
            c1 = AppPreferences.getNeonSkinColor1(requireContext());
            c2 = AppPreferences.getNeonSkinColor2(requireContext());
            c3 = AppPreferences.getNeonSkinColor3(requireContext());
        }
        String h1 = String.format(Locale.US, "#%06X", c1 & 0xFFFFFF);
        String h2 = String.format(Locale.US, "#%06X", c2 & 0xFFFFFF);
        String h3 = String.format(Locale.US, "#%06X", c3 & 0xFFFFFF);
        eval2d("Player2D.setNeonPalette(" + JSONObject.quote(mode) + ","
                + JSONObject.quote(h1) + "," + JSONObject.quote(h2) + ","
                + JSONObject.quote(h3) + ")");
    }

    private void pushMetadata() {
        eval3d("Player3D.setMetadata(" + JSONObject.quote(currentTitle) + "," + JSONObject.quote(currentArtist) + ")");
        eval2d("Player2D.setMetadata(" + JSONObject.quote(currentTitle) + "," + JSONObject.quote(currentArtist) + ")");
    }

    private void push2dProgress() {
        // Non-immersive 2D skins use the native seek bar, so crossing the WebView bridge
        // twice per second only wakes the renderer without changing anything on screen.
        if (!AppPreferences.STYLE_SPOTIFY_2D.equals(activeStyle)) return;
        eval2d("Player2D.setProgress(" + positionMs + "," + durationMs + ")");
    }

    private void loadArtworkForRenderers(String uri) {
        String requestedArtwork = value(uri);
        if (requestedArtwork.equals(currentArtworkUri) && artworkResolved) {
            // Consecutive songs can share the same album artwork. The swipe must still finish.
            if (pendingArtworkSwipeView != null && pendingArtworkSwipeView != rendererWeb) {
                completePendingArtworkSwipe();
            } else {
                resumeRendererArtworkSwipeWithCurrentArtwork();
            }
            return;
        }
        currentArtworkUri = requestedArtwork;
        artworkResolved = false;
        if (binding != null) binding.ringVisualizer.setArtworkLoading();
        // Keep the complete previous renderer visible while the next artwork is loading.
        // The artwork-only fallback is reserved for first load / missing renderer.
        if (!artworkSwipeTransitionRunning && !canKeepRendererVisibleForRefresh()) {
            showStaticArtwork();
        }
        if (currentArtworkUri.isEmpty()) {
            currentArtworkDataUrl = "";
            if (staticPlayerImage != null) {
                staticPlayerImage.setImageResource(R.drawable.ic_music);
                staticPlayerImage.setColorFilter(getResources().getColor(R.color.ink_muted, requireContext().getTheme()));
            }
            artworkResolved = true;
            if (binding != null) binding.ringVisualizer.setNoArtwork();
            currentPalette = UiPalette.fallback(requireContext());
            applyPaletteColors();
            applyPlayerBackgroundPalette();
            ensureRendererForCurrentState();
            if (pendingArtworkSwipeView != null && pendingArtworkSwipeView != rendererWeb) {
                completePendingArtworkSwipe();
            }
            return;
        }
        Glide.with(this).asBitmap().load(Uri.parse(currentArtworkUri)).centerCrop().into(new CustomTarget<Bitmap>(512, 512) {
            @Override public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                if (binding == null || !requestedArtwork.equals(currentArtworkUri)) return;
                currentArtworkDataUrl = bitmapToDataUrl(resource);
                if (staticPlayerImage != null) {
                    staticPlayerImage.clearColorFilter();
                    staticPlayerImage.setImageBitmap(resource);
                    staticPlayerImage.setVisibility(AppPreferences.STYLE_RING.equals(activeStyle)
                            || !AppPreferences.showPlayerArtwork(requireContext()) ? View.GONE : View.VISIBLE);
                }
                binding.ringVisualizer.setArtwork(resource);
                if (lyricsUi != null) lyricsUi.enforceTransparentArtworkHidden();
                enforceLyricsArtworkVisibility();
                currentPalette = UiPalette.fromBitmap(requireContext(), paletteCacheKey(), resource);
                applyPaletteColors();
                applyPlayerBackgroundPalette();
                artworkResolved = true;
                ensureRendererForCurrentState();
                if (pendingArtworkSwipeView != null && pendingArtworkSwipeView != rendererWeb) {
                    completePendingArtworkSwipe();
                }
            }

            @Override public void onLoadFailed(@Nullable android.graphics.drawable.Drawable errorDrawable) {
                if (binding == null || !requestedArtwork.equals(currentArtworkUri)) return;
                currentArtworkDataUrl = "";
                if (staticPlayerImage != null) {
                    staticPlayerImage.setImageResource(R.drawable.ic_music);
                    staticPlayerImage.setColorFilter(getResources().getColor(R.color.ink_muted, requireContext().getTheme()));
                    staticPlayerImage.setVisibility(AppPreferences.STYLE_RING.equals(activeStyle)
                            || !AppPreferences.showPlayerArtwork(requireContext()) ? View.GONE : View.VISIBLE);
                }
                binding.ringVisualizer.setNoArtwork();
                if (lyricsUi != null) lyricsUi.enforceTransparentArtworkHidden();
                enforceLyricsArtworkVisibility();
                currentPalette = UiPalette.fallback(requireContext());
                applyPaletteColors();
                applyPlayerBackgroundPalette();
                artworkResolved = true;
                ensureRendererForCurrentState();
                if (pendingArtworkSwipeView != null && pendingArtworkSwipeView != rendererWeb) {
                    completePendingArtworkSwipe();
                }
            }

            @Override public void onLoadCleared(@Nullable android.graphics.drawable.Drawable placeholder) { }
        });
    }

    private String bitmapToDataUrl(Bitmap bitmap) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 86, out);
        return "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
    }

    private void eval3d(String js) {
        if (!rendererReady || rendererWeb == null || rendererTwoDimensional) return;
        rendererWeb.evaluateJavascript(js, null);
    }

    private void eval2d(String js) {
        if (!rendererReady || rendererWeb == null || !rendererTwoDimensional) return;
        rendererWeb.evaluateJavascript(js, null);
    }

    private void destroyRenderer() {
        EqualizerSpectrum.setAnalysisEnabled(false);
        rendererGeneration++;
        rendererReady = false;
        rendererContentReady = false;
        rendererRefreshInPlace = false;
        rendererStyle = "";
        WebView web = rendererWeb;
        boolean preserveStaticArtworkSwipe = pendingArtworkSwipeView != null
                && pendingArtworkSwipeView != web;
        if (!preserveStaticArtworkSwipe) artworkSwipeTransitionRunning = false;
        if (pendingArtworkSwipeView == web) {
            pendingArtworkSwipeView = null;
            pendingArtworkSwipeDuration = 0;
        }
        rendererWeb = null;
        if (web == null) return;
        try {
            if (rendererTwoDimensional) web.evaluateJavascript("Player2D.dispose()", null);
            else web.evaluateJavascript("Player3D.dispose()", null);
        } catch (Throwable ignored) { }
        if (web.getParent() instanceof ViewGroup) ((ViewGroup) web.getParent()).removeView(web);
        destroyWebView(web);
    }

    private void openPlayerOptions() {
        if (!isAdded() || binding == null) return;

        LinearLayout menuContainer = new LinearLayout(requireContext());
        menuContainer.setOrientation(LinearLayout.VERTICAL);
        int minWidth = dp(192);
        menuContainer.setMinimumWidth(minWidth);
        menuContainer.setPadding(0, dp(8), 0, dp(8));

        int surfaceColor = resolveMaterialColor(com.google.android.material.R.attr.colorSurface, 0xFF202020);
        int onSurfaceColor = resolveMaterialColor(com.google.android.material.R.attr.colorOnSurface, Color.WHITE);

        MaterialCardView card = new MaterialCardView(requireContext());
        card.setCardBackgroundColor(surfaceColor);
        card.setRadius(dp(24));
        card.setCardElevation(dp(14));
        card.setUseCompatPadding(false);
        card.setStrokeWidth(0);
        card.addView(menuContainer, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final PopupWindow popupWindow = new PopupWindow(card,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true);
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popupWindow.setElevation(dp(18));
        popupWindow.setOutsideTouchable(true);
        popupWindow.setClippingEnabled(true);

        if (radioMedia || currentSong == null) {
            addPlayerOptionItem(menuContainer, "Ir a Música", onSurfaceColor, () -> {
                popupWindow.dismiss();
                navigateToMusicSongs();
            });
        } else {
            Song song = currentSong;
            addPlayerOptionItem(menuContainer, "Editar", onSurfaceColor, () -> {
                popupWindow.dismiss();
                openEditor();
            });
            addPlayerOptionItem(menuContainer, "Ocultar", onSurfaceColor, () -> {
                popupWindow.dismiss();
                hideCurrentSong(song);
            });
            addPlayerOptionItem(menuContainer, "Eliminar", onSurfaceColor, () -> {
                popupWindow.dismiss();
                requestDeleteCurrentSong(song);
            });
            addPlayerOptionItem(menuContainer, "Ubicar en canciones", onSurfaceColor, () -> {
                popupWindow.dismiss();
                locateCurrentSong(song);
            });
        }

        card.measure(
                View.MeasureSpec.makeMeasureSpec(requireView().getWidth(), View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(requireView().getHeight(), View.MeasureSpec.AT_MOST));
        int measuredWidth = Math.max(card.getMeasuredWidth(), minWidth);
        int xOffset = binding.moreButton.getWidth() - measuredWidth;
        popupWindow.showAsDropDown(binding.moreButton, xOffset, dp(6), Gravity.END);
    }

    private void addPlayerOptionItem(@NonNull LinearLayout container,
                                     @NonNull String label,
                                     int textColor,
                                     @NonNull Runnable action) {
        TextView itemView = new TextView(requireContext());
        itemView.setText(label);
        itemView.setTextColor(textColor);
        itemView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f);
        itemView.setTypeface(AppTypography.get(requireContext(), Typeface.NORMAL));
        itemView.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        itemView.setMinWidth(dp(192));
        itemView.setMinimumHeight(dp(48));
        itemView.setPadding(dp(20), dp(10), dp(20), dp(10));
        TypedValue outValue = new TypedValue();
        requireContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
        itemView.setBackgroundResource(outValue.resourceId);
        itemView.setOnClickListener(v -> action.run());
        container.addView(itemView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private int resolveMaterialColor(int attr, int fallbackColor) {
        try {
            return MaterialColors.getColor(requireContext(), attr, fallbackColor);
        } catch (Exception ignored) {
            return fallbackColor;
        }
    }

    private void hideCurrentSong(@NonNull Song song) {
        // Move playback first so the player never remains focused on a track that just became hidden.
        viewModel.next();
        musicViewModel.setHidden(Collections.singletonList(song), true);
    }

    private void requestDeleteCurrentSong(@NonNull Song song) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                pendingDeleteSong = song;
                PendingIntent request = MediaStore.createDeleteRequest(
                        requireContext().getContentResolver(),
                        Collections.singletonList(song.getContentUri()));
                deleteCurrentSongLauncher.launch(
                        new IntentSenderRequest.Builder(request.getIntentSender()).build());
            } catch (RuntimeException error) {
                pendingDeleteSong = null;
                android.widget.Toast.makeText(requireContext(),
                        "No se pudo solicitar la eliminación", android.widget.Toast.LENGTH_SHORT).show();
            }
            return;
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Eliminar canción")
                .setMessage("Se borrará físicamente del dispositivo.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Eliminar", (dialog, which) -> {
                    ContentResolver resolver = requireContext().getContentResolver();
                    try {
                        viewModel.next();
                        resolver.delete(song.getContentUri(), null, null);
                        musicViewModel.cleanupDeleted(Collections.singletonList(song));
                    } catch (Exception error) {
                        android.widget.Toast.makeText(requireContext(),
                                "No se pudo eliminar la canción", android.widget.Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    private void skipThenCleanupDeletedSong(@NonNull Song song) {
        if (viewModel == null || musicViewModel == null) return;
        viewModel.next();
        musicViewModel.cleanupDeleted(Collections.singletonList(song));
    }

    private void locateCurrentSong(@NonNull Song song) {
        if (!isAdded() || !(requireActivity() instanceof MainActivity)) return;
        musicViewModel.requestLocateSong(song.getMediaKey());
        MainActivity activity = (MainActivity) requireActivity();
        if (!activity.openTab(R.id.musicFragment)) return;
        NavController controller = activity.getActiveNavController();
        if (controller == null || controller.getCurrentDestination() == null) return;
        if (controller.getCurrentDestination().getId() != R.id.musicFragment) {
            controller.popBackStack(R.id.musicFragment, false);
        }
    }

    private void installFullscreenInsets() {
        if (binding == null) return;
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (view, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.statusBars() | WindowInsetsCompat.Type.displayCutout());
            if (statusBarInsetTop != bars.top) {
                statusBarInsetTop = bars.top;
                applyPlayerTopInsets(binding.playerHeader.getVisibility() == View.VISIBLE);
            }
            return insets;
        });
        ViewCompat.requestApplyInsets(binding.getRoot());
    }

    private void applyPlayerTopInsets(boolean headerVisible) {
        if (binding == null) return;
        int headerContentHeight = getResources().getDimensionPixelSize(R.dimen.bottom_nav_height);
        ViewGroup.LayoutParams headerParams = binding.playerHeader.getLayoutParams();
        int headerHeight = headerContentHeight + statusBarInsetTop;
        if (headerParams.height != headerHeight) {
            headerParams.height = headerHeight;
            binding.playerHeader.setLayoutParams(headerParams);
        }
        binding.playerHeader.setPadding(
                binding.playerHeader.getPaddingLeft(), statusBarInsetTop,
                binding.playerHeader.getPaddingRight(), binding.playerHeader.getPaddingBottom());

        ViewGroup.MarginLayoutParams scrollParams =
                (ViewGroup.MarginLayoutParams) binding.standardPlayerScroll.getLayoutParams();
        int topMargin = statusBarInsetTop + (headerVisible ? headerContentHeight : 0);
        if (scrollParams.topMargin != topMargin) {
            scrollParams.topMargin = topMargin;
            binding.standardPlayerScroll.setLayoutParams(scrollParams);
        }
    }

    private void setQueueExpanded(boolean expanded) {
        if (binding == null) return;
        boolean allowed = AppPreferences.showPlayerQueue(requireContext()) && !radioMedia;
        queueExpanded = allowed && expanded;
        binding.inlineQueueContainer.setVisibility(allowed ? View.VISIBLE : View.GONE);
        binding.inlineQueueContainer.setAlpha(1f);
        binding.queueToggleRow.setSelected(queueExpanded);
        binding.queueStateIcon.setImageResource(queueExpanded
                ? R.drawable.ic_visibility
                : R.drawable.ic_visibility_off);
        binding.queueStateIcon.setContentDescription(queueExpanded
                ? "Cola abierta: podés deslizar hacia abajo"
                : "Cola cerrada: tocá para abrir");
        ViewGroup.LayoutParams queueListParams = binding.inlineQueueList.getLayoutParams();
        queueListParams.height = queueExpanded
                ? ViewGroup.LayoutParams.WRAP_CONTENT
                : getResources().getDimensionPixelSize(R.dimen.music_row_height);
        binding.inlineQueueList.setLayoutParams(queueListParams);
        binding.inlineQueueList.setClipToPadding(true);
        if (inlineQueueAdapter != null) {
            inlineQueueAdapter.setExpanded(queueExpanded);
        }
        if (!queueExpanded) {
            binding.standardPlayerScroll.post(() -> {
                if (binding != null && !queueExpanded) binding.standardPlayerScroll.scrollTo(0, 0);
            });
        }
        if (queueExpanded && inlineQueueAdapter != null) {
            binding.inlineQueueList.post(() -> {
                if (binding != null && !binding.standardPlayerScroll.canScrollVertically(1)) {
                    inlineQueueAdapter.loadMore();
                }
            });
        }
    }

    private void navigateToMusicSongs() {
        if (!isAdded()) return;
        if (requireActivity() instanceof MainActivity) {
            ((MainActivity) requireActivity()).openTab(R.id.musicFragment);
        }
    }

    private void navigateBackToSource() {
        if (!isAdded()) return;
        androidx.navigation.NavController controller = NavHostFragment.findNavController(this);
        boolean popped = controller.popBackStack();
        Log.i("TabNavigation", "nowPlaying back popped=" + popped
                + " radioMedia=" + radioMedia
                + " destination=" + (controller.getCurrentDestination() == null
                ? "null" : controller.getCurrentDestination().getDisplayName()));
        if (!popped) {
            // Nothing exists below Reproduciendo in this tab's own back stack.
            // Fall back to normal Activity behavior instead of inventing a destination.
            requireActivity().finishAfterTransition();
        }
    }

    private void openEditor() {
        String key = value(viewModel.getMediaKey().getValue());
        if (key.isEmpty()) return;
        MediaEditorBottomSheet.newInstance(
                        key,
                        key.startsWith("radio:"),
                        value(viewModel.getTitle().getValue()),
                        value(viewModel.getArtist().getValue()),
                        value(viewModel.getAlbum().getValue()),
                        value(viewModel.getArtworkUri().getValue()))
                .show(getParentFragmentManager(), "edit-current-media");
    }

    private void applyPlayerAppearance() {
        if (binding == null) return;
        String style = AppPreferences.getPlayerStyle(requireContext());
        boolean styleChanged = !style.equals(activeStyle);
        activeStyle = style;
        boolean powerSaver = PowerSaverManager.isActive(requireContext());
        immersive2d = !powerSaver && AppPreferences.isImmersiveTwoDimensionalStyle(style);

        boolean equalizerSkin = AppPreferences.STYLE_EQUALIZER_2D.equals(style);
        boolean ringSkin = AppPreferences.STYLE_RING.equals(style);
        binding.immersiveRendererHost.setVisibility(immersive2d ? View.VISIBLE : View.GONE);
        binding.standardPlayerScroll.setVisibility(immersive2d ? View.GONE : View.VISIBLE);
        boolean standardBackgroundVisible = !(immersive2d || equalizerSkin || ringSkin);
        binding.playerBackground.setVisibility(standardBackgroundVisible ? View.VISIBLE : View.GONE);
        binding.artworkGradientBackground.setVisibility(standardBackgroundVisible ? View.VISIBLE : View.GONE);
        binding.getRoot().setBackgroundColor(ringSkin
                ? AppPreferences.getRingSkinBackgroundColor(requireContext()) : Color.TRANSPARENT);
        binding.equalizerSkinBackground.setSkinActive(equalizerSkin);
        binding.equalizerSkinBackground.applyPreferences();
        binding.ringVisualizer.setSkinActive(ringSkin);
        binding.ringVisualizer.setArtworkVisible(AppPreferences.showPlayerArtwork(requireContext()));
        binding.ringVisualizer.applyPreferences();
        attachStaticArtworkToHost(rendererHost());
        applyPlayerElementVisibility(ringSkin);
        binding.seekBar.setProgressStyle(AppPreferences.getPlayerProgressStyle(requireContext()));
        binding.seekBar.setProgressEmoji(AppPreferences.getPlayerProgressEmoji(requireContext()));

        binding.skinScrim.setVisibility(equalizerSkin ? View.VISIBLE : View.GONE);
        applyPaletteColors();
        applyArtworkPreferences(ringSkin);

        binding.title.setTypeface(AppTypography.get(requireContext(), Typeface.BOLD));
        binding.artist.setTypeface(AppTypography.get(requireContext(), Typeface.NORMAL));
        binding.album.setTypeface(AppTypography.get(requireContext(), Typeface.NORMAL));
        int metadataGravity = AppPreferences.centerPlayerInfo(requireContext())
                ? Gravity.CENTER_HORIZONTAL : Gravity.START;
        binding.title.setGravity(metadataGravity);
        binding.artist.setGravity(metadataGravity);
        binding.album.setGravity(metadataGravity);
        String playerBackground = AppPreferences.getPlayerBackground(requireContext());
        binding.playerBackground.setScene(
                playerBackground,
                AppPreferences.getPlayerAccentColor(requireContext()),
                AppPreferences.getAnimationMultiplier(requireContext()));
        applyPlayerBackgroundPalette();

        if (powerSaver) {
            destroyRenderer();
            showStaticArtwork();
            updateEqualizerAnalysisState();
            return;
        }

        if (equalizerSkin || ringSkin) {
            destroyRenderer();
            showStaticArtwork();
            updateEqualizerAnalysisState();
            return;
        }

        boolean desired2d = AppPreferences.isTwoDimensionalStyle(activeStyle);
        if (styleChanged || rendererWeb == null || rendererTwoDimensional != desired2d
                || !activeStyle.equals(rendererStyle)) {
            destroyRenderer();
            showStaticArtwork();
            ensureRendererForCurrentState();
        } else {
            pushRendererAppearance();
        }
        updateEqualizerAnalysisState();
    }


    private void applyPlayerBackgroundPalette() {
        if (binding == null) return;
        String mode = AppPreferences.getPlayerBackgroundColorMode(requireContext());
        if (AppPreferences.PLAYER_BACKGROUND_COLOR_ARTWORK.equals(mode)) {
            UiPalette palette = currentPalette == null ? UiPalette.fallback(requireContext()) : currentPalette;
            int middle = ColorUtils.blendARGB(palette.surface, palette.accent, 0.5f);
            binding.playerBackground.setPalette(
                    AppPreferences.APP_BACKGROUND_COLOR_GRADIENT_3,
                    palette.surface, middle, palette.accent);
            return;
        }
        binding.playerBackground.setPalette(
                mode,
                AppPreferences.getPlayerBackgroundColor1(requireContext()),
                AppPreferences.getPlayerBackgroundColor2(requireContext()),
                AppPreferences.getPlayerBackgroundColor3(requireContext()));
    }


    private void applyArtworkPreferences(boolean ringSkin) {
        if (binding == null) return;

        int availableWidth = Math.max(dp(220), getResources().getDisplayMetrics().widthPixels - dp(36));
        int largeSize = Math.min(availableWidth, dp(390));
        int artworkSize = AppPreferences.useLargeArtwork(requireContext())
                ? largeSize : Math.max(dp(210), Math.round(largeSize * 0.72f));

        ViewGroup.LayoutParams stageParams = binding.artworkStage.getLayoutParams();
        int stageHeight = ringSkin ? dp(430) : artworkSize + dp(18);
        if (stageParams.height != stageHeight) {
            stageParams.height = stageHeight;
            binding.artworkStage.setLayoutParams(stageParams);
        }

        if (staticPlayerImage == null) return;
        FrameLayout.LayoutParams imageParams;
        ViewGroup.LayoutParams current = staticPlayerImage.getLayoutParams();
        if (current instanceof FrameLayout.LayoutParams) {
            imageParams = (FrameLayout.LayoutParams) current;
        } else {
            imageParams = new FrameLayout.LayoutParams(artworkSize, artworkSize, Gravity.CENTER);
        }
        imageParams.width = artworkSize;
        imageParams.height = artworkSize;
        imageParams.gravity = Gravity.CENTER;
        staticPlayerImage.setLayoutParams(imageParams);

        String shape = AppPreferences.getArtworkShape(requireContext());
        float corner = AppPreferences.SHAPE_CIRCLE.equals(shape) ? artworkSize * 0.5f
                : AppPreferences.SHAPE_SQUARE.equals(shape) ? 0f : dp(24);
        staticPlayerImage.setShapeAppearanceModel(
                ShapeAppearanceModel.builder().setAllCornerSizes(corner).build());
        staticPlayerImage.setClipToOutline(true);
    }

    private void applyPlayerElementVisibility(boolean ringSkin) {
        if (binding == null) return;
        boolean showBack = AppPreferences.showPlayerBackButton(requireContext());
        boolean showHeader = AppPreferences.showPlayerHeaderLabel(requireContext());
        boolean showMusic = AppPreferences.showPlayerMusicButton(requireContext());
        boolean showQueue = AppPreferences.showPlayerQueue(requireContext()) && !radioMedia;
        boolean showMore = showMusic;
        boolean anyHeader = showBack || showHeader || showMore;

        binding.backButton.setVisibility(showBack ? View.VISIBLE : View.GONE);
        binding.headerLabel.setVisibility(showHeader ? View.VISIBLE : View.GONE);
        binding.moreButton.setVisibility(showMore ? View.VISIBLE : View.GONE);
        boolean lyricsShowing = lyricsUi != null && lyricsUi.isVisible();
        binding.playerHeader.setVisibility(anyHeader && !lyricsShowing ? View.VISIBLE : View.GONE);
        binding.queueToggleRow.setVisibility(showQueue ? View.VISIBLE : View.GONE);
        if (showQueue) {
            setQueueExpanded(queueExpanded);
        } else {
            setQueueExpanded(false);
        }
        applyPlayerTopInsets(anyHeader && !lyricsShowing);

        boolean showArtwork = AppPreferences.showPlayerArtwork(requireContext());
        binding.artworkStage.setVisibility((showArtwork || ringSkin) ? View.VISIBLE : View.GONE);
        binding.ringVisualizer.setArtworkVisible(showArtwork);
        if (staticPlayerImage != null && !ringSkin) {
            if (!showArtwork) staticPlayerImage.setVisibility(View.GONE);
        }

        boolean showProgress = AppPreferences.showPlayerProgressLine(requireContext()) && !radioMedia;
        binding.seekBar.setVisibility(showProgress ? View.VISIBLE : View.GONE);
        binding.progressTimesRow.setVisibility(showProgress ? View.VISIBLE : View.GONE);
        binding.radioStatusCard.setVisibility(radioMedia ? View.VISIBLE : View.GONE);
        updateRadioStatusUi();
        applyMetadataVisibility();
        binding.shuffleButton.setVisibility(AppPreferences.showPlayerShuffle(requireContext()) ? View.VISIBLE : View.GONE);
        binding.repeatButton.setVisibility(AppPreferences.showPlayerRepeat(requireContext()) ? View.VISIBLE : View.GONE);
        binding.playMode.setVisibility(View.GONE);
        pushSpotify2dState();
    }

    private void applyMetadataVisibility() {
        if (binding == null) return;
        binding.title.setVisibility(AppPreferences.showPlayerTitle(requireContext()) ? View.VISIBLE : View.GONE);
        binding.artist.setVisibility(AppPreferences.showPlayerArtist(requireContext()) ? View.VISIBLE : View.GONE);
        CharSequence albumText = binding.album.getText();
        boolean hasAlbum = albumText != null && albumText.length() > 0;
        binding.album.setVisibility(AppPreferences.showPlayerAlbum(requireContext()) && hasAlbum ? View.VISIBLE : View.GONE);
    }

    private void updateRadioStatusUi() {
        if (binding == null || !radioMedia) return;

        String label;
        int dotColor;
        if (radioConnectionState == PlayerController.RADIO_CONNECTION_CONNECTING) {
            label = "CONECTANDO…";
            dotColor = playerSecondaryColor;
        } else if (radioConnectionState == PlayerController.RADIO_CONNECTION_ERROR) {
            label = "SIN CONEXIÓN";
            dotColor = resolveMaterialColor(com.google.android.material.R.attr.colorError, playerSecondaryColor);
        } else if (playing) {
            label = "EN VIVO";
            dotColor = playerAccentColor;
        } else {
            label = "PAUSADA";
            dotColor = playerSecondaryColor;
        }
        binding.radioStatusText.setText(label);
        binding.radioStatusDot.setBackgroundTintList(ColorStateList.valueOf(dotColor));
        binding.radioStatusText.setTextColor(playerContentColor);
        binding.radioStatusCard.setCardBackgroundColor(ColorUtils.setAlphaComponent(playerContentColor, 28));
    }

    private void renderPlaybackModes() {
        if (binding == null) return;

        binding.shuffleButton.setImageResource(shuffleEnabled ? R.drawable.ic_shuffle : R.drawable.ic_sort);
        binding.shuffleButton.setContentDescription(shuffleEnabled
                ? "Orden aleatorio activado" : "Orden de lista activado");
        stylePlaybackModeButton(binding.shuffleButton, shuffleEnabled);

        boolean repeatEnabled = repeatMode != Player.REPEAT_MODE_OFF;
        binding.repeatButton.setImageResource(repeatMode == Player.REPEAT_MODE_ONE
                ? R.drawable.ic_repeat_one : R.drawable.ic_repeat);
        if (repeatMode == Player.REPEAT_MODE_ONE) {
            binding.repeatButton.setContentDescription("Repetir una canción");
        } else if (repeatEnabled) {
            binding.repeatButton.setContentDescription("Repetir lista");
        } else {
            binding.repeatButton.setContentDescription("Repetición desactivada");
        }
        stylePlaybackModeButton(binding.repeatButton, repeatEnabled);
        pushSpotify2dState();
    }

    private void stylePlaybackModeButton(@NonNull androidx.appcompat.widget.AppCompatImageButton button,
                                         boolean active) {
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.OVAL);
        background.setColor(active ? playerAccentColor : Color.TRANSPARENT);
        button.setBackground(background);
        button.setColorFilter(active
                ? UiPalette.readableTextColor(playerAccentColor)
                : playerSecondaryColor);
    }

    private void syncCurrentSong() {
        if (binding == null || musicViewModel == null || viewModel == null) return;
        String mediaKey = value(viewModel.getMediaKey().getValue());
        if (radioMedia || !mediaKey.startsWith("song:")) {
            currentSong = null;
            if (lyricsUi != null) lyricsUi.setSong(null);
            syncFavoriteButton();
            return;
        }
        currentSong = null;
        List<Song> songs = musicViewModel.getAllSongs().getValue();
        if (songs != null) {
            for (Song song : songs) {
                if (mediaKey.equals(song.getMediaKey())) {
                    currentSong = song;
                    break;
                }
            }
        }
        syncFavoriteButton();
        if (lyricsUi != null) lyricsUi.setCurrentSong(currentSong);
    }

    private void syncFavoriteButton() {
        if (binding == null || musicViewModel == null) return;
        if (radioMedia || currentSong == null) {
            binding.favoriteButton.setVisibility(View.GONE);
            pushSpotify2dState();
            return;
        }
        boolean favorite = musicViewModel.isFavorite(currentSong.getId());
        binding.favoriteButton.setVisibility(View.VISIBLE);
        binding.favoriteButton.setImageResource(favorite
                ? R.drawable.ic_favorite_filled : R.drawable.ic_favorite_border);
        binding.favoriteButton.setColorFilter(favorite ? playerAccentColor : playerContentColor);
        pushSpotify2dState();
    }

    private String paletteCacheKey() {
        String mediaKey = viewModel == null ? "" : value(viewModel.getMediaKey().getValue());
        return mediaKey.isEmpty() ? currentArtworkUri : mediaKey;
    }

    private void applyPaletteColors() {
        if (binding == null) return;
        int secondary = getResources().getColor(R.color.night_text_muted, requireContext().getTheme());
        int contentScrim = getResources().getColor(R.color.ui_scrim_dark_40, requireContext().getTheme());
        binding.artworkGradientBackground.setBackgroundColor(contentScrim);

        // PlayerBackgroundView already renders a dark base; this translucent scrim keeps content readable
        // without replacing the selected scene or its colors.
        int readableBackground = ColorUtils.compositeColors(contentScrim, Color.GRAY);
        playerGradientTop = readableBackground;
        playerGradientBottom = readableBackground;
        playerContentColor = Color.WHITE;
        playerSecondaryColor = secondary;
        int configuredAccent = AppPreferences.getPlayerAccentColor(requireContext());
        playerAccentColor = ensureAccentContrast(configuredAccent, playerGradientTop, playerGradientBottom);

        binding.title.setTextColor(playerContentColor);
        binding.artist.setTextColor(playerSecondaryColor);
        binding.album.setTextColor(playerSecondaryColor);
        binding.currentTime.setTextColor(playerSecondaryColor);
        binding.duration.setTextColor(playerSecondaryColor);
        binding.radioStatusText.setTextColor(playerContentColor);
        binding.radioStatusCard.setCardBackgroundColor(ColorUtils.setAlphaComponent(playerContentColor, 28));
        boolean spotifyImmersive = immersive2d && AppPreferences.STYLE_SPOTIFY_2D.equals(activeStyle);
        binding.headerLabel.setTextColor(spotifyImmersive ? Color.WHITE : playerContentColor);

        int subtleSurface = getResources().getColor(R.color.ui_scrim_light_24, requireContext().getTheme());
        int headerSurface = spotifyImmersive ? Color.TRANSPARENT : subtleSurface;
        int headerContent = spotifyImmersive ? Color.WHITE : playerContentColor;
        binding.backButton.setBackgroundTintList(ColorStateList.valueOf(headerSurface));
        binding.moreButton.setBackgroundTintList(ColorStateList.valueOf(headerSurface));
        binding.previous.setBackgroundTintList(ColorStateList.valueOf(subtleSurface));
        binding.next.setBackgroundTintList(ColorStateList.valueOf(subtleSurface));
        binding.queueToggleLabel.setTextColor(playerContentColor);
        binding.queueStateIcon.setColorFilter(playerContentColor);
        binding.inlineQueueEmpty.setTextColor(playerSecondaryColor);
        binding.backButton.setIconTint(ColorStateList.valueOf(headerContent));
        binding.moreButton.setIconTint(ColorStateList.valueOf(headerContent));
        binding.previous.setIconTint(ColorStateList.valueOf(playerContentColor));
        binding.next.setIconTint(ColorStateList.valueOf(playerContentColor));
        binding.playPause.setBackgroundTintList(ColorStateList.valueOf(playerAccentColor));
        binding.playPause.setIconTint(ColorStateList.valueOf(UiPalette.readableTextColor(playerAccentColor)));
        binding.seekBar.setPlayerColors(playerAccentColor, playerSecondaryColor);
        applySeekThumb(userSeeking);
        renderPlaybackModes();
        syncFavoriteButton();
        pushSpotify2dState();
    }

    private int darkenForContrast(int color, int foreground) {
        int result = Color.rgb(Color.red(color), Color.green(color), Color.blue(color));
        if (ColorUtils.calculateContrast(foreground, result) >= UiPalette.MIN_TEXT_CONTRAST) return result;
        for (int step = 1; step <= 10; step++) {
            result = ColorUtils.blendARGB(color, Color.BLACK, step / 10f);
            if (ColorUtils.calculateContrast(foreground, result) >= UiPalette.MIN_TEXT_CONTRAST) return result;
        }
        return Color.BLACK;
    }

    private int ensureAccentContrast(int accent, int firstBackground, int secondBackground) {
        int result = Color.rgb(Color.red(accent), Color.green(accent), Color.blue(accent));
        if (hasRequiredContrast(result, firstBackground, secondBackground)) return result;
        for (int step = 1; step <= 10; step++) {
            result = ColorUtils.blendARGB(accent, Color.WHITE, step / 10f);
            if (hasRequiredContrast(result, firstBackground, secondBackground)) return result;
        }
        return Color.WHITE;
    }

    private boolean hasRequiredContrast(int foreground, int firstBackground, int secondBackground) {
        return ColorUtils.calculateContrast(foreground, firstBackground) >= UiPalette.MIN_TEXT_CONTRAST
                && ColorUtils.calculateContrast(foreground, secondBackground) >= UiPalette.MIN_TEXT_CONTRAST;
    }

    private void applySeekThumb(boolean pressed) {
        if (binding == null) return;
        binding.seekBar.setTouchEmphasis(pressed);
    }

    private String formatTime(long millis) {
        long seconds = millis / 1000L;
        return String.format(Locale.getDefault(), "%d:%02d", seconds / 60L, seconds % 60L);
    }

    private String value(String value) { return value == null ? "" : value; }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    @Override public void onStart() {
        super.onStart();
        if (viewModel != null) viewModel.startUiUpdates();
    }

    @Override public void onResume() {
        super.onResume();
        if (viewModel != null && PowerSaverManager.isActive(requireContext())) viewModel.startUiUpdates();
        if (binding != null) {
            applyPlayerAppearance();
            updateEqualizerAnalysisState();
            AppTypography.applyToViewTree(binding.getRoot());
            if (rendererWeb != null && !PowerSaverManager.isActive(requireContext())) {
                try { rendererWeb.onResume(); } catch (Exception ignored) { }
                if (rendererTwoDimensional) eval2d("Player2D.setAppVisible(true)");
                else eval3d("Player3D.setAppVisible(true)");
                if (rendererContentReady) revealRenderer();
            } else {
                showStaticArtwork();
            }
        }
    }

    @Override public void onPause() {
        if (binding != null) {
            binding.equalizerSkinBackground.setHostVisible(false);
            binding.ringVisualizer.setHostVisible(false);
        }
        EqualizerSpectrum.setAnalysisEnabled(false);
        if (viewModel != null && isAdded() && PowerSaverManager.isActive(requireContext())) viewModel.stopUiUpdates();
        if (rendererWeb != null) {
            if (rendererTwoDimensional) eval2d("Player2D.setAppVisible(false)");
            else eval3d("Player3D.setAppVisible(false)");
            try { rendererWeb.onPause(); } catch (Exception ignored) { }
        }
        super.onPause();
    }

    @Override public void onStop() {
        if (viewModel != null) viewModel.stopUiUpdates();
        super.onStop();
    }

    @Override public void onDestroyView() {
        if (appearanceListener != null && isAdded()) {
            AppPreferences.prefs(requireContext()).unregisterOnSharedPreferenceChangeListener(appearanceListener);
            appearanceListener = null;
        }
        if (lyricsUi != null) { lyricsUi.release(); lyricsUi = null; }
        destroyRenderer();
        if (staticPlayerImage != null) staticPlayerImage.setImageDrawable(null);
        staticPlayerImage = null;
        binding = null;
        super.onDestroyView();
    }

    private void destroyWebView(WebView webView) {
        try { webView.removeJavascriptInterface("AndroidRenderer"); } catch (Throwable ignored) { }
        webView.loadUrl("about:blank");
        webView.stopLoading();
        webView.destroy();
    }
}
