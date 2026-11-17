package com.example.mainpage;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

/**
 * Thread-safe Notification Deduplicator helper using persistent SharedPreferences.
 * Prevents repeated duplicate notifications for identical events, water level readings,
 * weather updates, PAGASA advisories, and flood news across Firebase reconnections,
 * background workers, and FCM push messages.
 */
public class NotificationDeduplicator {

    private static final String PREF_NAME = "FloodNotificationDeduplicator";
    private static final String TAG = "NotificationDeduplicator";

    /**
     * Checks if an event with the given eventKey and valueHash is a duplicate within cooldownMs.
     */
    public static synchronized boolean isDuplicate(Context context, String eventKey, String valueHash, long cooldownMs) {
        if (context == null || eventKey == null || valueHash == null) return false;
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);

        String lastHashKey = "hash_" + eventKey;
        String lastTimeKey = "time_" + eventKey;

        String savedHash = prefs.getString(lastHashKey, null);
        long savedTime = prefs.getLong(lastTimeKey, 0L);
        long now = System.currentTimeMillis();

        if (valueHash.equals(savedHash)) {
            if (cooldownMs <= 0) {
                return true; // Permanent duplicate until value changes
            }
            if ((now - savedTime) < cooldownMs) {
                Log.d(TAG, "Duplicate suppressed for key: " + eventKey + " (Hash: " + valueHash + ")");
                return true;
            }
        }
        return false;
    }

    /**
     * Marks an eventKey and valueHash as delivered with the current timestamp.
     */
    public static synchronized void markDelivered(Context context, String eventKey, String valueHash) {
        if (context == null || eventKey == null || valueHash == null) return;
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .putString("hash_" + eventKey, valueHash)
                .putLong("time_" + eventKey, System.currentTimeMillis())
                .apply();
    }

    /**
     * Checks if a unique content hash (e.g. PAGASA advisory hash or News article ID) has already been processed.
     */
    public static synchronized boolean isHashProcessed(Context context, String hash) {
        if (context == null || hash == null || hash.isEmpty()) return false;
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.contains("processed_" + hash);
    }

    /**
     * Marks a content hash as processed.
     */
    public static synchronized void markHashProcessed(Context context, String hash) {
        if (context == null || hash == null || hash.isEmpty()) return;
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putLong("processed_" + hash, System.currentTimeMillis()).apply();
    }

    /**
     * Water level deduplication logic:
     * Returns true if the status and water level for a station have NOT meaningfully changed.
     */
    public static synchronized boolean isDuplicateWaterLevel(Context context, String stationId, String status, double level) {
        if (context == null || stationId == null || status == null) return false;
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);

        String statusKey = "water_status_" + stationId;
        String levelKey = "water_level_" + stationId;
        String timeKey = "water_time_" + stationId;

        String savedStatus = prefs.getString(statusKey, null);
        float savedLevel = prefs.getFloat(levelKey, -1.0f);
        long savedTime = prefs.getLong(timeKey, 0L);
        long now = System.currentTimeMillis();

        boolean sameStatus = status.equalsIgnoreCase(savedStatus);
        boolean levelUnchanged = Math.abs((float) level - savedLevel) < 2.0f; // Less than 2 cm change
        boolean cooldownActive = (now - savedTime) < 3600000L; // 1 hour cooldown for same status/level

        if (sameStatus && levelUnchanged && cooldownActive) {
            return true;
        }

        return false;
    }

    /**
     * Marks a water level alert state as delivered for a specific station.
     */
    public static synchronized void markWaterLevelDelivered(Context context, String stationId, String status, double level) {
        if (context == null || stationId == null || status == null) return;
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .putString("water_status_" + stationId, status)
                .putFloat("water_level_" + stationId, (float) level)
                .putLong("water_time_" + stationId, System.currentTimeMillis())
                .apply();
    }
}
