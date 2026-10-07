package com.appincreible.musicplayer.power;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.BatteryManager;
import android.os.Build;
import android.os.PowerManager;

import com.appincreible.musicplayer.player.audio.AudioPreferences;
import com.appincreible.musicplayer.player.audio.NormalizationAnalysisManager;
import com.appincreible.musicplayer.themes.AppPreferences;
import com.appincreible.musicplayer.themes.ListAppearance;

/** Central policy for the optional super battery saver mode. */
public final class PowerSaverManager {

    public static final String KEY_ENABLED = "super_power_saver_enabled";
    public static final String KEY_AUTO_BATTERY = "super_power_saver_auto_battery";
    public static final String KEY_BATTERY_THRESHOLD = "super_power_saver_battery_threshold";
    public static final String KEY_AUTO_SYSTEM = "super_power_saver_auto_system";
    public static final String KEY_FORCE_DARK_BACKGROUND = "super_power_saver_force_dark_background";
    public static final String KEY_RUNTIME_ACTIVE = "super_power_saver_runtime_active";

    private static final String KEY_SNAPSHOT_VALID = "super_power_saver_snapshot_valid";
    private static final String SNAP_STYLE = "super_power_saver_prev_style";
    private static final String SNAP_ANIMATION = "super_power_saver_prev_animation";
    private static final String SNAP_PLAYER_BACKGROUND = "super_power_saver_prev_player_background";
    private static final String SNAP_APP_BACKGROUND = "super_power_saver_prev_app_background";
    private static final String SNAP_APP_COLOR_MODE = "super_power_saver_prev_app_color_mode";
    private static final String SNAP_APP_COLOR_1 = "super_power_saver_prev_app_color_1";
    private static final String SNAP_APP_COLOR_2 = "super_power_saver_prev_app_color_2";
    private static final String SNAP_APP_COLOR_3 = "super_power_saver_prev_app_color_3";
    private static final String SNAP_LIST_TRANSPARENT = "super_power_saver_prev_list_transparent";
    private static final String SNAP_CROSSFADE = "super_power_saver_prev_crossfade";
    private static final String SNAP_SKIP_SAME_ALBUM = "super_power_saver_prev_skip_same_album";
    private static final String SNAP_GAPLESS = "super_power_saver_prev_gapless";
    private static final String SNAP_NORMALIZATION = "super_power_saver_prev_normalization";
    private static final String SNAP_DJ = "super_power_saver_prev_dj";

    private static final int DEFAULT_THRESHOLD = 20;
    private static final Object LOCK = new Object();
    private static boolean receiverRegistered;

    private static final BroadcastReceiver powerReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            refreshState(context.getApplicationContext());
        }
    };

    private PowerSaverManager() { }

    public static void startMonitoring(Context context) {
        Context app = context.getApplicationContext();
        synchronized (LOCK) {
            if (!receiverRegistered) {
                IntentFilter filter = new IntentFilter();
                filter.addAction(Intent.ACTION_BATTERY_CHANGED);
                filter.addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED);
                if (Build.VERSION.SDK_INT >= 33) {
                    app.registerReceiver(powerReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
                } else {
                    app.registerReceiver(powerReceiver, filter);
                }
                receiverRegistered = true;
            }
        }
        refreshState(app);
    }

    public static boolean isActive(Context context) {
        return requestedActive(context.getApplicationContext());
    }

    public static boolean isManualEnabled(Context context) {
        return AppPreferences.prefs(context).getBoolean(KEY_ENABLED, false);
    }

    public static boolean isAutoBatteryEnabled(Context context) {
        return AppPreferences.prefs(context).getBoolean(KEY_AUTO_BATTERY, false);
    }

    public static int batteryThreshold(Context context) {
        return clamp(AppPreferences.prefs(context).getInt(KEY_BATTERY_THRESHOLD, DEFAULT_THRESHOLD), 5, 50);
    }

    public static boolean isAutoSystemEnabled(Context context) {
        return AppPreferences.prefs(context).getBoolean(KEY_AUTO_SYSTEM, false);
    }

    public static boolean forceDarkBackground(Context context) {
        return AppPreferences.prefs(context).getBoolean(KEY_FORCE_DARK_BACKGROUND, true);
    }

    public static void setManualEnabled(Context context, boolean enabled) {
        AppPreferences.prefs(context).edit().putBoolean(KEY_ENABLED, enabled).commit();
        refreshState(context.getApplicationContext());
    }

    public static void setAutoBatteryEnabled(Context context, boolean enabled) {
        AppPreferences.prefs(context).edit().putBoolean(KEY_AUTO_BATTERY, enabled).commit();
        refreshState(context.getApplicationContext());
    }

    public static void setBatteryThreshold(Context context, int threshold) {
        AppPreferences.prefs(context).edit().putInt(KEY_BATTERY_THRESHOLD, clamp(threshold, 5, 50)).commit();
        refreshState(context.getApplicationContext());
    }

    public static void setAutoSystemEnabled(Context context, boolean enabled) {
        AppPreferences.prefs(context).edit().putBoolean(KEY_AUTO_SYSTEM, enabled).commit();
        refreshState(context.getApplicationContext());
    }

    public static void setForceDarkBackground(Context context, boolean enabled) {
        AppPreferences.prefs(context).edit().putBoolean(KEY_FORCE_DARK_BACKGROUND, enabled).commit();
    }

    public static int batteryPercent(Context context) {
        Intent battery = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (battery == null) return 100;
        int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        if (level < 0 || scale <= 0) return 100;
        return Math.max(0, Math.min(100, Math.round(level * 100f / scale)));
    }

    public static boolean systemPowerSaveMode(Context context) {
        PowerManager power = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        return power != null && power.isPowerSaveMode();
    }

    public static boolean isPowerSaverPreference(String key) {
        return KEY_ENABLED.equals(key)
                || KEY_AUTO_BATTERY.equals(key)
                || KEY_BATTERY_THRESHOLD.equals(key)
                || KEY_AUTO_SYSTEM.equals(key)
                || KEY_FORCE_DARK_BACKGROUND.equals(key)
                || KEY_RUNTIME_ACTIVE.equals(key);
    }

    private static boolean requestedActive(Context context) {
        SharedPreferences prefs = AppPreferences.prefs(context);
        if (prefs.getBoolean(KEY_ENABLED, false)) return true;
        if (prefs.getBoolean(KEY_AUTO_SYSTEM, false) && systemPowerSaveMode(context)) return true;
        return prefs.getBoolean(KEY_AUTO_BATTERY, false)
                && batteryPercent(context) <= clamp(prefs.getInt(KEY_BATTERY_THRESHOLD, DEFAULT_THRESHOLD), 5, 50);
    }

    private static void refreshState(Context context) {
        SharedPreferences prefs = AppPreferences.prefs(context);
        boolean active = requestedActive(context);
        boolean previous = prefs.getBoolean(KEY_RUNTIME_ACTIVE, false);
        if (active == previous) return;

        if (active) {
            saveSnapshot(context);
            NormalizationAnalysisManager.get(context).cancelForPowerSaver();
            prefs.edit().putBoolean(KEY_RUNTIME_ACTIVE, true).commit();
        } else {
            restoreSnapshot(context);
            prefs.edit().putBoolean(KEY_RUNTIME_ACTIVE, false).commit();
        }
    }

    private static void saveSnapshot(Context context) {
        SharedPreferences app = AppPreferences.prefs(context);
        if (app.getBoolean(KEY_SNAPSHOT_VALID, false)) return;
        SharedPreferences audio = AudioPreferences.prefs(context);
        app.edit()
                .putBoolean(KEY_SNAPSHOT_VALID, true)
                .putString(SNAP_STYLE, app.getString(AppPreferences.KEY_PLAYER_STYLE, AppPreferences.STYLE_CLASSIC))
                .putString(SNAP_ANIMATION, app.getString(AppPreferences.KEY_PLAYER_ANIMATION, AppPreferences.ANIMATION_SOFT))
                .putString(SNAP_PLAYER_BACKGROUND, app.getString(AppPreferences.KEY_PLAYER_BACKGROUND, AppPreferences.BACKGROUND_AURORA))
                .putString(SNAP_APP_BACKGROUND, app.getString(AppPreferences.KEY_APP_BACKGROUND, AppPreferences.BACKGROUND_DARK))
                .putString(SNAP_APP_COLOR_MODE, app.getString(AppPreferences.KEY_APP_BACKGROUND_COLOR_MODE, AppPreferences.APP_BACKGROUND_COLOR_ACCENT))
                .putInt(SNAP_APP_COLOR_1, app.getInt(AppPreferences.KEY_APP_BACKGROUND_COLOR_1, 0xFF7C4DFF))
                .putInt(SNAP_APP_COLOR_2, app.getInt(AppPreferences.KEY_APP_BACKGROUND_COLOR_2, 0xFF24C7FF))
                .putInt(SNAP_APP_COLOR_3, app.getInt(AppPreferences.KEY_APP_BACKGROUND_COLOR_3, 0xFFFF5FA2))
                .putBoolean(SNAP_LIST_TRANSPARENT, app.getBoolean(ListAppearance.KEY_LIST_TRANSPARENT, false))
                .putBoolean(SNAP_CROSSFADE, audio.getBoolean(AudioPreferences.KEY_CROSSFADE_ENABLED, false))
                .putBoolean(SNAP_SKIP_SAME_ALBUM, audio.getBoolean(AudioPreferences.KEY_SKIP_SAME_ALBUM, true))
                .putBoolean(SNAP_GAPLESS, audio.getBoolean(AudioPreferences.KEY_GAPLESS_ENABLED, true))
                .putBoolean(SNAP_NORMALIZATION, audio.getBoolean(AudioPreferences.KEY_NORMALIZATION_ENABLED, false))
                .putBoolean(SNAP_DJ, audio.getBoolean(AudioPreferences.KEY_DJ_ENABLED, false))
                .commit();
    }

    private static void restoreSnapshot(Context context) {
        SharedPreferences app = AppPreferences.prefs(context);
        if (!app.getBoolean(KEY_SNAPSHOT_VALID, false)) return;
        SharedPreferences audio = AudioPreferences.prefs(context);

        audio.edit()
                .putBoolean(AudioPreferences.KEY_CROSSFADE_ENABLED, app.getBoolean(SNAP_CROSSFADE, false))
                .putBoolean(AudioPreferences.KEY_SKIP_SAME_ALBUM, app.getBoolean(SNAP_SKIP_SAME_ALBUM, true))
                .putBoolean(AudioPreferences.KEY_GAPLESS_ENABLED, app.getBoolean(SNAP_GAPLESS, true))
                .putBoolean(AudioPreferences.KEY_NORMALIZATION_ENABLED, app.getBoolean(SNAP_NORMALIZATION, false))
                .putBoolean(AudioPreferences.KEY_DJ_ENABLED, app.getBoolean(SNAP_DJ, false))
                .commit();

        app.edit()
                .putString(AppPreferences.KEY_PLAYER_STYLE, app.getString(SNAP_STYLE, AppPreferences.STYLE_CLASSIC))
                .putString(AppPreferences.KEY_PLAYER_ANIMATION, app.getString(SNAP_ANIMATION, AppPreferences.ANIMATION_SOFT))
                .putString(AppPreferences.KEY_PLAYER_BACKGROUND, app.getString(SNAP_PLAYER_BACKGROUND, AppPreferences.BACKGROUND_AURORA))
                .putString(AppPreferences.KEY_APP_BACKGROUND, app.getString(SNAP_APP_BACKGROUND, AppPreferences.BACKGROUND_DARK))
                .putString(AppPreferences.KEY_APP_BACKGROUND_COLOR_MODE, app.getString(SNAP_APP_COLOR_MODE, AppPreferences.APP_BACKGROUND_COLOR_ACCENT))
                .putInt(AppPreferences.KEY_APP_BACKGROUND_COLOR_1, app.getInt(SNAP_APP_COLOR_1, 0xFF7C4DFF))
                .putInt(AppPreferences.KEY_APP_BACKGROUND_COLOR_2, app.getInt(SNAP_APP_COLOR_2, 0xFF24C7FF))
                .putInt(AppPreferences.KEY_APP_BACKGROUND_COLOR_3, app.getInt(SNAP_APP_COLOR_3, 0xFFFF5FA2))
                .putBoolean(ListAppearance.KEY_LIST_TRANSPARENT, app.getBoolean(SNAP_LIST_TRANSPARENT, false))
                .putBoolean(KEY_SNAPSHOT_VALID, false)
                .commit();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
