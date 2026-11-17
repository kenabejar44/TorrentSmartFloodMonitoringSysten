package com.example.mainpage;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;


public class AIAnalyticsActivity extends AppCompatActivity {

    private static final String TAG = "AI_ANALYTICS";
    private static final String DB_URL = "https://finalfirebase-45c72-default-rtdb.firebaseio.com";

    // UI Views
    private TextView txtAnalyticsStation, btnChangeStationAnalytics;
    private View cardStationSelector;

    private LinearLayout loadingLayout, emptyStateLayout;
    private TextView txtEmptyMessage;
    private Button btnRetryAnalytics;
    private ScrollView contentScrollView;

    // Section 1: Current Risk Classification
    private TextView txtAssessmentBadge, txtCurrentWaterLevelDisplay, txtAssessmentTitle, txtAssessmentExplanation;

    // Section 2: Trend & Rate Analysis
    private TextView txtCurrentLevelVal, txtPastLevelVal, txtRateOfChangeVal, txtTrendStatus;

    // Section 3: Mathematical Forecasting
    private TextView txtPredictedRiskBadge;
    private TextView txtEst10MinVal, txtEst10MinRisk;
    private TextView txtEst20MinVal, txtEst20MinRisk;
    private TextView txtEst30MinVal, txtEst30MinRisk;

    // Section 4: Historical Graph
    private WaterLevelChartView waterLevelChart;

    // Section 5 & 6: Explanation & Recommendations
    private TextView txtPredictionExplanation, txtRecommendation;
    private ImageView imgRecommendationIcon;

    // Database & State
    private DatabaseReference databaseRef;
    private DatabaseReference activeCurrentRef;
    private DatabaseReference activeHistoryRef;
    private ValueEventListener currentListener;
    private ValueEventListener historyListener;

    private String stationId = "ST001";
    private double currentWaterLevel = -1.0;
    private boolean hasValidCurrentReading = false;

    // Historical readings container
    private static class ReadingPoint {
        final long timestampMs;
        final double levelCm;

        ReadingPoint(long timestampMs, double levelCm) {
            this.timestampMs = timestampMs;
            this.levelCm = levelCm;
        }
    }

    private final List<ReadingPoint> historicalReadings = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ai_analytics);

        if (getIntent().hasExtra("station_id")) {
            stationId = getIntent().getStringExtra("station_id");
            FloodStatusHelper.saveSelectedStation(this, stationId, FloodStatusHelper.getStationDisplayName(stationId));
        } else {
            stationId = FloodStatusHelper.getSavedStationId(this);
        }

        initViews();
        setupToolbar();

        databaseRef = FirebaseDatabase.getInstance(DB_URL).getReference();

        loadAnalyticsData();
    }

    private void initViews() {
        txtAnalyticsStation = findViewById(R.id.txtAnalyticsStation);
        btnChangeStationAnalytics = findViewById(R.id.btnChangeStationAnalytics);
        cardStationSelector = findViewById(R.id.cardStationSelector);

        loadingLayout = findViewById(R.id.loadingLayout);
        emptyStateLayout = findViewById(R.id.emptyStateLayout);
        txtEmptyMessage = findViewById(R.id.txtEmptyMessage);
        btnRetryAnalytics = findViewById(R.id.btnRetryAnalytics);
        contentScrollView = findViewById(R.id.contentScrollView);

        txtAssessmentBadge = findViewById(R.id.txtAssessmentBadge);
        txtCurrentWaterLevelDisplay = findViewById(R.id.txtCurrentWaterLevelDisplay);
        txtAssessmentTitle = findViewById(R.id.txtAssessmentTitle);
        txtAssessmentExplanation = findViewById(R.id.txtAssessmentExplanation);

        txtCurrentLevelVal = findViewById(R.id.txtCurrentLevelVal);
        txtPastLevelVal = findViewById(R.id.txtPastLevelVal);
        txtRateOfChangeVal = findViewById(R.id.txtRateOfChangeVal);
        txtTrendStatus = findViewById(R.id.txtTrendStatus);

        txtPredictedRiskBadge = findViewById(R.id.txtPredictedRiskBadge);
        txtEst10MinVal = findViewById(R.id.txtEst10MinVal);
        txtEst10MinRisk = findViewById(R.id.txtEst10MinRisk);
        txtEst20MinVal = findViewById(R.id.txtEst20MinVal);
        txtEst20MinRisk = findViewById(R.id.txtEst20MinRisk);
        txtEst30MinVal = findViewById(R.id.txtEst30MinVal);
        txtEst30MinRisk = findViewById(R.id.txtEst30MinRisk);

        waterLevelChart = findViewById(R.id.waterLevelChart);
        txtPredictionExplanation = findViewById(R.id.txtPredictionExplanation);
        txtRecommendation = findViewById(R.id.txtRecommendation);
        imgRecommendationIcon = findViewById(R.id.imgRecommendationIcon);

        updateStationTitle();

        if (cardStationSelector != null) {
            cardStationSelector.setOnClickListener(v -> showStationSelectionDialog());
        }
        if (btnChangeStationAnalytics != null) {
            btnChangeStationAnalytics.setOnClickListener(v -> showStationSelectionDialog());
        }
        if (btnRetryAnalytics != null) {
            btnRetryAnalytics.setOnClickListener(v -> loadAnalyticsData());
        }
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        SafeAreaHelper.apply(this, findViewById(R.id.includeToolbar), null);

        if (toolbar != null) {
            toolbar.setTitle("Flood Analytics");
            toolbar.setNavigationOnClickListener(v -> finish());
        }
    }

    private void updateStationTitle() {
        String displayName = FloodStatusHelper.getStationDisplayName(stationId);
        if (txtAnalyticsStation != null) {
            txtAnalyticsStation.setText(String.format("Station: %s (%s)", displayName, stationId));
        }
    }

    private void showStationSelectionDialog() {
        final String[] ids = FloodStatusHelper.STATION_IDS;
        final String[] names = FloodStatusHelper.STATION_NAMES;

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Area / Barangay");
        builder.setItems(names, (dialog, which) -> {
            stationId = ids[which];
            FloodStatusHelper.saveSelectedStation(this, stationId, names[which]);

            Intent serviceIntent = new Intent(this, FloodMonitorService.class);
            serviceIntent.putExtra("station_id", stationId);
            serviceIntent.putExtra("station_name", names[which]);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }

            updateStationTitle();
            loadAnalyticsData();
        });
        builder.show();
    }

    private void showLoading(boolean show) {
        if (loadingLayout != null) loadingLayout.setVisibility(show ? View.VISIBLE : View.GONE);
        if (emptyStateLayout != null) emptyStateLayout.setVisibility(View.GONE);
        if (contentScrollView != null) contentScrollView.setVisibility(show ? View.GONE : View.VISIBLE);
    }

    private void showError(String message) {
        if (loadingLayout != null) loadingLayout.setVisibility(View.GONE);
        if (contentScrollView != null) contentScrollView.setVisibility(View.GONE);
        if (emptyStateLayout != null) {
            emptyStateLayout.setVisibility(View.VISIBLE);
            if (txtEmptyMessage != null) txtEmptyMessage.setText(message);
        }
    }

    private void detachListeners() {
        if (currentListener != null && activeCurrentRef != null) {
            activeCurrentRef.removeEventListener(currentListener);
        }
        if (historyListener != null && activeHistoryRef != null) {
            activeHistoryRef.removeEventListener(historyListener);
        }
    }

    private void loadAnalyticsData() {
        showLoading(true);
        detachListeners();

        boolean isCityWide = "MUN_ALL".equals(stationId);

        // 1. Setup Current Reading Listener
        currentListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (isFinishing() || isDestroyed()) return;

                currentWaterLevel = -1.0;
                hasValidCurrentReading = false;

                if (snapshot.exists()) {
                    try {
                        if (isCityWide) {
                            double maxLvl = -1.0;
                            for (DataSnapshot stationSnap : snapshot.getChildren()) {
                                DataSnapshot currentSnap = stationSnap.child("Current");
                                Object rawValue = FloodStatusHelper.getWaterLevelValueFromSnapshot(currentSnap);
                                if (rawValue != null && FloodStatusHelper.isValidWaterLevelReading(rawValue)) {
                                    double lvl = FloodStatusHelper.parseWaterLevel(rawValue);
                                    if (lvl > maxLvl) maxLvl = lvl;
                                    hasValidCurrentReading = true;
                                }
                            }
                            if (hasValidCurrentReading) currentWaterLevel = maxLvl;
                        } else {
                            Object rawValue = FloodStatusHelper.getWaterLevelValueFromSnapshot(snapshot);
                            if (rawValue != null && FloodStatusHelper.isValidWaterLevelReading(rawValue)) {
                                currentWaterLevel = FloodStatusHelper.parseWaterLevel(rawValue);
                                hasValidCurrentReading = true;
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing current level: " + e.getMessage());
                    }
                }

                // Fetch historical records to complete trend & forecasting analysis
                fetchHistoryData();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (isFinishing() || isDestroyed()) return;
                Log.e(TAG, "Database error: " + error.getMessage());
                showError("Unable to retrieve live sensor reading.\nPlease check database connection.");
            }
        };

        if (isCityWide) {
            activeCurrentRef = databaseRef.child("FloodMonitoringSystem").child("Stations");
        } else {
            activeCurrentRef = databaseRef.child("FloodMonitoringSystem").child("Stations").child(stationId).child("Current");
        }
        activeCurrentRef.addValueEventListener(currentListener);
    }

    private void fetchHistoryData() {
        boolean isCityWide = "MUN_ALL".equals(stationId);

        if (isCityWide) {
            activeHistoryRef = databaseRef.child("FloodMonitoringSystem").child("Stations");
        } else {
            activeHistoryRef = databaseRef.child("FloodMonitoringSystem").child("Stations").child(stationId).child("History");
        }

        historyListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (isFinishing() || isDestroyed()) return;

                historicalReadings.clear();

                if (snapshot.exists()) {
                    if (isCityWide) {
                        for (DataSnapshot stSnap : snapshot.getChildren()) {
                            DataSnapshot histSnap = stSnap.child("History");
                            for (DataSnapshot data : histSnap.getChildren()) {
                                addSnapshotToHistoricalReadings(data);
                            }
                        }
                    } else {
                        for (DataSnapshot data : snapshot.getChildren()) {
                            addSnapshotToHistoricalReadings(data);
                        }
                    }
                }

                // Append current reading if available and newer
                if (hasValidCurrentReading) {
                    long now = System.currentTimeMillis();
                    if (historicalReadings.isEmpty() || historicalReadings.get(historicalReadings.size() - 1).timestampMs < now - 5000) {
                        historicalReadings.add(new ReadingPoint(now, currentWaterLevel));
                    }
                }

                // Sort readings chronologically by timestamp
                historicalReadings.sort((a, b) -> Long.compare(a.timestampMs, b.timestampMs));

                // Process Analysis & Mathematical Forecasting
                performAnalyticsAndForecasting();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (isFinishing() || isDestroyed()) return;
                performAnalyticsAndForecasting(); // Proceed with current reading if history fetch fails
            }
        };

        activeHistoryRef.addListenerForSingleValueEvent(historyListener);
    }

    private void addSnapshotToHistoricalReadings(DataSnapshot data) {
        Object val = FloodStatusHelper.getWaterLevelValueFromSnapshot(data);
        Object tsObj = FloodStatusHelper.getTimestampValueFromSnapshot(data);

        if (val != null && FloodStatusHelper.isValidWaterLevelReading(val)) {
            double lvl = FloodStatusHelper.parseWaterLevel(val);
            long ts = FloodStatusHelper.parseTimestampToMillis(tsObj);
            if (ts <= 0) {
                ts = System.currentTimeMillis(); // Fallback timestamp if missing
            }
            historicalReadings.add(new ReadingPoint(ts, lvl));
        }
    }


    private void performAnalyticsAndForecasting() {
        if (!hasValidCurrentReading && historicalReadings.isEmpty()) {
            showError("No sensor data available for station " + FloodStatusHelper.getStationDisplayName(stationId) + ".");
            return;
        }

        showLoading(false);

        double levelToDisplay = hasValidCurrentReading ? currentWaterLevel : historicalReadings.get(historicalReadings.size() - 1).levelCm;

        // 1. Current Risk Classification
        String currentRiskStatus = FloodStatusHelper.getStatus(levelToDisplay);
        int currentStatusColor = FloodStatusHelper.getStatusColor(this, levelToDisplay);

        if (txtAssessmentBadge != null) {
            txtAssessmentBadge.setText(currentRiskStatus);
            txtAssessmentBadge.setBackgroundResource(getBadgeDrawableRes(currentRiskStatus));
        }
        if (txtCurrentWaterLevelDisplay != null) {
            txtCurrentWaterLevelDisplay.setText(String.format(Locale.getDefault(), "%.1f cm", levelToDisplay));
            txtCurrentWaterLevelDisplay.setTextColor(currentStatusColor);
        }
        if (txtAssessmentTitle != null) {
            txtAssessmentTitle.setText(String.format("%s Condition Classification", currentRiskStatus));
        }
        if (txtAssessmentExplanation != null) {
            txtAssessmentExplanation.setText(FloodStatusHelper.getStatusDescription(levelToDisplay));
        }

        // 2. Historical Trend Analysis & Rate of Change Calculation
        long now = System.currentTimeMillis();
        long oneHourAgo = now - 3600000L;
        double level1HourAgo = -1.0;

        // Find closest reading near 1 hour ago
        for (int i = historicalReadings.size() - 1; i >= 0; i--) {
            ReadingPoint pt = historicalReadings.get(i);
            if (pt.timestampMs <= oneHourAgo) {
                level1HourAgo = pt.levelCm;
                break;
            }
        }
        if (level1HourAgo < 0 && historicalReadings.size() > 1) {
            level1HourAgo = historicalReadings.get(0).levelCm; // Fallback to oldest available reading
        }

        // Calculate rate of change (m = slope in cm/minute) using Linear Regression over recent window (last 2 hours)
        long analysisCutoff = now - (2 * 3600000L);
        List<ReadingPoint> recentPoints = new ArrayList<>();
        for (ReadingPoint pt : historicalReadings) {
            if (pt.timestampMs >= analysisCutoff) {
                recentPoints.add(pt);
            }
        }
        if (recentPoints.size() < 2 && historicalReadings.size() >= 2) {
            recentPoints = historicalReadings.subList(Math.max(0, historicalReadings.size() - 10), historicalReadings.size());
        }

        double slopePerMinute = 0.0; // cm / min
        if (recentPoints.size() >= 2) {
            double sumX = 0, sumY = 0, sumXY = 0, sumXX = 0;
            long refTime = recentPoints.get(recentPoints.size() - 1).timestampMs; // Current time reference

            for (ReadingPoint pt : recentPoints) {
                double xMins = (pt.timestampMs - refTime) / 60000.0; // Minutes relative to latest reading (<= 0)
                double yLevel = pt.levelCm;

                sumX += xMins;
                sumY += yLevel;
                sumXY += (xMins * yLevel);
                sumXX += (xMins * xMins);
            }

            int n = recentPoints.size();
            double denominator = (n * sumXX) - (sumX * sumX);
            if (Math.abs(denominator) > 1e-6) {
                slopePerMinute = ((n * sumXY) - (sumX * sumY)) / denominator;
            }
        }

        double ratePer10Mins = slopePerMinute * 10.0; // cm / 10 min

        // Determine Trend direction
        String trendDirection;
        if (ratePer10Mins > 0.5) {
            trendDirection = FloodStatusHelper.TREND_RISING;
        } else if (ratePer10Mins < -0.5) {
            trendDirection = FloodStatusHelper.TREND_FALLING;
        } else {
            trendDirection = FloodStatusHelper.TREND_STABLE;
        }

        int trendColor = FloodStatusHelper.getTrendColor(this, trendDirection);

        if (txtCurrentLevelVal != null) {
            txtCurrentLevelVal.setText(String.format(Locale.getDefault(), "%.1f cm", levelToDisplay));
        }
        if (txtPastLevelVal != null) {
            txtPastLevelVal.setText(level1HourAgo < 0 ? "N/A" : String.format(Locale.getDefault(), "%.1f cm", level1HourAgo));
        }
        if (txtRateOfChangeVal != null) {
            String sign = ratePer10Mins > 0 ? "+" : "";
            txtRateOfChangeVal.setText(String.format(Locale.getDefault(), "%s%.1f cm / 10m", sign, ratePer10Mins));
        }
        if (txtTrendStatus != null) {
            txtTrendStatus.setText(trendDirection);
            txtTrendStatus.setTextColor(trendColor);
        }

        // 3. Mathematical Forecasting (+10m, +20m, +30m Projections)
        double est10Min = Math.max(0.0, levelToDisplay + (slopePerMinute * 10.0));
        double est20Min = Math.max(0.0, levelToDisplay + (slopePerMinute * 20.0));
        double est30Min = Math.max(0.0, levelToDisplay + (slopePerMinute * 30.0));

        String risk10Min = FloodStatusHelper.getStatus(est10Min);
        String risk20Min = FloodStatusHelper.getStatus(est20Min);
        String risk30Min = FloodStatusHelper.getStatus(est30Min);

        // Determine overall predicted risk classification (max risk over 30 min window)
        String overallPredictedRisk = getHighestRisk(risk10Min, risk20Min, risk30Min);

        if (txtEst10MinVal != null) txtEst10MinVal.setText(String.format(Locale.getDefault(), "%.1f cm", est10Min));
        if (txtEst10MinRisk != null) {
            txtEst10MinRisk.setText(risk10Min);
            txtEst10MinRisk.setBackgroundResource(getBadgeDrawableRes(risk10Min));
        }

        if (txtEst20MinVal != null) txtEst20MinVal.setText(String.format(Locale.getDefault(), "%.1f cm", est20Min));
        if (txtEst20MinRisk != null) {
            txtEst20MinRisk.setText(risk20Min);
            txtEst20MinRisk.setBackgroundResource(getBadgeDrawableRes(risk20Min));
        }

        if (txtEst30MinVal != null) txtEst30MinVal.setText(String.format(Locale.getDefault(), "%.1f cm", est30Min));
        if (txtEst30MinRisk != null) {
            txtEst30MinRisk.setText(risk30Min);
            txtEst30MinRisk.setBackgroundResource(getBadgeDrawableRes(risk30Min));
        }

        if (txtPredictedRiskBadge != null) {
            txtPredictedRiskBadge.setText(String.format("PREDICTED: %s", overallPredictedRisk));
            txtPredictedRiskBadge.setBackgroundResource(getBadgeDrawableRes(overallPredictedRisk));
        }

        // 4. Update Historical Graph
        if (waterLevelChart != null) {
            List<WaterLevelChartView.ChartPoint> chartPoints = new ArrayList<>();
            for (ReadingPoint pt : historicalReadings) {
                chartPoints.add(new WaterLevelChartView.ChartPoint(pt.timestampMs, pt.levelCm));
            }
            waterLevelChart.setChartData(chartPoints);
        }

        // 5. Generate Prediction Explanation Narrative
        String explanationText = buildPredictionExplanation(levelToDisplay, currentRiskStatus, trendDirection, ratePer10Mins, slopePerMinute, est10Min, est20Min, est30Min);
        if (txtPredictionExplanation != null) {
            txtPredictionExplanation.setText(explanationText);
        }

        // 6. Actionable Safety Recommendation
        String recommendationText = buildSafetyRecommendation(currentRiskStatus, overallPredictedRisk, trendDirection);
        if (txtRecommendation != null) {
            txtRecommendation.setText(recommendationText);
        }

        if (imgRecommendationIcon != null) {
            imgRecommendationIcon.setColorFilter(currentStatusColor);
        }
    }

    private int getBadgeDrawableRes(String status) {
        if (FloodStatusHelper.STATUS_SAFE.equalsIgnoreCase(status)) {
            return R.drawable.bg_badge_safe;
        } else if (FloodStatusHelper.STATUS_WARNING.equalsIgnoreCase(status)) {
            return R.drawable.bg_badge_warning;
        } else if (FloodStatusHelper.isDangerOrCritical(status)) {
            return R.drawable.bg_badge_critical;
        } else {
            return R.drawable.bg_badge_nodata;
        }
    }

    private String getHighestRisk(String... risks) {
        boolean hasCritical = false;
        boolean hasWarning = false;

        for (String r : risks) {
            if (FloodStatusHelper.isDangerOrCritical(r)) hasCritical = true;
            else if (FloodStatusHelper.STATUS_WARNING.equalsIgnoreCase(r)) hasWarning = true;
        }

        if (hasCritical) return FloodStatusHelper.STATUS_DANGER;
        if (hasWarning) return FloodStatusHelper.STATUS_WARNING;
        return FloodStatusHelper.STATUS_SAFE;
    }

    private String buildPredictionExplanation(double currentLevel, String currentStatus, String trend, double ratePer10Min, double slopePerMinute, double est10, double est20, double est30) {
        String stationName = FloodStatusHelper.getStationDisplayName(stationId);
        StringBuilder sb = new StringBuilder();

        sb.append(String.format(Locale.getDefault(),
                "The water level at %s is currently %.1f cm (%s condition) and is %s at approximately %.1f cm every 10 minutes.\n\n",
                stationName, currentLevel, currentStatus, trend.toLowerCase(), Math.abs(ratePer10Min)));

        sb.append(String.format(Locale.getDefault(),
                "Mathematical Forecasting (Linear Trend Model):\n" +
                        "• Estimated level in +10 mins: %.1f cm (%s)\n" +
                        "• Estimated level in +20 mins: %.1f cm (%s)\n" +
                        "• Estimated level in +30 mins: %.1f cm (%s)\n\n",
                est10, FloodStatusHelper.getStatus(est10),
                est20, FloodStatusHelper.getStatus(est20),
                est30, FloodStatusHelper.getStatus(est30)));

        if (slopePerMinute > 0.05) {
            if (currentLevel <= 50.0 && est30 > 50.0) {
                double minsToWarning = (50.0 - currentLevel) / slopePerMinute;
                sb.append(String.format(Locale.getDefault(),
                        "Threshold Analysis: Water level is projected to cross the WARNING threshold (50.0 cm) within approximately %.0f minutes.\n\n",
                        minsToWarning));
            } else if (currentLevel <= 70.0 && est30 > 70.0) {
                double minsToCritical = (70.0 - currentLevel) / slopePerMinute;
                sb.append(String.format(Locale.getDefault(),
                        "Threshold Warning: Water level is projected to reach the DANGER threshold (70.0 cm) within approximately %.0f minutes!\n\n",
                        minsToCritical));
            }
        }

        return sb.toString();
    }

    private String buildSafetyRecommendation(String currentStatus, String predictedStatus, String trend) {
        if (FloodStatusHelper.isDangerOrCritical(currentStatus) || FloodStatusHelper.isDangerOrCritical(predictedStatus)) {
            return "DANGER WARNING: Water level is at or approaching danger levels (>70 cm). Residents in low-lying barangays should prepare for immediate evacuation. Keep emergency kits, flashlights, and communications ready.";
        } else if (FloodStatusHelper.STATUS_WARNING.equalsIgnoreCase(currentStatus) || FloodStatusHelper.STATUS_WARNING.equalsIgnoreCase(predictedStatus)) {
            return "WARNING ADVISORY: Water level is elevated (50 cm – 70 cm) and trending " + trend.toLowerCase() + ". Stay alert, secure household items, and monitor official barangay and PAGASA weather announcements.";
        } else {
            return "NORMAL CONDITIONS: Water level is within the safe range (<50 cm). No immediate flood hazard. Continue to stay informed via local weather updates.";
        }
    }

    @Override
    protected void onDestroy() {
        detachListeners();
        super.onDestroy();
    }
}
