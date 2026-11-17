package com.example.mainpage;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || context == null) return;
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) ||
                "android.intent.action.QUICKBOOT_POWERON".equals(action) ||
                Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {

            Log.d("BootReceiver", "Re-starting FloodMonitorService following system boot or update.");
            Intent serviceIntent = new Intent(context, FloodMonitorService.class);
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent);
                } else {
                    context.startService(serviceIntent);
                }
            } catch (Exception e) {
                Log.e("BootReceiver", "Error starting FloodMonitorService from boot: " + e.getMessage());
            }

            // Ensure background weather WorkManager is scheduled
            WeatherHelper.schedulePeriodicWeatherCheck(context);
        }
    }
}
