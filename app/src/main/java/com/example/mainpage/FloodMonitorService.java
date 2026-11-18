package com.example.mainpage;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class FloodMonitorService extends Service {

    private static final String TAG = "FloodMonitorService";
    private static final String ALERT_CHANNEL_ID = "flood_alerts_v3";
    private static final String FOREGROUND_CHANNEL_ID = "flood_monitor_foreground";
    private static final int FOREGROUND_NOTIFICATION_ID = 1001;
    private static final int ALERT_NOTIFICATION_ID = 3;

    private DatabaseReference databaseRef;
    private DatabaseReference activeServiceRef;
    private ValueEventListener monitorListener;
    private String selectedStationId = "ST001";
    private String selectedStationName = "Putatan";
    private String lastStatus = null;
    private String lastNotifiedStatus = null;
    private MediaPlayer alertMediaPlayer;
    private final Handler soundHandler = new Handler(Looper.getMainLooper());
    private static final long CRITICAL_SOUND_COOLDOWN_MS = 3600000L; // 1 hour (60 minutes)
    private static final long SOUND_DURATION_MS = 5000L; // 5 seconds
    private final String DB_URL = "https://finalfirebase-45c72-default-rtdb.firebaseio.com";
    
    private String currentMunicipality = "Muntinlupa";
    private final String WEATHER_API_KEY = BuildConfig.WEATHER_API_KEY;
    private MainActivity.WeatherService weatherService;
    private final Handler weatherHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannels();

        // Load saved station and municipality preferences
        selectedStationId = FloodStatusHelper.getSavedStationId(this);
        selectedStationName = FloodStatusHelper.getSavedStationName(this);
        currentMunicipality = FloodStatusHelper.getSavedMunicipality(this);
        lastStatus = FloodStatusHelper.getSavedLastStatus(this);
        lastNotifiedStatus = FloodStatusHelper.getSavedLastNotifiedStatusForStation(this, selectedStationId);

        startForeground(FOREGROUND_NOTIFICATION_ID, getForegroundNotification());
        
        databaseRef = FirebaseDatabase.getInstance(DB_URL).getReference();
        
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://api.openweathermap.org/data/2.5/")
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        weatherService = retrofit.create(MainActivity.WeatherService.class);
        
        startMonitoring();
        scheduleWeatherCheck();
    }

    private void scheduleWeatherCheck() {
        weatherHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                checkWeather();
                weatherHandler.postDelayed(this, 1800000); // Check every 30 minutes
            }
        }, 5000); // Initial check after 5 seconds
    }

    private void checkWeather() {
        // 1. PAGASA-FIRST: Check for official PAGASA weather advisories on background thread
        backgroundExecutor.execute(() -> {
            try {
                PagasaWeatherHelper.checkAndNotifyPagasaAdvisories(FloodMonitorService.this);
            } catch (Throwable t) {
                Log.w(TAG, "PAGASA check note: " + t.getMessage());
            }
        });

        if (weatherService == null) return;
        // Default coordinates for Muntinlupa if location not yet updated
        double lat = 14.3917;
        double lon = 121.0445;

        // Fetch Current Weather
        weatherService.getCurrentWeatherByCoords(lat, lon, WEATHER_API_KEY, "metric")
                .enqueue(new Callback<MainActivity.WeatherResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<MainActivity.WeatherResponse> call, @NonNull Response<MainActivity.WeatherResponse> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            MainActivity.WeatherResponse weather = response.body();
                            if (weather.weather != null && weather.weather.length > 0 && weather.main != null) {
                                String condition = weather.weather[0].main;
                                double temp = weather.main.temp;
                                double humidity = weather.main.humidity;
                                double windSpeed = (weather.wind != null) ? weather.wind.speed : 0.0;

                                checkForecast(lat, lon, condition, temp, humidity, windSpeed);
                            }
                        } else {
                            Log.e(TAG, "Weather API Error Code: " + response.code());
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<MainActivity.WeatherResponse> call, @NonNull Throwable t) {
                        Log.e(TAG, "Weather API Failed: " + t.getMessage());
                    }
                });
    }

    private void checkForecast(double lat, double lon, String currentCondition, double temp, double humidity, double windSpeed) {
        if (weatherService == null) return;
        weatherService.getForecastByCoords(lat, lon, WEATHER_API_KEY, "metric")
                .enqueue(new Callback<MainActivity.ForecastResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<MainActivity.ForecastResponse> call, @NonNull Response<MainActivity.ForecastResponse> response) {
                        double rainProb = 0.0;
                        if (response.isSuccessful() && response.body() != null && response.body().list != null && !response.body().list.isEmpty()) {
                            MainActivity.ForecastResponse.ForecastItem firstItem = response.body().list.get(0);
                            if (firstItem != null) {
                                rainProb = firstItem.pop * 100;
                            }
                        }

                        WeatherHelper.checkAndNotifyWeatherChange(FloodMonitorService.this, currentCondition, temp, humidity, windSpeed, rainProb, selectedStationName);
                    }

                    @Override
                    public void onFailure(@NonNull Call<MainActivity.ForecastResponse> call, @NonNull Throwable t) {
                        Log.e(TAG, "Forecast fetch failed: " + t.getMessage());
                    }
                });
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager == null) return;

            // Channel for foreground service
            NotificationChannel fgChannel = new NotificationChannel(
                    FOREGROUND_CHANNEL_ID,
                    "Monitoring Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            fgChannel.setShowBadge(false);
            manager.createNotificationChannel(fgChannel);

            // Channel for high priority alerts
            NotificationChannel alertChannel = new NotificationChannel(
                    ALERT_CHANNEL_ID,
                    "Emergency Alerts",
                    NotificationManager.IMPORTANCE_HIGH
            );
            alertChannel.enableVibration(true);
            alertChannel.setVibrationPattern(new long[]{0, 800, 200, 800, 200, 800});
            if (FloodStatusHelper.isNotificationPolicyAccessGranted(this)) {
                try {
                    alertChannel.setBypassDnd(true);
                } catch (Throwable t) {
                    Log.w(TAG, "Could not set bypass DND: " + t.getMessage());
                }
            }
            alertChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            android.media.AudioAttributes alarmAttributes = new android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            alertChannel.setSound(android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM), alarmAttributes);
            manager.createNotificationChannel(alertChannel);
        }
    }

    private Notification getForegroundNotification() {
        return getForegroundNotification(lastStatus != null ? lastStatus : "SAFE");
    }

    private Notification getForegroundNotification(String status) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        notificationIntent.putExtra("station_id", selectedStationId);
        notificationIntent.putExtra("station_name", selectedStationName);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String statusStr = (status != null) ? " (" + status + ")" : "";

        return new NotificationCompat.Builder(this, FOREGROUND_CHANNEL_ID)
                .setContentTitle("Flood Monitoring Active")
                .setContentText("Monitoring water levels at " + selectedStationName + statusStr)
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setContentIntent(pendingIntent)
                .build();
    }

    private void updateForegroundNotification(String status) {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(FOREGROUND_NOTIFICATION_ID, getForegroundNotification(status));
        }
    }

    private void startMonitoring() {
        if (monitorListener != null && activeServiceRef != null) {
            activeServiceRef.removeEventListener(monitorListener);
        }

        lastNotifiedStatus = FloodStatusHelper.getSavedLastNotifiedStatusForStation(this, selectedStationId);
        boolean isCityWide = "MUN_ALL".equals(selectedStationId);

        monitorListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    try {
                        if (isCityWide) {
                            double maxLevel = -1.0;
                            Object maxTsObj = null;
                            boolean valid = false;
                            for (DataSnapshot stationSnap : snapshot.getChildren()) {
                                DataSnapshot currentSnap = stationSnap.child("Current");
                                Object rawValue = FloodStatusHelper.getWaterLevelValueFromSnapshot(currentSnap);
                                Object tsObj = FloodStatusHelper.getTimestampValueFromSnapshot(currentSnap);
                                long tsMillis = FloodStatusHelper.parseTimestampToMillis(tsObj);
                                boolean isStale = FloodStatusHelper.isStaleTimestamp(tsMillis);

                                if (rawValue != null && FloodStatusHelper.isValidWaterLevelReading(rawValue) && !isStale) {
                                    double lvl = FloodStatusHelper.parseWaterLevel(rawValue);
                                    if (lvl > maxLevel) {
                                        maxLevel = lvl;
                                        maxTsObj = tsObj;
                                    }
                                    valid = true;
                                }
                            }
                            if (valid) {
                                checkAndNotifyLevel(maxLevel, maxTsObj);
                            }
                        } else {
                            Object rawValue = FloodStatusHelper.getWaterLevelValueFromSnapshot(snapshot);
                            Object tsObj = FloodStatusHelper.getTimestampValueFromSnapshot(snapshot);
                            if (rawValue != null && FloodStatusHelper.isValidWaterLevelReading(rawValue)) {
                                double level = FloodStatusHelper.parseWaterLevel(rawValue);
                                checkAndNotifyLevel(level, tsObj);
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error in data listener: " + e.getMessage());
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Database monitoring cancelled: " + error.getMessage());
            }
        };

        if (isCityWide) {
            activeServiceRef = databaseRef.child("FloodMonitoringSystem").child("Stations");
        } else {
            activeServiceRef = databaseRef.child("FloodMonitoringSystem").child("Stations").child(selectedStationId).child("Current");
        }
        activeServiceRef.addValueEventListener(monitorListener);
    }

    private void checkAndNotifyLevel(double level, Object timestampObj) {
        long tsMillis = FloodStatusHelper.parseTimestampToMillis(timestampObj);
        long now = System.currentTimeMillis();
        boolean isStale = (tsMillis > 0 && (now - tsMillis) > 7200000L); // 2 hours

        String currentStatus = FloodStatusHelper.getStatus(level);
        String stationName = FloodStatusHelper.getStationDisplayName(selectedStationId);

        String statusDisplay = isStale ? currentStatus + " (Stale Data)" : currentStatus;
        updateForegroundNotification(statusDisplay);

        if (isStale) {
            lastStatus = currentStatus;
            lastNotifiedStatus = currentStatus;
            FloodStatusHelper.saveLastStatus(this, lastStatus);
            FloodStatusHelper.saveLastNotifiedStatusForStation(this, selectedStationId, lastNotifiedStatus);
            FlashlightHelper.stopFlashAlert(this);
            stopSoundAlert();
            stopVibration();
            return;
        }

        boolean isDanger = FloodStatusHelper.isDangerOrCritical(currentStatus);
        boolean isWarning = FloodStatusHelper.STATUS_WARNING.equalsIgnoreCase(currentStatus);

        boolean wasDanger = FloodStatusHelper.isDangerOrCritical(lastNotifiedStatus);
        boolean isNewTransitionToDanger = isDanger && !wasDanger;
        boolean statusChanged = !currentStatus.equalsIgnoreCase(lastNotifiedStatus);

        if (isDanger) {
            // EVENT-BASED DANGER THRESHOLD (> 70.0 cm)
            long lastSoundTime = FloodStatusHelper.getSavedLastCriticalSoundTime(this);
            boolean cooldownExpired = (lastSoundTime == 0) || ((now - lastSoundTime) >= CRITICAL_SOUND_COOLDOWN_MS);

            if (isNewTransitionToDanger || cooldownExpired) {
                FloodStatusHelper.saveLastCriticalSoundTime(this, now);

                String title = "DANGER FLOOD ALERT: " + stationName;
                String message = "DANGER: Water level at " + stationName + " is " + String.format(java.util.Locale.getDefault(), "%.1f", level) + " cm! Seek higher ground immediately.";
                sendAlertNotification(title, message, ALERT_NOTIFICATION_ID, true);

                FlashlightHelper.triggerFlashAlert(this);
                triggerVibration(true);
                triggerSoundAlert(); // Rings for exactly 5 seconds then auto-stops
                NotificationDeduplicator.markWaterLevelDelivered(this, selectedStationId, currentStatus, level);
            } else if (!NotificationDeduplicator.isDuplicateWaterLevel(this, selectedStationId, currentStatus, level)) {
                String title = "DANGER FLOOD ALERT: " + stationName;
                String message = "DANGER: Water level at " + stationName + " is " + String.format(java.util.Locale.getDefault(), "%.1f", level) + " cm! Seek higher ground immediately.";
                sendAlertNotification(title, message, ALERT_NOTIFICATION_ID, true);
                NotificationDeduplicator.markWaterLevelDelivered(this, selectedStationId, currentStatus, level);
            }

            lastNotifiedStatus = currentStatus;
            lastStatus = currentStatus;
            FloodStatusHelper.saveLastStatus(this, lastStatus);
            FloodStatusHelper.saveLastNotifiedStatusForStation(this, selectedStationId, lastNotifiedStatus);

        } else if (isWarning) {
            // Water level reached WARNING threshold (50.1 - 70.0 cm)
            // STOP & RESET danger-only alerts
            stopSoundAlert();
            FlashlightHelper.stopFlashAlert(this);
            stopVibration();
            FloodStatusHelper.saveLastCriticalSoundTime(this, 0L);

            if ((statusChanged || wasDanger) || !NotificationDeduplicator.isDuplicateWaterLevel(this, selectedStationId, currentStatus, level)) {
                String title = "FLOOD WARNING: " + stationName;
                String message = "Water level at " + stationName + " has reached WARNING level (" + String.format(java.util.Locale.getDefault(), "%.1f", level) + " cm). Stay alert and prepare emergency supplies.";
                sendAlertNotification(title, message, ALERT_NOTIFICATION_ID, false);
                NotificationDeduplicator.markWaterLevelDelivered(this, selectedStationId, currentStatus, level);
            }

            lastNotifiedStatus = currentStatus;
            lastStatus = currentStatus;
            FloodStatusHelper.saveLastStatus(this, lastStatus);
            FloodStatusHelper.saveLastNotifiedStatusForStation(this, selectedStationId, lastNotifiedStatus);

        } else {
            // Water level returned to SAFE (<= 50.0 cm)
            // STOP & RESET danger-only alerts
            stopSoundAlert();
            FlashlightHelper.stopFlashAlert(this);
            stopVibration();
            FloodStatusHelper.saveLastCriticalSoundTime(this, 0L);

            if (wasDanger) {
                String title = "FLOOD CLEAR ADVISORY: " + stationName;
                String message = "Water level at " + stationName + " has returned to SAFE range (" + String.format(java.util.Locale.getDefault(), "%.1f", level) + " cm).";
                sendClearNotification(title, message, ALERT_NOTIFICATION_ID);
                NotificationDeduplicator.markWaterLevelDelivered(this, selectedStationId, currentStatus, level);
            } else if (statusChanged || !NotificationDeduplicator.isDuplicateWaterLevel(this, selectedStationId, currentStatus, level)) {
                String title = "FLOOD ADVISORY: " + stationName;
                String message = "Water level at " + stationName + " is in SAFE range (" + String.format(java.util.Locale.getDefault(), "%.1f", level) + " cm).";
                sendClearNotification(title, message, ALERT_NOTIFICATION_ID);
                NotificationDeduplicator.markWaterLevelDelivered(this, selectedStationId, currentStatus, level);
            }

            lastNotifiedStatus = currentStatus;
            lastStatus = currentStatus;
            FloodStatusHelper.saveLastStatus(this, lastStatus);
            FloodStatusHelper.saveLastNotifiedStatusForStation(this, selectedStationId, lastNotifiedStatus);
        }
    }

    private void sendAlertNotification(String title, String message, int notificationId, boolean isCritical) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "Cannot post alert notification: POST_NOTIFICATIONS permission not granted.");
                return;
            }
        }

        Intent notificationIntent = new Intent(this, MainActivity.class);
        notificationIntent.putExtra("station_id", selectedStationId);
        notificationIntent.putExtra("station_name", selectedStationName);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        
        PendingIntent pendingIntent = PendingIntent.getActivity(this, notificationId, notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Intent to stop vibration and alarm
        Intent stopVibeIntent = new Intent(this, FloodMonitorService.class);
        stopVibeIntent.setAction("STOP_VIBRATION");
        PendingIntent stopVibePending = PendingIntent.getService(this, notificationId + 100, stopVibeIntent, PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(isCritical ? NotificationCompat.PRIORITY_MAX : NotificationCompat.PRIORITY_HIGH)
                .setVibrate(new long[]{0, 800, 200, 800, 200, 800})
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(false)
                .setAutoCancel(true)
                .setTimeoutAfter(isCritical ? 7200000L : 3600000L)
                .setContentIntent(pendingIntent)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Silence Alarm", stopVibePending);

        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(notificationId, builder.build());
        }
    }

    private void sendClearNotification(String title, String message, int notificationId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
        }

        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.cancel(notificationId);
        }

        Intent notificationIntent = new Intent(this, MainActivity.class);
        notificationIntent.putExtra("station_id", selectedStationId);
        notificationIntent.putExtra("station_name", selectedStationName);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(this, notificationId + 50, notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setOngoing(false)
                .setAutoCancel(true)
                .setTimeoutAfter(1800000L)
                .setContentIntent(pendingIntent);

        if (manager != null) {
            manager.notify(notificationId + 50, builder.build());
        }
    }

    private Vibrator getVibratorService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager vm = (VibratorManager) getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            return vm != null ? vm.getDefaultVibrator() : null;
        } else {
            return (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        }
    }

    private void triggerVibration(boolean isCritical) {
        try {
            Vibrator v = getVibratorService();
            if (v == null || !v.hasVibrator()) return;

            v.cancel(); // Stop current vibe

            if (isCritical) {
                // Pattern for DANGER/CRITICAL: ~7 seconds total with ALARM audio attributes
                long[] pattern = {0, 1000, 500, 1000, 500, 1000, 500, 1000, 500, 1000};
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    android.media.AudioAttributes audioAttributes = new android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build();
                    v.vibrate(VibrationEffect.createWaveform(pattern, -1), audioAttributes);
                } else {
                    v.vibrate(pattern, -1);
                }
            } else {
                // Short pulse for WARNING
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    v.vibrate(500);
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "Failed to trigger vibration: " + t.getMessage());
        }
    }

    private void stopVibration() {
        try {
            Vibrator v = getVibratorService();
            if (v != null) {
                v.cancel();
            }
        } catch (Throwable ignored) {}
    }

    private void triggerSoundAlert() {
        try {
            stopSoundAlert();
            alertMediaPlayer = MediaPlayer.create(this, R.raw.whistle);
            if (alertMediaPlayer == null) {
                Uri alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
                if (alarmUri == null) alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
                alertMediaPlayer = MediaPlayer.create(this, alarmUri);
            }
            if (alertMediaPlayer != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    AudioAttributes aa = new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build();
                    alertMediaPlayer.setAudioAttributes(aa);
                }
                alertMediaPlayer.setLooping(true);
                alertMediaPlayer.start();

                // Automatically stop sound after exactly 5 seconds
                soundHandler.removeCallbacksAndMessages(null);
                soundHandler.postDelayed(this::stopSoundAlert, SOUND_DURATION_MS);
            }
        } catch (Throwable t) {
            Log.e(TAG, "Failed to play sound alert: " + t.getMessage());
        }
    }

    private void stopSoundAlert() {
        try {
            soundHandler.removeCallbacksAndMessages(null);
            if (alertMediaPlayer != null) {
                if (alertMediaPlayer.isPlaying()) {
                    alertMediaPlayer.stop();
                }
                alertMediaPlayer.release();
                alertMediaPlayer = null;
            }
        } catch (Throwable ignored) {}
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            if ("STOP_VIBRATION".equals(intent.getAction()) || "STOP_ALARM".equals(intent.getAction())) {
                FlashlightHelper.stopFlashAlert(this);
                stopSoundAlert();
                stopVibration();
                FloodStatusHelper.saveLastCriticalSoundTime(this, System.currentTimeMillis());
            }

            if (intent.hasExtra("municipality")) {
                currentMunicipality = intent.getStringExtra("municipality");
                FloodStatusHelper.saveMunicipality(this, currentMunicipality);
            }
            
            boolean needsRestart = false;
            
            if (intent.hasExtra("station_name")) {
                selectedStationName = intent.getStringExtra("station_name");
            }

            if (intent.hasExtra("station_id")) {
                String newStation = intent.getStringExtra("station_id");
                if (newStation != null && !newStation.equals(selectedStationId)) {
                    selectedStationId = newStation;
                    needsRestart = true;
                }
            }
            
            // Save station info to SharedPreferences
            FloodStatusHelper.saveSelectedStation(this, selectedStationId, selectedStationName);

            // Update foreground notification
            updateForegroundNotification(lastStatus != null ? lastStatus : "SAFE");

            if (needsRestart || monitorListener == null) {
                startMonitoring();
            }
        } else {
            // Restarted by OS with null intent
            selectedStationId = FloodStatusHelper.getSavedStationId(this);
            selectedStationName = FloodStatusHelper.getSavedStationName(this);
            currentMunicipality = FloodStatusHelper.getSavedMunicipality(this);
            lastStatus = FloodStatusHelper.getSavedLastStatus(this);
            lastNotifiedStatus = FloodStatusHelper.getSavedLastNotifiedStatusForStation(this, selectedStationId);
            if (monitorListener == null) {
                startMonitoring();
            }
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (monitorListener != null && activeServiceRef != null) {
            activeServiceRef.removeEventListener(monitorListener);
        }
        if (weatherHandler != null) {
            weatherHandler.removeCallbacksAndMessages(null);
        }
        backgroundExecutor.shutdown();
        FlashlightHelper.stopFlashAlert(this);
        stopSoundAlert();
        stopVibration();
        super.onDestroy();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        try {
            Intent restartServiceIntent = new Intent(getApplicationContext(), this.getClass());
            restartServiceIntent.setPackage(getPackageName());

            PendingIntent restartServicePendingIntent;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                restartServicePendingIntent = PendingIntent.getForegroundService(
                        getApplicationContext(),
                        1,
                        restartServiceIntent,
                        PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE
                );
            } else {
                restartServicePendingIntent = PendingIntent.getService(
                        getApplicationContext(),
                        1,
                        restartServiceIntent,
                        PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE
                );
            }
            AlarmManager alarmService = (AlarmManager) getApplicationContext().getSystemService(Context.ALARM_SERVICE);
            if (alarmService != null) {
                alarmService.set(
                        AlarmManager.ELAPSED_REALTIME,
                        SystemClock.elapsedRealtime() + 1000,
                        restartServicePendingIntent
                );
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to schedule restart alarm: " + e.getMessage());
        }
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
