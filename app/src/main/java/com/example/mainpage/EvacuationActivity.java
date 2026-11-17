package com.example.mainpage;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class EvacuationActivity extends AppCompatActivity {

    public static class EvacuationCenter {
        String name;
        double lat;
        double lon;
        String type; // Barangay Name (e.g., Putatan, Tunasan)

        EvacuationCenter(String name, double lat, double lon, String type) {
            this.name = name;
            this.lat = lat;
            this.lon = lon;
            this.type = type;
        }
    }

    public static class DisplayItem {
        public static final int TYPE_HEADER = 0;
        public static final int TYPE_CENTER = 1;

        final int viewType;
        final String headerTitle;
        final EvacuationCenter center;

        DisplayItem(String headerTitle) {
            this.viewType = TYPE_HEADER;
            this.headerTitle = headerTitle;
            this.center = null;
        }

        DisplayItem(EvacuationCenter center) {
            this.viewType = TYPE_CENTER;
            this.headerTitle = null;
            this.center = center;
        }
    }

    private final List<EvacuationCenter> knownCenters = new ArrayList<>();
    private final List<DisplayItem> displayedItems = new ArrayList<>();
    private EvacuationAdapter adapter;
    private View emptyStateEvacuation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_evacuation);

        Toolbar toolbar = findViewById(R.id.toolbar);
        SafeAreaHelper.apply(this, findViewById(R.id.includeToolbar), null);

        if (toolbar != null) {
            toolbar.setTitle("Evacuation Centers");
            toolbar.setNavigationOnClickListener(v -> finish());
        }

        emptyStateEvacuation = findViewById(R.id.emptyStateEvacuation);
        EditText edtSearchCenter = findViewById(R.id.edtSearchCenter);

        initSampleCenters();
        buildDisplayList("");

        setupRecyclerView();

        if (edtSearchCenter != null) {
            edtSearchCenter.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    filterCenters(s.toString());
                }

                @Override
                public void afterTextChanged(Editable s) {}
            });
        }
    }

    private void buildDisplayList(String query) {
        displayedItems.clear();
        String q = query.trim().toLowerCase();

        // Group centers by barangay using LinkedHashMap to preserve order
        Map<String, List<EvacuationCenter>> grouped = new LinkedHashMap<>();

        for (EvacuationCenter center : knownCenters) {
            if (q.isEmpty() || center.name.toLowerCase().contains(q) || center.type.toLowerCase().contains(q)) {
                String barangay = center.type;
                if (!grouped.containsKey(barangay)) {
                    grouped.put(barangay, new ArrayList<>());
                }
                List<EvacuationCenter> list = grouped.get(barangay);
                if (list != null) {
                    list.add(center);
                }
            }
        }

        for (Map.Entry<String, List<EvacuationCenter>> entry : grouped.entrySet()) {
            displayedItems.add(new DisplayItem("Barangay " + entry.getKey()));
            for (EvacuationCenter center : entry.getValue()) {
                displayedItems.add(new DisplayItem(center));
            }
        }
    }

    private void filterCenters(String query) {
        buildDisplayList(query);

        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }

        if (emptyStateEvacuation != null) {
            emptyStateEvacuation.setVisibility(displayedItems.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }

    private void setupRecyclerView() {
        RecyclerView recyclerCenters = findViewById(R.id.recyclerCenters);
        recyclerCenters.setLayoutManager(new LinearLayoutManager(this));

        adapter = new EvacuationAdapter(displayedItems, (center) -> {
            try {
                Uri gmmIntentUri = Uri.parse("google.navigation:q=" + center.lat + "," + center.lon);
                Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
                mapIntent.setPackage("com.google.android.apps.maps");
                if (mapIntent.resolveActivity(getPackageManager()) != null) {
                    startActivity(mapIntent);
                } else {
                    Toast.makeText(this, "Opening map navigation...", Toast.LENGTH_SHORT).show();
                    Intent genericMapIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("geo:" + center.lat + "," + center.lon + "?q=" + Uri.encode(center.name)));
                    startActivity(genericMapIntent);
                }
            } catch (Exception e) {
                Toast.makeText(this, "No map application available.", Toast.LENGTH_SHORT).show();
            }
        });
        recyclerCenters.setAdapter(adapter);
    }

    private void initSampleCenters() {
        knownCenters.clear();

        // BRGY. PUTATAN
        knownCenters.add(new EvacuationCenter("Danilo R. Teves Covered Court", 14.3995, 121.0350, "Putatan"));
        knownCenters.add(new EvacuationCenter("Brgy. Hall Covered Putatan", 14.3980, 121.0360, "Putatan"));
        knownCenters.add(new EvacuationCenter("Putatan Elementary School", 14.3984, 121.0364, "Putatan"));
        knownCenters.add(new EvacuationCenter("South Greenheights Covered Court", 14.3930, 121.0280, "Putatan"));

        // BRGY. TUNASAN
        knownCenters.add(new EvacuationCenter("Tunasan Pavilion", 14.3810, 121.0410, "Tunasan"));
        knownCenters.add(new EvacuationCenter("Tunasan Elementary School", 14.3830, 121.0430, "Tunasan"));
        knownCenters.add(new EvacuationCenter("Tunasan Covered Court", 14.3824, 121.0427, "Tunasan"));
        knownCenters.add(new EvacuationCenter("JRF Covered Court Victoria Homes", 14.3680, 121.0250, "Tunasan"));
        knownCenters.add(new EvacuationCenter("JRF Pavilion Victoria Homes", 14.3685, 121.0255, "Tunasan"));
        knownCenters.add(new EvacuationCenter("Victoria Homes Elementary School", 14.3670, 121.0240, "Tunasan"));
        knownCenters.add(new EvacuationCenter("Laguerta Covered Court", 14.3750, 121.0350, "Tunasan"));

        // BRGY. SUCAT
        knownCenters.add(new EvacuationCenter("Barangay Hall Sucat Covered Court", 14.4605, 121.0505, "Sucat"));
        knownCenters.add(new EvacuationCenter("Sucat Elementary School GYM", 14.4600, 121.0500, "Sucat"));
        knownCenters.add(new EvacuationCenter("Bagong Silang Elementary School", 14.4550, 121.0480, "Sucat"));

        // BRGY. POBLACION
        knownCenters.add(new EvacuationCenter("Muntinlupa Evacuation Center", 14.3720, 121.0300, "Poblacion"));
        knownCenters.add(new EvacuationCenter("Muntinlupa Elementary School", 14.3883, 121.0442, "Poblacion"));
        knownCenters.add(new EvacuationCenter("Itaas Elementary School", 14.3780, 121.0390, "Poblacion"));
        knownCenters.add(new EvacuationCenter("Muntinlupa National High School", 14.3820, 121.0340, "Poblacion"));
        knownCenters.add(new EvacuationCenter("Poblacion Elementary School", 14.3890, 121.0450, "Poblacion"));
        knownCenters.add(new EvacuationCenter("Brgy. Poblacion Covered Court", 14.3870, 121.0430, "Poblacion"));
        knownCenters.add(new EvacuationCenter("Biazon Covered Court", 14.3850, 121.0420, "Poblacion"));

        // BRGY. CUPANG
        knownCenters.add(new EvacuationCenter("JRF Multi-Purpose Hall", 14.4375, 121.0480, "Cupang"));
        knownCenters.add(new EvacuationCenter("Mullet Compound Covered Court", 14.4385, 121.0460, "Cupang"));
        knownCenters.add(new EvacuationCenter("Cupang Elementary School-Main", 14.4372, 121.0478, "Cupang"));
        knownCenters.add(new EvacuationCenter("Cupang Senior High", 14.4360, 121.0470, "Cupang"));

        // BRGY. BULI
        knownCenters.add(new EvacuationCenter("Buli Covered Court", 14.4485, 121.0494, "Buli"));

        // BRGY. BAYANAN
        knownCenters.add(new EvacuationCenter("Bayanan Baywalk Covered Court", 14.4108, 121.0469, "Bayanan"));
        knownCenters.add(new EvacuationCenter("Bayanan Elementary School Unit 1", 14.4100, 121.0450, "Bayanan"));
        knownCenters.add(new EvacuationCenter("Bayanan Elementary School Main", 14.4090, 121.0440, "Bayanan"));
        knownCenters.add(new EvacuationCenter("Pleasant Covered Court", 14.4130, 121.0430, "Bayanan"));

        // BRGY. AYALA ALABANG
        knownCenters.add(new EvacuationCenter("Brgy. Ayala Alabang Community Center", 14.4059, 121.0223, "Ayala Alabang"));
        knownCenters.add(new EvacuationCenter("Ayala Alabang Basilan Covered Court", 14.4040, 121.0200, "Ayala Alabang"));
    }

    private interface OnCenterClickListener {
        void onCenterClick(EvacuationCenter center);
    }

    private static class EvacuationAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private final List<DisplayItem> items;
        private final OnCenterClickListener listener;

        EvacuationAdapter(List<DisplayItem> items, OnCenterClickListener listener) {
            this.items = items;
            this.listener = listener;
        }

        @Override
        public int getItemViewType(int position) {
            return items.get(position).viewType;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == DisplayItem.TYPE_HEADER) {
                View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_evacuation_header, parent, false);
                return new HeaderViewHolder(view);
            } else {
                View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_evacuation_center, parent, false);
                return new CenterViewHolder(view);
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            DisplayItem item = items.get(position);
            if (holder instanceof HeaderViewHolder) {
                ((HeaderViewHolder) holder).headerTitle.setText(item.headerTitle);
            } else if (holder instanceof CenterViewHolder) {
                CenterViewHolder centerHolder = (CenterViewHolder) holder;
                EvacuationCenter center = item.center;
                if (center != null) {
                    centerHolder.name.setText(center.name);
                    centerHolder.type.setText(String.format("Barangay: %s", center.type));

                    centerHolder.itemView.setOnClickListener(v -> listener.onCenterClick(center));
                    if (centerHolder.btnDirections != null) {
                        centerHolder.btnDirections.setOnClickListener(v -> listener.onCenterClick(center));
                    }
                }
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class HeaderViewHolder extends RecyclerView.ViewHolder {
            TextView headerTitle;

            HeaderViewHolder(View itemView) {
                super(itemView);
                headerTitle = itemView.findViewById(R.id.txtBarangayHeader);
            }
        }

        static class CenterViewHolder extends RecyclerView.ViewHolder {
            TextView name, type;
            ImageView icon;
            View btnDirections;

            CenterViewHolder(View itemView) {
                super(itemView);
                name = itemView.findViewById(R.id.centerName);
                type = itemView.findViewById(R.id.centerType);
                icon = itemView.findViewById(R.id.centerIcon);
                btnDirections = itemView.findViewById(R.id.btnDirections);
            }
        }
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        android.content.res.Configuration override = new android.content.res.Configuration(newBase.getResources().getConfiguration());
        override.fontScale = 1.0f;
        applyOverrideConfiguration(override);
        super.attachBaseContext(newBase);
    }
}
