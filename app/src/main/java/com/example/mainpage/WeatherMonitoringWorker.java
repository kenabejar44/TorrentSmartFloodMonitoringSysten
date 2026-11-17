package com.example.mainpage;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class WeatherMonitoringWorker extends Worker {

    public static final String WORK_NAME = "WeatherMonitoringWorkerJob";

    public WeatherMonitoringWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        try {
            // 1. PAGASA-FIRST: Check for official PAGASA advisories
            try {
                PagasaWeatherHelper.checkAndNotifyPagasaAdvisories(context);
            } catch (Throwable t) {
                Log.w("WeatherMonitoringWorker", "PAGASA advisory check note: " + t.getMessage());
            }

            // 2. OPENWEATHERMAP-SUPPORTED: Fetch supplementary numerical measurements
            String stationName = FloodStatusHelper.getSavedStationName(context);

            Retrofit retrofit = new Retrofit.Builder()
                    .baseUrl("https://api.openweathermap.org/data/2.5/")
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
            MainActivity.WeatherService service = retrofit.create(MainActivity.WeatherService.class);

            double lat = 14.3917;
            double lon = 121.0445;

            Response<MainActivity.WeatherResponse> response = service.getCurrentWeatherByCoords(lat, lon, BuildConfig.WEATHER_API_KEY, "metric").execute();
            if (response.isSuccessful() && response.body() != null) {
                MainActivity.WeatherResponse weather = response.body();
                if (weather.weather != null && weather.weather.length > 0 && weather.main != null) {
                    String condition = weather.weather[0].main;
                    double temp = weather.main.temp;
                    double humidity = weather.main.humidity;
                    double windSpeed = (weather.wind != null) ? weather.wind.speed : 0.0;

                    double rainProb = 0.0;
                    try {
                        Response<MainActivity.ForecastResponse> forecastResp = service.getForecastByCoords(lat, lon, BuildConfig.WEATHER_API_KEY, "metric").execute();
                        if (forecastResp.isSuccessful() && forecastResp.body() != null && forecastResp.body().list != null && !forecastResp.body().list.isEmpty()) {
                            MainActivity.ForecastResponse.ForecastItem item = forecastResp.body().list.get(0);
                            if (item != null) rainProb = item.pop * 100;
                        }
                    } catch (Exception ignored) {}

                    WeatherHelper.checkAndNotifyWeatherChange(context, condition, temp, humidity, windSpeed, rainProb, stationName);
                    return Result.success();
                }
            }
            return Result.retry();
        } catch (Exception e) {
            Log.e("WeatherMonitoringWorker", "Error in background weather worker: " + e.getMessage());
            return Result.retry();
        }
    }
}
