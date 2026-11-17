# Smart Flood Monitoring and Early Warning System

## Project Overview

The Smart Flood Monitoring and Early Warning System is an IoT-based flood monitoring solution developed to provide real-time water-level monitoring, weather information, and early warnings for flood-prone communities in the City of Muntinlupa.

The project integrates IoT hardware, Firebase Realtime Database, AI-based analytics, and an Android mobile application.

This repository contains the source code of the Android mobile application.

## Project Objectives

- Monitor water-level readings from IoT sensor stations.
- Display real-time flood monitoring information.
- Provide weather forecasts and current weather information.
- Analyze water-level trends to support early warnings.
- Provide access to evacuation centers and emergency information.
- Help users access relevant flood-related information.

## Technologies Used

| Component | Technology |
|---|---|
| Mobile Application | Android Studio |
| Programming Language | Java |
| User Interface | XML |
| Database | Firebase Realtime Database |
| IoT Microcontroller | Arduino Uno |
| Wi-Fi Module | ESP8266 NodeMCU |
| Water-Level Sensor | Ultrasonic Sensor |
| Weather Information | Weather API |
| Version Control | Git and GitHub |

## Main Application Features

- Dashboard and water-level monitoring
- Current weather information
- Weather forecast
- AI-based safety advisory
- Water-level prediction and analysis
- Historical monitoring data
- Weather and flood-related news
- Evacuation center information
- Emergency hotlines
- Precautionary measures

## System Architecture

1. The ultrasonic sensor measures the water level.
2. The Arduino processes the sensor readings.
3. The ESP8266 transmits the readings through Wi-Fi.
4. Firebase Realtime Database stores the monitoring data.
5. The Android application retrieves and displays the information.
6. The application provides analysis and relevant warnings based on available data.

## Repository Scope

This repository is intended for source-code examination and technical evaluation by IT experts and project evaluators.

It contains the Android mobile application source code and related project configuration.

## Installation and Setup

1. Clone or download this repository.
2. Open the project using Android Studio.
3. Configure the required local API credentials in `secrets.properties`.
4. Add the required Firebase configuration file.
5. Allow Gradle to synchronize dependencies.
6. Build and run the application.

Do not upload private credentials, signing keys, or passwords to this repository.

## Project Information

**Project Title:** Development of a Smart Flood Monitoring and Early Warning System Using IoT, AI Analytics, and Mobile Application Integration for the City of Muntinlupa.

**Institution:** Pamantasan ng Lungsod ng Muntinlupa

**Program:** Bachelor of Science in Information Technology

**Purpose:** Academic Capstone Project and Technical Evaluation