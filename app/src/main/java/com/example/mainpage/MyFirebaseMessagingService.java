package com.example.mainpage;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

/**
 * Handles Firebase Cloud Messaging (FCM) background push notifications.
 * Delivers Critical Water-Level Alerts, Official PAGASA Advisories,
 * Severe Weather Warnings, and Flood News when the app is in the background or killed.
 */
public class MyFirebaseMessagingService extends FirebaseMessagingService {

    private static final String TAG = "FCM_SERVICE";

    public static final String CHANNEL_CRITICAL = "flood_alerts_v3";
    public static final String CHANNEL_PAGASA = "pagasa_official_alerts_v1";
    public static final String CHANNEL_WEATHER = "weather_alerts_v2";
    public static final String CHANNEL_NEWS = "flood_news_alerts_v1";

    public static final int NOTIF_ID_CRITICAL = 3;
    public static final int NOTIF_ID_PAGASA = 1004;
    public static final int NOTIF_ID_WEATHER = 1003;
    public static final int NOTIF_ID_NEWS = 1005;

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        Log.d(TAG, "Refreshed FCM token: " + token);
        subscribeToTopics();
    }

    public static void subscribeToTopics() {
        try {
            FirebaseMessaging fm = FirebaseMessaging.getInstance();
            fm.subscribeToTopic("flood_alerts")
                    .addOnCompleteListener(task -> Log.d(TAG, "Subscribed to flood_alerts: " + task.isSuccessful()));
            fm.subscribeToTopic("pagasa_advisories")
                    .addOnCompleteListener(task -> Log.d(TAG, "Subscribed to pagasa_advisories: " + task.isSuccessful()));
            fm.subscribeToTopic("flood_news")
                    .addOnCompleteListener(task -> Log.d(TAG, "Subscribed to flood_news: " + task.isSuccessful()));
        } catch (Throwable t) {
            Log.e(TAG, "Failed to subscribe to FCM topics: " + t.getMessage());
        }
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        Log.d(TAG, "FCM message received from: " + remoteMessage.getFrom());

        Map<String, String> data = remoteMessage.getData();
        String title = null;
        String body = null;

        if (remoteMessage.getNotification() != null) {
            title = remoteMessage.getNotification().getTitle();
            body = remoteMessage.getNotification().getBody();
        }

        if (data.containsKey("title")) {
            title = data.get("title");
        }
        if (data.containsKey("body")) {
            body = data.get("body");
        } else if (data.containsKey("message")) {
            body = data.get("message");
        }

        if (title == null || title.isEmpty()) {
            title = "Flood Monitoring System";
        }
        if (body == null || body.isEmpty()) {
            body = "New update available.";
        }

        String type = data.get("type");
        if (type == null || type.isEmpty()) {
            type = "flood_alert";
        }

        String contentHash = data.get("hash");
        if (contentHash == null || contentHash.isEmpty()) {
            contentHash = Integer.toHexString((title + body).hashCode());
        }

        // Deduplication Check
        if (NotificationDeduplicator.isHashProcessed(this, contentHash)) {
            Log.d(TAG, "Duplicate FCM message suppressed (Hash: " + contentHash + ")");
            return;
        }

        NotificationDeduplicator.markHashProcessed(this, contentHash);

        switch (type.toLowerCase()) {
            case "critical":
            case "flood_alert":
            case "water_level":
                handleCriticalWaterAlert(data, title, body);
                break;
            case "pagasa":
            case "pagasa_advisory":
                handlePagasaAdvisory(data, title, body);
                break;
            case "weather":
            case "weather_alert":
                handleWeatherAlert(data, title, body);
                break;
            case "news":
            case "flood_news":
                handleFloodNews(data, title, body);
                break;
            default:
                handleCriticalWaterAlert(data, title, body);
                break;
        }
    }

    private void handleCriticalWaterAlert(Map<String, String> data, String title, String body) {
        String stationId = data.getOrDefault("station_id", "ST001");
        String stationName = data.getOrDefault("station_name", "Putatan");

        ensureChannel(CHANNEL_CRITICAL, "Emergency Water-Level Alerts", NotificationManager.IMPORTANCE_HIGH, true);

        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("station_id", stationId);
        intent.putExtra("station_name", stationName);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                NOTIF_ID_CRITICAL,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_CRITICAL)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        postNotification(NOTIF_ID_CRITICAL, builder.build());
    }

    private void handlePagasaAdvisory(Map<String, String> data, String title, String body) {
        String targetTab = data.getOrDefault("target_tab", "weather");

        ensureChannel(CHANNEL_PAGASA, "PAGASA Official Advisories", NotificationManager.IMPORTANCE_HIGH, false);

        Intent intent = new Intent(this, FloodNewsActivity.class);
        intent.putExtra("target_tab", targetTab);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                NOTIF_ID_PAGASA,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String displayTitle = title.toUpperCase().startsWith("PAGASA") ? title : "PAGASA OFFICIAL ADVISORY: " + title;

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_PAGASA)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(displayTitle)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_EVENT)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        postNotification(NOTIF_ID_PAGASA, builder.build());
    }

    private void handleWeatherAlert(Map<String, String> data, String title, String body) {
        boolean isSevere = "true".equalsIgnoreCase(data.get("is_severe"));

        ensureChannel(CHANNEL_WEATHER, "Local Weather Forecasts", isSevere ? NotificationManager.IMPORTANCE_HIGH : NotificationManager.IMPORTANCE_DEFAULT, false);

        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                NOTIF_ID_WEATHER,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String displayTitle = title.toUpperCase().startsWith("LOCAL WEATHER") ? title : "LOCAL WEATHER UPDATE: " + title;

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_WEATHER)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(displayTitle)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(isSevere ? NotificationCompat.PRIORITY_HIGH : NotificationCompat.PRIORITY_DEFAULT)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        postNotification(NOTIF_ID_WEATHER, builder.build());
    }

    private void handleFloodNews(Map<String, String> data, String title, String body) {
        ensureChannel(CHANNEL_NEWS, "Flood News Updates", NotificationManager.IMPORTANCE_DEFAULT, false);

        Intent intent = new Intent(this, FloodNewsActivity.class);
        intent.putExtra("target_tab", "flood");
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                NOTIF_ID_NEWS,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_NEWS)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        postNotification(NOTIF_ID_NEWS, builder.build());
    }

    private void ensureChannel(String channelId, String name, int importance, boolean bypassDnd) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                NotificationChannel channel = new NotificationChannel(channelId, name, importance);
                channel.setLockscreenVisibility(android.app.Notification.VISIBILITY_PUBLIC);
                if (bypassDnd && FloodStatusHelper.isNotificationPolicyAccessGranted(this)) {
                    try {
                        channel.setBypassDnd(true);
                    } catch (Throwable ignored) {}
                }
                nm.createNotificationChannel(channel);
            }
        }
    }

    private void postNotification(int notificationId, android.app.Notification notification) {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(notificationId, notification);
        }
    }
}
