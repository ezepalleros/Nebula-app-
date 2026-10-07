package com.appincreible.musicplayer.metadata.model;

public class ArtworkOption {
    private final String label;
    private final String imageUrl;

    public ArtworkOption(String label, String imageUrl) {
        this.label = label == null ? "" : label;
        this.imageUrl = imageUrl == null ? "" : imageUrl;
    }

    public String getLabel() { return label; }
    public String getImageUrl() { return imageUrl; }
}
