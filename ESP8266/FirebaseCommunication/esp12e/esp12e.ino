
#include <ESP8266WiFi.h>
#include <Firebase_ESP_Client.h>
#include <math.h>

#include "addons/TokenHelper.h"
#include "addons/RTDBHelper.h"

#define WIFI_SSID "WIFI SSID"
#define WIFI_PASSWORD "WIFI PASSWORD"
#define API_KEY "YOUR API KEY"
#define DATABASE_URL "YOUR DATABASE URL"

FirebaseData fbdo;
FirebaseAuth auth;
FirebaseConfig config;

#define CURRENT_PATH "/FloodMonitoringSystem/Stations/ST001/Current"
#define HISTORY_PATH "/FloodMonitoringSystem/Stations/ST001/History"

const float MAX_WATER_LEVEL = 200.0;
const unsigned long HISTORY_INTERVAL = 600000UL;
unsigned long lastHistorySave = 0;

bool parseArduinoData(
  String data,
  float &level,
  String &status
) {

  data.trim();

  String levelString = "";

  // FORMAT 1: COMMA SEPARATED

  int comma = data.indexOf(',');

  if (comma > 0) {

    levelString = data.substring(0, comma);

    status = data.substring(comma + 1);

  }

  // FORMAT 2: DESCRIPTIVE SERIAL DATA

  else {

    int waterLabel = data.indexOf("Water Level:");
    int statusLabel = data.indexOf("Status:");

    if (waterLabel < 0 || statusLabel < 0) {
      return false;
    }

    int startPosition = waterLabel + 12;

    int cmPosition = data.indexOf("cm", startPosition);

    if (cmPosition < 0 || cmPosition > statusLabel) {
      return false;
    }

    levelString = data.substring(
      startPosition,
      cmPosition
    );

    status = data.substring(statusLabel + 7);
  }

  levelString.trim();
  status.trim();
  status.toUpperCase();

  // VALIDATE STATUS

  if (status != "SAFE" &&
      status != "WARNING" &&
      status != "DANGER") {

    return false;
  }

  // CONVERT WATER LEVEL

  char* endPointer;

  level = strtof(
    levelString.c_str(),
    &endPointer
  );

  if (levelString.length() == 0 ||
      endPointer == levelString.c_str() ||
      *endPointer != '\0' ||
      !isfinite(level)) {

    return false;
  }

  // VALIDATE CENTIMETERS

  if (level < 0 || level > MAX_WATER_LEVEL) {
    return false;
  }

  return true;
}



bool saveHistory(float level, String status) {

  FirebaseJson json;

  json.set("WaterLevel", level);
  json.set("Status", status);
  json.set("Timestamp/.sv", "timestamp");

  if (Firebase.RTDB.pushJSON(
        &fbdo,
        HISTORY_PATH,
        &json
      )) {

    Serial.println("HISTORY SAVED SUCCESSFULLY");

    return true;
  }

  Serial.print("HISTORY SAVE FAILED: ");
  Serial.println(fbdo.errorReason());

  return false;
}



void setup() {

  Serial.begin(9600);

  Serial.setTimeout(1000);

  delay(1000);

  Serial.println();
  Serial.println("ESP8266 FLOOD MONITORING SYSTEM");

  // WIFI

  WiFi.mode(WIFI_STA);

  WiFi.begin(
    WIFI_SSID,
    WIFI_PASSWORD
  );

  Serial.print("Connecting to WiFi");

  int attempts = 0;

  while (WiFi.status() != WL_CONNECTED &&
         attempts < 40) {

    delay(500);

    Serial.print(".");

    attempts++;
  }

  if (WiFi.status() != WL_CONNECTED) {

    Serial.println();
    Serial.println("WiFi CONNECTION FAILED");

    while (true) {
      delay(1000);
    }
  }

  Serial.println();
  Serial.println("WIFI CONNECTED");

  Serial.print("IP: ");
  Serial.println(WiFi.localIP());

  // FIREBASE CONFIGURATION

  config.api_key = API_KEY;
  config.database_url = DATABASE_URL;

  // FIREBASE AUTHENTICATION

  if (Firebase.signUp(
        &config,
        &auth,
        "",
        ""
      )) {

    Serial.println("FIREBASE SIGNUP SUCCESSFUL");

  }

  else {

    Serial.print("FIREBASE SIGNUP FAILED: ");

    Serial.println(
      config.signer.signupError.message.c_str()
    );

  }

  // START FIREBASE

  Firebase.begin(
    &config,
    &auth
  );

  Firebase.reconnectWiFi(true);

  Serial.println("FIREBASE INITIALIZED");

  Serial.println("WAITING FOR ARDUINO DATA...");
}



void loop() {

  // CHECK WIFI

  if (WiFi.status() != WL_CONNECTED) {

    WiFi.reconnect();

    delay(1000);

    return;
  }

  // CHECK SERIAL DATA

  if (!Serial.available()) {
    return;
  }

  String data = Serial.readStringUntil('\n');

  data.trim();

  if (data.length() == 0) {
    return;
  }

  Serial.print("RECEIVED: ");

  Serial.println(data);

  // PARSE DATA

  float waterLevel = 0;

  String status = "";

  if (!parseArduinoData(
        data,
        waterLevel,
        status
      )) {

    Serial.println("INVALID SERIAL DATA!");

    return;
  }

  Serial.println("SERIAL DATA PARSED SUCCESSFULLY");

  Serial.print("WATER LEVEL: ");

  Serial.print(waterLevel, 2);

  Serial.println(" cm");

  Serial.print("STATUS: ");

  Serial.println(status);

  // CHECK FIREBASE

  if (!Firebase.ready()) {

    Serial.println("FIREBASE NOT READY");

    return;
  }

  // READ PREVIOUS STATUS

  String previousStatus = "";

  bool previousStatusRead = Firebase.RTDB.getString(
    &fbdo,
    String(CURRENT_PATH) + "/Status"
  );

  if (previousStatusRead) {

    previousStatus = fbdo.stringData();

    Serial.print("PREVIOUS STATUS: ");

    Serial.println(previousStatus);

  }

  else {

    Serial.println("PREVIOUS STATUS NOT AVAILABLE");

  }

  // DETECT NEW DANGER TRANSITION

  bool newDangerEvent =
    previousStatusRead &&
    status == "DANGER" &&
    previousStatus != "DANGER";

  // UPDATE WATER LEVEL

  bool levelUpdated = Firebase.RTDB.setFloat(
    &fbdo,
    String(CURRENT_PATH) + "/WaterLevel",
    waterLevel
  );

  // UPDATE STATUS

  bool statusUpdated = Firebase.RTDB.setString(
    &fbdo,
    String(CURRENT_PATH) + "/Status",
    status
  );

  // UPDATE TIMESTAMP

  bool timestampUpdated = Firebase.RTDB.setTimestamp(
    &fbdo,
    String(CURRENT_PATH) + "/Timestamp"
  );

  // CHECK UPDATE RESULT

  if (levelUpdated &&
      statusUpdated &&
      timestampUpdated) {
  }

  else {

    Serial.println("CURRENT DATA UPDATE INCOMPLETE");

    if (!levelUpdated) {
      Serial.println(fbdo.errorReason());
    }

    if (!statusUpdated) {
      Serial.println(fbdo.errorReason());
    }

    if (!timestampUpdated) {
      Serial.println(fbdo.errorReason());
    }
  }

  // SAVE NEW DANGER EVENT

  if (newDangerEvent &&
      levelUpdated &&
      statusUpdated &&
      timestampUpdated) {

    Serial.println("NEW DANGER EVENT!");

    saveHistory(waterLevel, "DANGER");

  }

  // SAVE REGULAR HISTORY EVERY 10 MINUTES

  if (millis() - lastHistorySave >= HISTORY_INTERVAL) {

    if (saveHistory(waterLevel, status)) {

      lastHistorySave = millis();

    }
  }

  Serial.println();
}
