# Keep model classes for Gson & Retrofit
-keepclassmembers class com.example.mainpage.MainActivity$WeatherResponse** { *; }
-keepclassmembers class com.example.mainpage.MainActivity$ForecastResponse** { *; }
-keepclassmembers class com.example.mainpage.HistoryActivity$HistoryRecord** { *; }
-keepclassmembers class com.example.mainpage.EvacuationActivity$EvacuationCenter** { *; }

# Keep Retrofit interface methods and annotations
-keepclassmembers interface com.example.mainpage.MainActivity$WeatherService { *; }

# Preserve line numbers for stack traces
-keepattributes SourceFile,LineNumberTable
