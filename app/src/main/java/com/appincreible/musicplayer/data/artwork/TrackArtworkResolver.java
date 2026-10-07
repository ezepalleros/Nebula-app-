package com.appincreible.musicplayer.data.artwork;

import android.content.ContentResolver;
import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * Resolves artwork for a single audio track.
 *
 * Priority:
 * 1) Embedded artwork inside the audio file (MP3/FLAC/M4A tags).
 * 2) MediaStore album artwork URI, copied into our private cache when readable.
 * 3) Empty string so the UI can show its normal placeholder.
 *
 * The result is a stable file:// URI in the app cache. Heavy work is expected to
 * run from MusicRepository's background executor, never on the main thread.
 */
public final class TrackArtworkResolver {

    private static final String CACHE_DIR = "track_artwork";

    private final Context appContext;
    private final ContentResolver resolver;
    private final File cacheDir;

    public TrackArtworkResolver(Context context) {
        appContext = context.getApplicationContext();
        resolver = appContext.getContentResolver();
        cacheDir = new File(appContext.getCacheDir(), CACHE_DIR);
        //noinspection ResultOfMethodCallIgnored
        cacheDir.mkdirs();
    }

    public String resolve(Uri audioUri, long songId, long modifiedSeconds, Uri albumArtworkUri) {
        File cached = cacheFile(songId, modifiedSeconds);
        if (cached.isFile() && cached.length() > 0L) {
            return Uri.fromFile(cached).toString();
        }

        cleanupOldVersions(songId, cached.getName());

        byte[] embedded = readEmbeddedPicture(audioUri);
        if (embedded != null && embedded.length > 0 && writeBytes(cached, embedded)) {
            return Uri.fromFile(cached).toString();
        }

        if (albumArtworkUri != null && copyUriToFile(albumArtworkUri, cached)) {
            return Uri.fromFile(cached).toString();
        }

        return "";
    }

    private byte[] readEmbeddedPicture(Uri audioUri) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(appContext, audioUri);
            return retriever.getEmbeddedPicture();
        } catch (RuntimeException ignored) {
            return null;
        } finally {
            try {
                retriever.release();
            } catch (Exception ignored) {
                // release() may throw IOException on newer Android SDKs.
            }
        }
    }

    private boolean copyUriToFile(Uri source, File target) {
        try (InputStream input = resolver.openInputStream(source);
             FileOutputStream output = new FileOutputStream(target)) {
            if (input == null) return false;
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            output.flush();
            if (target.length() <= 0L) {
                //noinspection ResultOfMethodCallIgnored
                target.delete();
                return false;
            }
            return true;
        } catch (Exception ignored) {
            //noinspection ResultOfMethodCallIgnored
            target.delete();
            return false;
        }
    }

    private boolean writeBytes(File target, byte[] bytes) {
        try (FileOutputStream output = new FileOutputStream(target)) {
            output.write(bytes);
            output.flush();
            return target.length() > 0L;
        } catch (Exception ignored) {
            //noinspection ResultOfMethodCallIgnored
            target.delete();
            return false;
        }
    }

    private File cacheFile(long songId, long modifiedSeconds) {
        long version = Math.max(0L, modifiedSeconds);
        return new File(cacheDir, songId + "_" + version + ".art");
    }

    private void cleanupOldVersions(long songId, String keepName) {
        File[] files = cacheDir.listFiles();
        if (files == null) return;
        String prefix = songId + "_";
        for (File file : files) {
            if (!file.getName().equals(keepName) && file.getName().startsWith(prefix)) {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }
        }
    }
}
