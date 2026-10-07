package com.appincreible.musicplayer;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

import com.appincreible.musicplayer.databinding.ActivityMainBinding;
import com.appincreible.musicplayer.power.PowerSaverManager;
import com.appincreible.musicplayer.themes.AppPreferences;
import com.appincreible.musicplayer.themes.AppTypography;
import com.appincreible.musicplayer.themes.UiPalette;
import com.appincreible.musicplayer.ui.music.MusicViewModel;
import com.appincreible.musicplayer.ui.player.PlayerViewModel;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.google.android.material.color.MaterialColors;

import java.util.LinkedHashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    public static final String ACTION_OPEN_NOW_PLAYING = "com.appincreible.musicplayer.action.OPEN_NOW_PLAYING";

    private static final String LOG_TAB = "TabNavigation";
    private static final String STATE_ACTIVE_TAB = "active_tab";

    private static final String TAG_MUSIC = "tab_host_music";
    private static final String TAG_RADIO = "tab_host_radio";
    private static final String TAG_GAMES = "tab_host_games";
    private static final String TAG_SETTINGS = "tab_host_settings";

    private ActivityMainBinding binding;
    private PlayerViewModel playerViewModel;
    private boolean hasMedia;
    private UiPalette miniPlayerArtworkPalette;
    private String miniArtworkUri = "";
    private SharedPreferences.OnSharedPreferenceChangeListener visualPreferenceListener;
    private FragmentManager.FragmentLifecycleCallbacks typographyLifecycleCallbacks;

    private final Map<Integer, NavHostFragment> tabHosts = new LinkedHashMap<>();
    private int activeTabId = R.id.musicFragment;
    private boolean suppressBottomNavCallback;
    private boolean pendingNotificationNowPlaying;
    private Insets systemBarInsets = Insets.NONE;
    private int bottomNavigationBaseHeight;
    private int bottomNavigationBasePaddingLeft;
    private int bottomNavigationBasePaddingTop;
    private int bottomNavigationBasePaddingRight;
    private int bottomNavigationBasePaddingBottom;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        PowerSaverManager.startMonitoring(this);
        AppPreferences.applySavedTheme(this);
        super.onCreate(savedInstanceState);
        if (!PermissionSetupActivity.hasRequiredPermissions(this)) {
            Intent gateIntent = new Intent(this, PermissionSetupActivity.class);
            if (getIntent() != null && ACTION_OPEN_NOW_PLAYING.equals(getIntent().getAction())) {
                gateIntent.putExtra(PermissionSetupActivity.EXTRA_FORWARD_ACTION, ACTION_OPEN_NOW_PLAYING);
            }
            startActivity(gateIntent);
            finish();
            return;
        }
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        captureBottomNavigationBaseMetrics();
        configureSystemBars();
        applyInsets();
        applyVisualPreferences();
        installTypographyPropagation();

        visualPreferenceListener = (prefs, key) -> {
            if (!isVisualPreference(key)) return;
            applyVisualPreferences();
            if (AppPreferences.KEY_PLAYER_TYPOGRAPHY.equals(key)) {
                AppTypography.refresh(binding.getRoot());
            } else {
                AppTypography.applyToViewTree(binding.getRoot());
            }
            renderChrome();
        };
        AppPreferences.prefs(this).registerOnSharedPreferenceChangeListener(visualPreferenceListener);

        applyBottomNavigationPreferences();
        setupTabHosts(savedInstanceState);
        setupBottomNavigation();

        playerViewModel = new ViewModelProvider(this).get(PlayerViewModel.class);
        MusicViewModel musicViewModel = new ViewModelProvider(this).get(MusicViewModel.class);

        playerViewModel.getTitle().observe(this, binding.miniPlayerTitle::setText);
        playerViewModel.getArtist().observe(this, binding.miniPlayerSubtitle::setText);
        playerViewModel.getArtworkUri().observe(this, artwork -> {
            String currentArtworkUri = artwork == null ? "" : artwork;
            miniArtworkUri = currentArtworkUri;
            miniPlayerArtworkPalette = null;
            if (AppPreferences.PLAYER_BACKGROUND_COLOR_ARTWORK.equals(
                    AppPreferences.getPlayerBackgroundColorMode(this))) {
                miniPlayerArtworkPalette = UiPalette.fallback(this);
                applyMiniPlayerBackground();
            }
            Glide.with(binding.miniArtwork)
                    .load(currentArtworkUri.isEmpty() ? null : Uri.parse(currentArtworkUri))
                    .placeholder(R.drawable.ic_music)
                    .error(R.drawable.ic_music)
                    .centerCrop()
                    .listener(new RequestListener<Drawable>() {
                        @Override
                        public boolean onLoadFailed(@Nullable GlideException e, Object model,
                                                    @NonNull Target<Drawable> target, boolean isFirstResource) {
                            if (binding != null && currentArtworkUri.equals(miniArtworkUri)
                                    && AppPreferences.PLAYER_BACKGROUND_COLOR_ARTWORK.equals(
                                    AppPreferences.getPlayerBackgroundColorMode(MainActivity.this))) {
                                miniPlayerArtworkPalette = UiPalette.fallback(MainActivity.this);
                                applyMiniPlayerBackground();
                            }
                            return false;
                        }

                        @Override
                        public boolean onResourceReady(@NonNull Drawable resource, Object model,
                                                       @NonNull Target<Drawable> target,
                                                       @NonNull DataSource dataSource, boolean isFirstResource) {
                            if (binding != null && currentArtworkUri.equals(miniArtworkUri)
                                    && AppPreferences.PLAYER_BACKGROUND_COLOR_ARTWORK.equals(
                                    AppPreferences.getPlayerBackgroundColorMode(MainActivity.this))) {
                                miniPlayerArtworkPalette = resource instanceof BitmapDrawable
                                        ? UiPalette.fromBitmap(
                                        MainActivity.this, "mini:" + currentArtworkUri,
                                        ((BitmapDrawable) resource).getBitmap())
                                        : UiPalette.fallback(MainActivity.this);
                                applyMiniPlayerBackground();
                            }
                            return false;
                        }
                    })
                    .into(binding.miniArtwork);
            if (currentArtworkUri.isEmpty()) {
                miniPlayerArtworkPalette = UiPalette.fallback(this);
                applyMiniPlayerBackground();
            }
        });
        playerViewModel.getPlaying().observe(this, playing -> {
            boolean playbackActive = Boolean.TRUE.equals(playing);
            binding.miniPlayPause.setIconResource(playbackActive
                    ? R.drawable.ic_pause : R.drawable.ic_play_arrow);
        });
        playerViewModel.getHasMedia().observe(this, value -> {
            hasMedia = Boolean.TRUE.equals(value);
            renderChrome();
            consumeNotificationNowPlayingIfReady();
        });
        playerViewModel.getControllerReady().observe(this, ready ->
                consumeNotificationNowPlayingIfReady());

        binding.miniPrevious.setOnClickListener(v -> playerViewModel.previous());
        binding.miniPlayPause.setOnClickListener(v -> playerViewModel.togglePlayPause());
        binding.miniNext.setOnClickListener(v -> playerViewModel.next());
        binding.miniPlayerCard.setOnClickListener(v -> openNowPlayingFromMiniPlayer());

        handleLaunchIntent(getIntent());
        if (hasAudioPermission()) musicViewModel.loadSongs();
    }


    private void handleLaunchIntent(@Nullable Intent intent) {
        if (intent == null || !ACTION_OPEN_NOW_PLAYING.equals(intent.getAction())) return;
        pendingNotificationNowPlaying = true;
        // Consume the command so a configuration recreation does not navigate a second time.
        intent.setAction(null);
        consumeNotificationNowPlayingIfReady();
    }

    private void consumeNotificationNowPlayingIfReady() {
        if (!pendingNotificationNowPlaying || playerViewModel == null) return;
        if (!Boolean.TRUE.equals(playerViewModel.getControllerReady().getValue())) return;

        pendingNotificationNowPlaying = false;
        if (!Boolean.TRUE.equals(playerViewModel.getHasMedia().getValue())) {
            // The notification may outlive the final media item briefly. In that case just open
            // the app normally, as requested.
            return;
        }

        NavController controller = getActiveNavController();
        if (controller == null || controller.getCurrentDestination() == null) return;
        if (controller.getCurrentDestination().getId() == R.id.nowPlayingFragment) return;
        if (controller.getGraph().findNode(R.id.nowPlayingFragment) != null) {
            controller.navigate(R.id.nowPlayingFragment);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleLaunchIntent(intent);
    }

    private void setupTabHosts(Bundle savedInstanceState) {
        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction createTx = fm.beginTransaction().setReorderingAllowed(true);

        NavHostFragment music = findOrCreateHost(fm, createTx, TAG_MUSIC, R.navigation.nav_music);
        NavHostFragment radio = findOrCreateHost(fm, createTx, TAG_RADIO, R.navigation.nav_radio);
        NavHostFragment games = findOrCreateHost(fm, createTx, TAG_GAMES, R.navigation.nav_games);
        NavHostFragment settings = findOrCreateHost(fm, createTx, TAG_SETTINGS, R.navigation.nav_settings);
        createTx.commitNow();

        tabHosts.clear();
        tabHosts.put(R.id.musicFragment, music);
        tabHosts.put(R.id.radioFragment, radio);
        tabHosts.put(R.id.gamesFragment, games);
        tabHosts.put(R.id.settingsFragment, settings);

        int requestedTab = savedInstanceState != null
                ? savedInstanceState.getInt(STATE_ACTIVE_TAB, resolveConfiguredStartTab())
                : resolveConfiguredStartTab();
        activeTabId = isTabVisibleInMenu(requestedTab) ? requestedTab : firstVisibleTab();

        FragmentTransaction visibilityTx = fm.beginTransaction().setReorderingAllowed(true);
        for (Map.Entry<Integer, NavHostFragment> entry : tabHosts.entrySet()) {
            NavHostFragment host = entry.getValue();
            if (entry.getKey() == activeTabId) {
                visibilityTx.show(host);
                visibilityTx.setMaxLifecycle(host, Lifecycle.State.RESUMED);
                visibilityTx.setPrimaryNavigationFragment(host);
            } else {
                visibilityTx.hide(host);
                visibilityTx.setMaxLifecycle(host, Lifecycle.State.STARTED);
            }
        }
        visibilityTx.commitNow();

        for (Map.Entry<Integer, NavHostFragment> entry : tabHosts.entrySet()) {
            final int tabId = entry.getKey();
            entry.getValue().getNavController().addOnDestinationChangedListener((controller, destination, arguments) -> {
                if (tabId == activeTabId) {
                    Log.i(LOG_TAB, "active destination tab=" + tabName(tabId)
                            + " destination=" + destination.getDisplayName());
                    renderChrome();
                }
            });
        }

        binding.bottomNavigation.setSelectedItemId(activeTabId);
        Log.i(LOG_TAB, "hosts ready active=" + tabName(activeTabId));
    }

    private NavHostFragment findOrCreateHost(FragmentManager fm, FragmentTransaction tx,
                                              String tag, int navGraphRes) {
        Fragment existing = fm.findFragmentByTag(tag);
        if (existing instanceof NavHostFragment) return (NavHostFragment) existing;
        NavHostFragment host = NavHostFragment.create(navGraphRes);
        tx.add(R.id.tabHostContainer, host, tag);
        return host;
    }

    private void setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener(item -> {
            if (suppressBottomNavCallback) return true;
            int target = item.getItemId();
            Log.i(LOG_TAB, "tap source=" + tabName(activeTabId) + " target=" + tabName(target)
                    + " activeDestination=" + currentDestinationName());
            boolean switched = switchTabInternal(target);
            Log.i(LOG_TAB, "tap result=" + switched + " active=" + tabName(activeTabId)
                    + " activeDestination=" + currentDestinationName());
            return switched;
        });
        binding.bottomNavigation.setOnItemReselectedListener(item ->
                Log.i(LOG_TAB, "reselect tab=" + tabName(item.getItemId())
                        + " destination=" + currentDestinationName()));
        binding.bottomNavigation.setSelectedItemId(activeTabId);
    }

    /** Switches top-level tabs without navigating inside another tab's back stack. */
    public boolean openTab(int tabId) {
        boolean switched = switchTabInternal(tabId);
        if (switched && binding != null && binding.bottomNavigation.getSelectedItemId() != tabId) {
            suppressBottomNavCallback = true;
            binding.bottomNavigation.setSelectedItemId(tabId);
            suppressBottomNavCallback = false;
        }
        return switched;
    }

    private boolean switchTabInternal(int tabId) {
        NavHostFragment target = tabHosts.get(tabId);
        if (target == null || !isTabVisibleInMenu(tabId)) {
            Log.w(LOG_TAB, "openTab rejected target=" + tabName(tabId));
            return false;
        }
        if (tabId == activeTabId) {
            renderChrome();
            return true;
        }

        NavHostFragment current = tabHosts.get(activeTabId);
        FragmentTransaction tx = getSupportFragmentManager().beginTransaction().setReorderingAllowed(true);
        if (current != null) {
            tx.hide(current);
            tx.setMaxLifecycle(current, Lifecycle.State.STARTED);
        }
        tx.show(target);
        tx.setMaxLifecycle(target, Lifecycle.State.RESUMED);
        tx.setPrimaryNavigationFragment(target);
        tx.commitNow();

        activeTabId = tabId;
        renderChrome();
        Log.i(LOG_TAB, "switched active=" + tabName(activeTabId)
                + " destination=" + currentDestinationName());
        return true;
    }

    private void openNowPlayingFromMiniPlayer() {
        int destinationTab = activeTabId;
        NavController controller = getActiveNavController();
        if (controller == null || controller.getGraph().findNode(R.id.nowPlayingFragment) == null) {
            boolean isRadio = playerViewModel != null && Boolean.TRUE.equals(playerViewModel.getRadioMedia().getValue());
            destinationTab = isRadio ? R.id.radioFragment : R.id.musicFragment;
            if (!openTab(destinationTab)) return;
            controller = getActiveNavController();
        }
        if (controller != null && controller.getCurrentDestination() != null
                && controller.getCurrentDestination().getId() != R.id.nowPlayingFragment) {
            controller.navigate(R.id.nowPlayingFragment);
        }
    }

    public NavController getActiveNavController() {
        NavHostFragment host = tabHosts.get(activeTabId);
        return host == null ? null : host.getNavController();
    }

    private String currentDestinationName() {
        NavController controller = getActiveNavController();
        return controller == null || controller.getCurrentDestination() == null
                ? "none" : String.valueOf(controller.getCurrentDestination().getDisplayName());
    }

    private int resolveConfiguredStartTab() {
        String page = AppPreferences.getStartPage(this);
        if (AppPreferences.START_RADIO.equals(page)) return R.id.radioFragment;
                if (AppPreferences.START_SETTINGS.equals(page)) return R.id.settingsFragment;
        return R.id.musicFragment;
    }

    private int firstVisibleTab() {
        Menu menu = binding.bottomNavigation.getMenu();
        return menu.size() > 0 ? menu.getItem(0).getItemId() : R.id.settingsFragment;
    }

    private boolean isTabVisibleInMenu(int tabId) {
        return binding != null && binding.bottomNavigation.getMenu().findItem(tabId) != null;
    }

    private String tabName(int id) {
        if (id == R.id.musicFragment) return "MUSIC";
        if (id == R.id.radioFragment) return "RADIO";
        if (id == R.id.gamesFragment) return "GAMES";
        if (id == R.id.settingsFragment) return "SETTINGS";
        return "UNKNOWN(" + id + ")";
    }

    private void configureSystemBars() {
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(), binding.getRoot());
        controller.setAppearanceLightStatusBars(!isDarkTheme());
        controller.setAppearanceLightNavigationBars(!isDarkTheme());
    }

    private void captureBottomNavigationBaseMetrics() {
        bottomNavigationBaseHeight = getResources().getDimensionPixelSize(R.dimen.bottom_nav_height);
        bottomNavigationBasePaddingLeft = binding.bottomNavigation.getPaddingLeft();
        bottomNavigationBasePaddingTop = binding.bottomNavigation.getPaddingTop();
        bottomNavigationBasePaddingRight = binding.bottomNavigation.getPaddingRight();
        bottomNavigationBasePaddingBottom = binding.bottomNavigation.getPaddingBottom();
    }

    private void applyInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (view, windowInsets) -> {
            Insets bars = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            Insets navigationBars = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars());
            Insets stableNavigationBars = windowInsets.getInsetsIgnoringVisibility(
                    WindowInsetsCompat.Type.navigationBars());
            int navigationBottom = Math.max(navigationBars.bottom, stableNavigationBars.bottom);
            systemBarInsets = Insets.of(bars.left, bars.top, bars.right, navigationBottom);
            applyTabHostInsets(isFullPlayerDestination(), systemBarInsets);

            // Edge-to-edge robusto: la barra ocupa el área del sistema y desplaza sus
            // botones hacia arriba con padding. Evita que algunos ROM (MIUI/HyperOS)
            // dejen la navegación de la app detrás de la barra de navegación del sistema.
            ConstraintLayout.LayoutParams bottomParams =
                    (ConstraintLayout.LayoutParams) binding.bottomNavigation.getLayoutParams();
            bottomParams.height = bottomNavigationBaseHeight + navigationBottom;
            bottomParams.bottomMargin = 0;
            bottomParams.leftMargin = bars.left;
            bottomParams.rightMargin = bars.right;
            binding.bottomNavigation.setLayoutParams(bottomParams);
            binding.bottomNavigation.setPadding(
                    bottomNavigationBasePaddingLeft,
                    bottomNavigationBasePaddingTop,
                    bottomNavigationBasePaddingRight,
                    bottomNavigationBasePaddingBottom + navigationBottom);

            return windowInsets;
        });
        ViewCompat.requestApplyInsets(binding.getRoot());
    }

    private boolean isFullPlayerDestination() {
        NavController controller = getActiveNavController();
        return controller != null && controller.getCurrentDestination() != null
                && controller.getCurrentDestination().getId() == R.id.nowPlayingFragment;
    }

    private void applyTabHostInsets(boolean fullPlayer, @NonNull Insets bars) {
        if (binding == null) return;
        ConstraintLayout.LayoutParams hostParams =
                (ConstraintLayout.LayoutParams) binding.tabHostContainer.getLayoutParams();
        int topMargin = fullPlayer ? 0 : bars.top;
        if (hostParams.topMargin != topMargin
                || hostParams.leftMargin != bars.left
                || hostParams.rightMargin != bars.right) {
            hostParams.topMargin = topMargin;
            hostParams.leftMargin = bars.left;
            hostParams.rightMargin = bars.right;
            binding.tabHostContainer.setLayoutParams(hostParams);
            ViewCompat.requestApplyInsets(binding.tabHostContainer);
        }
    }

    private void applyVisualPreferences() {
        boolean showSubtitle = AppPreferences.showMiniSubtitle(this);
        binding.miniPlayerSubtitle.setVisibility(showSubtitle ? View.VISIBLE : View.GONE);
        binding.miniPlayerTitle.setTypeface(AppTypography.get(this, android.graphics.Typeface.BOLD));
        binding.miniPlayerSubtitle.setTypeface(AppTypography.get(this, android.graphics.Typeface.NORMAL));
        applyMiniPlayerBackground();

        int accent = AppPreferences.getPlayerAccentColor(this);
        binding.miniPlayPause.setBackgroundTintList(ColorStateList.valueOf(accent));
        binding.miniPlayPause.setIconTint(ColorStateList.valueOf(contrastOn(accent)));
        binding.appBackground.setLightMode(!isDarkTheme());
        binding.appBackground.setPalette(
                AppPreferences.getAppBackgroundColorMode(this),
                AppPreferences.getAppBackgroundColor1(this),
                AppPreferences.getAppBackgroundColor2(this),
                AppPreferences.getAppBackgroundColor3(this));
        binding.appBackground.setScene(AppPreferences.getAppBackground(this), accent,
                AppPreferences.getAnimationMultiplier(this) * 0.75f);
        UiPalette palette = UiPalette.fallback(this);
        int navSurface = ColorUtils.setAlphaComponent(palette.surface, 168);
        int activeContent = ColorUtils.calculateContrast(accent, palette.surface) >= UiPalette.MIN_TEXT_CONTRAST
                ? accent : palette.onSurface;
        int[][] states = new int[][] { new int[] { android.R.attr.state_checked }, new int[] {} };
        ColorStateList navTint = new ColorStateList(states, new int[] { activeContent, palette.onSurface });
        binding.bottomNavigation.setBackgroundTintList(ColorStateList.valueOf(navSurface));
        binding.bottomNavigation.setItemIconTintList(navTint);
        binding.bottomNavigation.setItemTextColor(navTint);
        binding.bottomNavigation.setItemActiveIndicatorEnabled(true);
        binding.bottomNavigation.setItemActiveIndicatorColor(
                ColorStateList.valueOf(ColorUtils.setAlphaComponent(accent, 36)));
        binding.bottomNavigation.setElevation(0f);
    }

    private void applyMiniPlayerBackground() {
        if (binding == null) return;

        if (!AppPreferences.miniPlayerUsesPlayerBackground(this)) {
            int surface = MaterialColors.getColor(binding.miniPlayerCard, com.google.android.material.R.attr.colorSurfaceContainerLow);
            int onSurface = MaterialColors.getColor(binding.miniPlayerCard, com.google.android.material.R.attr.colorOnSurface);
            int onSurfaceVariant = MaterialColors.getColor(binding.miniPlayerCard, com.google.android.material.R.attr.colorOnSurfaceVariant);
            binding.miniPlayerBackground.setVisibility(View.GONE);
            binding.miniPlayerCard.setCardBackgroundColor(surface);
            binding.miniPlayerScrim.setBackgroundColor(Color.TRANSPARENT);
            binding.miniPlayerTitle.setTextColor(onSurface);
            binding.miniPlayerSubtitle.setTextColor(onSurfaceVariant);
            ColorStateList controls = ColorStateList.valueOf(onSurface);
            binding.miniPrevious.setImageTintList(controls);
            binding.miniNext.setImageTintList(controls);
            return;
        }

        binding.miniPlayerCard.setCardBackgroundColor(Color.TRANSPARENT);
        binding.miniPlayerBackground.setVisibility(View.VISIBLE);
        int accent = AppPreferences.getPlayerAccentColor(this);
        String mode = AppPreferences.getPlayerBackgroundColorMode(this);
        binding.miniPlayerBackground.setLightMode(false);
        binding.miniPlayerBackground.setScene(
                AppPreferences.BACKGROUND_NONE, accent, AppPreferences.getAnimationMultiplier(this));

        if (AppPreferences.PLAYER_BACKGROUND_COLOR_ARTWORK.equals(mode)) {
            if (miniPlayerArtworkPalette == null && binding.miniArtwork.getDrawable() instanceof BitmapDrawable) {
                miniPlayerArtworkPalette = UiPalette.fromBitmap(
                        this, "mini:" + miniArtworkUri,
                        ((BitmapDrawable) binding.miniArtwork.getDrawable()).getBitmap());
            }
            UiPalette palette = miniPlayerArtworkPalette == null
                    ? UiPalette.fallback(this) : miniPlayerArtworkPalette;
            int middle = ColorUtils.blendARGB(palette.surface, palette.accent, 0.5f);
            binding.miniPlayerBackground.setPalette(
                    AppPreferences.APP_BACKGROUND_COLOR_GRADIENT_3,
                    palette.surface, middle, palette.accent);
        } else {
            binding.miniPlayerBackground.setPalette(
                    mode,
                    AppPreferences.getPlayerBackgroundColor1(this),
                    AppPreferences.getPlayerBackgroundColor2(this),
                    AppPreferences.getPlayerBackgroundColor3(this));
        }

        // Worst-case readability is calculated against white. The resulting dark scrim keeps
        // white content at AA contrast over every solid/gradient/rainbow color underneath.
        UiPalette.Readability readability = UiPalette.resolveText(Color.WHITE, Color.WHITE);
        binding.miniPlayerScrim.setBackgroundColor(readability.scrimColor);
        ColorStateList contentTint = ColorStateList.valueOf(readability.textColor);
        binding.miniPlayerTitle.setTextColor(readability.textColor);
        binding.miniPlayerSubtitle.setTextColor(readability.textColor);
        binding.miniPrevious.setImageTintList(contentTint);
        binding.miniNext.setImageTintList(contentTint);
    }

    private void installTypographyPropagation() {
        typographyLifecycleCallbacks = new FragmentManager.FragmentLifecycleCallbacks() {
            @Override
            public void onFragmentViewCreated(@NonNull FragmentManager fm, @NonNull Fragment fragment,
                                              @NonNull View view, Bundle savedInstanceState) {
                AppTypography.applyToViewTree(view);
            }

            @Override
            public void onFragmentResumed(@NonNull FragmentManager fm, @NonNull Fragment fragment) {
                View view = fragment.getView();
                if (view != null) AppTypography.applyToViewTree(view);
            }
        };
        getSupportFragmentManager().registerFragmentLifecycleCallbacks(typographyLifecycleCallbacks, true);
        AppTypography.applyToViewTree(binding.getRoot());
    }

    private boolean isVisualPreference(String key) {
        return AppPreferences.KEY_APP_BACKGROUND.equals(key)
                || AppPreferences.KEY_PLAYER_BACKGROUND.equals(key)
                || AppPreferences.KEY_PLAYER_ACCENT.equals(key)
                || AppPreferences.KEY_CUSTOM_ACCENT_COLOR.equals(key)
                || AppPreferences.KEY_USE_CUSTOM_ACCENT.equals(key)
                || AppPreferences.KEY_PLAYER_ANIMATION.equals(key)
                || AppPreferences.KEY_PLAYER_TYPOGRAPHY.equals(key)
                || AppPreferences.KEY_PLAYER_STYLE.equals(key)
                || AppPreferences.KEY_ARTWORK_SHAPE.equals(key)
                || AppPreferences.KEY_LARGE_ARTWORK.equals(key)
                || AppPreferences.KEY_CENTER_PLAYER_INFO.equals(key)
                || AppPreferences.KEY_SHOW_MINI_SUBTITLE.equals(key)
                || AppPreferences.KEY_MINI_PLAYER_USE_PLAYER_BACKGROUND.equals(key)
                || AppPreferences.KEY_APP_BACKGROUND_COLOR_MODE.equals(key)
                || AppPreferences.KEY_APP_BACKGROUND_COLOR_1.equals(key)
                || AppPreferences.KEY_APP_BACKGROUND_COLOR_2.equals(key)
                || AppPreferences.KEY_APP_BACKGROUND_COLOR_3.equals(key)
                || AppPreferences.KEY_PLAYER_BACKGROUND_COLOR_MODE.equals(key)
                || AppPreferences.KEY_PLAYER_BACKGROUND_COLOR_1.equals(key)
                || AppPreferences.KEY_PLAYER_BACKGROUND_COLOR_2.equals(key)
                || AppPreferences.KEY_PLAYER_BACKGROUND_COLOR_3.equals(key)
                || AppPreferences.KEY_SHOW_BOTTOM_NAV_NOW_PLAYING.equals(key)
                || PowerSaverManager.isPowerSaverPreference(key);
    }

    private int contrastOn(int color) {
        double luminance = (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255.0;
        return luminance > 0.66 ? Color.rgb(18, 20, 25) : Color.WHITE;
    }

    private boolean isDarkTheme() {
        int mask = getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return mask == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    private void applyBottomNavigationPreferences() {
        Menu menu = binding.bottomNavigation.getMenu();
        menu.clear();
        String[] order = AppPreferences.getBottomNavOrder(this).split(",");
        for (String item : order) {
            if (AppPreferences.NAV_MUSIC.equals(item) && AppPreferences.showMusicNav(this)) {
                menu.add(Menu.NONE, R.id.musicFragment, Menu.NONE, R.string.music).setIcon(R.drawable.ic_music);
            } else if (AppPreferences.NAV_RADIO.equals(item) && AppPreferences.showRadioNav(this)) {
                menu.add(Menu.NONE, R.id.radioFragment, Menu.NONE, R.string.radio).setIcon(R.drawable.ic_radio);
            } else if (AppPreferences.NAV_GAMES.equals(item) && AppPreferences.showGamesNav(this)) {
                menu.add(Menu.NONE, R.id.gamesFragment, Menu.NONE, R.string.games).setIcon(R.drawable.ic_gamepad);
            }
        }
        menu.add(Menu.NONE, R.id.settingsFragment, Menu.NONE, R.string.settings).setIcon(R.drawable.ic_settings);
    }

    private void renderChrome() {
        if (binding == null) return;
        NavController controller = getActiveNavController();
        boolean fullPlayer = isFullPlayerDestination();
        boolean gameInProgress = controller != null && controller.getCurrentDestination() != null
                && (controller.getCurrentDestination().getId() == R.id.guessSongFragment
                || controller.getCurrentDestination().getId() == R.id.intruderGameFragment);
        boolean showBottomNav = !fullPlayer || AppPreferences.showBottomNavInNowPlaying(this);
        binding.bottomNavigation.setVisibility(showBottomNav ? View.VISIBLE : View.GONE);
        if (showBottomNav) {
            binding.bottomNavigation.setTranslationY(0f);
            binding.bottomNavigation.bringToFront();
        }
        binding.miniPlayerCard.setVisibility(!fullPlayer && !gameInProgress && hasMedia ? View.VISIBLE : View.GONE);
        if (binding.miniPlayerCard.getVisibility() == View.VISIBLE) {
            binding.miniPlayerCard.bringToFront();
            binding.bottomNavigation.bringToFront();
        }
        applyTabHostInsets(fullPlayer, systemBarInsets);

        WindowInsetsControllerCompat barsController =
                WindowCompat.getInsetsController(getWindow(), binding.getRoot());
        barsController.setAppearanceLightStatusBars(fullPlayer ? false : !isDarkTheme());
    }

    public void refreshChrome() {
        if (binding == null) return;
        applyVisualPreferences();
        renderChrome();
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyVisualPreferences();
        renderChrome();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putInt(STATE_ACTIVE_TAB, activeTabId);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        if (visualPreferenceListener != null) {
            AppPreferences.prefs(this).unregisterOnSharedPreferenceChangeListener(visualPreferenceListener);
            visualPreferenceListener = null;
        }
        if (typographyLifecycleCallbacks != null) {
            getSupportFragmentManager().unregisterFragmentLifecycleCallbacks(typographyLifecycleCallbacks);
            typographyLifecycleCallbacks = null;
        }
        binding = null;
        super.onDestroy();
    }

    private boolean hasAudioPermission() {
        String permission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                ? Manifest.permission.READ_MEDIA_AUDIO
                : Manifest.permission.READ_EXTERNAL_STORAGE;
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED;
    }
}
