/*
 * Sensor Test Sketch (with WiFi and Firebase Integration)
 * 
 * Pins:
 * - MQ-5 Gas Sensor: GPIO 34 (Analog Input)
 * - DHT22 Sensor:    GPIO 27 (Data)
 * - LCD Display (I2C): GPIO 21 (SDA), GPIO 22 (SCL)
 * - Buzzer:          GPIO 19 (Alert Output)
 * - HX711 Load Cell: GPIO 25 (DT), GPIO 26 (SCK)
 * - Green LED:       GPIO 23 (Status OK)
 * - Red LED:         GPIO 32 (Status Alert)
 * - Servo Motor:     GPIO 13 (Valve Controller)
 */

#include <Wire.h>
#include <LiquidCrystal_I2C.h>
#include <DHT.h>
#include <HX711.h>
#include <ESP32Servo.h>
#include <WiFi.h>
#include <Firebase_ESP_Client.h>
#include <Preferences.h>

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
#define CALIBRATION_FACTOR 92946.11
#define DEFAULT_TARE_OFFSET 397352.00
#define PLATFORM_WEIGHT_KG 1.394
#define EMPTY_CYLINDER_KG  8.6
#define GAS_CAPACITY_KG    5.0

// ========== NETWORK CREDENTIALS ==========
#define WIFI_SSID              "Demi"
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
long rawADC = 0;
bool loadCellTareSaved = false;
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

void setup() {
  Serial.begin(115200);
  delay(1500);
  Serial.println("\n--- ESP32 Sensor Test Sketch (Firebase Mode) ---");

  Serial.println("[DEBUG] 1. Initializing I2C Wire bus...");
  Wire.begin(I2C_SDA, I2C_SCL);
  
  Serial.println("[DEBUG] 2. Initializing LCD Display...");
  lcd.init();
  lcd.backlight();
  lcd.clear();
  lcd.setCursor(0, 0);
  lcd.print("Booting Test...");

  Serial.println("[DEBUG] 3. Initializing DHT22 Sensor...");
  dht.begin();

  // Initialize scale with default calibration directly
  scale.begin(LOADCELL_DT, LOADCELL_SCK);
  scale.set_scale(CALIBRATION_FACTOR);
  
  // Auto-zero on boot (measures empty platform raw ADC as 0.00kg baseline)
  Serial.println("[DEBUG] Auto-zeroing scale... Please make sure scale is empty.");
  if (scale.wait_ready_timeout(2000)) {
    scale.tare(10); // Take 10 readings to establish stable zero-point
    Serial.printf("Scale auto-zeroed! Current Offset = %ld, Factor = %.2f\n", scale.get_offset(), (float)CALIBRATION_FACTOR);
  } else {
    Serial.printf("Warning: HX711 offline on boot. Using fallback offset: %ld\n", (long)DEFAULT_TARE_OFFSET);
    scale.set_offset(DEFAULT_TARE_OFFSET);
  }

  Serial.println("[DEBUG] 5. Initializing Actuators (LEDs & Buzzer)...");
  pinMode(BUZZER_PIN, OUTPUT);
  digitalWrite(BUZZER_PIN, LOW);

  pinMode(GREEN_LED_PIN, OUTPUT);
  pinMode(RED_LED_PIN, OUTPUT);
  digitalWrite(GREEN_LED_PIN, HIGH); // Green ON (Safe default)
  digitalWrite(RED_LED_PIN, LOW);   // Red OFF

  Serial.println("[DEBUG] 6. Initializing Servo Valve...");
  gasValve.attach(SERVO_PIN, 500, 2400);
  gasValve.write(VALVE_OPEN);
  valveOpen = true;

  Serial.println("[DEBUG] 7. Connecting to WiFi...");
  connectWiFi();

  Serial.println("[DEBUG] 8. Initializing Firebase...");
  initFirebase();

  Serial.println("[SUCCESS] System Ready! Local safety monitoring active.");
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
    printSensorDataToSerial();
    updateLCD();
  }

  // 3. PERIODIC NETWORK OPERATIONS (Non-blocking, failsafe)
  if (WiFi.status() == WL_CONNECTED && Firebase.ready()) {
    if (currentMillis - lastFirebaseUpdate >= FIREBASE_INTERVAL_MS) {
      lastFirebaseUpdate = currentMillis;
      writeFirebaseStatus();
    }
  }

  // 4. SERIAL COMMANDS FOR CALIBRATION (Send 't' or 'T' to tare scale with platform only)
  if (Serial.available() > 0) {
    char cmd = Serial.read();
    if (cmd == 't' || cmd == 'T') {
      performTare();
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

  // Read Load Cell Raw ADC & Weights
  if (scale.wait_ready_timeout(100)) {
    rawADC = scale.read();
    getWeightData(cylinderWeight, gasWeight, gasPercentage);
  } else {
    Serial.println("HX711 not found!");
  }
}

void printSensorDataToSerial() {
  Serial.println("\n=== LIVE SENSOR DATA ===");
  Serial.printf("Gas Level:       %d MQ-5\n", gasLevel);
  Serial.printf("Temperature:     %.1f C\n", temperature);
  Serial.printf("Humidity:        %.1f %%\n", humidity);
  Serial.printf("Raw ADC:         %ld\n", rawADC);
  Serial.printf("Tare Offset:     %ld\n", scale.get_offset());
  Serial.printf("Scale Factor:    %.2f\n", scale.get_scale());
  Serial.printf("Cylinder Weight: %.3f kg\n", cylinderWeight);
  Serial.printf("Gas Weight:      %.3f kg\n", gasWeight);
  Serial.printf("Gas Percentage:  %.1f %%\n", gasPercentage);
  Serial.println("========================");
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
      
      // --- OPTION A: For Active Buzzer (Default) ---
      digitalWrite(BUZZER_PIN, blinkState); 
      
      // --- OPTION B: For Passive Buzzer (Uncomment if using a passive buzzer) ---
      // if(blinkState) tone(BUZZER_PIN, BUZZER_FREQ); else noTone(BUZZER_PIN);
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
      // noTone(BUZZER_PIN); // Uncomment if Option B was used above
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
    // Display alert status
    lcd.setCursor(0, 0);
    lcd.print("LEAK DETECTED!  ");
    char line1[17];
    snprintf(line1, sizeof(line1), "Gas Level: %-5d", gasLevel);
    lcd.setCursor(0, 1);
    lcd.print(line1);
    return;
  }

  // Determine current screen cycle (0, 1, or 2)
  int cycle = (millis() / 3000) % 3;

  char line0[17];
  char line1[17];

  if (cycle == 0) {
    // Screen 1: Gas & Weight
    snprintf(line0, sizeof(line0), "Gas: %-4d MQ-5  ", gasLevel);
    snprintf(line1, sizeof(line1), "Weight: %-5.2fkg", cylinderWeight);
  } else if (cycle == 1) {
    // Screen 2: Temperature & Humidity
    snprintf(line0, sizeof(line0), "Temp: %-5.1f C  ", temperature);
    snprintf(line1, sizeof(line1), "Humid: %-3.0f%%    ", humidity);
  } else {
    // Screen 3: Load Cell Raw ADC & Weight
    snprintf(line0, sizeof(line0), "Raw: %-11ld", rawADC);
    snprintf(line1, sizeof(line1), "Weight: %-5.2fkg", cylinderWeight);
  }

  // Ensure line length is exactly 16 characters
  int len0 = strlen(line0);
  while (len0 < 16) line0[len0++] = ' ';
  line0[16] = '\0';

  int len1 = strlen(line1);
  while (len1 < 16) line1[len1++] = ' ';
  line1[16] = '\0';

  lcd.setCursor(0, 0);
  lcd.print(line0);
  lcd.setCursor(0, 1);
  lcd.print(line1);
}

// ========== NETWORK & WIFI FUNCTIONS ==========
void connectWiFi() {
  Serial.printf("Connecting to WiFi SSID: %s\n", WIFI_SSID);
  WiFi.mode(WIFI_STA);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);

  // Non-blocking connection check during setup (timeout after 15s)
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

  // Authenticate (up to 5 seconds check)
  unsigned long startReady = millis();
  while (!Firebase.ready() && millis() - startReady < 5000) {
    delay(200);
    Serial.print(".");
  }
  Serial.println();
  Serial.printf("Firebase initialization %s.\n", Firebase.ready() ? "SUCCESSFUL" : "FAILED (Pending Connection)");
}

// ========== FIREBASE DATA PACKAGING HELPERS ==========
void getWeightData(float &cylinderWeight, float &gasWeight, float &gasPercentage) {
  // Calculate weight directly from global rawADC and active scale offset to bypass conflicts
  float netWeight = (float)(rawADC - scale.get_offset()) / CALIBRATION_FACTOR;
  if (netWeight < 0.0) netWeight = 0.0;
  
  cylinderWeight = netWeight;
  gasWeight = cylinderWeight - EMPTY_CYLINDER_KG;
  if (gasWeight < 0.0) gasWeight = 0.0;
  
  gasPercentage = (gasWeight / GAS_CAPACITY_KG) * 100.0;
  if (gasPercentage > 100.0) gasPercentage = 100.0;
}

// ========== FIREBASE WRITE OPERATIONS ==========
void writeFirebaseStatus() {
  const char* weightStateStr = "IDLE";
  if (cylinderWeight >= 0.2) {
    weightStateStr = "STABLE";
  }

  FirebaseJson json;
  json.set("deviceId", FIREBASE_DEVICE_ID);
  json.set("gasLevel", gasLevel);
  json.set("gasThreshold", GAS_THRESHOLD);
  json.set("rawADC", rawADC);
  json.set("gasWeightKg", gasWeight);
  json.set("cylinderWeightKg", cylinderWeight);
  json.set("gasPercentage", gasPercentage);
  json.set("temperatureC", temperature);
  json.set("humidityPercent", humidity);
  json.set("gasDetected", (gasLevel > GAS_THRESHOLD));
  json.set("alarmActive", alarmActive);
  json.set("valveState", valveOpen ? "OPEN" : "CLOSED");
  json.set("weightState", weightStateStr);
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
  ev.set("rawADC", rawADC);
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

// ========== MANUAL CALIBRATION AND TARE FUNCTIONS ==========
void performTare() {
  Serial.println("\nTaring scale... Please ensure ONLY the platform is on the scale.");
  lcd.clear();
  lcd.setCursor(0, 0);
  lcd.print("Taring scale...");
  lcd.setCursor(0, 1);
  lcd.print("Platform ONLY!");
  delay(1500);
  
  if (scale.wait_ready_timeout(2000)) {
    scale.tare(10); // Take 10 readings for high accuracy
    long newOffset = scale.get_offset();
    
    Preferences preferences;
    preferences.begin("gasguard", false); // Open in read-write mode
    preferences.putInt("hx_offset", newOffset);
    preferences.putFloat("hx_scale", CALIBRATION_FACTOR);
    preferences.putBool("hx_tared", true);
    preferences.end();
    
    loadCellTareSaved = true;
    Serial.printf("Scale tared successfully! New offset: %ld, scale factor: %.2f\n", newOffset, (float)CALIBRATION_FACTOR);
    lcd.clear();
    lcd.setCursor(0, 0);
    lcd.print("Tare Success!");
    lcd.setCursor(0, 1);
    lcd.print("Offset Saved.");
    delay(1500);
  } else {
    Serial.println("Error: HX711 not ready. Tare failed.");
    lcd.clear();
    lcd.setCursor(0, 0);
    lcd.print("Tare Failed!");
    lcd.setCursor(0, 1);
    lcd.print("HX711 Offline");
    delay(1500);
  }
}
