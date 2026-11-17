
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")
}

// Load local secrets
val secretsProperties = Properties()
val secretsFile = rootProject.file("secrets.properties")

if (secretsFile.exists()) {
    secretsFile.inputStream().use {
        secretsProperties.load(it)
    }
}

val weatherApiKey = secretsProperties.getProperty("WEATHER_API_KEY", "")

android {
    namespace = "com.example.mainpage"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.mainpage"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField(
            "String",
            "WEATHER_API_KEY",
            "\"${weatherApiKey}\""
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.database)
    implementation(libs.material)

    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)

    implementation(platform("com.google.firebase:firebase-bom:34.14.0"))
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation(libs.play.services.maps)
    implementation(libs.osmdroid)
    implementation(libs.retrofit)
    implementation(libs.converter.gson)
    implementation(libs.gson)
    implementation(libs.firebase.ai)
    implementation(libs.reactive.streams)

    implementation("com.google.firebase:firebase-appcheck-debug")
    implementation("com.google.firebase:firebase-appcheck-playintegrity")
    implementation("com.google.firebase:firebase-appcheck")
    implementation("com.google.firebase:firebase-messaging")
    implementation("com.google.guava:guava:33.7.1-android")
    implementation("androidx.work:work-runtime:2.9.1")
}
