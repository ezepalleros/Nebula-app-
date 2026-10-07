package com.appincreible.musicplayer.metadata.network;

import com.google.gson.annotations.SerializedName;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface MusicBrainzApi {

    @GET("ws/2/release-group")
    Call<ReleaseGroupSearchResponse> searchReleaseGroups(
            @Query("query") String query,
            @Query("fmt") String format,
            @Query("limit") int limit
    );

    class ReleaseGroupSearchResponse {
        @SerializedName("release-groups") public List<ReleaseGroup> releaseGroups;
    }

    class ReleaseGroup {
        public String id;
        public String title;
        @SerializedName("artist-credit") public List<ArtistCredit> artistCredit;
    }

    class ArtistCredit {
        public String name;
    }
}
