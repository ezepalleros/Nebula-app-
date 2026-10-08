package com.appincreible.musicplayer.ui.player;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.util.Base64;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.SeekBar;

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
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.appincreible.musicplayer.R;
import com.appincreible.musicplayer.data.model.Song;
import com.appincreible.musicplayer.MainActivity;
import com.appincreible.musicplayer.power.PowerSaverManager;
import com.appincreible.musicplayer.player.audio.EqualizerSpectrum;
import com.appincreible.musicplayer.databinding.FragmentNowPlayingBinding;
import com.appincreible.musicplayer.themes.AppPreferences;
import com.appincreible.musicplayer.themes.AppTypography;
import com.appincreible.musicplayer.themes.UiPalette;
import com.appincreible.musicplayer.ui.metadata.MediaEditorBottomSheet;
import com.appincreible.musicplayer.ui.music.MusicViewModel;
import com.bumptech.glide.Glide;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.shape.ShapeAppearanceModel;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
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
    private boolean playing;
    private boolean immersive2d;
    private boolean rendererTwoDimensional;
    private boolean rendererReady;
    private boolean rendererContentReady;
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
    private Song currentSong;
    private UiPalette currentPalette;
    private int playerContentColor = Color.WHITE;
    private int playerSecondaryColor = Color.WHITE;
    private int playerAccentColor = Color.WHITE;
    private int playerGradientTop = Color.BLACK;
    private int playerGradientBottom = Color.BLACK;
    private QueueBottomSheet.QueueAdapter inlineQueueAdapter;
    private boolean queueExpanded;
    private int statusBarInsetTop;

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

    private void showStaticArtwork() {
        if (binding == null || staticPlayerImage == null) return;
        FrameLayout host = rendererHost();
        attachStaticArtworkToHost(host);
        staticPlayerImage.animate().cancel();
        staticPlayerImage.setAlpha(1f);
        if (AppPreferences.STYLE_RING.equals(activeStyle) || !AppPreferences.showPlayerArtwork(requireContext())) {
            staticPlayerImage.setVisibility(View.GONE);
        } else {
            staticPlayerImage.setVisibility(View.VISIBLE);
        }
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
        final int generation = ++rendererGeneration;

        WebView web = new WebView(requireContext());
        rendererWeb = web;
        configureLocalWebView(web);
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
                if (isResumed()) revealRenderer();
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
                staticPlayerImage.setVisibility(View.VISIBLE);
                staticPlayerImage.setAlpha(1f);
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
            if (PowerSaverManager.isActive(requireContext())) destroyRenderer();
            showStaticArtwork();
            return;
        }
        boolean shouldBe2d = AppPreferences.isTwoDimensionalStyle(activeStyle);
        if (rendererWeb == null || rendererTwoDimensional != shouldBe2d || !activeStyle.equals(rendererStyle)) {
            createRenderer();
        } else if (rendererReady) {
            prepareCurrentRenderer();
        }
    }

    private void prepareCurrentRenderer() {
        if (!rendererReady || rendererWeb == null || binding == null || !hasMedia || !artworkResolved) return;
        rendererContentReady = false;
        showStaticArtwork();
        int accent = AppPreferences.getPlayerAccentColor(requireContext());
        String hex = String.format(Locale.US, "#%06X", accent & 0xFFFFFF);
        String style = activeStyle;
        String typography = AppPreferences.getPlayerTypography(requireContext());
        float animation = AppPreferences.getAnimationMultiplier(requireContext());
        if (rendererTwoDimensional) {
            push2dVisibility();
            pushSpotify2dState();
            rendererWeb.evaluateJavascript("Player2D.setProgress(" + positionMs + "," + durationMs + ")", null);
            rendererWeb.evaluateJavascript("Player2D.prepare("
                    + JSONObject.quote(style) + "," + JSONObject.quote(hex) + "," + animation + ","
                    + JSONObject.quote(typography) + "," + playing + ","
                    + JSONObject.quote(currentTitle) + "," + JSONObject.quote(currentArtist) + ","
                    + JSONObject.quote(currentArtworkDataUrl) + ","
                    + JSONObject.quote(AppPreferences.getArtworkShape(requireContext())) + ")", null);
        } else {
            rendererWeb.evaluateJavascript("Player3D.prepare("
                    + JSONObject.quote(style) + "," + JSONObject.quote(hex) + "," + animation + ","
                    + JSONObject.quote(typography) + "," + playing + ","
                    + JSONObject.quote(currentTitle) + "," + JSONObject.quote(currentArtist) + ","
                    + JSONObject.quote(currentArtworkDataUrl) + ")", null);
        }
    }

    private void pushRendererAppearance() {
        if (!rendererReady || rendererWeb == null || binding == null) return;
        int accent = AppPreferences.getPlayerAccentColor(requireContext());
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

    private void push2dVisibility() {
        if (!rendererReady || rendererWeb == null || !rendererTwoDimensional) return;
        eval2d("Player2D.setVisibility("
                + AppPreferences.showPlayerArtwork(requireContext()) + ","
                + AppPreferences.showPlayerTitle(requireContext()) + ","
                + AppPreferences.showPlayerArtist(requireContext()) + ","
                + AppPreferences.showPlayerProgressLine(requireContext()) + ","
                + true + ","
                + AppPreferences.showPlayerShuffle(requireContext()) + ","
                + AppPreferences.showPlayerRepeat(requireContext()) + ","
                + AppPreferences.showPlayerModeText(requireContext()) + ")");
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
        if (requestedArtwork.equals(currentArtworkUri) && artworkResolved) return;
        currentArtworkUri = requestedArtwork;
        artworkResolved = false;
        if (binding != null) binding.ringVisualizer.setArtworkLoading();
        showStaticArtwork();
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
            return;
        }
        Glide.with(this).asBitmap().load(Uri.parse(currentArtworkUri)).centerCrop().into(new CustomTarget<Bitmap>(512, 512) {
            @Override public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                if (binding == null) return;
                currentArtworkDataUrl = bitmapToDataUrl(resource);
                if (staticPlayerImage != null) {
                    staticPlayerImage.clearColorFilter();
                    staticPlayerImage.setImageBitmap(resource);
                    staticPlayerImage.setVisibility(AppPreferences.STYLE_RING.equals(activeStyle)
                            || !AppPreferences.showPlayerArtwork(requireContext()) ? View.GONE : View.VISIBLE);
                }
                binding.ringVisualizer.setArtwork(resource);
                currentPalette = UiPalette.fromBitmap(requireContext(), paletteCacheKey(), resource);
                applyPaletteColors();
                applyPlayerBackgroundPalette();
                artworkResolved = true;
                ensureRendererForCurrentState();
            }

            @Override public void onLoadFailed(@Nullable android.graphics.drawable.Drawable errorDrawable) {
                if (binding == null) return;
                currentArtworkDataUrl = "";
                if (staticPlayerImage != null) {
                    staticPlayerImage.setImageResource(R.drawable.ic_music);
                    staticPlayerImage.setColorFilter(getResources().getColor(R.color.ink_muted, requireContext().getTheme()));
                    staticPlayerImage.setVisibility(AppPreferences.STYLE_RING.equals(activeStyle)
                            || !AppPreferences.showPlayerArtwork(requireContext()) ? View.GONE : View.VISIBLE);
                }
                binding.ringVisualizer.setNoArtwork();
                currentPalette = UiPalette.fallback(requireContext());
                applyPaletteColors();
                applyPlayerBackgroundPalette();
                artworkResolved = true;
                ensureRendererForCurrentState();
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
        rendererStyle = "";
        WebView web = rendererWeb;
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
        if (!isAdded()) return;
        boolean showMusic = AppPreferences.showPlayerMusicButton(requireContext());
        if (!showMusic) return;

        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        View content = getLayoutInflater().inflate(R.layout.bottom_sheet_player_options, null, false);
        View queue = content.findViewById(R.id.playerOptionQueue);
        View music = content.findViewById(R.id.playerOptionMusic);
        queue.setVisibility(View.GONE);
        music.setVisibility(View.VISIBLE);
        music.setOnClickListener(v -> { dialog.dismiss(); navigateToMusicSongs(); });
        dialog.setContentView(content);
        dialog.show();
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
        binding.playerHeader.setVisibility(anyHeader ? View.VISIBLE : View.GONE);
        binding.queueToggleRow.setVisibility(showQueue ? View.VISIBLE : View.GONE);
        if (showQueue) {
            setQueueExpanded(queueExpanded);
        } else {
            setQueueExpanded(false);
        }
        applyPlayerTopInsets(anyHeader);

        boolean showArtwork = AppPreferences.showPlayerArtwork(requireContext());
        binding.artworkStage.setVisibility((showArtwork || ringSkin) ? View.VISIBLE : View.GONE);
        binding.ringVisualizer.setArtworkVisible(showArtwork);
        if (staticPlayerImage != null && !ringSkin) {
            if (!showArtwork) staticPlayerImage.setVisibility(View.GONE);
        }

        binding.seekBar.setVisibility(AppPreferences.showPlayerProgressLine(requireContext()) ? View.VISIBLE : View.GONE);
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
