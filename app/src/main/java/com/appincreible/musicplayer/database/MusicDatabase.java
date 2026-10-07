package com.appincreible.musicplayer.database;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(
        entities = {
                FavoriteRadioEntity.class,
                MediaOverrideEntity.class,
                SongStateEntity.class,
                PlaylistEntity.class,
                PlaylistSongEntity.class,
                LoudnessAnalysisEntity.class,
                LibraryFolderStateEntity.class
        },
        version = 5,
        exportSchema = false
)
public abstract class MusicDatabase extends RoomDatabase {

    private static volatile MusicDatabase instance;

    public abstract FavoriteRadioDao favoriteRadioDao();
    public abstract MediaOverrideDao mediaOverrideDao();
    public abstract SongStateDao songStateDao();
    public abstract PlaylistDao playlistDao();
    public abstract LoudnessAnalysisDao loudnessAnalysisDao();
    public abstract LibraryFolderStateDao libraryFolderStateDao();

    private static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `media_overrides` (`mediaKey` TEXT NOT NULL, `title` TEXT, `artist` TEXT, `album` TEXT, `artworkUri` TEXT, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`mediaKey`))");
        }
    };

    private static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `song_states` (`songId` INTEGER NOT NULL, `hidden` INTEGER NOT NULL, `favorite` INTEGER NOT NULL, PRIMARY KEY(`songId`))");
            database.execSQL("CREATE TABLE IF NOT EXISTS `playlists` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT, `createdAt` INTEGER NOT NULL)");
            database.execSQL("CREATE TABLE IF NOT EXISTS `playlist_songs` (`playlistId` INTEGER NOT NULL, `songId` INTEGER NOT NULL, `addedAt` INTEGER NOT NULL, PRIMARY KEY(`playlistId`, `songId`))");
        }
    };


    private static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `loudness_analysis` (`mediaId` TEXT NOT NULL, `gainDb` REAL NOT NULL, `rmsDb` REAL NOT NULL, `peak` REAL NOT NULL, `source` TEXT NOT NULL, `analyzedAt` INTEGER NOT NULL, PRIMARY KEY(`mediaId`))");
        }
    };


    private static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `library_folder_states` (`folderKey` TEXT NOT NULL, `hidden` INTEGER NOT NULL, PRIMARY KEY(`folderKey`))");
        }
    };

    public static MusicDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (MusicDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                            context.getApplicationContext(),
                            MusicDatabase.class,
                            "app_increible.db"
                    ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5).build();
                }
            }
        }
        return instance;
    }
}
