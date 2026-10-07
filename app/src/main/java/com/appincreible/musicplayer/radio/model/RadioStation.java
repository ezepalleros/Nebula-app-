package com.appincreible.musicplayer.radio.model;

import com.appincreible.musicplayer.database.MediaOverrideEntity;
import com.google.gson.annotations.SerializedName;

public class RadioStation {

    @SerializedName("stationuuid") private String stationUuid;
    @SerializedName("name") private String name;
    @SerializedName("url") private String url;
    @SerializedName("url_resolved") private String urlResolved;
    @SerializedName("favicon") private String favicon;
    @SerializedName("state") private String state;
    @SerializedName("tags") private String tags;
    @SerializedName("codec") private String codec;
    @SerializedName("bitrate") private int bitrate;
    @SerializedName("hls") private int hls;
    @SerializedName("votes") private int votes;

    private transient String customName = "";
    private transient String customArtist = "";
    private transient String customAlbum = "";
    private transient String customArtwork = "";

    public String getStationUuid() { return safe(stationUuid); }
    public String getMediaKey() { return "radio:" + getStationUuid(); }
    public String getName() {
        if (!customName.isEmpty()) return customName;
        String base = safe(name).trim();
        return base.isEmpty() ? "Radio sin nombre" : base;
    }
    public String getArtist() {
        if (!customArtist.isEmpty()) return customArtist;
        return getState().isEmpty() ? "Radio Argentina" : "Radio Argentina · " + getState();
    }
    public String getAlbum() {
        return customAlbum.isEmpty() ? getTags() : customAlbum;
    }
    public String getFavicon() { return customArtwork.isEmpty() ? safe(favicon).trim() : customArtwork; }
    public String getOriginalFavicon() { return safe(favicon).trim(); }
    public String getState() { return safe(state).trim(); }
    public String getTags() { return safe(tags).trim(); }
    public String getCodec() { return safe(codec).trim(); }
    public int getBitrate() { return bitrate; }
    public boolean isHls() { return hls == 1; }
    public String getOriginalStreamUrl() { return safe(url).trim(); }
    public String getResolvedStreamUrl() { return safe(urlResolved).trim(); }
    public int getVotes() { return votes; }

    public String getStreamUrl() {
        String resolved = safe(urlResolved).trim();
        return resolved.isEmpty() ? safe(url).trim() : resolved;
    }

    public String getSubtitle() {
        if (!customArtist.isEmpty() || !customAlbum.isEmpty()) {
            StringBuilder custom = new StringBuilder(getArtist());
            if (!getAlbum().isEmpty()) custom.append(" · ").append(getAlbum());
            return custom.toString();
        }
        StringBuilder builder = new StringBuilder();
        if (!getState().isEmpty()) builder.append(getState());
        if (!getCodec().isEmpty()) {
            if (builder.length() > 0) builder.append(" · ");
            builder.append(getCodec());
        }
        if (bitrate > 0) {
            if (builder.length() > 0) builder.append(" · ");
            builder.append(bitrate).append(" kbps");
        }
        if (builder.length() == 0 && !getTags().isEmpty()) builder.append(getTags());
        return builder.length() == 0 ? "Argentina · Online" : builder.toString();
    }

    public String searchableText() {
        return getName() + " " + getArtist() + " " + getAlbum() + " " + getState() + " "
                + getTags() + " " + getCodec() + " " + bitrate;
    }

    public void applyOverride(MediaOverrideEntity override) {
        customName = override == null ? "" : safe(override.title).trim();
        customArtist = override == null ? "" : safe(override.artist).trim();
        customAlbum = override == null ? "" : safe(override.album).trim();
        customArtwork = override == null ? "" : safe(override.artworkUri).trim();
    }

    private String safe(String value) { return value == null ? "" : value; }
}
