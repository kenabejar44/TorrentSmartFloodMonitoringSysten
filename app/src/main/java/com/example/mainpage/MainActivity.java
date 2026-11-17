package com.example.mainpage;

import android.Manifest;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.net.Uri;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.material.navigation.NavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.GET;
import retrofit2.http.Query;

public class MainActivity extends AppCompatActivity {

    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private com.google.android.material.appbar.MaterialToolbar toolbar;
    private TextView txtWaterLevel, txtAIAdvice;
    private TextView statusText, trendText, lastUpdatedText, stationName, changeStationBtn;
    private View waterLevelCircle;
    private View fabAI;

    // Drawer Header Views
    private TextView txtDrawerName, txtDrawerPhone;

    // Firebase
    private FirebaseAuth mAuth;
    private DatabaseReference databaseRef;
    private DatabaseReference activeWaterLevelRef;
    private DatabaseReference connectedRef;
    private ValueEventListener waterLevelListener;
    private ValueEventListener connectedListener;
    private ValueEventListener drawerUserListener;
    private String selectedStationId = "ST001";
    private String selectedStationName = "Putatan";
    private final String DB_URL = "https://finalfirebase-45c72-default-rtdb.firebaseio.com";
    
    private FusedLocationProviderClient fusedLocationClient;
    private double currentWaterLevel = 0.0;
    private String currentMunicipality = "Muntinlupa"; // Default
    private double currentLat = 14.3917, currentLon = 121.0445; // Defaults
    private String lastStatus = "SAFE"; // Track status changes
    private String lastNotifiedStatus = null;
    private MediaPlayer foregroundMediaPlayer;
    private final Handler foregroundSoundHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();

    // Weather API (OpenWeatherMap)
    private static final String WEATHER_API_KEY = BuildConfig.WEATHER_API_KEY;
    private WeatherService weatherService;
    private FirebaseAIHelper aiHelper;

    // Smart AI Logic
    public static class SmartAI {
        public static String getWeatherAdvice(String condition, double temp, double waterLevel) {
            String advice;
            if (waterLevel > 70) {
                advice = "DANGER: High water levels detected! Evacuate immediately to the nearest center if your area is prone to flooding.";
            } else if (condition != null && (condition.toLowerCase().contains("rain") || condition.toLowerCase().contains("storm") || condition.toLowerCase().contains("thunderstorm"))) {
                if (waterLevel > 50) {
                    advice = "WARNING: " + condition + " detected with rising water levels. Prepare your emergency kit and stay alert.";
                } else {
                    advice = "ADVISORY: " + condition + " detected. Roads may be slippery and visibility reduced. Avoid low-lying areas.";
                }
            } else if (waterLevel > 50) {
                advice = "ADVISORY: Water levels are rising even without local rain. Please check conditions in neighboring areas.";
            } else {
                advice = "Current status is SAFE. Stay informed of weather updates via PAGASA and local authorities.";
            }
            return "AI SAFETY UPDATE: " + advice;
        }
    }

    // Retrofit Interface for Weather
    public interface WeatherService {
        @GET("weather")
        Call<WeatherResponse> getCurrentWeather(
            @Query("q") String city,
            @Query("appid") String apiKey,
            @Query("units") String units
        );

        @GET("weather")
        Call<WeatherResponse> getCurrentWeatherByCoords(
            @Query("lat") double lat,
            @Query("lon") double lon,
            @Query("appid") String apiKey,
            @Query("units") String units
        );

        @GET("forecast")
        Call<ForecastResponse> getForecast(
            @Query("q") String city,
            @Query("appid") String apiKey,
            @Query("units") String units
        );

        @GET("forecast")
        Call<ForecastResponse> getForecastByCoords(
            @Query("lat") double lat,
            @Query("lon") double lon,
            @Query("appid") String apiKey,
            @Query("units") String units
        );
    }

    // Weather Data Model
    public static class WeatherResponse {
        public Main main;
        public Weather[] weather;
        public Wind wind;
        public static class Main { public double temp; public double humidity; }
        public static class Weather { public String main; public String description; }
        public static class Wind { public double speed; }
    }

    public static class ForecastResponse {
        public List<ForecastItem> list;
        public static class ForecastItem {
            public long dt;
            public WeatherResponse.Main main;
            public WeatherResponse.Weather[] weather;
            public WeatherResponse.Wind wind;
            public double pop; // Probability of precipitation
            public String dt_txt;
        }
    }

    private final ActivityResultLauncher<String[]> locationPermissionRequest =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                boolean coarseGranted = Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_COARSE_LOCATION));
                boolean fineGranted = Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_FINE_LOCATION));

                if (coarseGranted) {
                    String msg = fineGranted ? "Precise location enabled" : "Approximate location enabled";
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();

                    if (isLocationEnabled()) {
                        getCurrentLocation();
                    } else {
                        showLocationSettingsDialog();
                    }
                } else {
                    Toast.makeText(this, "Location denied. Cannot detect Barangay automatically.", Toast.LENGTH_LONG).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        
        // Handle incoming station from notification or restore saved station
        if (getIntent().hasExtra("station_id")) {
            selectedStationId = getIntent().getStringExtra("station_id");
            selectedStationName = getIntent().getStringExtra("station_name");
            FloodStatusHelper.saveSelectedStation(this, selectedStationId, selectedStationName);
        } else {
            selectedStationId = FloodStatusHelper.getSavedStationId(this);
            selectedStationName = FloodStatusHelper.getSavedStationName(this);
        }

        mAuth = FirebaseAuth.getInstance();
        databaseRef = FirebaseDatabase.getInstance(DB_URL).getReference();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Initialize Weather Service
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://api.openweathermap.org/data/2.5/")
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        weatherService = retrofit.create(WeatherService.class);

        try {
            aiHelper = new FirebaseAIHelper();
        } catch (Throwable t) {
            Log.e("MAIN", "AI Helper init failed: " + t.getMessage());
        }

        // UI
        drawerLayout = findViewById(R.id.drawerLayout);
        navigationView = findViewById(R.id.navigationView);
        toolbar = findViewById(R.id.toolbar);
        View appBarLayout = findViewById(R.id.includeToolbar);
        
        txtWaterLevel = findViewById(R.id.txtWaterLevel);
        waterLevelCircle = findViewById(R.id.waterLevelCircle);
        fabAI = findViewById(R.id.cardFabAI);
        txtAIAdvice = findViewById(R.id.txtAIAdvice);
        statusText = findViewById(R.id.statusText);
        trendText = findViewById(R.id.trendText);
        lastUpdatedText = findViewById(R.id.lastUpdatedText);
        stationName = findViewById(R.id.stationName);
        changeStationBtn = findViewById(R.id.changeStationBtn);

        if (toolbar != null) {
            toolbar.setNavigationIcon(android.R.drawable.ic_menu_sort_by_size);
            toolbar.setNavigationIconTint(ContextCompat.getColor(this, R.color.white));
            toolbar.setTitle("Dashboard");
        }

        if (changeStationBtn != null) {
            changeStationBtn.setOnClickListener(v -> showStationSelectionDialog());
        }

        if (drawerLayout == null || navigationView == null || toolbar == null) {
            Log.e("APP_DEBUG", "Critical UI views are NULL! Check IDs in activity_main.xml");
            return;
        }

        SafeAreaHelper.apply(this, appBarLayout, findViewById(R.id.bottomNavigation));

        toolbar.setNavigationOnClickListener(v -> drawerLayout.openDrawer(navigationView));

        com.google.android.material.bottomnavigation.BottomNavigationView bottomNav = findViewById(R.id.bottomNavigation);
        if (bottomNav != null) {
            bottomNav.setOnItemSelectedListener(item -> {
                int id = item.getItemId();
                if (id == R.id.nav_home) {
                    return true;
                } else if (id == R.id.nav_history) {
                    Intent intent = new Intent(this, HistoryActivity.class);
                    intent.putExtra("station_id", selectedStationId);
                    startActivity(intent);
                    return true;
                } else if (id == R.id.nav_analytics) {
                    Intent intent = new Intent(this, AIAnalyticsActivity.class);
                    intent.putExtra("station_id", selectedStationId);
                    startActivity(intent);
                    return true;
                }
                return false;
            });
        }

        if (fabAI != null) {
            fabAI.setOnClickListener(v -> showWeatherUpdate());
        }

        navigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home_drawer) {
                // Dashboard
            } else if (id == R.id.news) {
                Intent intent = new Intent(MainActivity.this, FloodNewsActivity.class);
                startActivity(intent);
            } else if (id == R.id.hotline) {
                Intent intent = new Intent(MainActivity.this, HotlinesActivity.class);
                startActivity(intent);
            } else if (id == R.id.precaution) {
                Intent intent = new Intent(MainActivity.this, PrecautionActivity.class);
                startActivity(intent);
            } else if (id == R.id.evacuation) {
                Intent intent = new Intent(MainActivity.this, EvacuationActivity.class);
                intent.putExtra("lat", currentLat);
                intent.putExtra("lon", currentLon);
                intent.putExtra("municipality", currentMunicipality);
                startActivity(intent);
            } else {
                Toast.makeText(this, item.getTitle() + " coming soon!", Toast.LENGTH_SHORT).show();
            }
            drawerLayout.closeDrawers();
            return true;
        });

        checkLocationPermission();
        monitorConnectionStatus();
        loadWaterLevel();
        setupDrawerHeader();
        
        updateFloodMonitorService();

        checkAndPromptDndAccess();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }

        fetchWeatherAndNotify();
    }

    private void monitorConnectionStatus() {
        connectedRef = FirebaseDatabase.getInstance(DB_URL).getReference(".info/connected");
        connectedListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                boolean connected = Boolean.TRUE.equals(snapshot.getValue(Boolean.class));
                if (!connected && lastUpdatedText != null && (txtWaterLevel == null || txtWaterLevel.getText().toString().equals("--.-"))) {
                    lastUpdatedText.setText("Connecting / Network Unavailable...");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };
        connectedRef.addValueEventListener(connectedListener);
    }

    private void checkAndPromptDndAccess() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null && !nm.isNotificationPolicyAccessGranted()) {
            android.content.SharedPreferences prefs = getSharedPreferences(FloodStatusHelper.PREFS_NAME, Context.MODE_PRIVATE);
            boolean prompted = prefs.getBoolean("dnd_prompt_declined", false);
            if (!prompted) {
                new AlertDialog.Builder(this)
                        .setTitle("Critical Alert Access")
                        .setMessage("To ensure critical flood vibration alerts can notify you even when Do Not Disturb is enabled, please grant Notification Policy Access.")
                        .setPositiveButton("Grant Access", (dialog, which) -> {
                            try {
                                Intent intent = new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
                                startActivity(intent);
                            } catch (Exception e) {
                                Log.e("MAIN", "Failed to open DND settings: " + e.getMessage());
                            }
                        })
                        .setNegativeButton("Not Now", (dialog, which) -> {
                            prefs.edit().putBoolean("dnd_prompt_declined", true).apply();
                        })
                        .setCancelable(true)
                        .show();
            }
        }
    }

    private void setupDrawerHeader() {
        View headerView = navigationView.getHeaderView(0);
        if (headerView == null) return;

        SafeAreaHelper.applySystemBarPadding(headerView, true, false);

        txtDrawerName = headerView.findViewById(R.id.txtDrawerName);
        txtDrawerPhone = headerView.findViewById(R.id.txtDrawerPhone);

        if (mAuth.getCurrentUser() != null) {
            String uid = mAuth.getCurrentUser().getUid();
            if (drawerUserListener != null) {
                databaseRef.child("Users").child(uid).removeEventListener(drawerUserListener);
            }
            drawerUserListener = new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (isFinishing() || isDestroyed()) return;
                    if (snapshot.exists()) {
                        String first = snapshot.child("firstname").getValue(String.class);
                        String last = snapshot.child("lastname").getValue(String.class);
                        String phone = snapshot.child("phone").getValue(String.class);

                        if (first != null && last != null && txtDrawerName != null) {
                            txtDrawerName.setText(first + " " + last);
                        }
                        if (phone != null && txtDrawerPhone != null) {
                            txtDrawerPhone.setText(phone);
                        }
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {}
            };
            databaseRef.child("Users").child(uid).addValueEventListener(drawerUserListener);
        }
    }

    private void checkLocationPermission() {
        boolean hasCoarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean hasNotif = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasNotif = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        }

        if (hasCoarse && hasNotif) {
            if (isLocationEnabled()) {
                getCurrentLocation();
            } else {
                showLocationSettingsDialog();
            }
        } else {
            List<String> permsList = new ArrayList<>();
            permsList.add(Manifest.permission.ACCESS_FINE_LOCATION);
            permsList.add(Manifest.permission.ACCESS_COARSE_LOCATION);
            permsList.add(Manifest.permission.CAMERA);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permsList.add(Manifest.permission.POST_NOTIFICATIONS);
            }
            locationPermissionRequest.launch(permsList.toArray(new String[0]));
        }
    }

    private boolean isLocationEnabled() {
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
    }

    private void showLocationSettingsDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Enable Location")
                .setMessage("Location services are disabled. Please enable them to detect your Barangay.")
                .setPositiveButton("Settings", (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                    startActivity(intent);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void getCurrentLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
            if (location != null) {
                updateUserAddress(location);
            } else {
                requestFreshLocation();
            }
        });
    }

    private void requestFreshLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        int priority = (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
                ? Priority.PRIORITY_HIGH_ACCURACY
                : Priority.PRIORITY_BALANCED_POWER_ACCURACY;

        fusedLocationClient.getCurrentLocation(priority, null)
                .addOnSuccessListener(this, location -> {
                    if (location != null) {
                        updateUserAddress(location);
                    } else {
                        Log.e("LOCATION", "Failed to get fresh location");
                    }
                })
                .addOnFailureListener(e -> Log.e("LOCATION", "Fresh location error: " + e.getMessage()));
    }

    private void updateUserAddress(Location location) {
        currentLat = location.getLatitude();
        currentLon = location.getLongitude();
        
        backgroundExecutor.execute(() -> {
            Geocoder geocoder = new Geocoder(MainActivity.this, Locale.getDefault());
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1, addresses -> {
                    if (addresses != null && !addresses.isEmpty()) {
                        handleAddress(addresses.get(0));
                    }
                });
            } else {
                try {
                    List<Address> addresses = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
                    if (addresses != null && !addresses.isEmpty()) {
                        handleAddress(addresses.get(0));
                    }
                } catch (Exception e) {
                    Log.e("LOCATION", "Geocoder error: " + e.getMessage());
                }
            }
        });
    }

    private void handleAddress(Address address) {
        String fullAddress = address.getAddressLine(0);
        String city = address.getLocality();
        
        if (city != null) {
            if (city.contains("Las Piñas") || city.contains("Las Pinas")) {
                currentMunicipality = "Muntinlupa";
            } else {
                currentMunicipality = city;
            }
        }

        if (fullAddress == null) {
            fullAddress = "Unknown Address";
        }

        final String finalAddress = fullAddress;

        updateFloodMonitorService();

        if (mAuth.getCurrentUser() != null) {
            String uid = mAuth.getCurrentUser().getUid();
            databaseRef.child("Users").child(uid).child("address").setValue(finalAddress)
                    .addOnFailureListener(e -> Log.e("LOCATION", "Failed to update address for UID " + uid + ": " + e.getMessage()));
        }

        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;
            fetchWeatherAndNotify();
        });
    }

    private void loadWaterLevel() {
        boolean isCityWide = "MUN_ALL".equals(selectedStationId);

        if (waterLevelListener != null && activeWaterLevelRef != null) {
            activeWaterLevelRef.removeEventListener(waterLevelListener);
        }

        if (txtWaterLevel != null) txtWaterLevel.setText("--.-");
        if (statusText != null) statusText.setText("LOADING");
        if (lastUpdatedText != null) lastUpdatedText.setText("Connecting...");

        waterLevelListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (isFinishing() || isDestroyed()) return;
                if (snapshot.exists()) {
                    try {
                        if (isCityWide) {
                            double maxLevel = -1.0;
                            String maxTs = "";
                            long maxTsMillis = 0;
                            Double maxPct = null;
                            boolean valid = false;
                            for (DataSnapshot stationSnap : snapshot.getChildren()) {
                                DataSnapshot currentSnap = stationSnap.child("Current");
                                Object rawValue = FloodStatusHelper.getWaterLevelValueFromSnapshot(currentSnap);
                                if (rawValue != null && FloodStatusHelper.isValidWaterLevelReading(rawValue)) {
                                    double lvl = FloodStatusHelper.parseWaterLevel(rawValue);
                                    if (lvl > maxLevel) {
                                        maxLevel = lvl;
                                        maxPct = FloodStatusHelper.getPercentageValueFromSnapshot(currentSnap);
                                        Object tsObj = FloodStatusHelper.getTimestampValueFromSnapshot(currentSnap);
                                        if (tsObj instanceof Number) {
                                            maxTsMillis = ((Number) tsObj).longValue();
                                            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault());
                                            maxTs = sdf.format(new java.util.Date(maxTsMillis));
                                        } else if (tsObj instanceof String) {
                                            maxTs = (String) tsObj;
                                        }
                                    }
                                    valid = true;
                                }
                            }
                            if (valid) {
                                updateUI(maxLevel, maxTs, maxTsMillis, true, maxPct);
                            } else {
                                updateUI(Double.NaN, "Data Unavailable", 0, false, null);
                            }
                        } else {
                            Object rawValue = FloodStatusHelper.getWaterLevelValueFromSnapshot(snapshot);
                            Object tsObj = FloodStatusHelper.getTimestampValueFromSnapshot(snapshot);
                            Double arduinoPct = FloodStatusHelper.getPercentageValueFromSnapshot(snapshot);
                            
                            long tsMillis = FloodStatusHelper.parseTimestampToMillis(tsObj);
                            String timestamp = "";
                            if (tsMillis > 0) {
                                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault());
                                timestamp = sdf.format(new java.util.Date(tsMillis));
                            } else if (tsObj instanceof String) {
                                timestamp = (String) tsObj;
                            }

                            if (rawValue != null && FloodStatusHelper.isValidWaterLevelReading(rawValue)) {
                                double level = FloodStatusHelper.parseWaterLevel(rawValue);
                                updateUI(level, timestamp, tsMillis, true, arduinoPct);
                            } else {
                                updateUI(Double.NaN, "Data Unavailable", 0, false, null);
                            }
                        }
                    } catch (Exception e) {
                        Log.e("FIREBASE_DEBUG", "Error parsing level: " + e.getMessage());
                        updateUI(Double.NaN, "Data error", 0, false, null);
                    }
                } else {
                    updateUI(Double.NaN, "Station empty", 0, false, null);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (isFinishing() || isDestroyed()) return;
                Log.e("FIREBASE_DEBUG", "Database Error: " + error.getMessage());
                if (lastUpdatedText != null) lastUpdatedText.setText("Connecting / Network Unavailable...");
            }
        };

        if (isCityWide) {
            activeWaterLevelRef = databaseRef.child("FloodMonitoringSystem").child("Stations");
        } else {
            activeWaterLevelRef = databaseRef.child("FloodMonitoringSystem").child("Stations").child(selectedStationId).child("Current");
        }
        activeWaterLevelRef.addValueEventListener(waterLevelListener);
    }

    private void showStationSelectionDialog() {
        final String[] stationIds = FloodStatusHelper.STATION_IDS;
        final String[] displayNames = FloodStatusHelper.STATION_NAMES;
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Barangay / Area");
        builder.setItems(displayNames, (dialog, which) -> {
            selectedStationId = stationIds[which];
            selectedStationName = displayNames[which];
            FloodStatusHelper.saveSelectedStation(this, selectedStationId, selectedStationName);
            Toast.makeText(this, "Location changed to " + selectedStationName, Toast.LENGTH_SHORT).show();
            
            loadWaterLevel();
            fetchWeatherAndNotify();
            updateFloodMonitorService();
        });
        builder.show();
    }

    private void updateUI(double level, String timestamp, long timestampMillis, boolean isValidReading) {
        updateUI(level, timestamp, timestampMillis, isValidReading, null);
    }

    private void updateUI(double level, String timestamp, long timestampMillis, boolean isValidReading, Double explicitPct) {
        int textColor = ContextCompat.getColor(this, R.color.blue_primary);

        TextView lblWaterLevel = findViewById(R.id.lblWaterLevel);
        TextView txtUnit = findViewById(R.id.txtUnit);
        ImageView imgLevelIcon = findViewById(R.id.imgLevelIcon);

        if (lblWaterLevel != null) lblWaterLevel.setTextColor(textColor);
        if (txtUnit != null) txtUnit.setTextColor(textColor);
        if (imgLevelIcon != null) imgLevelIcon.setColorFilter(textColor, android.graphics.PorterDuff.Mode.SRC_ATOP);

        if (!isValidReading || Double.isNaN(level)) {
            currentWaterLevel = 0.0;
            if (txtWaterLevel != null) {
                txtWaterLevel.setText("--.-");
                txtWaterLevel.setTextColor(textColor);
            }
            if (statusText != null) {
                statusText.setText("NO DATA");
                statusText.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            }
            if (trendText != null) {
                trendText.setText("N/A");
                trendText.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            }
            if (lastUpdatedText != null) {
                lastUpdatedText.setText("Data Unavailable");
            }
            if (stationName != null) {
                stationName.setText(selectedStationName);
            }
            stopForegroundSoundAlert();
            stopForegroundVibration();
            FlashlightHelper.stopFlashAlert(this);
            return;
        }

        double oldLevel = currentWaterLevel;
        currentWaterLevel = level;
        String status = FloodStatusHelper.getStatus(level);
        int colorRes = FloodStatusHelper.getStatusColorForStatus(this, status);

        if (txtWaterLevel != null) {
            txtWaterLevel.setText(String.format(Locale.getDefault(), "%.1f", level));
            txtWaterLevel.setTextColor(textColor);
        }

        if (statusText != null) {
            statusText.setText(status);
            statusText.setTextColor(colorRes);
        }

        if (!FloodStatusHelper.isDangerOrCritical(status)) {
            stopForegroundSoundAlert();
            stopForegroundVibration();
            FlashlightHelper.stopFlashAlert(this);
        }
        lastNotifiedStatus = status;
        lastStatus = status;

        if (Math.abs(level - oldLevel) > 0.5 || oldLevel == 0.0) {
            calculateAndDisplayTrend(level);
        }

        if (lastUpdatedText != null) {
            if (timestampMillis > 0) {
                lastUpdatedText.setText(FloodStatusHelper.formatFreshnessWithAge(timestampMillis));
            } else if (timestamp != null && !timestamp.isEmpty() && !timestamp.startsWith("Data error") && !timestamp.startsWith("No data") && !timestamp.startsWith("Station empty")) {
                lastUpdatedText.setText(FloodStatusHelper.formatFreshness(timestamp));
            } else {
                lastUpdatedText.setText("Data Unavailable");
            }
        }

        if (stationName != null) {
            stationName.setText(selectedStationName);
        }

        if (waterLevelCircle instanceof ImageView) {
            ((ImageView) waterLevelCircle).setColorFilter(textColor, android.graphics.PorterDuff.Mode.SRC_ATOP);
            
            android.view.ViewGroup.LayoutParams lp = waterLevelCircle.getLayoutParams();
            if (lp != null) {
                int minHeight = (int) (40 * getResources().getDisplayMetrics().density);
                int maxHeight = (int) (200 * getResources().getDisplayMetrics().density);
                double pctRatio = (explicitPct != null) ? (explicitPct / 100.0) : Math.min(1.0, Math.max(0.0, level / FloodStatusHelper.REFERENCE_HEIGHT));
                int newHeight = minHeight + (int) (pctRatio * (maxHeight - minHeight));
                lp.height = Math.min(newHeight, maxHeight);
                waterLevelCircle.setLayoutParams(lp);
            }
        }

        toolbar.setTitle("Dashboard");
    }

    private void calculateAndDisplayTrend(double currentLevel) {
        long oneHourAgoMillis = System.currentTimeMillis() - 3600000;
        boolean isCityWide = "MUN_ALL".equals(selectedStationId);
        
        DatabaseReference historyQueryRef;
        if (isCityWide) {
            historyQueryRef = databaseRef.child("FloodMonitoringSystem").child("Stations");
        } else {
            historyQueryRef = databaseRef.child("FloodMonitoringSystem").child("Stations").child(selectedStationId).child("History");
        }

        historyQueryRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (isFinishing() || isDestroyed()) return;
                    double pastLevel = -1.0;

                    if (isCityWide) {
                        for (DataSnapshot stationSnap : snapshot.getChildren()) {
                            DataSnapshot histSnap = stationSnap.child("History");
                            for (DataSnapshot data : histSnap.getChildren()) {
                                try {
                                    Object tsObj = FloodStatusHelper.getTimestampValueFromSnapshot(data);
                                    long ts = FloodStatusHelper.parseTimestampToMillis(tsObj);

                                    if (ts > 0 && ts <= oneHourAgoMillis) {
                                        Object val = FloodStatusHelper.getWaterLevelValueFromSnapshot(data);
                                        if (val != null) {
                                            double pLvl = FloodStatusHelper.parseWaterLevel(val);
                                            if (pLvl > pastLevel) pastLevel = pLvl;
                                        }
                                    }
                                } catch (Exception ignored) {}
                            }
                        }
                    } else {
                        for (DataSnapshot data : snapshot.getChildren()) {
                            try {
                                Object tsObj = FloodStatusHelper.getTimestampValueFromSnapshot(data);
                                long ts = FloodStatusHelper.parseTimestampToMillis(tsObj);

                                if (ts > 0 && ts <= oneHourAgoMillis) {
                                    Object val = FloodStatusHelper.getWaterLevelValueFromSnapshot(data);
                                    if (val != null) {
                                        pastLevel = FloodStatusHelper.parseWaterLevel(val);
                                    }
                                }
                            } catch (Exception ignored) {}
                        }
                    }

                    String trend = FloodStatusHelper.getTrend(currentLevel, pastLevel);
                    int trendColor = FloodStatusHelper.getTrendColor(MainActivity.this, trend);

                    if (trendText != null) {
                        trendText.setText(trend);
                        trendText.setTextColor(trendColor);
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {}
            });
    }

    private void fetchWeatherAndNotify() {
        new Thread(() -> {
            try {
                PagasaWeatherHelper.checkAndNotifyPagasaAdvisories(MainActivity.this);
            } catch (Throwable t) {
                Log.w("MAIN", "PAGASA advisory check note: " + t.getMessage());
            }
        }).start();

        if (weatherService == null) {
            if (txtAIAdvice != null) txtAIAdvice.setText("Weather: Service offline.");
            return;
        }

        if (txtAIAdvice != null) txtAIAdvice.setText("Fetching weather...");

        weatherService.getCurrentWeatherByCoords(currentLat, currentLon, WEATHER_API_KEY, "metric")
                .enqueue(new Callback<WeatherResponse>() {
                    @Override
                    public void onResponse(Call<WeatherResponse> call, Response<WeatherResponse> response) {
                        if (isFinishing() || isDestroyed()) return;
                        if (response.isSuccessful() && response.body() != null) {
                            WeatherResponse weather = response.body();
                            if (weather.weather != null && weather.weather.length > 0 && weather.main != null) {
                                String condition = weather.weather[0].main;
                                double temp = weather.main.temp;
                                double humidity = weather.main.humidity;
                                double windSpeed = (weather.wind != null) ? weather.wind.speed : 0.0;

                                String fallbackAdvice = SmartAI.getWeatherAdvice(condition, temp, currentWaterLevel);

                                WeatherHelper.checkAndNotifyWeatherChange(MainActivity.this, condition, temp, humidity, windSpeed, 0.0, selectedStationName);

                                if (aiHelper != null) {
                                    aiHelper.generateSafetyAdvice(
                                        condition, temp, humidity, windSpeed, 0.0, currentWaterLevel, currentMunicipality, "Current weather: " + condition,
                                        new FirebaseAIHelper.AICallback() {
                                            @Override
                                            public void onSuccess(String result) {
                                                runOnUiThread(() -> {
                                                    if (isFinishing() || isDestroyed()) return;
                                                    try {
                                                        String advice = result;
                                                        if (result != null && !result.trim().isEmpty() && result.trim().startsWith("{")) {
                                                            org.json.JSONObject json = new org.json.JSONObject(result);
                                                            String summary = json.optString("summary", "");
                                                            String rec = json.optString("recommendation", "");
                                                            if (!summary.isEmpty()) {
                                                                advice = summary + (rec.isEmpty() ? "" : " " + rec);
                                                            }
                                                        }
                                                        if (txtAIAdvice != null && advice != null && !advice.isEmpty()) {
                                                            txtAIAdvice.setText(advice);
                                                        }
                                                    } catch (Exception e) {
                                                        if (txtAIAdvice != null) txtAIAdvice.setText(fallbackAdvice);
                                                    }
                                                });
                                            }

                                            @Override
                                            public void onError(Throwable t) {
                                                runOnUiThread(() -> {
                                                    if (isFinishing() || isDestroyed()) return;
                                                    if (txtAIAdvice != null) txtAIAdvice.setText(fallbackAdvice);
                                                });
                                            }
                                        }
                                    );
                                } else {
                                    if (txtAIAdvice != null) txtAIAdvice.setText(fallbackAdvice);
                                }
                            } else {
                                if (txtAIAdvice != null) txtAIAdvice.setText("Weather: Incomplete data.");
                            }
                        } else {
                            if (txtAIAdvice != null) {
                                txtAIAdvice.setText("Weather: Service temporarily unavailable.");
                            }
                            Log.e("WEATHER", "Error Code: " + response.code());
                        }
                    }

                    @Override
                    public void onFailure(Call<WeatherResponse> call, Throwable t) {
                        if (isFinishing() || isDestroyed()) return;
                        Log.e("MainActivity", "Failed: " + t.getMessage());
                        if (txtAIAdvice != null) {
                            txtAIAdvice.setText("Weather data currently unavailable. Check local PAGASA advisories.");
                        }
                    }
                });
    }

    @Override
    protected void onDestroy() {
        if (waterLevelListener != null && activeWaterLevelRef != null) {
            activeWaterLevelRef.removeEventListener(waterLevelListener);
            try {
                activeWaterLevelRef.keepSynced(false);
            } catch (Throwable ignored) {}
        }
        if (connectedListener != null && connectedRef != null) {
            connectedRef.removeEventListener(connectedListener);
        }
        if (drawerUserListener != null && mAuth != null && mAuth.getCurrentUser() != null && databaseRef != null) {
            databaseRef.child("Users").child(mAuth.getCurrentUser().getUid())
                    .removeEventListener(drawerUserListener);
        }
        backgroundExecutor.shutdown();
        FlashlightHelper.stopFlashAlert(this);
        stopForegroundSoundAlert();
        stopForegroundVibration();
        super.onDestroy();
    }

    private void showWeatherUpdate() {
        if (weatherService == null) return;
        
        Toast.makeText(getApplicationContext(), "Updating Safety Assessment...", Toast.LENGTH_SHORT).show();
        
        weatherService.getCurrentWeatherByCoords(currentLat, currentLon, WEATHER_API_KEY, "metric")
                .enqueue(new Callback<WeatherResponse>() {
                    @Override
                    public void onResponse(Call<WeatherResponse> call, Response<WeatherResponse> response) {
                        if (isFinishing() || isDestroyed()) return;
                        if (response.isSuccessful() && response.body() != null) {
                            WeatherResponse weather = response.body();
                            if (weather.weather != null && weather.weather.length > 0 && weather.main != null) {
                                String condition = weather.weather[0].main;
                                double temp = weather.main.temp;
                                
                                String advice = SmartAI.getWeatherAdvice(condition, temp, currentWaterLevel);
                                
                                runOnUiThread(() -> {
                                    if (isFinishing() || isDestroyed()) return;
                                    if (txtAIAdvice != null) txtAIAdvice.setText(advice);
                                    
                                    try {
                                        new AlertDialog.Builder(MainActivity.this)
                                                .setTitle("Weather Safety Advisory")
                                                .setMessage("Location: " + currentMunicipality + " (" + selectedStationName + ")\n" +
                                                        "Weather: " + condition + "\n" +
                                                        "Temperature: " + String.format(Locale.getDefault(), "%.1f°C", temp) + "\n" +
                                                        "Water Level: " + String.format(Locale.getDefault(), "%.1f cm", currentWaterLevel) + "\n\n" +
                                                        advice)
                                                .setPositiveButton("Close", null)
                                                .show();
                                    } catch (Exception e) {
                                        Log.e("MAIN", "Failed to show weather dialog: " + e.getMessage());
                                    }
                                });
                            }
                        }
                    }

                    @Override
                    public void onFailure(Call<WeatherResponse> call, Throwable t) {
                        if (isFinishing() || isDestroyed()) return;
                        Toast.makeText(getApplicationContext(), "Connection error", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void updateFloodMonitorService() {
        Intent serviceIntent = new Intent(this, FloodMonitorService.class);
        serviceIntent.putExtra("municipality", currentMunicipality);
        serviceIntent.putExtra("station_id", selectedStationId);
        serviceIntent.putExtra("station_name", selectedStationName);
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    private void triggerForegroundVibration(boolean isCritical) {
        try {
            Vibrator v;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                android.os.VibratorManager vm = (android.os.VibratorManager) getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                v = vm != null ? vm.getDefaultVibrator() : null;
            } else {
                v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            }
            if (v == null || !v.hasVibrator()) return;

            v.cancel();

            if (isCritical) {
                long[] pattern = {0, 1000, 500, 1000, 500, 1000, 500, 1000, 500, 1000};
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    android.media.AudioAttributes audioAttributes = new android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build();
                    v.vibrate(android.os.VibrationEffect.createWaveform(pattern, -1), audioAttributes);
                } else {
                    v.vibrate(pattern, -1);
                }
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(android.os.VibrationEffect.createOneShot(500, android.os.VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    v.vibrate(500);
                }
            }
        } catch (Throwable t) {
            Log.e("MainActivity", "Failed to trigger vibration: " + t.getMessage());
        }
    }

    private void stopForegroundVibration() {
        try {
            Vibrator v;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                android.os.VibratorManager vm = (android.os.VibratorManager) getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                v = vm != null ? vm.getDefaultVibrator() : null;
            } else {
                v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            }
            if (v != null) {
                v.cancel();
            }
        } catch (Throwable ignored) {}
    }

    private void triggerForegroundSoundAlert() {
        try {
            stopForegroundSoundAlert();
            foregroundMediaPlayer = MediaPlayer.create(this, R.raw.whistle);
            if (foregroundMediaPlayer == null) {
                Uri alarmUri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM);
                if (alarmUri == null) alarmUri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION);
                foregroundMediaPlayer = MediaPlayer.create(this, alarmUri);
            }
            if (foregroundMediaPlayer != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    android.media.AudioAttributes aa = new android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build();
                    foregroundMediaPlayer.setAudioAttributes(aa);
                }
                foregroundMediaPlayer.setLooping(true);
                foregroundMediaPlayer.start();

                // Automatically stop sound after exactly 5 seconds
                foregroundSoundHandler.removeCallbacksAndMessages(null);
                foregroundSoundHandler.postDelayed(this::stopForegroundSoundAlert, 5000L);
            }
        } catch (Throwable t) {
            Log.e("MainActivity", "Failed to play foreground sound alert: " + t.getMessage());
        }
    }

    private void stopForegroundSoundAlert() {
        try {
            foregroundSoundHandler.removeCallbacksAndMessages(null);
            if (foregroundMediaPlayer != null) {
                if (foregroundMediaPlayer.isPlaying()) {
                    foregroundMediaPlayer.stop();
                }
                foregroundMediaPlayer.release();
                foregroundMediaPlayer = null;
            }
        } catch (Throwable ignored) {}
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        Configuration override = new Configuration(newBase.getResources().getConfiguration());
        override.fontScale = 1.0f;
        applyOverrideConfiguration(override);
        super.attachBaseContext(newBase);
    }
}
