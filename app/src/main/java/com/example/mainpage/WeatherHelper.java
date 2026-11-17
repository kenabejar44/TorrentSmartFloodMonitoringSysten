package com.example.mainpage;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class WeatherHelper {

    public static final String WEATHER_CHANNEL_ID = "weather_alerts_v2";
    public static final int WEATHER_NOTIFICATION_ID = 1003;

    public static class WeatherAdvisory {
        public final String stateKey;
        public final String title;
        public final String description;
        public final boolean isSevere;
        public final int severityRank;

        public WeatherAdvisory(String stateKey, String title, String description, boolean isSevere, int severityRank) {
            this.stateKey = stateKey;
            this.title = title;
            this.description = description;
            this.isSevere = isSevere;
            this.severityRank = severityRank;
        }
    }

    /**
     * Calculates the Heat Index (Apparent Temperature) in Celsius.
     * Formula based on Rothfusz regression.
     */
    public static double calculateHeatIndex(double tempCelsius, double humidity) {
        if (tempCelsius < 26.7) return tempCelsius;

        double T = (tempCelsius * 9/5) + 32;
        double R = humidity;

        double HI = 0.5 * (T + 61.0 + ((T - 68.0) * 1.2) + (R * 0.094));

        if (HI >= 80) {
            HI = -42.379 + 2.04901523 * T + 10.14333127 * R - 0.22475541 * T * R
                    - 0.00683783 * T * T - 0.05481717 * R * R + 0.00122874 * T * T * R
                    + 0.00085282 * T * R * R - 0.00000199 * T * T * R * R;

            if (R < 13 && T >= 80 && T <= 112) {
                HI -= ((13 - R) / 4) * Math.sqrt((17 - Math.abs(T - 95.0)) / 17);
            } else if (R > 85 && T >= 80 && T <= 87) {
                HI += ((R - 85) / 10) * ((87 - T) / 5);
            }
        }

        return (HI - 32) * 5/9;
    }

    public static String getHeatIndexCategory(double hiCelsius) {
        if (hiCelsius < 27) return "Normal";
        if (hiCelsius < 33) return "Caution";
        if (hiCelsius < 40) return "Extreme Caution";
        if (hiCelsius < 52) return "Danger";
        return "Extreme Danger";
    }

    public static WeatherAdvisory evaluateWeather(String condition, double temp, double humidity, double windSpeed, double rainProb, String stationName) {
        double heatIndex = calculateHeatIndex(temp, humidity);
        String condLower = condition != null ? condition.toLowerCase() : "";
        String location = (stationName != null && !stationName.isEmpty()) ? stationName : "your area";

        // 1. Extreme Heat (>= 42°C)
        if (heatIndex >= 42.0 || temp >= 40.0) {
            return new WeatherAdvisory(
                "EXTREME_HEAT",
                "LOCAL WEATHER ALERT: EXTREME HEAT",
                String.format(Locale.getDefault(), "Extreme heat index detected in %s (%.1f°C). Stay indoors and drink fluids.", location, heatIndex),
                true,
                6
            );
        }

        // 2. Severe Thunderstorms / Squall / Tornado
        if (condLower.contains("thunderstorm") || condLower.contains("squall") || condLower.contains("tornado") || condLower.contains("extreme")) {
            return new WeatherAdvisory(
                "THUNDERSTORM",
                "LOCAL WEATHER ALERT: THUNDERSTORM",
                "Thunderstorm detected in " + location + ". Exercise caution and monitor local updates.",
                true,
                5
            );
        }

        // 3. Heavy Rain
        if (condLower.contains("heavy") || (condLower.contains("rain") && rainProb >= 70.0)) {
            return new WeatherAdvisory(
                "HEAVY_RAIN",
                "LOCAL WEATHER ALERT: HEAVY RAINFALL",
                "Heavy rainfall detected in " + location + ". Stay alert for localized flooding.",
                true,
                4
            );
        }

        // 4. High Heat Index (37°C - 41.9°C)
        if (heatIndex >= 37.0) {
            return new WeatherAdvisory(
                "HIGH_HEAT",
                "LOCAL WEATHER UPDATE: HIGH HEAT INDEX",
                String.format(Locale.getDefault(), "High heat index in %s (%.1f°C). Limit outdoor exposure and stay hydrated.", location, heatIndex),
                false,
                3
            );
        }

        // 5. Strong Winds (> 10 m/s)
        if (windSpeed >= 10.0) {
            return new WeatherAdvisory(
                "STRONG_WIND",
                "LOCAL WEATHER UPDATE: STRONG WINDS",
                String.format(Locale.getDefault(), "Strong winds detected in %s (%.1f m/s). Secure loose outdoor items.", location, windSpeed),
                false,
                2
            );
        }

        // 6. Ordinary Rain / Drizzle
        if (condLower.contains("rain") || condLower.contains("drizzle")) {
            return new WeatherAdvisory(
                "RAIN",
                "LOCAL WEATHER UPDATE: RAINFALL",
                "Rainfall detected in " + location + ". Keep an umbrella handy.",
                false,
                1
            );
        }

        // 7. Normal / Clear / Clouds
        return new WeatherAdvisory(
            "NORMAL",
            "LOCAL WEATHER UPDATE: FAIR WEATHER",
            "Weather conditions in " + location + " are fair.",
            false,
            0
        );
    }

    public static void checkAndNotifyWeatherChange(Context context, String condition, double temp, double humidity, double windSpeed, double rainProb, String stationName) {
        if (context == null) return;

        WeatherAdvisory advisory = evaluateWeather(condition, temp, humidity, windSpeed, rainProb, stationName);
        
        String lastState = FloodStatusHelper.getSavedLastWeatherState(context);
        long lastTime = FloodStatusHelper.getSavedLastWeatherTime(context);
        long now = System.currentTimeMillis();

        boolean isNormal = "NORMAL".equals(advisory.stateKey);
        boolean wasNormal = "NORMAL".equals(lastState) || lastState == null;

        if (isNormal) {
            if (!wasNormal) {
                // Weather improved back to normal from an active advisory
                sendWeatherNotification(context, advisory.title, advisory.description, false, WEATHER_NOTIFICATION_ID);
                FloodStatusHelper.saveLastWeatherState(context, "NORMAL");
                FloodStatusHelper.saveLastWeatherTime(context, now);
            }
            return;
        }

        boolean stateChanged = !advisory.stateKey.equals(lastState);
        boolean timeExpired = (now - lastTime) > 7200000L; // 2 hours cooldown for identical state

        if (stateChanged || timeExpired) {
            sendWeatherNotification(context, advisory.title, advisory.description, advisory.isSevere, WEATHER_NOTIFICATION_ID);
            FloodStatusHelper.saveLastWeatherState(context, advisory.stateKey);
            FloodStatusHelper.saveLastWeatherTime(context, now);
        }
    }

    public static void sendWeatherNotification(Context context, String title, String message, boolean isSevere, int notificationId) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    WEATHER_CHANNEL_ID,
                    "Local Weather Forecasts",
                    isSevere ? NotificationManager.IMPORTANCE_HIGH : NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setLockscreenVisibility(android.app.Notification.VISIBILITY_PUBLIC);
            manager.createNotificationChannel(channel);
        }

        Intent intent = new Intent(context, MainActivity.class);
        intent.putExtra("station_name", FloodStatusHelper.getSavedStationName(context));
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                notificationId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, WEATHER_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(isSevere ? NotificationCompat.PRIORITY_HIGH : NotificationCompat.PRIORITY_DEFAULT)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setOngoing(false)
                .setTimeoutAfter(3600000L)
                .setContentIntent(pendingIntent);

        manager.notify(notificationId, builder.build());
    }

    public static void schedulePeriodicWeatherCheck(Context context) {
        if (context == null) return;
        try {
            Constraints constraints = new Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build();

            PeriodicWorkRequest workRequest = new PeriodicWorkRequest.Builder(
                    WeatherMonitoringWorker.class,
                    30, TimeUnit.MINUTES
            )
                    .setConstraints(constraints)
                    .build();

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    WeatherMonitoringWorker.WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    workRequest
            );
        } catch (Throwable t) {
            Log.e("WEATHER_WORKER", "Failed to schedule WorkManager: " + t.getMessage());
        }
    }
}
