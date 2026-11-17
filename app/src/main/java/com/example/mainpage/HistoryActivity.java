package com.example.mainpage;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class HistoryActivity extends AppCompatActivity {

    private RecyclerView historyRecyclerView;
    private HistoryAdapter adapter;
    private List<HistoryRecord> rawRecordList = new ArrayList<>();
    private List<HistoryRecord> filteredRecordList = new ArrayList<>();

    private TextView txtFilterStation, txtTotalRecords, txtPeakLevel, txtLatestReading;
    private View emptyStateLayout;
    private Button btnFilterAll, btnFilter6h, btnFilter12h, btnFilter24h, btnRetryHistory;

    private DatabaseReference databaseRef;
    private DatabaseReference activeHistoryRef;
    private ValueEventListener historyListener;
    private final String DB_URL = "https://finalfirebase-45c72-default-rtdb.firebaseio.com";
    private String stationId = "ST001";
    private int selectedTimeRangeHours = 0; // 0 = All

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        if (getIntent().hasExtra("station_id")) {
            stationId = getIntent().getStringExtra("station_id");
            FloodStatusHelper.saveSelectedStation(this, stationId, FloodStatusHelper.getStationDisplayName(stationId));
        } else {
            stationId = FloodStatusHelper.getSavedStationId(this);
        }

        txtFilterStation = findViewById(R.id.txtFilterStation);
        txtTotalRecords = findViewById(R.id.txtTotalRecords);
        txtPeakLevel = findViewById(R.id.txtPeakLevel);
        txtLatestReading = findViewById(R.id.txtLatestReading);
        emptyStateLayout = findViewById(R.id.emptyStateLayout);

        btnFilterAll = findViewById(R.id.btnFilterAll);
        btnFilter6h = findViewById(R.id.btnFilter6h);
        btnFilter12h = findViewById(R.id.btnFilter12h);
        btnFilter24h = findViewById(R.id.btnFilter24h);
        btnRetryHistory = findViewById(R.id.btnRetryHistory);

        com.google.android.material.appbar.MaterialToolbar toolbar = findViewById(R.id.toolbar);
        SafeAreaHelper.apply(this, findViewById(R.id.includeToolbar), null);

        if (toolbar != null) {
            toolbar.setTitle("Station History");
            toolbar.setNavigationOnClickListener(v -> finish());
        }

        updateFilterText();

        View cardFilter = findViewById(R.id.cardStationFilter);
        if (cardFilter != null) {
            cardFilter.setOnClickListener(v -> showStationSelectionDialog());
        }

        setupTimeRangeButtons();

        if (btnRetryHistory != null) {
            btnRetryHistory.setOnClickListener(v -> loadHistoryData());
        }

        historyRecyclerView = findViewById(R.id.historyRecyclerView);
        historyRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        
        adapter = new HistoryAdapter(filteredRecordList);
        historyRecyclerView.setAdapter(adapter);

        databaseRef = FirebaseDatabase.getInstance(DB_URL).getReference();
        loadHistoryData();
    }

    private void setupTimeRangeButtons() {
        View.OnClickListener listener = v -> {
            int id = v.getId();
            if (id == R.id.btnFilterAll) selectedTimeRangeHours = 0;
            else if (id == R.id.btnFilter6h) selectedTimeRangeHours = 6;
            else if (id == R.id.btnFilter12h) selectedTimeRangeHours = 12;
            else if (id == R.id.btnFilter24h) selectedTimeRangeHours = 24;

            updateTimeRangeButtonStyles();
            applyTimeRangeFilter();
        };

        if (btnFilterAll != null) btnFilterAll.setOnClickListener(listener);
        if (btnFilter6h != null) btnFilter6h.setOnClickListener(listener);
        if (btnFilter12h != null) btnFilter12h.setOnClickListener(listener);
        if (btnFilter24h != null) btnFilter24h.setOnClickListener(listener);
    }

    private void updateTimeRangeButtonStyles() {
        int activeBg = ContextCompat.getColor(this, R.color.primary_blue);
        int inactiveBg = ContextCompat.getColor(this, R.color.white);
        int activeText = ContextCompat.getColor(this, R.color.white);
        int inactiveText = ContextCompat.getColor(this, R.color.primary_blue);

        setButtonStyle(btnFilterAll, selectedTimeRangeHours == 0, activeBg, inactiveBg, activeText, inactiveText);
        setButtonStyle(btnFilter6h, selectedTimeRangeHours == 6, activeBg, inactiveBg, activeText, inactiveText);
        setButtonStyle(btnFilter12h, selectedTimeRangeHours == 12, activeBg, inactiveBg, activeText, inactiveText);
        setButtonStyle(btnFilter24h, selectedTimeRangeHours == 24, activeBg, inactiveBg, activeText, inactiveText);
    }

    private void setButtonStyle(Button btn, boolean isActive, int activeBg, int inactiveBg, int activeText, int inactiveText) {
        if (btn == null) return;
        if (isActive) {
            btn.setBackgroundColor(activeBg);
            btn.setTextColor(activeText);
        } else {
            btn.setBackgroundColor(inactiveBg);
            btn.setTextColor(inactiveText);
        }
    }

    private void updateFilterText() {
        String displayName = FloodStatusHelper.getStationDisplayName(stationId);
        if (txtFilterStation != null) {
            txtFilterStation.setText(displayName);
        }
    }

    private void showStationSelectionDialog() {
        final String[] stationIds = FloodStatusHelper.STATION_IDS;
        final String[] displayNames = FloodStatusHelper.STATION_NAMES;
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Barangay / Area");
        builder.setItems(displayNames, (dialog, which) -> {
            stationId = stationIds[which];
            FloodStatusHelper.saveSelectedStation(this, stationId, displayNames[which]);
            
            // Notify service about station change
            android.content.Intent serviceIntent = new android.content.Intent(this, FloodMonitorService.class);
            serviceIntent.putExtra("station_id", stationId);
            serviceIntent.putExtra("station_name", displayNames[which]);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }

            updateFilterText();
            loadHistoryData();
        });
        builder.show();
    }

    private HistoryRecord parseHistoryRecord(DataSnapshot data, String defaultStationName) {
        HistoryRecord record = new HistoryRecord();
        record.stationName = defaultStationName;
        
        Object tsObj = FloodStatusHelper.getTimestampValueFromSnapshot(data);
        record.timeMillis = FloodStatusHelper.parseTimestampToMillis(tsObj);
        if (record.timeMillis > 0) {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault());
            record.Timestamp = sdf.format(new java.util.Date(record.timeMillis));
        } else if (tsObj instanceof String) {
            record.Timestamp = (String) tsObj;
        } else {
            record.Timestamp = "Unknown Time";
        }
        record.WaterLevel = FloodStatusHelper.getWaterLevelValueFromSnapshot(data);
        record.Status = FloodStatusHelper.getStatusFromSnapshot(data);
        return record;
    }

    private void loadHistoryData() {
        boolean isCityWide = "MUN_ALL".equals(stationId);
        
        if (historyListener != null && activeHistoryRef != null) {
            activeHistoryRef.removeEventListener(historyListener);
        }

        historyListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (isFinishing() || isDestroyed()) return;
                rawRecordList.clear();
                
                if (isCityWide) {
                    for (DataSnapshot stationSnap : snapshot.getChildren()) {
                        String stId = stationSnap.getKey();
                        String stName = FloodStatusHelper.getStationDisplayName(stId);
                        DataSnapshot histSnap = stationSnap.child("History");
                        if (histSnap.exists()) {
                            for (DataSnapshot recordSnap : histSnap.getChildren()) {
                                rawRecordList.add(parseHistoryRecord(recordSnap, stName));
                            }
                        }
                    }
                } else {
                    String stName = FloodStatusHelper.getStationDisplayName(stationId);
                    for (DataSnapshot recordSnap : snapshot.getChildren()) {
                        rawRecordList.add(parseHistoryRecord(recordSnap, stName));
                    }
                }

                Collections.sort(rawRecordList, (a, b) -> Long.compare(b.timeMillis, a.timeMillis));
                applyTimeRangeFilter();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (isFinishing() || isDestroyed()) return;
                Log.e("HISTORY_DEBUG", "Database Error: " + error.getMessage());
                showEmptyState(true);
            }
        };

        if (isCityWide) {
            activeHistoryRef = databaseRef.child("FloodMonitoringSystem").child("Stations");
        } else {
            activeHistoryRef = databaseRef.child("FloodMonitoringSystem").child("Stations").child(stationId).child("History");
        }
        activeHistoryRef.addValueEventListener(historyListener);
    }

    private void applyTimeRangeFilter() {
        filteredRecordList.clear();

        long now = System.currentTimeMillis();
        long filterCutoff = selectedTimeRangeHours > 0 ? now - (selectedTimeRangeHours * 3600000L) : 0;

        double maxLevel = 0.0;
        double latestLevel = 0.0;

        for (HistoryRecord record : rawRecordList) {
            if (selectedTimeRangeHours == 0 || record.timeMillis >= filterCutoff) {
                filteredRecordList.add(record);
                double level = record.getLevel();
                if (level > maxLevel) maxLevel = level;
            }
        }

        if (!filteredRecordList.isEmpty()) {
            latestLevel = filteredRecordList.get(0).getLevel();
        }

        if (txtTotalRecords != null) txtTotalRecords.setText(String.valueOf(filteredRecordList.size()));
        if (txtPeakLevel != null) txtPeakLevel.setText(String.format(Locale.getDefault(), "%.1f cm", maxLevel));
        if (txtLatestReading != null) txtLatestReading.setText(String.format(Locale.getDefault(), "%.1f cm", latestLevel));

        adapter.notifyDataSetChanged();
        showEmptyState(filteredRecordList.isEmpty());
    }

    private void showEmptyState(boolean show) {
        if (emptyStateLayout != null) {
            emptyStateLayout.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        if (historyRecyclerView != null) {
            historyRecyclerView.setVisibility(show ? View.GONE : View.VISIBLE);
        }
    }

    // Model Class
    public static class HistoryRecord {
        public String Timestamp;
        public Object WaterLevel;
        public String Status;
        public long timeMillis = 0;
        public String stationName = "";

        public HistoryRecord() {}

        public double getLevel() {
            return FloodStatusHelper.parseWaterLevel(WaterLevel);
        }

        public String getStatus() {
            return FloodStatusHelper.getStatus(getLevel());
        }
    }

    // Adapter Class
    private class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {
        private List<HistoryRecord> records;

        public HistoryAdapter(List<HistoryRecord> records) {
            this.records = records;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_history, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            HistoryRecord record = records.get(position);
            double level = record.getLevel();
            
            holder.txtLevel.setText(String.format(Locale.getDefault(), "%.1f cm", level));
            
            String fullTime = record.Timestamp != null ? record.Timestamp : "Unknown Time";
            String datePart = fullTime;
            String timePart = "";
            if (fullTime.contains(" ")) {
                int firstSpace = fullTime.indexOf(" ");
                int lastSpace = fullTime.lastIndexOf(" ");
                if (firstSpace > 0 && lastSpace > firstSpace) {
                    datePart = fullTime.substring(0, lastSpace).trim();
                    timePart = fullTime.substring(lastSpace).trim();
                }
            }
            if (record.stationName != null && !record.stationName.isEmpty()) {
                datePart = datePart + " • " + record.stationName;
            }
            holder.txtDate.setText(datePart);
            holder.txtTime.setText(timePart);

            // Status Indicator & Badge
            String status = record.getStatus();
            int color = FloodStatusHelper.getStatusColorForStatus(holder.itemView.getContext(), status);

            if (holder.txtStatusBadge != null) {
                holder.txtStatusBadge.setText(status);
                holder.txtStatusBadge.setBackgroundColor(color);
            }
            if (holder.statusIndicator != null) {
                holder.statusIndicator.setBackgroundColor(color);
            }
        }

        @Override
        public int getItemCount() {
            return records.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView txtTime, txtDate, txtLevel, txtStatusBadge;
            View statusIndicator;

            ViewHolder(View itemView) {
                super(itemView);
                txtTime = itemView.findViewById(R.id.txtHistoryTime);
                txtDate = itemView.findViewById(R.id.txtHistoryDate);
                txtLevel = itemView.findViewById(R.id.txtHistoryLevel);
                txtStatusBadge = itemView.findViewById(R.id.txtHistoryStatusBadge);
                statusIndicator = itemView.findViewById(R.id.statusIndicator);
            }
        }
    }

    @Override
    protected void onDestroy() {
        if (historyListener != null && activeHistoryRef != null) {
            activeHistoryRef.removeEventListener(historyListener);
        }
        super.onDestroy();
    }
}
