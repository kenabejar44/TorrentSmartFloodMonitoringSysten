package com.example.mainpage;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.core.content.ContextCompat;
import com.google.firebase.database.DataSnapshot;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Standardized helper for Water Level Status, Thresholds, Trends, Stations, and Data Freshness.
 * Ensures consistent terminology and thresholds across the entire application:
 * - SAFE: Water level <= 50.0 cm
 * - WARNING: Water level 50.1 - 70.0 cm
 * - DANGER / CRITICAL: Water level > 70.0 cm
 * - Reference Height: 200.0 cm
 * - Trends: Rising, Falling, Stable
 */
public class FloodStatusHelper {

    public static final String PREFS_NAME = "FloodMonitorPrefs";
    public static final String KEY_STATION_ID = "selected_station_id";
    public static final String KEY_STATION_NAME = "selected_station_name";
    public static final String KEY_MUNICIPALITY = "current_municipality";
    public static final String KEY_LAST_STATUS = "last_notified_status";
    public static final String KEY_LAST_WEATHER_STATE = "last_notified_weather_state";
    public static final String KEY_LAST_WEATHER_TIME = "last_notified_weather_time";

    public static void saveSelectedStation(Context context, String stationId, String stationName) {
        if (context == null) return;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .putString(KEY_STATION_ID, stationId)
                .putString(KEY_STATION_NAME, stationName)
                .apply();
    }

    public static String getSavedStationId(Context context) {
        if (context == null) return "ST001";
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_STATION_ID, "ST001");
    }

    public static String getSavedStationName(Context context) {
        if (context == null) return "Putatan";
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_STATION_NAME, "Putatan");
    }

    public static void saveMunicipality(Context context, String municipality) {
        if (context == null) return;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_MUNICIPALITY, municipality).apply();
    }

    public static String getSavedMunicipality(Context context) {
        if (context == null) return "Muntinlupa";
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_MUNICIPALITY, "Muntinlupa");
    }

    public static void saveLastStatus(Context context, String status) {
        if (context == null) return;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_LAST_STATUS, status).apply();
    }

    public static String getSavedLastStatus(Context context) {
        if (context == null) return null;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_LAST_STATUS, null);
    }

    public static void saveLastNotifiedStatusForStation(Context context, String stationId, String status) {
        if (context == null || stationId == null) return;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_LAST_STATUS + "_" + stationId, status).apply();
    }

    public static String getSavedLastNotifiedStatusForStation(Context context, String stationId) {
        if (context == null || stationId == null) return null;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_LAST_STATUS + "_" + stationId, null);
    }

    public static final String KEY_LAST_CRITICAL_SOUND_TIME = "last_critical_sound_time";

    public static void saveLastCriticalSoundTime(Context context, long timeMs) {
        if (context == null) return;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putLong(KEY_LAST_CRITICAL_SOUND_TIME, timeMs).apply();
    }

    public static long getSavedLastCriticalSoundTime(Context context) {
        if (context == null) return 0L;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getLong(KEY_LAST_CRITICAL_SOUND_TIME, 0L);
    }

    public static void saveLastWeatherState(Context context, String state) {
        if (context == null) return;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_LAST_WEATHER_STATE, state).apply();
    }

    public static String getSavedLastWeatherState(Context context) {
        if (context == null) return "NORMAL";
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_LAST_WEATHER_STATE, "NORMAL");
    }

    public static void saveLastWeatherTime(Context context, long timeMs) {
        if (context == null) return;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putLong(KEY_LAST_WEATHER_TIME, timeMs).apply();
    }

    public static long getSavedLastWeatherTime(Context context) {
        if (context == null) return 0L;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getLong(KEY_LAST_WEATHER_TIME, 0L);
    }

    public static final String KEY_LAST_PAGASA_HASH = "last_pagasa_advisory_hash";
    public static final String KEY_LAST_PAGASA_TITLE = "last_pagasa_advisory_title";
    public static final String KEY_LAST_PAGASA_TIME = "last_pagasa_advisory_time";

    public static void saveLastPagasaAdvisory(Context context, String hash, String title, long timeMs) {
        if (context == null) return;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .putString(KEY_LAST_PAGASA_HASH, hash)
                .putString(KEY_LAST_PAGASA_TITLE, title)
                .putLong(KEY_LAST_PAGASA_TIME, timeMs)
                .apply();
    }

    public static String getSavedLastPagasaHash(Context context) {
        if (context == null) return "";
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_LAST_PAGASA_HASH, "");
    }

    public static String getSavedLastPagasaTitle(Context context) {
        if (context == null) return "";
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_LAST_PAGASA_TITLE, "");
    }

    public static double parseWaterLevel(Object rawValue) {
        if (rawValue == null) return Double.NaN;
        if (rawValue instanceof Number) {
            double val = ((Number) rawValue).doubleValue();
            return Double.isNaN(val) || Double.isInfinite(val) ? Double.NaN : val;
        }
        if (rawValue instanceof String) {
            String str = ((String) rawValue).trim();
            if (str.isEmpty()) return Double.NaN;
            try {
                return Double.parseDouble(str);
            } catch (NumberFormatException e) {
                String numericOnly = str.replaceAll("[^0-9.-]", "");
                if (!numericOnly.isEmpty() && !"-".equals(numericOnly) && !".".equals(numericOnly)) {
                    try {
                        return Double.parseDouble(numericOnly);
                    } catch (Exception ignored) {}
                }
            }
        }
        return Double.NaN;
    }

    public static boolean isValidWaterLevelReading(Object rawValue) {
        if (rawValue == null) return false;
        double level = parseWaterLevel(rawValue);
        return !Double.isNaN(level) && !Double.isInfinite(level) && level >= 0.0 && level <= 2000.0;
    }

    public static Object getWaterLevelValueFromSnapshot(DataSnapshot snapshot) {
        if (snapshot == null || !snapshot.exists()) return null;
        String[] fieldKeys = {
            "WaterLevel", "Waterlevel", "waterLevel", "waterlevel", "water_level",
            "WaterLevel::", "Waterlevel::", "Level", "level", "Value", "value"
        };
        for (String key : fieldKeys) {
            if (snapshot.hasChild(key)) {
                Object val = snapshot.child(key).getValue();
                if (val != null) return val;
            }
        }
        // Fallback: If the snapshot itself holds a primitive value (e.g. directly at node)
        Object val = snapshot.getValue();
        if (val instanceof Number || val instanceof String) {
            return val;
        }
        return null;
    }

    public static Object getTimestampValueFromSnapshot(DataSnapshot snapshot) {
        if (snapshot == null || !snapshot.exists()) return null;
        String[] fieldKeys = {
            "Timestamp", "timestamp", "Timestamp::", "timestamp::", "Time", "time", "Date", "date"
        };
        for (String key : fieldKeys) {
            if (snapshot.hasChild(key)) {
                Object val = snapshot.child(key).getValue();
                if (val != null) return val;
            }
        }
        // Fallback: Check direct value if not a map with children
        Object val = snapshot.getValue();
        if (val instanceof Number || val instanceof String) {
            return val;
        }
        return null;
    }

    public static String getStatusFromSnapshot(DataSnapshot snapshot) {
        if (snapshot == null || !snapshot.exists()) return null;
        Object rawVal = getWaterLevelValueFromSnapshot(snapshot);
        if (isValidWaterLevelReading(rawVal)) {
            double lvl = parseWaterLevel(rawVal);
            return getStatus(lvl);
        }
        return null;
    }

    public static Double getPercentageValueFromSnapshot(DataSnapshot snapshot) {
        if (snapshot == null || !snapshot.exists()) return null;
        String[] fieldKeys = {
            "Percentage", "percentage", "Percent", "percent", "WaterLevelPercent", "water_level_percent"
        };
        for (String key : fieldKeys) {
            if (snapshot.hasChild(key)) {
                Object val = snapshot.child(key).getValue();
                if (val != null) {
                    double parsed = parseWaterLevel(val);
                    if (!Double.isNaN(parsed)) return parsed;
                }
            }
        }
        return null;
    }

    public static long parseTimestampToMillis(Object tsObj) {
        if (tsObj == null) return 0L;
        if (tsObj instanceof Number) {
            long val = ((Number) tsObj).longValue();
            if (val > 0 && val < 10000000000L) {
                return val * 1000L;
            }
            return val;
        }
        if (tsObj instanceof String) {
            String str = ((String) tsObj).trim();
            if (str.isEmpty()) return 0L;
            try {
                long val = Long.parseLong(str);
                if (val > 0 && val < 10000000000L) return val * 1000L;
                if (val >= 10000000000L) return val;
            } catch (NumberFormatException ignored) {}

            String[] formats = {
                "MMM dd, yyyy hh:mm a",
                "MMM dd, yyyy HH:mm",
                "yyyy-MM-dd HH:mm:ss",
                "yyyy-MM-dd'T'HH:mm:ss"
            };
            for (String fmt : formats) {
                try {
                    SimpleDateFormat sdf = new SimpleDateFormat(fmt, Locale.getDefault());
                    Date d = sdf.parse(str);
                    if (d != null) return d.getTime();
                } catch (Exception ignored) {}
            }
        }
        return 0L;
    }

    public static final double SAFE_THRESHOLD = 50.0;
    public static final double WARNING_THRESHOLD = 70.0;
    public static final double REFERENCE_HEIGHT = 200.0;

    public static final String STATUS_SAFE = "SAFE";
    public static final String STATUS_WARNING = "WARNING";
    public static final String STATUS_DANGER = "DANGER";
    public static final String STATUS_CRITICAL = "CRITICAL";

    public static final String TREND_RISING = "Rising";
    public static final String TREND_FALLING = "Falling";
    public static final String TREND_STABLE = "Stable";

    public static final String[] STATION_IDS = {
        "MUN_ALL", "ST001", "ST002", "ST003", "ST004", "ST005", "ST006", "ST007", "ST008", "ST009"
    };

    public static final String[] STATION_NAMES = {
        "All Stations", "Putatan", "Alabang", "Bayanan", "Buli", "Cupang", "Poblacion", "Sucat", "Tunasan", "Ayala Alabang"
    };

    public static boolean isDangerOrCritical(String status) {
        if (status == null) return false;
        String s = status.trim().toUpperCase(Locale.ROOT);
        return STATUS_DANGER.equals(s) || STATUS_CRITICAL.equals(s) || s.contains("DANG") || s.contains("CRIT");
    }

    public static String getStatus(double waterLevelCm) {
        if (Double.isNaN(waterLevelCm) || Double.isInfinite(waterLevelCm) || waterLevelCm < 0.0) {
            return "NO DATA";
        }
        if (waterLevelCm <= SAFE_THRESHOLD) {
            return STATUS_SAFE;
        } else if (waterLevelCm <= WARNING_THRESHOLD) {
            return STATUS_WARNING;
        } else {
            return STATUS_DANGER;
        }
    }

    public static int getStatusColor(Context context, double waterLevelCm) {
        return getStatusColorForStatus(context, getStatus(waterLevelCm));
    }

    public static int getStatusColorForStatus(Context context, String status) {
        if (context == null) return 0;
        if (status == null) {
            return ContextCompat.getColor(context, R.color.text_secondary);
        }
        String upper = status.trim().toUpperCase(Locale.ROOT);
        if (STATUS_SAFE.equals(upper) || upper.contains("SAFE")) {
            return ContextCompat.getColor(context, R.color.safe_green);
        } else if (STATUS_WARNING.equals(upper) || upper.contains("WARN")) {
            return ContextCompat.getColor(context, R.color.warning_orange);
        } else if (isDangerOrCritical(upper)) {
            return ContextCompat.getColor(context, R.color.critical_red);
        } else {
            return ContextCompat.getColor(context, R.color.text_secondary);
        }
    }

    public static String getStatusDescription(double waterLevelCm) {
        return getStatusDescriptionForStatus(getStatus(waterLevelCm));
    }

    public static String getStatusDescriptionForStatus(String status) {
        if (status == null) {
            return "Unable to retrieve live sensor reading from station.";
        }
        String upper = status.trim().toUpperCase(Locale.ROOT);
        if (STATUS_SAFE.equals(upper) || upper.contains("SAFE")) {
            return "Water level is within safe limit (<= 50.0 cm). No immediate hazard.";
        } else if (STATUS_WARNING.equals(upper) || upper.contains("WARN")) {
            return "Water level is elevated (50.1 - 70.0 cm). Stay alert and prepare emergency supplies.";
        } else if (isDangerOrCritical(upper)) {
            return "Danger water level reached (> 70.0 cm)! Evacuate low-lying areas immediately.";
        } else {
            return "Unable to retrieve live sensor reading from station.";
        }
    }

    public static String getTrend(double currentLevel, double pastLevel) {
        if (pastLevel < 0 || Double.isNaN(currentLevel) || Double.isNaN(pastLevel)) {
            return TREND_STABLE;
        }
        double diff = currentLevel - pastLevel;
        if (diff > 2.0) {
            return TREND_RISING;
        } else if (diff < -2.0) {
            return TREND_FALLING;
        } else {
            return TREND_STABLE;
        }
    }

    public static int getTrendColor(Context context, String trend) {
        if (TREND_RISING.equals(trend)) {
            return ContextCompat.getColor(context, R.color.critical_red);
        } else if (TREND_FALLING.equals(trend)) {
            return ContextCompat.getColor(context, R.color.safe_green);
        } else {
            return ContextCompat.getColor(context, R.color.primary_blue);
        }
    }

    public static String getStationDisplayName(String stationId) {
        if (stationId == null) return "Putatan";
        for (int i = 0; i < STATION_IDS.length; i++) {
            if (STATION_IDS[i].equalsIgnoreCase(stationId)) {
                return STATION_NAMES[i];
            }
        }
        return stationId;
    }

    public static String formatFreshness(String timestamp) {
        if (timestamp == null || timestamp.trim().isEmpty()) {
            return "Data Freshness: Unknown";
        }
        return "Last Updated: " + timestamp;
    }

    public static String formatFreshnessWithAge(long timestampMillis) {
        if (timestampMillis <= 0) {
            return "Last Updated: Unknown";
        }
        long diffMs = System.currentTimeMillis() - timestampMillis;
        long mins = diffMs / 60000;
        
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault());
        String formattedDate = sdf.format(new Date(timestampMillis));

        if (mins < 15) {
            return "Last Updated: " + formattedDate + " (Live)";
        } else if (mins < 60) {
            return "Last Updated: " + formattedDate + " (" + mins + "m ago)";
        } else {
            return "Last Updated: " + formattedDate + " (Potentially Outdated)";
        }
    }

    public static boolean isNotificationPolicyAccessGranted(Context context) {
        if (context == null) return true;
        android.app.NotificationManager nm = (android.app.NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        return nm != null && nm.isNotificationPolicyAccessGranted();
    }
}
