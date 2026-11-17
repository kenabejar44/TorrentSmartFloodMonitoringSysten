package com.example.mainpage;

import android.os.Bundle;
import android.content.Context;
import android.content.res.Configuration;
import androidx.appcompat.app.AppCompatActivity;

public class PrecautionActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_precaution);

        com.google.android.material.appbar.MaterialToolbar toolbar = findViewById(R.id.toolbar);
        SafeAreaHelper.apply(this, findViewById(R.id.includeToolbar), null);

        if (toolbar != null) {
            toolbar.setTitle("Safety Precautions");
            toolbar.setNavigationOnClickListener(v -> finish());
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        Configuration override = new Configuration(newBase.getResources().getConfiguration());
        override.fontScale = 1.0f;
        applyOverrideConfiguration(override);
        super.attachBaseContext(newBase);
    }
}