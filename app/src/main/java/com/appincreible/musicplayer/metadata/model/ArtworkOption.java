package com.appincreible.musicplayer.metadata.model;

/** A single artwork candidate. The two-argument constructor is retained for radio logos. */
public final class ArtworkOption {
    private final String label;
    private final String imageUrl;
    private final String fallbackUrl;
    private final String title;
    private final String artist;
    private final String album;
    private final String source;
    private final double score;
    private final boolean recommended;

    public ArtworkOption(String label, String imageUrl) {
        this(label, imageUrl, "", label, "", "", "", 0d, false);
    }

    public ArtworkOption(String label, String imageUrl, String fallbackUrl, String title,
                         String artist, String album, String source, double score, boolean recommended) {
        this.label = label == null ? "" : label;
        this.imageUrl = imageUrl == null ? "" : imageUrl;
        this.fallbackUrl = fallbackUrl == null ? "" : fallbackUrl;
        this.title = title == null ? "" : title;
        this.artist = artist == null ? "" : artist;
        this.album = album == null ? "" : album;
        this.source = source == null ? "" : source;
        this.score = score;
        this.recommended = recommended;
    }

    public String getLabel() { return label; }
    public String getImageUrl() { return imageUrl; }
    public String getFallbackUrl() { return fallbackUrl; }
    public String getTitle() { return title; }
    public String getArtist() { return artist; }
    public String getAlbum() { return album; }
    public String getSource() { return source; }
    public double getScore() { return score; }
    public boolean isRecommended() { return recommended; }
}
