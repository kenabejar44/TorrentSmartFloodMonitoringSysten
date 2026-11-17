package com.example.mainpage;

import android.app.Application;
import android.util.Log;
import com.google.firebase.FirebaseApp;
import com.google.firebase.appcheck.FirebaseAppCheck;
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory;
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory;
import com.google.firebase.auth.FirebaseAuth;

public class MainApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        
        // Initialize Firebase
        FirebaseApp.initializeApp(this);

        
        // Automatic guest authentication for unauthenticated monitoring access
        try {
            FirebaseAuth auth = FirebaseAuth.getInstance();
            if (auth.getCurrentUser() == null) {
                auth.signInAnonymously()
                        .addOnSuccessListener(result -> Log.d("MAIN_APP", "Guest anonymous login active."))
                        .addOnFailureListener(e -> Log.e("MAIN_APP", "Anonymous login note: " + e.getMessage()));
            }
        } catch (Throwable t) {
            Log.e("MAIN_APP", "Auth init error: " + t.getMessage());
        }

        // Initialize App Check
        try {
            FirebaseAppCheck firebaseAppCheck = FirebaseAppCheck.getInstance();
            firebaseAppCheck.setTokenAutoRefreshEnabled(true);
            
            if (BuildConfig.DEBUG) {
                firebaseAppCheck.installAppCheckProviderFactory(
                        DebugAppCheckProviderFactory.getInstance());
            } else {
                firebaseAppCheck.installAppCheckProviderFactory(
                        PlayIntegrityAppCheckProviderFactory.getInstance());
            }
        } catch (Throwable t) {
            Log.e("MAIN_APP", "App Check init error: " + t.getMessage());
        }

        // Schedule periodic WorkManager background weather checks
        WeatherHelper.schedulePeriodicWeatherCheck(this);

        // Subscribe to FCM Push Topics for background alerts
        MyFirebaseMessagingService.subscribeToTopics();
    }
}
