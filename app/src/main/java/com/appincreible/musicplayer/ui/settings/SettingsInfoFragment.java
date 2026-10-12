package com.appincreible.musicplayer.ui.settings;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.appincreible.musicplayer.BuildConfig;
import com.appincreible.musicplayer.R;
import com.appincreible.musicplayer.databinding.FragmentSettingsInfoBinding;
import com.appincreible.musicplayer.themes.ListAppearance;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class SettingsInfoFragment extends Fragment implements ListAppearance.Listener {

    private static final String REPOSITORY_URL = "https://github.com/ezepalleros/Nebula-app-";
    private static final String LATEST_RELEASE_API = "https://api.github.com/repos/ezepalleros/Nebula-app-/releases/latest";
    private static final String DONATION_ALIAS = "eze.palleros";
    private static final String DONATION_CVU = "0000003100067543622083";
    private static final String DONATION_NAME = "Ezequiel German Palleros Ibarra";

    private FragmentSettingsInfoBinding binding;
    private volatile boolean updateCheckInProgress;
    private String latestReleaseUrl;
    private String latestVersion;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentSettingsInfoBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding.infoBackButton.setOnClickListener(v -> Navigation.findNavController(v).navigateUp());
        binding.versionValue.setText("Nebula " + BuildConfig.VERSION_NAME);
        binding.developerValue.setText("Desarrollado por elequiweb");
        binding.donationAliasValue.setText(DONATION_ALIAS);
        binding.donationCvuValue.setText(DONATION_CVU);
        binding.donationNameValue.setText(DONATION_NAME);

        binding.githubCard.setOnClickListener(v -> openUrl(REPOSITORY_URL));
        binding.updateCard.setOnClickListener(v -> {
            if (latestReleaseUrl != null && latestVersion != null
                    && isNewerVersion(BuildConfig.VERSION_NAME, latestVersion)) {
                showUpdateDialog(latestVersion, latestReleaseUrl);
            } else {
                checkForUpdates(true);
            }
        });
        binding.copyAliasButton.setOnClickListener(v -> copy("Alias", DONATION_ALIAS));
        binding.copyCvuButton.setOnClickListener(v -> copy("CVU", DONATION_CVU));

        applyAppearance();
        checkForUpdates(false);
    }

    private void checkForUpdates(boolean userInitiated) {
        if (updateCheckInProgress || binding == null) return;
        updateCheckInProgress = true;
        binding.updateStatus.setText("Buscando actualizaciones…");

        new Thread(() -> {
            String foundVersion = null;
            String foundUrl = null;
            Exception error = null;
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(LATEST_RELEASE_API).openConnection();
                connection.setConnectTimeout(6000);
                connection.setReadTimeout(6000);
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Accept", "application/vnd.github+json");
                connection.setRequestProperty("User-Agent", "Nebula-Android");
                int code = connection.getResponseCode();
                if (code != HttpURLConnection.HTTP_OK) {
                    throw new IOException("GitHub HTTP " + code);
                }
                StringBuilder body = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                        connection.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) body.append(line);
                }
                JSONObject json = new JSONObject(body.toString());
                foundVersion = normalizeVersion(json.optString("tag_name", ""));
                foundUrl = json.optString("html_url", REPOSITORY_URL + "/releases");
                if (foundVersion.isEmpty()) throw new IOException("Release sin versión");
            } catch (Exception e) {
                error = e;
            } finally {
                if (connection != null) connection.disconnect();
            }

            final String version = foundVersion;
            final String releaseUrl = foundUrl;
            final Exception failure = error;
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                updateCheckInProgress = false;
                if (binding == null) return;
                if (failure != null) {
                    binding.updateStatus.setText("No se pudo comprobar ahora");
                    if (userInitiated) {
                        Toast.makeText(requireContext(), "No se pudo comprobar GitHub Releases", Toast.LENGTH_SHORT).show();
                    }
                    return;
                }

                latestVersion = version;
                latestReleaseUrl = releaseUrl;
                if (isNewerVersion(BuildConfig.VERSION_NAME, version)) {
                    binding.updateStatus.setText("Nueva versión disponible: " + version);
                    if (userInitiated) showUpdateDialog(version, releaseUrl);
                } else {
                    binding.updateStatus.setText("Estás al día · " + BuildConfig.VERSION_NAME);
                    if (userInitiated) {
                        Toast.makeText(requireContext(), "Nebula está actualizado", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }, "nebula-update-check").start();
    }

    private void showUpdateDialog(String version, String releaseUrl) {
        if (!isAdded()) return;
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Actualización disponible")
                .setMessage("Nebula " + version + " está disponible en GitHub.")
                .setNegativeButton("Ahora no", null)
                .setPositiveButton("Ver actualización", (dialog, which) -> openUrl(releaseUrl))
                .show();
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (RuntimeException e) {
            Toast.makeText(requireContext(), "No se pudo abrir el enlace", Toast.LENGTH_SHORT).show();
        }
    }

    private void copy(String label, String value) {
        ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) return;
        clipboard.setPrimaryClip(ClipData.newPlainText(label, value));
        Toast.makeText(requireContext(), label + " copiado", Toast.LENGTH_SHORT).show();
    }

    private static String normalizeVersion(String version) {
        if (version == null) return "";
        String value = version.trim();
        if (value.startsWith("v") || value.startsWith("V")) value = value.substring(1);
        return value.trim();
    }

    private static boolean isNewerVersion(String current, String candidate) {
        int[] currentParts = numericParts(normalizeVersion(current));
        int[] candidateParts = numericParts(normalizeVersion(candidate));
        int count = Math.max(currentParts.length, candidateParts.length);
        for (int i = 0; i < count; i++) {
            int a = i < currentParts.length ? currentParts[i] : 0;
            int b = i < candidateParts.length ? candidateParts[i] : 0;
            if (b != a) return b > a;
        }
        return false;
    }

    private static int[] numericParts(String version) {
        String[] raw = version.split("\\.");
        int[] result = new int[raw.length];
        for (int i = 0; i < raw.length; i++) {
            String digits = raw[i].replaceAll("[^0-9].*$", "");
            try {
                result[i] = digits.isEmpty() ? 0 : Integer.parseInt(digits);
            } catch (NumberFormatException ignored) {
                result[i] = 0;
            }
        }
        return result;
    }

    @Override
    public void onResume() {
        super.onResume();
        ListAppearance.addListener(this);
        applyAppearance();
    }

    @Override
    public void onPause() {
        ListAppearance.removeListener(this);
        super.onPause();
    }

    @Override
    public void onListAppearanceChanged() {
        applyAppearance();
    }

    private void applyAppearance() {
        if (binding == null) return;
        ListAppearance.Appearance appearance = ListAppearance.resolve(requireContext());
        binding.infoTitle.setTextColor(appearance.primaryText);
        binding.infoSubtitle.setTextColor(appearance.secondaryText);
        binding.infoBackButton.setColorFilter(appearance.primaryText);

        MaterialCardView[] cards = new MaterialCardView[]{
                binding.versionCard, binding.updateCard, binding.developerCard,
                binding.githubCard, binding.donationCard
        };
        for (MaterialCardView card : cards) card.setCardBackgroundColor(appearance.containerColor);

        setTextColors(appearance.primaryText, appearance.secondaryText,
                binding.versionLabel, binding.versionValue,
                binding.updateLabel, binding.updateStatus,
                binding.developerLabel, binding.developerValue,
                binding.githubLabel, binding.githubValue,
                binding.donationLabel, binding.donationHint,
                binding.donationAliasLabel, binding.donationAliasValue,
                binding.donationCvuLabel, binding.donationCvuValue,
                binding.donationNameLabel, binding.donationNameValue);
    }

    private static void setTextColors(int primary, int secondary, TextView... views) {
        for (int i = 0; i < views.length; i += 2) {
            if (i < views.length && views[i] != null) views[i].setTextColor(primary);
            if (i + 1 < views.length && views[i + 1] != null) views[i + 1].setTextColor(secondary);
        }
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }
}
