
#include <SoftwareSerial.h>
#include <Wire.h>
#include <LiquidCrystal_I2C.h>

LiquidCrystal_I2C lcd(0x27, 16, 2);

#define TRIG_PIN 6
#define ECHO_PIN 7
#define GREEN_LED 8
#define ORANGE_LED 11
#define RED_LED 10
#define BUZZER_PIN 4

SoftwareSerial espSerial(2, 3);

// Reference distance: Sensor to riverbed
const float REFERENCE_HEIGHT = 200.0;

// Water-level thresholds in centimeters
const float SAFE_LIMIT = 50.0;
const float WARNING_LIMIT = 70.0;

// Latest readings
float lastWaterLevel = 0;

String lastStatus = "SAFE";
bool hasValidReading = false;

// Sensor reading interval
unsigned long lastSensorRead = 0;
const unsigned long SENSOR_INTERVAL = 2000;

bool buzzerActive = false;
bool buzzerCooldown = false;

unsigned long buzzerTimer = 0;

const unsigned long BUZZER_DURATION = 5000;
const unsigned long BUZZER_COOLDOWN = 10000;

bool redLedState = false;
unsigned long redLedTimer = 0;

const unsigned long RED_BLINK_INTERVAL = 100;

void displayLine(byte row, String message) {
  lcd.setCursor(0, row);
  lcd.print("                ");

  lcd.setCursor(0, row);
  lcd.print(message.substring(0, 16));
}



void updateBuzzer(String status) {

  unsigned long currentTime = millis();

  if (!hasValidReading || status != "DANGER") {

    digitalWrite(BUZZER_PIN, LOW);

    buzzerActive = false;
    buzzerCooldown = false;

    return;
  }

  if (!buzzerActive && !buzzerCooldown) {

    buzzerActive = true;
    buzzerTimer = currentTime;

    digitalWrite(BUZZER_PIN, HIGH);
  }

  if (buzzerActive &&
      currentTime - buzzerTimer >= BUZZER_DURATION) {

    digitalWrite(BUZZER_PIN, LOW);

    buzzerActive = false;
    buzzerCooldown = true;

    buzzerTimer = currentTime;
  }

  if (buzzerCooldown &&
      currentTime - buzzerTimer >= BUZZER_COOLDOWN) {

    buzzerCooldown = false;
    buzzerActive = true;

    buzzerTimer = currentTime;

    digitalWrite(BUZZER_PIN, HIGH);
  }
}



void updateRedLED(String status) {

  unsigned long currentTime = millis();

  if (hasValidReading && status == "DANGER") {

    if (currentTime - redLedTimer >= RED_BLINK_INTERVAL) {

      redLedTimer = currentTime;

      redLedState = !redLedState;

      digitalWrite(RED_LED, redLedState);
    }
  }

  else {

    redLedState = false;

    digitalWrite(RED_LED, LOW);
  }
}



void readWaterLevel() {

  long duration;
  float distance;

  digitalWrite(TRIG_PIN, LOW);
  delayMicroseconds(5);

  digitalWrite(TRIG_PIN, HIGH);
  delayMicroseconds(10);

  digitalWrite(TRIG_PIN, LOW);

  duration = pulseIn(ECHO_PIN, HIGH, 30000);

  if (duration == 0) {

    Serial.println("Sensor timeout - no valid reading");

    hasValidReading = false;

    displayLine(0, "SENSOR ERROR");
    displayLine(1, "Check HC-SR04");

    digitalWrite(BUZZER_PIN, LOW);

    digitalWrite(GREEN_LED, LOW);
    digitalWrite(ORANGE_LED, LOW);
    digitalWrite(RED_LED, LOW);

    buzzerActive = false;
    buzzerCooldown = false;

    redLedState = false;

    espSerial.println("-1.0,NO_DATA");

    return;
  }

  distance = duration * 0.0343 / 2.0;

  // Validate HC-SR04 measurement
  if (distance < 2.0 || distance > 400.0) {

    Serial.println("Invalid sensor distance");

    hasValidReading = false;

    displayLine(0, "INVALID READING");
    displayLine(1, "Check Sensor");

    digitalWrite(BUZZER_PIN, LOW);

    digitalWrite(GREEN_LED, LOW);
    digitalWrite(ORANGE_LED, LOW);
    digitalWrite(RED_LED, LOW);

    buzzerActive = false;
    buzzerCooldown = false;

    redLedState = false;

    espSerial.println("-1.0,NO_DATA");

    return;
  }

  // Calculate water level
  float waterLevel = REFERENCE_HEIGHT - distance;

  if (waterLevel < 0) {
    waterLevel = 0;
  }

  if (waterLevel > REFERENCE_HEIGHT) {
    waterLevel = REFERENCE_HEIGHT;
  }

  String status;



  if (waterLevel <= SAFE_LIMIT) {

    status = "SAFE";

    digitalWrite(GREEN_LED, HIGH);
    digitalWrite(ORANGE_LED, LOW);
    digitalWrite(RED_LED, LOW);
  }

  else if (waterLevel <= WARNING_LIMIT) {

    status = "WARNING";

    digitalWrite(GREEN_LED, LOW);
    digitalWrite(ORANGE_LED, HIGH);
    digitalWrite(RED_LED, LOW);
  }

  else {

    status = "DANGER";

    digitalWrite(GREEN_LED, LOW);
    digitalWrite(ORANGE_LED, LOW);

    // Red LED controlled in loop()
  }

  lastWaterLevel = waterLevel;
  lastStatus = status;

  hasValidReading = true;



  displayLine(0, "STATUS: " + status);

  displayLine(
    1,
    "Water: " + String(waterLevel, 1) + "cm"
  );



  espSerial.print(waterLevel, 2);
  espSerial.print(",");
  espSerial.println(status);


  Serial.print("Distance: ");
  Serial.print(distance, 1);

  Serial.print(" cm | Water Level: ");
  Serial.print(waterLevel, 1);

  Serial.print(" cm | Status: ");
  Serial.println(status);
}



void setup() {

  pinMode(TRIG_PIN, OUTPUT);
  pinMode(ECHO_PIN, INPUT);

  pinMode(GREEN_LED, OUTPUT);
  pinMode(ORANGE_LED, OUTPUT);
  pinMode(RED_LED, OUTPUT);

  pinMode(BUZZER_PIN, OUTPUT);

  digitalWrite(BUZZER_PIN, LOW);

  digitalWrite(GREEN_LED, LOW);
  digitalWrite(ORANGE_LED, LOW);
  digitalWrite(RED_LED, LOW);

  Serial.begin(9600);
  espSerial.begin(9600);

  lcd.init();
  lcd.backlight();
  lcd.clear();

  lcd.setCursor(0, 0);
  lcd.print("WATER MONITOR");

  lcd.setCursor(0, 1);
  lcd.print("Initializing...");

  delay(2000);

  lcd.clear();

  Serial.println("Water Monitoring System Started");
  Serial.println("Reference Height: 200 cm");
  Serial.println("SAFE: 0-50 cm");
  Serial.println("WARNING: Above 50-70 cm");
  Serial.println("DANGER: Above 70 cm");

  lastSensorRead = millis() - SENSOR_INTERVAL;
}



void loop() {

  unsigned long currentTime = millis();

  if (currentTime - lastSensorRead >= SENSOR_INTERVAL) {

    lastSensorRead = currentTime;

    readWaterLevel();
  }

  updateRedLED(lastStatus);

  updateBuzzer(lastStatus);
}
