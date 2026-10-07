package com.appincreible.musicplayer.ui.music;

public final class LibraryEntry {
    public static final int TYPE_ALL = 1;
    public static final int TYPE_ARTISTS_ROOT = 2;
    public static final int TYPE_ALBUMS_ROOT = 3;
    public static final int TYPE_ARTIST = 4;
    public static final int TYPE_ALBUM = 5;
    public static final int TYPE_USER_PLAYLIST = 6;
    public static final int TYPE_HIDDEN_ROOT = 7;

    public final String stableId;
    public final int type;
    public final String title;
    public final String subtitle;
    public final String value;
    public final long playlistId;
    public final String folderKey;
    public final boolean hideable;
    public final boolean hidden;

    public LibraryEntry(String stableId, int type, String title, String subtitle,
                        String value, long playlistId, String folderKey,
                        boolean hideable, boolean hidden) {
        this.stableId = stableId;
        this.type = type;
        this.title = title;
        this.subtitle = subtitle;
        this.value = value == null ? "" : value;
        this.playlistId = playlistId;
        this.folderKey = folderKey == null ? "" : folderKey;
        this.hideable = hideable;
        this.hidden = hidden;
    }
}
