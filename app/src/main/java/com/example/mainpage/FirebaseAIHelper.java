package com.example.mainpage;

import android.util.Log;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.ai.FirebaseAI;
import com.google.firebase.ai.GenerativeModel;
import com.google.firebase.ai.java.GenerativeModelFutures;
import com.google.firebase.ai.type.Content;
import com.google.firebase.ai.type.GenerateContentResponse;
import com.google.firebase.ai.type.GenerationConfig;
import com.google.firebase.ai.type.GenerativeBackend;

import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Implements the AI Logic layer using the Firebase AI Logic SDK.
 * Architecture: App -> Firebase AI Logic SDK -> Gemini Developer API (No-Cost Backend)
 * This version uses Gemini 3 Flash to avoid retirement errors in the current project timeline.
 */
public class FirebaseAIHelper {
    private static final String TAG = "FirebaseAIHelper";
    
    // The API Key is now managed by Firebase AI Logic using your project's configuration.
    // Hardcoded keys are no longer required in source code.
    
    private final GenerativeModelFutures model;
    private final Executor executor = Executors.newSingleThreadExecutor();

    public FirebaseAIHelper() {
        GenerativeModelFutures tempModel = null;
        try {
            // Initialize Firebase AI Logic with the Google AI (no-cost) backend
            // This uses the Gemini Developer API through Firebase.
            GenerationConfig config = new GenerationConfig.Builder()
                    .setResponseMimeType("application/json")
                    .build();

            // Using gemini-2.0-flash with fallback to gemini-1.5-flash for maximum reliability
            GenerativeModel ai = null;
            try {
                ai = FirebaseAI.getInstance(GenerativeBackend.googleAI())
                        .generativeModel("gemini-2.0-flash", config);
            } catch (Throwable t1) {
                try {
                    ai = FirebaseAI.getInstance(GenerativeBackend.googleAI())
                            .generativeModel("gemini-1.5-flash", config);
                } catch (Throwable t2) {
                    Log.e(TAG, "Fallback AI model init failed: " + t2.getMessage());
                }
            }

            if (ai != null) {
                tempModel = GenerativeModelFutures.from(ai);
            }
        } catch (Throwable t) {
            Log.e(TAG, "AI Model Initialization failed: " + t.getMessage());
        }
        model = tempModel;
    }

    public interface AICallback {
        void onSuccess(String result);
        void onError(Throwable t);
    }

    public static String stripEmojis(String text) {
        if (text == null) return null;
        return text.replaceAll("[\\uD83C-\\uDBFF\\uDC00-\\uDFFF\\u2600-\\u27BF\\u2300-\\u23FF\\u2B50\\u2B55]", "");
    }

    public void generateSafetyAdvice(String condition, double temp, double humidity, double windSpeed, double rainProb, double waterLevel, String location, String forecastData, AICallback callback) {
        if (model == null) {
            callback.onError(new Exception("AI Model not initialized"));
            return;
        }
        double heatIndex = WeatherHelper.calculateHeatIndex(temp, humidity);
        String hiCategory = WeatherHelper.getHeatIndexCategory(heatIndex);

        // Ensure we explicitly mention the city to prevent AI hallucinations, focusing on Muntinlupa generally
        String cityContext = location;
        if (location.toLowerCase().contains("muntinlupa") || location.toLowerCase().contains("putatan") || location.toLowerCase().contains("alabang") || location.toLowerCase().contains("bayanan") || location.toLowerCase().contains("buli") || location.toLowerCase().contains("cupang") || location.toLowerCase().contains("poblacion") || location.toLowerCase().contains("sucat") || location.toLowerCase().contains("tunasan") || location.toLowerCase().contains("ayala alabang")) {
            cityContext = "Muntinlupa City";
        }

        String prompt = String.format(Locale.getDefault(),
            "Act as a professional PAGASA weather analyst. Analyze the following data for %s:\n" +
            "METRICS:\n" +
            "- Condition: %s, Temp: %.1f°C, Humidity: %.1f%%, Heat Index: %.1f°C (%s), Wind: %.1f m/s, Rain Prob: %.0f%%\n" +
            "- Current Water Level: %.1f cm\n" +
            "FORECAST CONTEXT:\n%s\n\n" +
            "TASKS:\n" +
            "Provide an AI-assisted assessment. Return a JSON object string. Use these exact keys: " +
            "\"riskLevel\" (string: LOW/MEDIUM/HIGH/CRITICAL), \"summary\" (string), \"heatAdvice\" (string), \"floodPrecautions\" (string), \"recommendation\" (string), \"assessment\" (string).\n" +
            "IMPORTANT: Focus on Muntinlupa City (which has 9 barangays). Provide a generalized assessment for the city while acknowledging the specific metrics provided. Mention neighboring cities ONLY if they directly affect local weather or flood flow.\n" +
            "Include specific heat-related and rain/flood-related precautions based on the data.\n" +
            "Do NOT include any emoji characters in the response.\n" +
            "Keep response concise. Max 150 words total.",
            cityContext, condition, temp, humidity, heatIndex, hiCategory, windSpeed, rainProb, waterLevel, forecastData
        );

        Content content = new Content.Builder()
            .addText(prompt)
            .build();

        try {
            ListenableFuture<GenerateContentResponse> response = model.generateContent(content);
            Futures.addCallback(response, new FutureCallback<GenerateContentResponse>() {
                @Override
                public void onSuccess(GenerateContentResponse result) {
                    if (result == null) {
                        callback.onError(new Exception("Null response from AI"));
                        return;
                    }
                    String text = result.getText();
                    if (text != null) {
                        // Pre-clean the AI response for the parser
                        String rawText = text.trim();
                        String finalResult = rawText;
                        
                        // Use regex to extract JSON if it's wrapped in markdown code blocks
                        try {
                            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(?s)```(?:json)?\\s*(.*?)\\s*```");
                            java.util.regex.Matcher matcher = pattern.matcher(rawText);
                            if (matcher.find()) {
                                String match = matcher.group(1);
                                if (match != null) {
                                    finalResult = match.trim();
                                }
                            }
                        } catch (Exception e) {
                            Log.w(TAG, "Regex cleaning failed", e);
                        }
                        callback.onSuccess(stripEmojis(finalResult));
                    } else {
                        callback.onError(new Exception("Empty response from AI"));
                    }
                }

                @Override
                public void onFailure(Throwable t) {
                    Log.e(TAG, "AI Error: " + t.getMessage(), t);
                    callback.onError(t);
                }
            }, executor);
        } catch (Throwable t) {
            Log.e(TAG, "Exception calling generateContent: " + t.getMessage(), t);
            callback.onError(t);
        }
    }

    public void generateAnalyticsInsight(String stationName, double currentLevel, double pastLevel, AICallback callback) {
        if (model == null) {
            callback.onError(new Exception("AI Model not initialized"));
            return;
        }
        String trend = currentLevel > pastLevel ? "increasing" : (currentLevel < pastLevel ? "decreasing" : "stable");
        double diff = Math.abs(currentLevel - pastLevel);

        // Explicitly mention Muntinlupa generally
        String locationContext = "Muntinlupa City (Station: " + stationName + ")";

        String prompt = String.format(Locale.getDefault(),
            "Act as a flood management AI Expert. Perform an AI Assessment & Prediction for %s:\n" +
            "DATA HISTORY & TREND:\n" +
            "- Current Water Level: %.1f cm\n" +
            "- Past Level (1 hour ago): %.1f cm\n" +
            "- Trend: %s by %.1f cm\n\n" +
            "TASKS:\n" +
            "Return a JSON object with two keys: \"prediction\" (a detailed summary and 3-6 hour prediction) and \"recommendation\" (actionable safety advice).\n" +
            "IMPORTANT: Focus on Muntinlupa City generalized assessment (covering all 9 barangays). Do not confuse with neighboring cities unless relevant to flood flow.\n" +
            "Do NOT include any emoji characters in the response.\n" +
            "Keep response professional and data-driven. Maximum 100 words.",
            locationContext, currentLevel, pastLevel, trend, diff
        );

        Content content = new Content.Builder()
            .addText(prompt)
            .build();

        try {
            ListenableFuture<GenerateContentResponse> response = model.generateContent(content);
            Futures.addCallback(response, new FutureCallback<GenerateContentResponse>() {
                @Override
                public void onSuccess(GenerateContentResponse result) {
                    if (result == null) {
                        callback.onError(new Exception("Null response from AI"));
                        return;
                    }
                    String text = result.getText();
                    if (text != null) {
                        String rawText = text.trim();
                        String finalResult = rawText;
                        try {
                            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(?s)```(?:json)?\\s*(.*?)\\s*```");
                            java.util.regex.Matcher matcher = pattern.matcher(rawText);
                            if (matcher.find()) {
                                String match = matcher.group(1);
                                if (match != null) finalResult = match.trim();
                            }
                        } catch (Exception e) {
                            Log.w(TAG, "Regex cleaning failed", e);
                        }
                        callback.onSuccess(stripEmojis(finalResult));
                    } else {
                        callback.onError(new Exception("Empty response from AI"));
                    }
                }

                @Override
                public void onFailure(Throwable t) {
                    callback.onError(t);
                }
            }, executor);
        } catch (Throwable t) {
            Log.e(TAG, "Exception calling generateContent: " + t.getMessage(), t);
            callback.onError(t);
        }
    }
}
