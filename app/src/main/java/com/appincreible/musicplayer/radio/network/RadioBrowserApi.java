package com.appincreible.musicplayer.radio.network;

import com.appincreible.musicplayer.radio.model.RadioStation;

import java.util.List;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface RadioBrowserApi {

    @GET("json/stations/bycountrycodeexact/AR")
    Call<List<RadioStation>> getArgentinaStations(
            @Query("hidebroken") boolean hideBroken,
            @Query("order") String order,
            @Query("reverse") boolean reverse,
            @Query("limit") int limit
    );

    @GET("json/stations/search")
    Call<List<RadioStation>> searchArgentinaStations(
            @Query("countrycode") String countryCode,
            @Query("name") String name,
            @Query("hidebroken") boolean hideBroken,
            @Query("order") String order,
            @Query("reverse") boolean reverse,
            @Query("limit") int limit
    );

    @GET("json/url/{stationUuid}")
    Call<ResponseBody> countClick(@Path("stationUuid") String stationUuid);
}
