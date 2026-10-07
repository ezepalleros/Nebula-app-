package com.appincreible.musicplayer;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.appincreible.musicplayer.databinding.ActivityPermissionSetupBinding;
import com.appincreible.musicplayer.themes.AppPreferences;
import com.google.android.material.color.MaterialColors;

import java.util.ArrayList;
import java.util.List;

public class PermissionSetupActivity extends AppCompatActivity {

    public static final String EXTRA_FORWARD_ACTION = "permission_setup_forward_action";

    private ActivityPermissionSetupBinding binding;
    private boolean launchedMain;
    private String forwardAction;

    private final ActivityResultLauncher<String[]> permissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), result -> refreshPermissionState());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppPreferences.applySavedTheme(this);
        super.onCreate(savedInstanceState);

        forwardAction = getIntent() == null ? null : getIntent().getStringExtra(EXTRA_FORWARD_ACTION);
        if (hasRequiredPermissions(this)) {
            openMainIfReady();
            return;
        }

        binding = ActivityPermissionSetupBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.grantPermissionsButton.setOnClickListener(v -> requestMissingPermissions());
        binding.openSettingsButton.setOnClickListener(v -> openAppSettings());
        refreshPermissionState();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (binding != null) refreshPermissionState();
    }

    @Override
    protected void onDestroy() {
        binding = null;
        super.onDestroy();
    }

    public static boolean hasRequiredPermissions(@NonNull Context context) {
        return hasAudioPermission(context) && hasNotificationPermission(context);
    }

    private static boolean hasAudioPermission(@NonNull Context context) {
        String permission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                ? Manifest.permission.READ_MEDIA_AUDIO
                : Manifest.permission.READ_EXTERNAL_STORAGE;
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED;
    }

    private static boolean hasNotificationPermission(@NonNull Context context) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestMissingPermissions() {
        List<String> missing = new ArrayList<>(2);
        String audioPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                ? Manifest.permission.READ_MEDIA_AUDIO
                : Manifest.permission.READ_EXTERNAL_STORAGE;
        if (ContextCompat.checkSelfPermission(this, audioPermission) != PackageManager.PERMISSION_GRANTED) {
            missing.add(audioPermission);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            missing.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (missing.isEmpty()) {
            openMainIfReady();
            return;
        }
        permissionLauncher.launch(missing.toArray(new String[0]));
    }

    private void refreshPermissionState() {
        if (binding == null) return;
        boolean audioGranted = hasAudioPermission(this);
        boolean notificationsGranted = hasNotificationPermission(this);
        int accent = MaterialColors.getColor(binding.getRoot(), com.google.android.material.R.attr.colorPrimary);
        int muted = MaterialColors.getColor(binding.getRoot(), com.google.android.material.R.attr.colorOnSurfaceVariant);

        binding.audioPermissionStatus.setText(audioGranted
                ? R.string.permission_status_granted : R.string.permission_status_required);
        binding.audioPermissionStatus.setTextColor(audioGranted ? accent : muted);
        binding.notificationPermissionStatus.setText(notificationsGranted
                ? R.string.permission_status_granted : R.string.permission_status_required);
        binding.notificationPermissionStatus.setTextColor(notificationsGranted ? accent : muted);

        binding.grantPermissionsButton.setEnabled(!(audioGranted && notificationsGranted));
        if (audioGranted && notificationsGranted) openMainIfReady();
    }

    private void openAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", getPackageName(), null));
        startActivity(intent);
    }

    private void openMainIfReady() {
        if (launchedMain || !hasRequiredPermissions(this)) return;
        launchedMain = true;
        Intent intent = new Intent(this, MainActivity.class);
        if (MainActivity.ACTION_OPEN_NOW_PLAYING.equals(forwardAction)) intent.setAction(forwardAction);
        startActivity(intent);
        finish();
    }
}
