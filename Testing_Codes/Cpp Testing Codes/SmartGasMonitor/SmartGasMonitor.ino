/*
 * SmartGasMonitor - Reliable Gas Safety & Monitoring System
 * 
 * Pins:
 * - MQ-5 Gas Sensor: GPIO 34 (Analog Input)
 * - DHT22 Temp/Hum:  GPIO 27 (Data)
 * - HX711 Load Cell: GPIO 25 (DT), GPIO 26 (SCK)
 * - Buzzer:          GPIO 19 (PWM Alert)
 * - Green LED:       GPIO 23 (Status OK)
 * - Red LED:         GPIO 32 (Status Alert)
 * - Servo Motor:     GPIO 13 (Valve Controller)
 * - LCD 1602 (I2C):  GPIO 21 (SDA), GPIO 22 (SCL)
 */

#include <Wire.h>
#include <LiquidCrystal_I2C.h>
#include <DHT.h>
#include <HX711.h>
#include <ESP32Servo.h>
#include <WiFi.h>
#include <Firebase_ESP_Client.h>

// ========== PIN DEFINITIONS ==========
#define MQ5_PIN            34
#define DHTPIN             27
#define DHTTYPE            DHT22
#define I2C_SDA            21
#define I2C_SCL            22
#define LCD_ADDRESS        0x27
#define LCD_COLUMNS        16
#define LCD_ROWS           2
#define BUZZER_PIN         19
#define BUZZER_FREQ        2000
#define LOADCELL_DT        25
#define LOADCELL_SCK       26
#define GREEN_LED_PIN      23
#define RED_LED_PIN        32
#define SERVO_PIN          13

// ========== SAFETY CONFIGURATION ==========
#define GAS_THRESHOLD      500    // Alarm triggers above this value
#define VALVE_CLOSED       0      // Servo angle to shut valve (anti-clockwise)
#define VALVE_OPEN         90     // Servo angle to open valve (clockwise)
#define ALARM_BLINK_MS     200    // LED blink speed during alarm

// ========== WEIGHT CONFIGURATION ==========
#define CALIBRATION_FACTOR 99651.00
#define PLATFORM_WEIGHT_KG 1.394
#define EMPTY_CYLINDER_KG  8.6
#define GAS_CAPACITY_KG    5.0

// ========== NETWORK CREDENTIALS ==========
#define WIFI_SSID              "test"
#define WIFI_PASSWORD          "12345678"
#define FIREBASE_API_KEY       "AIzaSyAnUdXb9dJzbqOF90RwgAxMdtHJzfchaSg"
#define FIREBASE_DATABASE_URL  "https://gasguardkdu-default-rtdb.asia-southeast1.firebasedatabase.app"
#define FIREBASE_USER_EMAIL    "gasgurd@gmail.com"
#define FIREBASE_USER_PASSWORD "gasgurd"
#define FIREBASE_DEVICE_ID     "gasguard-esp32-01"

// ========== OBJECTS ==========
LiquidCrystal_I2C lcd(LCD_ADDRESS, LCD_COLUMNS, LCD_ROWS);
DHT dht(DHTPIN, DHTTYPE);
HX711 scale;
Servo gasValve;

// Firebase Data Objects
FirebaseData fbData;
FirebaseAuth fbAuth;
FirebaseConfig fbConfig;

// ========== SYSTEM STATE VARIABLES ==========
int gasLevel = 0;
float temperature = 0.0;
float humidity = 0.0;
float rawWeight = 0.0;
float cylinderWeight = 0.0;
float gasWeight = 0.0;
float gasPercentage = 0.0;

bool alarmActive = false;
bool valveOpen = true;
unsigned long lastBlinkTime = 0;
bool blinkState = false;

// Non-blocking timers
unsigned long lastFirebaseUpdate = 0;
const unsigned long FIREBASE_INTERVAL_MS = 5000; // Update Firebase every 5s

unsigned long lastSensorRead = 0;
const unsigned long SENSOR_INTERVAL_MS = 1000;   // Read sensors every 1s

// ========== SETUP ==========
void setup() {
  Serial.begin(115200);
  delay(1000);
  Serial.println("\n=============================================");
  Serial.println("         SmartGasMonitor SYSTEM BOOT");
  Serial.println("=============================================");

  // Initialize I2C and LCD
  Wire.begin(I2C_SDA, I2C_SCL);
  lcd.init();
  lcd.backlight();
  lcd.clear();
  lcd.setCursor(0, 0);
  lcd.print("System Booting...");

  // Initialize Sensors
  dht.begin();
  scale.begin(LOADCELL_DT, LOADCELL_SCK);
  scale.set_scale(CALIBRATION_FACTOR);
  
  // Perform software tare on startup
  lcd.setCursor(0, 1);
  lcd.print("Taring Scale...");
  if (scale.wait_ready_timeout(2000)) {
    scale.tare();
    Serial.println("Load cell tared successfully.");
  } else {
    Serial.println("Warning: HX711 not found during setup!");
  }

  // Initialize Actuators (Buzzer & LEDs)
  pinMode(BUZZER_PIN, OUTPUT);
  digitalWrite(BUZZER_PIN, LOW);

  pinMode(GREEN_LED_PIN, OUTPUT);
  pinMode(RED_LED_PIN, OUTPUT);
  digitalWrite(GREEN_LED_PIN, HIGH); // Green ON (Safe default)
  digitalWrite(RED_LED_PIN, LOW);   // Red OFF

  // Initialize Servo Valve
  gasValve.attach(SERVO_PIN, 500, 2400);
  gasValve.write(VALVE_OPEN);
  valveOpen = true;
  lcd.clear();
  lcd.print("Valve: OPEN");
  delay(1000);

  // Initialize WiFi (Asynchronous)
  connectWiFi();

  // Initialize Firebase
  initFirebase();

  Serial.println("System Ready! Local safety monitoring active.");
}

// ========== MAIN LOOP ==========
void loop() {
  // 1. LOCAL SAFETY ACTIONS (Must run instantly and never block)
  readGasSensor();
  handleSafetyThresholds();

  // 2. PERIODIC LOGICAL TASKS (DHT22, Loadcell, LCD)
  unsigned long currentMillis = millis();
  if (currentMillis - lastSensorRead >= SENSOR_INTERVAL_MS) {
    lastSensorRead = currentMillis;
    readOtherSensors();
    updateLCD();
  }

  // 3. PERIODIC NETWORK OPERATIONS (Non-blocking, failsafe)
  if (WiFi.status() == WL_CONNECTED && Firebase.ready()) {
    if (currentMillis - lastFirebaseUpdate >= FIREBASE_INTERVAL_MS) {
      lastFirebaseUpdate = currentMillis;
      writeFirebaseStatus();
    }
  }
}

// ========== SENSOR READING FUNCTIONS ==========
void readGasSensor() {
  gasLevel = analogRead(MQ5_PIN);
}

void readOtherSensors() {
  // Read DHT22
  float t = dht.readTemperature();
  float h = dht.readHumidity();
  if (!isnan(t) && !isnan(h)) {
    temperature = t;
    humidity = h;
  }

  // Read Load Cell (Weight in KG)
  if (scale.wait_ready_timeout(100)) {
    rawWeight = scale.get_units(3); // Average of 3 readings
    if (rawWeight < 0.0) rawWeight = 0.0;
    
    cylinderWeight = rawWeight; 
    gasWeight = cylinderWeight - EMPTY_CYLINDER_KG;
    if (gasWeight < 0.0) gasWeight = 0.0;
    
    gasPercentage = (gasWeight / GAS_CAPACITY_KG) * 100.0;
    if (gasPercentage > 100.0) gasPercentage = 100.0;
  }
}

// ========== LOCAL SAFETY LOGIC ==========
void handleSafetyThresholds() {
  if (gasLevel > GAS_THRESHOLD) {
    alarmActive = true;
    digitalWrite(GREEN_LED_PIN, LOW);

    // Blinking Alert LED & Active Buzzer
    if (millis() - lastBlinkTime >= ALARM_BLINK_MS) {
      lastBlinkTime = millis();
      blinkState = !blinkState;
      digitalWrite(RED_LED_PIN, blinkState);
      digitalWrite(BUZZER_PIN, blinkState); // Toggles active buzzer pin HIGH/LOW
    }

    // Emergency Valve Closure
    if (valveOpen) {
      lcd.clear();
      lcd.setCursor(0, 0);
      lcd.print("ALERT: GAS LEAK");
      lcd.setCursor(0, 1);
      lcd.print("Closing Valve...");
      
      gasValve.write(VALVE_CLOSED); // Rotate to 0 degrees
      delay(1500);                  // Block loop briefly to allow full rotation
      valveOpen = false;

      // Send event to Firebase immediately
      pushFirebaseEvent("gas_leak_detected", "Gas leak detected. Shutting valve.");
    }
  } else {
    // Normal operations
    if (alarmActive) {
      // Transition from Alarm to Safe
      alarmActive = false;
      digitalWrite(RED_LED_PIN, LOW);
      digitalWrite(BUZZER_PIN, LOW);
      digitalWrite(GREEN_LED_PIN, HIGH);

      // Re-opening Valve
      if (!valveOpen) {
        lcd.clear();
        lcd.setCursor(0, 0);
        lcd.print("SYSTEM SAFE");
        lcd.setCursor(0, 1);
        lcd.print("Opening Valve...");
        
        gasValve.write(VALVE_OPEN); // Rotate back to 90 degrees
        delay(1500);                // Block loop briefly to allow full rotation
        valveOpen = true;

        // Send event to Firebase immediately
        pushFirebaseEvent("alarm_cleared", "Gas levels normal. Valve opened.");
      }
    }
  }
}

// ========== DISPLAY FUNCTIONS ==========
void updateLCD() {
  if (alarmActive) {
    // Keep critical leak info on display
    lcd.setCursor(0, 0);
    lcd.print("LEAK DETECTED!  ");
    char line1[17];
    snprintf(line1, sizeof(line1), "Gas Level: %-5d", gasLevel);
    lcd.setCursor(0, 1);
    lcd.print(line1);
  } else {
    char line0[17];
    char line1[17];

    // Row 0: Gas Level & Cylinder weight
    snprintf(line0, sizeof(line0), "G:%-4d Cyl:%-4.2fkg", gasLevel, cylinderWeight);
    int len0 = strlen(line0);
    while (len0 < 16) line0[len0++] = ' ';
    line0[16] = '\0';

    // Row 1: Temp & Humidity
    snprintf(line1, sizeof(line1), "T:%-4.1fC H:%-3.0f%%  ", temperature, humidity);
    int len1 = strlen(line1);
    while (len1 < 16) line1[len1++] = ' ';
    line1[16] = '\0';

    lcd.setCursor(0, 0);
    lcd.print(line0);
    lcd.setCursor(0, 1);
    lcd.print(line1);
  }
}

// ========== NETWORK & WIFI FUNCTIONS ==========
void connectWiFi() {
  Serial.printf("Connecting to WiFi SSID: %s\n", WIFI_SSID);
  WiFi.mode(WIFI_STA);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);

  // Non-blocking connection check (Setup will show progress)
  unsigned long startAttempt = millis();
  while (WiFi.status() != WL_CONNECTED && millis() - startAttempt < 15000) {
    delay(500);
    Serial.print(".");
  }
  Serial.println();

  if (WiFi.status() == WL_CONNECTED) {
    Serial.print("Connected! IP Address: ");
    Serial.println(WiFi.localIP());
    
    // Time sync for SSL validation required by Firebase
    configTime(19800, 0, "pool.ntp.org", "time.nist.gov"); // UTC+5:30
  } else {
    Serial.println("WiFi Connection Timeout. Local safety monitoring running.");
  }
}

void initFirebase() {
  fbConfig.api_key = FIREBASE_API_KEY;
  fbConfig.database_url = FIREBASE_DATABASE_URL;
  fbAuth.user.email = FIREBASE_USER_EMAIL;
  fbAuth.user.password = FIREBASE_USER_PASSWORD;
  
  // Reconnect WiFi automatically if dropped
  Firebase.reconnectWiFi(true);
  
  // Limit buffer size to save heap RAM
  fbData.setBSSLBufferSize(2048, 2048);

  Firebase.begin(&fbConfig, &fbAuth);

  // Authenticate (up to 5 seconds non-blocking check)
  unsigned long startReady = millis();
  while (!Firebase.ready() && millis() - startReady < 5000) {
    delay(200);
    Serial.print(".");
  }
  Serial.println();
  Serial.printf("Firebase initialization %s.\n", Firebase.ready() ? "SUCCESSFUL" : "FAILED (Pending Connection)");
}

// ========== FIREBASE WRITE OPERATIONS ==========
void writeFirebaseStatus() {
  FirebaseJson json;
  json.set("deviceId", FIREBASE_DEVICE_ID);
  json.set("gasLevel", gasLevel);
  json.set("gasThreshold", GAS_THRESHOLD);
  json.set("gasWeightKg", gasWeight);
  json.set("cylinderWeightKg", cylinderWeight);
  json.set("gasPercentage", gasPercentage);
  json.set("temperatureC", temperature);
  json.set("humidityPercent", humidity);
  json.set("gasDetected", (gasLevel > GAS_THRESHOLD));
  json.set("alarmActive", alarmActive);
  json.set("valveState", valveOpen ? "OPEN" : "CLOSED");
  json.set("weightState", alarmActive ? "ALARM" : "MONITORING");
  json.set("uptimeSeconds", millis() / 1000);
  json.set("updatedAt/.sv", "timestamp"); // Server-side timestamp

  String path = "/devices/" + String(FIREBASE_DEVICE_ID) + "/latest";
  
  Serial.println("Pushing live status to Firebase Realtime Database...");
  if (Firebase.RTDB.setJSON(&fbData, path.c_str(), &json)) {
    Serial.println("Status pushed successfully.");
  } else {
    Serial.printf("Firebase write failed: %s\n", fbData.errorReason().c_str());
  }
}

void pushFirebaseEvent(const char* eventType, const char* message) {
  if (WiFi.status() != WL_CONNECTED || !Firebase.ready()) return;

  FirebaseJson ev;
  ev.set("type", eventType);
  ev.set("message", message);
  ev.set("gasLevel", gasLevel);
  ev.set("gasThreshold", GAS_THRESHOLD);
  ev.set("temperatureC", temperature);
  ev.set("humidityPercent", humidity);
  ev.set("gasWeightKg", gasWeight);
  ev.set("cylinderWeightKg", cylinderWeight);
  ev.set("gasPercentage", gasPercentage);
  ev.set("valveState", valveOpen ? "OPEN" : "CLOSED");
  ev.set("createdAt/.sv", "timestamp");

  String path = "/devices/" + String(FIREBASE_DEVICE_ID) + "/events";
  
  Serial.println("Logging event to Firebase...");
  if (!Firebase.RTDB.pushJSON(&fbData, path.c_str(), &ev)) {
    Serial.printf("Firebase event log failed: %s\n", fbData.errorReason().c_str());
  }
}
