package com.appincreible.musicplayer.ui.metadata;

import android.app.Dialog;
import android.app.Activity;
import android.app.RecoverableSecurityException;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.MediaStore;
import android.content.IntentSender;
import androidx.activity.result.IntentSenderRequest;
import androidx.core.content.ContextCompat;
import com.appincreible.musicplayer.metadata.write.AudioTagFileWriter;
import java.util.Collections;
import java.util.concurrent.Executors;
import android.os.Handler;
import android.os.Looper;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.appincreible.musicplayer.R;
import com.appincreible.musicplayer.database.MediaOverrideEntity;
import com.appincreible.musicplayer.databinding.DialogMediaEditorBinding;
import com.appincreible.musicplayer.metadata.model.ArtworkOption;
import com.appincreible.musicplayer.metadata.repository.ArtworkSearchRepository;
import com.appincreible.musicplayer.metadata.repository.ArtworkDownloadRepository;
import com.appincreible.musicplayer.metadata.repository.MediaMetadataRepository;
import com.appincreible.musicplayer.power.PowerSaverManager;
import com.appincreible.musicplayer.themes.AppPreferences;
import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.imageview.ShapeableImageView;

import java.util.List;
import java.util.Objects;

public class MediaEditorBottomSheet extends BottomSheetDialogFragment {

    public static final String RESULT_KEY = "media_editor_result";
    public static final String RESULT_MEDIA_KEY = "media_key";
    public static final String RESULT_TITLE = "title";
    public static final String RESULT_ARTIST = "artist";
    public static final String RESULT_ALBUM = "album";
    public static final String RESULT_ARTWORK = "artwork";
    public static final String RESULT_RESET = "reset";

    private static final String ARG_MEDIA_KEY = "arg_media_key";
    private static final String ARG_KIND = "arg_kind";
    private static final String ARG_TITLE = "arg_title";
    private static final String ARG_ARTIST = "arg_artist";
    private static final String ARG_ALBUM = "arg_album";
    private static final String ARG_ARTWORK = "arg_artwork";

    private DialogMediaEditorBinding binding;
    private MediaMetadataRepository metadataRepository;
    private ArtworkSearchRepository artworkSearchRepository;
    private ArtworkDownloadRepository artworkDownloadRepository;
    private ArtworkResultsSheet artworkResultsSheet;
    private String selectedArtwork = "";
    private String initialTitle = "";
    private String initialArtist = "";
    private String initialAlbum = "";
    private String initialArtwork = "";
    private boolean suppressDirty;
    private boolean userEdited;
    private MediaOverrideEntity pendingFileChange;
    private boolean restoringFile;
    private boolean changeTitle, changeArtist, changeAlbum, changeArtwork;
    private final Handler fileUi = new Handler(Looper.getMainLooper());
    private final java.util.concurrent.ExecutorService fileExecutor = Executors.newSingleThreadExecutor();

    private final ActivityResultLauncher<IntentSenderRequest> writePermission = registerForActivityResult(
            new ActivityResultContracts.StartIntentSenderForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK) performFileOperation();
                else finishFileOperation(false, "Permiso denegado; cambio guardado solo en la app");
            });
    private final ActivityResultLauncher<String> oldWritePermission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), allowed -> {
                if (allowed) performFileOperation();
                else finishFileOperation(false, "Permiso denegado; cambio guardado solo en la app");
            });

    private final ActivityResultLauncher<String[]> imagePicker = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri == null || binding == null) return;
                try {
                    requireContext().getContentResolver().takePersistableUriPermission(
                            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (Exception ignored) { }
                selectedArtwork = uri.toString();
                userEdited = true;
                renderPreview();
            });

    public static MediaEditorBottomSheet newInstance(String mediaKey, boolean radio, String title,
                                                      String artist, String album, String artwork) {
        MediaEditorBottomSheet sheet = new MediaEditorBottomSheet();
        Bundle args = new Bundle();
        args.putString(ARG_MEDIA_KEY, mediaKey);
        args.putString(ARG_KIND, radio ? "radio" : "song");
        args.putString(ARG_TITLE, title);
        args.putString(ARG_ARTIST, artist);
        args.putString(ARG_ALBUM, album);
        args.putString(ARG_ARTWORK, artwork);
        sheet.setArguments(args);
        return sheet;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        dialog.setCanceledOnTouchOutside(false);
        dialog.setOnKeyListener((ignored, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
                attemptDismiss();
                return true;
            }
            return false;
        });
        dialog.setOnShowListener(ignored -> {
            // A BottomSheetDialog measures its sheet as wrap_content by default. This editor's
            // scrolling area uses 0dp + ConstraintLayout constraints, so without forcing a real
            // sheet height Android may measure only the fixed 72dp action bar at the bottom.
            // Make the sheet full-height first, then expand it.
            FrameLayout bottomSheet = dialog.findViewById(
                    com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                ViewGroup.LayoutParams params = bottomSheet.getLayoutParams();
                params.height = ViewGroup.LayoutParams.MATCH_PARENT;
                bottomSheet.setLayoutParams(params);
                bottomSheet.requestLayout();
            }

            BottomSheetBehavior<FrameLayout> behavior = dialog.getBehavior();
            behavior.setFitToContents(false);
            behavior.setExpandedOffset(0);
            behavior.setSkipCollapsed(true);
            behavior.setHideable(false);
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);

            Window window = dialog.getWindow();
            if (window != null) {
                window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
                if (PowerSaverManager.isActive(requireContext())) window.setWindowAnimations(0);
            }
        });
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = DialogMediaEditorBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        metadataRepository = new MediaMetadataRepository(requireContext());
        artworkSearchRepository = new ArtworkSearchRepository(requireContext());
        artworkDownloadRepository = new ArtworkDownloadRepository(requireContext());

        Bundle args = requireArguments();
        String mediaKey = value(args, ARG_MEDIA_KEY);
        boolean radio = "radio".equals(value(args, ARG_KIND));
        String baseTitle = value(args, ARG_TITLE);
        String baseArtist = value(args, ARG_ARTIST);
        String baseAlbum = value(args, ARG_ALBUM);
        String baseArtwork = value(args, ARG_ARTWORK);

        suppressDirty = true;
        selectedArtwork = baseArtwork;
        binding.editorTitle.setText(radio ? "Editar emisora" : "Editar canción");
        binding.titleInput.setText(baseTitle);
        binding.artistInput.setText(baseArtist);
        binding.albumInput.setText(baseAlbum);
        if (!radio) {
            binding.artistInputLayout.setVisibility(AppPreferences.libraryArtistsEnabled(requireContext())
                    ? View.VISIBLE : View.GONE);
            binding.albumInputLayout.setVisibility(AppPreferences.libraryAlbumsEnabled(requireContext())
                    ? View.VISIBLE : View.GONE);
        }
        suppressDirty = false;
        captureBaseline();
        renderPreview();
        installDirtyTracking();

        metadataRepository.get(mediaKey, existing -> {
            if (binding == null || existing == null || userEdited) return;
            suppressDirty = true;
            if (!existing.title.isEmpty()) binding.titleInput.setText(existing.title);
            if (!existing.artist.isEmpty()) binding.artistInput.setText(existing.artist);
            if (!existing.album.isEmpty()) binding.albumInput.setText(existing.album);
            if (!existing.artworkUri.isEmpty()) selectedArtwork = existing.artworkUri;
            suppressDirty = false;
            captureBaseline();
            renderPreview();
        });

        View.OnClickListener chooseArtwork = v -> imagePicker.launch(new String[]{"image/*"});
        binding.cameraButton.setOnClickListener(chooseArtwork);
        binding.pickArtworkButton.setOnClickListener(chooseArtwork);
        binding.searchArtworkButton.setOnClickListener(v -> searchArtwork(radio));
        binding.cancelButton.setOnClickListener(v -> attemptDismiss());
        binding.saveButton.setOnClickListener(v -> save(mediaKey));
        binding.resetButton.setOnClickListener(v -> {
            if (!radio && AudioTagFileWriter.hasOriginalBackup(requireContext(), mediaKey)) {
                restoringFile = true;
                pendingFileChange = new MediaOverrideEntity(mediaKey, baseTitle, baseArtist,
                        baseAlbum, baseArtwork, System.currentTimeMillis());
                startFileOperation();
            } else {
                metadataRepository.delete(mediaKey, () -> {
                    sendResult(mediaKey, baseTitle, baseArtist, baseAlbum, baseArtwork, true);
                    dismissAllowingStateLoss();
                });
            }
        });
    }

    private void installDirtyTracking() {
        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!suppressDirty) userEdited = true;
            }
            @Override public void afterTextChanged(Editable s) { }
        };
        binding.titleInput.addTextChangedListener(watcher);
        binding.artistInput.addTextChangedListener(watcher);
        binding.albumInput.addTextChangedListener(watcher);
    }

    private void captureBaseline() {
        if (binding == null) return;
        initialTitle = text(binding.titleInput);
        initialArtist = text(binding.artistInput);
        initialAlbum = text(binding.albumInput);
        initialArtwork = selectedArtwork == null ? "" : selectedArtwork;
        userEdited = false;
    }

    private boolean hasUnsavedChanges() {
        if (binding == null) return false;
        return !Objects.equals(initialTitle, text(binding.titleInput))
                || !Objects.equals(initialArtist, text(binding.artistInput))
                || !Objects.equals(initialAlbum, text(binding.albumInput))
                || !Objects.equals(initialArtwork, selectedArtwork == null ? "" : selectedArtwork);
    }

    private void attemptDismiss() {
        if (binding == null || !hasUnsavedChanges()) {
            dismissAllowingStateLoss();
            return;
        }
        androidx.appcompat.app.AlertDialog confirm = new MaterialAlertDialogBuilder(requireContext())
                .setTitle("¿Descartar cambios?")
                .setMessage("Hay cambios sin guardar en esta canción.")
                .setNegativeButton("Seguir editando", null)
                .setPositiveButton("Descartar", (dialog, which) -> dismissAllowingStateLoss())
                .create();
        confirm.setOnShowListener(ignored -> {
            Window window = confirm.getWindow();
            if (window != null && PowerSaverManager.isActive(requireContext())) window.setWindowAnimations(0);
        });
        confirm.show();
    }

    private void searchArtwork(boolean radio) {
        if (!radio) {
            searchSongCovers();
            return;
        }
        String title = text(binding.titleInput);
        String artist = text(binding.artistInput);
        if (title.isEmpty()) {
            binding.titleInput.setError("Escribí un nombre primero");
            return;
        }
        binding.searchArtworkButton.setEnabled(false);
        binding.searchProgress.setVisibility(View.VISIBLE);
        binding.searchStatus.setVisibility(View.GONE);
        binding.artworkOptionsScroll.setVisibility(View.GONE);

        ArtworkSearchRepository.CallbackResult callback = new ArtworkSearchRepository.CallbackResult() {
            @Override public void onLoaded(List<ArtworkOption> options) {
                if (binding == null) return;
                binding.searchArtworkButton.setEnabled(true);
                binding.searchProgress.setVisibility(View.GONE);
                showArtworkOptions(options);
            }

            @Override public void onError(String message) {
                if (binding == null) return;
                binding.searchArtworkButton.setEnabled(true);
                binding.searchProgress.setVisibility(View.GONE);
                binding.searchStatus.setText(message);
                binding.searchStatus.setVisibility(View.VISIBLE);
            }
        };

        if (radio) artworkSearchRepository.searchRadio(title, callback);
        else artworkSearchRepository.searchSong(title, artist, callback);
    }

    /** Song covers use a separate chooser; radio-logo search keeps its existing UI. */
    private void searchSongCovers() {
        if (binding == null) return;
        String title = text(binding.titleInput);
        if (title.isEmpty()) {
            binding.titleInput.setError("Escribí un título primero");
            return;
        }
        if (artworkResultsSheet != null) artworkResultsSheet.dismiss();
        artworkSearchRepository.cancel();
        artworkResultsSheet = new ArtworkResultsSheet(requireContext(), new ArtworkResultsSheet.Actions() {
            @Override public void select(ArtworkOption option) {
                final ArtworkResultsSheet sheet = artworkResultsSheet;
                if (sheet == null) return;
                sheet.showDownloading();
                artworkDownloadRepository.download(option, (uri, error) -> {
                    if (binding == null || artworkResultsSheet != sheet || !sheet.isShowing()) return;
                    if (error != null) {
                        sheet.showDownloadError(error);
                        return;
                    }
                    selectedArtwork = uri;
                    userEdited = true;
                    renderPreview();
                    sheet.dismiss();
                    Toast.makeText(requireContext(), "Portada lista; tocá Guardar para aplicarla", Toast.LENGTH_SHORT).show();
                });
            }
            @Override public void pickFile() { imagePicker.launch(new String[]{"image/*"}); }
        }, () -> {
            artworkSearchRepository.cancel();
            artworkDownloadRepository.cancelActive();
        });
        final ArtworkResultsSheet sheet = artworkResultsSheet;
        sheet.show();
        artworkSearchRepository.searchSong(title, text(binding.artistInput), text(binding.albumInput),
                new ArtworkSearchRepository.CallbackResult() {
                    @Override public void onLoaded(List<ArtworkOption> options) {
                        if (binding != null && artworkResultsSheet == sheet) sheet.setResults(options);
                    }
                    @Override public void onError(String message) {
                        if (binding != null && artworkResultsSheet == sheet) sheet.setError(message);
                    }
                });
    }

    private void showArtworkOptions(List<ArtworkOption> options) {
        binding.artworkOptions.removeAllViews();
        for (ArtworkOption option : options) {
            ShapeableImageView image = new ShapeableImageView(requireContext());
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(92), dp(92));
            params.setMarginEnd(dp(10));
            image.setLayoutParams(params);
            image.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
            image.setContentDescription(option.getLabel());
            image.setBackgroundResource(R.drawable.bg_artwork_small);
            Glide.with(image).load(option.getImageUrl())
                    .placeholder(R.drawable.bg_artwork_small)
                    .error(R.drawable.bg_artwork_small)
                    .centerCrop().into(image);
            image.setOnClickListener(v -> {
                selectedArtwork = option.getImageUrl();
                userEdited = true;
                renderPreview();
                Toast.makeText(requireContext(), "Portada seleccionada", Toast.LENGTH_SHORT).show();
            });
            binding.artworkOptions.addView(image);
        }
        binding.artworkOptionsScroll.setVisibility(options.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void save(String mediaKey) {
        MediaOverrideEntity entity = new MediaOverrideEntity(
                mediaKey, text(binding.titleInput), text(binding.artistInput),
                text(binding.albumInput), selectedArtwork, System.currentTimeMillis());
        boolean fileEnabled = !"radio".equals(value(requireArguments(), ARG_KIND))
                && AppPreferences.writeTagsToFiles(requireContext());
        changeTitle = !Objects.equals(initialTitle, entity.title);
        changeArtist = !Objects.equals(initialArtist, entity.artist);
        changeAlbum = !Objects.equals(initialAlbum, entity.album);
        changeArtwork = !Objects.equals(initialArtwork, entity.artworkUri);
        metadataRepository.save(entity, saved -> {
            sendResult(saved.mediaKey, saved.title, saved.artist, saved.album, saved.artworkUri, false);
            if (fileEnabled) {
                pendingFileChange = saved;
                restoringFile = false;
                startFileOperation();
            } else dismissAllowingStateLoss();
        });
    }

    private void startFileOperation() {
        if (pendingFileChange == null) return;
        if (binding != null) {
            binding.saveButton.setEnabled(false);
            binding.resetButton.setEnabled(false);
        }
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                IntentSender sender = MediaStore.createWriteRequest(requireContext().getContentResolver(),
                        Collections.singletonList(AudioTagFileWriter.uriForKey(pendingFileChange.mediaKey)))
                        .getIntentSender();
                writePermission.launch(new IntentSenderRequest.Builder(sender).build());
            } else if (Build.VERSION.SDK_INT <= 28 && ContextCompat.checkSelfPermission(
                    requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                oldWritePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            } else performFileOperation();
        } catch (Exception error) {
            finishFileOperation(false, error.getMessage());
        }
    }

    private void performFileOperation() {
        if (pendingFileChange == null) return;
        final android.content.Context app = requireContext().getApplicationContext();
        MediaOverrideEntity item = pendingFileChange;
        boolean isRestore = restoringFile;
        fileExecutor.execute(() -> {
            try {
                if (isRestore) AudioTagFileWriter.restore(app, item.mediaKey);
                else AudioTagFileWriter.write(app, item, changeTitle, changeArtist, changeAlbum, changeArtwork);
                fileUi.post(() -> finishFileOperation(true, null));
            } catch (Exception error) {
                if (Build.VERSION.SDK_INT == 29 && error instanceof RecoverableSecurityException) {
                    IntentSender sender = ((RecoverableSecurityException) error).getUserAction()
                            .getActionIntent().getIntentSender();
                    fileUi.post(() -> writePermission.launch(new IntentSenderRequest.Builder(sender).build()));
                } else fileUi.post(() -> finishFileOperation(false,
                        error.getMessage() == null ? "No se pudo escribir el archivo" : error.getMessage()));
            }
        });
    }

    private void finishFileOperation(boolean success, String reason) {
        if (!isAdded()) return;
        if (success && restoringFile && pendingFileChange != null) {
            MediaOverrideEntity value = pendingFileChange;
            metadataRepository.delete(value.mediaKey, () -> {
                sendResult(value.mediaKey, value.title, value.artist, value.album, value.artworkUri, true);
                Toast.makeText(requireContext(), "1 archivo restaurado, 0 fallaron", Toast.LENGTH_LONG).show();
                dismissAllowingStateLoss();
            });
            return;
        }
        String report = success ? "1 archivo actualizado, 0 fallaron"
                : "0 archivos actualizados, 1 falló: " + reason;
        if (!success && restoringFile) report += ". No se restauraron los datos de la app.";
        Toast.makeText(requireContext(), report, Toast.LENGTH_LONG).show();
        if (!restoringFile) dismissAllowingStateLoss();
        else if (binding != null) {
            binding.saveButton.setEnabled(true);
            binding.resetButton.setEnabled(true);
        }
    }

    @Override public void onDestroy() {
        fileExecutor.shutdown();
        super.onDestroy();
    }

    private void sendResult(String mediaKey, String title, String artist, String album,
                            String artwork, boolean reset) {
        Bundle result = new Bundle();
        result.putString(RESULT_MEDIA_KEY, mediaKey);
        result.putString(RESULT_TITLE, title);
        result.putString(RESULT_ARTIST, artist);
        result.putString(RESULT_ALBUM, album);
        result.putString(RESULT_ARTWORK, artwork);
        result.putBoolean(RESULT_RESET, reset);
        getParentFragmentManager().setFragmentResult(RESULT_KEY, result);
    }

    private void renderPreview() {
        if (binding == null) return;
        Glide.with(binding.artworkPreview)
                .load(selectedArtwork.isEmpty() ? null : Uri.parse(selectedArtwork))
                .placeholder(R.drawable.bg_artwork)
                .error(R.drawable.bg_artwork)
                .centerCrop()
                .into(binding.artworkPreview);
    }

    private String text(android.widget.TextView view) {
        return view.getText() == null ? "" : view.getText().toString().trim();
    }

    private String value(Bundle bundle, String key) {
        String value = bundle.getString(key);
        return value == null ? "" : value;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onDestroyView() {
        if (artworkResultsSheet != null) { artworkResultsSheet.dismiss(); artworkResultsSheet = null; }
        if (artworkSearchRepository != null) artworkSearchRepository.close();
        if (artworkDownloadRepository != null) artworkDownloadRepository.close();
        if (metadataRepository != null) metadataRepository.close();
        binding = null;
        super.onDestroyView();
    }
}
