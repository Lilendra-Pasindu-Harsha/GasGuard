/*
 * SMARTGASML - Gas Monitoring System v3.1 (Optimized)
 * ESP32 DevKit V1
 * 
 * Pins: MQ5=34, GreenLED=23, RedLED=32, Buzzer=19,
 *       Servo=13, HX711(DT=25,SCK=26), DHT22=27, LCD(SDA=21,SCL=22)
 */

#include <Wire.h>
#include <LiquidCrystal_I2C.h>
#include <ESP32Servo.h>
#include <HX711.h>
#include <DHT.h>
#include <WiFi.h>
#include <Firebase_ESP_Client.h>
#include <Preferences.h>

// ========== PIN DEFINITIONS ==========
#define MQ5_PIN         34
#define GREEN_LED_PIN   23
#define RED_LED_PIN     32
#define BUZZER_PIN      19
#define BUZZER_RESOLUTION 8
#define SERVO_PIN       13
#define LOADCELL_DT     25
#define LOADCELL_SCK    26
#define DHTPIN          27
#define DHTTYPE         DHT22
#define I2C_SDA         21
#define I2C_SCL         22
#define LCD_ADDRESS     0x27
#define LCD_COLUMNS     16
#define LCD_ROWS        2

// ========== CONFIGURATION ==========
#define GAS_THRESHOLD   200
#define ALARM_BLINK     200
#define BUZZER_FREQ     2000
#define VALVE_CLOSED    0
#define VALVE_OPEN      90

#define LCD_UPDATE_DELAY    500
#define DHT_READ_DELAY      2000
#define STATUS_PRINT_DELAY  30000
#define LOOP_MIN_INTERVAL   50

// Load cell calibration
#define CALIBRATION_FACTOR  99651.00
#define PLATFORM_WEIGHT_KG  1.394
#define EMPTY_CYLINDER_KG   8.6
#define GAS_CAPACITY_KG     5.0
#define LOAD_CELL_READ_DELAY  100
#define LOAD_CELL_PRINT_DELAY 1000
#define LOAD_CELL_SAMPLES   1

// Weight stability
#define WEIGHT_DETECT_THRESHOLD 0.2
#define WEIGHT_STABLE_RANGE     0.03
#define WEIGHT_CHANGE_THRESHOLD 0.05
#define WEIGHT_BUFFER_SIZE      10

// Network
#define WIFI_SSID       "Demi"
#define WIFI_PASSWORD   "12345678"
#define FIREBASE_API_KEY       "AIzaSyAnUdXb9dJzbqOF90RwgAxMdtHJzfchaSg"
#define FIREBASE_DATABASE_URL  "https://gasguardkdu-default-rtdb.asia-southeast1.firebasedatabase.app"
#define FIREBASE_USER_EMAIL    "gasgurd@gmail.com"
#define FIREBASE_USER_PASSWORD "gasgurd"
#define FIREBASE_DEVICE_ID     "gasguard-esp32-01"
#define FIREBASE_UPDATE_DELAY  1000
#define FIREBASE_COMMANDS_DELAY 3000

// ========== OBJECTS ==========
LiquidCrystal_I2C lcd(LCD_ADDRESS, LCD_COLUMNS, LCD_ROWS);
Servo gasValve;
HX711 scale;
DHT dht(DHTPIN, DHTTYPE);
FirebaseData firebaseData;
FirebaseAuth firebaseAuth;
FirebaseConfig fbConfig;
Preferences prefs;

// ========== GLOBAL VARIABLES ==========
bool alarmActive = false, gasDetected = false, blinkState = false;
bool loadCellTareSaved = false, backlightOn = true;
int gasLevel = 0, gasThreshold = GAS_THRESHOLD;
float cylinderWeight = 0, gasWeight = 0, gasPercentage = 0;
float temperature = 0, humidity = 0;
float loadCellScaleFactor = CALIBRATION_FACTOR;
float stableWeight = 0, lastRawWeight = 0;

enum WeightState { WS_IDLE, WS_SETTLING, WS_STABLE };
WeightState weightState = WS_IDLE;
float weightBuffer[WEIGHT_BUFFER_SIZE];
int weightBufferIndex = 0, weightBufferCount = 0;

unsigned long clearTime = 0, lastBlinkTime = 0;
unsigned long lastLCDUpdate = 0, lastStatusPrint = 0, lastDHTRead = 0;
unsigned long lastFirebaseUpdate = 0, lastLoadCellRead = 0;
unsigned long lastLoadCellPrint = 0, lastLoopTime = 0, lastCommandCheck = 0;

char lcdLine0[17] = "", lcdLine1[17] = "";

// ========== BUZZER (LEDC v3.x) ==========
void buzzerOn(uint32_t freq) { ledcWriteTone(BUZZER_PIN, freq); }
void buzzerOff() { ledcWriteTone(BUZZER_PIN, 0); }

// ========== LCD HELPER ==========
void lcdWriteLine(int row, const char* text) {
  char padded[17];
  int len = strlen(text);
  if (len > 16) len = 16;
  memcpy(padded, text, len);
  for (int i = len; i < 16; i++) padded[i] = ' ';
  padded[16] = '\0';
  char* cur = (row == 0) ? lcdLine0 : lcdLine1;
  if (strcmp(cur, padded) != 0) {
    lcd.setCursor(0, row);
    lcd.print(padded);
    memcpy(cur, padded, 17);
  }
}

// ========== WEIGHT STABILITY ==========
void weightBufferAdd(float v) {
  weightBuffer[weightBufferIndex] = v;
  weightBufferIndex = (weightBufferIndex + 1) % WEIGHT_BUFFER_SIZE;
  if (weightBufferCount < WEIGHT_BUFFER_SIZE) weightBufferCount++;
}

void weightBufferReset() { weightBufferIndex = 0; weightBufferCount = 0; }

bool weightBufferIsStable(float &avgOut) {
  if (weightBufferCount < WEIGHT_BUFFER_SIZE) return false;
  float minV = weightBuffer[0], maxV = weightBuffer[0], sum = 0;
  for (int i = 0; i < WEIGHT_BUFFER_SIZE; i++) {
    if (weightBuffer[i] < minV) minV = weightBuffer[i];
    if (weightBuffer[i] > maxV) maxV = weightBuffer[i];
    sum += weightBuffer[i];
  }
  avgOut = sum / WEIGHT_BUFFER_SIZE;
  return (maxV - minV) <= WEIGHT_STABLE_RANGE;
}

float weightBufferAverage() {
  if (weightBufferCount == 0) return 0;
  float sum = 0;
  for (int i = 0; i < weightBufferCount; i++) sum += weightBuffer[i];
  return sum / weightBufferCount;
}

// ========== LOAD CELL ==========
void initializeLoadCell() {
  prefs.begin("gasguard", true);
  loadCellScaleFactor = prefs.getFloat("hx_scale", CALIBRATION_FACTOR);
  loadCellTareSaved = prefs.getBool("hx_tared", false);
  int32_t savedOffset = prefs.getInt("hx_offset", 0);
  prefs.end();

  scale.begin(LOADCELL_DT, LOADCELL_SCK);
  scale.set_scale(loadCellScaleFactor);
  Serial.printf("HX711 Ready. Factor: %.2f\n", loadCellScaleFactor);

  if (loadCellTareSaved) {
    scale.set_offset(savedOffset);
    Serial.printf("Tare loaded (Offset: %d)\n", savedOffset);
  } else {
    Serial.println("No saved tare. Run 'L' to calibrate.");
  }
}

void calculateGasRemaining() {
  gasWeight = cylinderWeight - EMPTY_CYLINDER_KG;
  if (gasWeight < 0) gasWeight = 0;
  gasPercentage = (gasWeight / GAS_CAPACITY_KG) * 100.0;
}

// ========== WIFI & FIREBASE ==========
void connectWiFi() {
  Serial.printf("WiFi: %s ", WIFI_SSID);
  WiFi.mode(WIFI_STA);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  lcdWriteLine(0, "WiFi Connecting");

  unsigned long start = millis();
  while (WiFi.status() != WL_CONNECTED && millis() - start < 20000) {
    Serial.print(".");
    delay(500);
  }
  Serial.println();

  if (WiFi.status() == WL_CONNECTED) {
    Serial.print("Connected: ");
    Serial.println(WiFi.localIP());
    lcdWriteLine(0, "WiFi Connected");

    // ESP32 Core 3.x REQUIRES time sync for SSL certificate validation
    Serial.print("NTP time sync...");
    configTime(19800, 0, "pool.ntp.org", "time.nist.gov"); // 19800 = UTC+5:30 (IST)
    struct tm timeinfo;
    int retry = 0;
    while (!getLocalTime(&timeinfo) && retry < 10) {
      Serial.print(".");
      delay(500);
      retry++;
    }
    if (retry < 10) {
      Serial.println(" OK");
    } else {
      Serial.println(" TIMEOUT (SSL may fail)");
    }
  } else {
    Serial.println("WiFi failed");
    lcdWriteLine(0, "WiFi Failed");
  }
  delay(500);
}

void initFirebase() {
  Serial.printf("Free heap: %d bytes\n", ESP.getFreeHeap());

  fbConfig.api_key = FIREBASE_API_KEY;
  fbConfig.database_url = FIREBASE_DATABASE_URL;
  firebaseAuth.user.email = FIREBASE_USER_EMAIL;
  firebaseAuth.user.password = FIREBASE_USER_PASSWORD;
  Firebase.reconnectWiFi(true);

  // Reduce SSL buffer to save heap (default is 16384/16384)
  firebaseData.setBSSLBufferSize(2048, 2048);

  Firebase.begin(&fbConfig, &firebaseAuth);

  // Wait for token to be ready (up to 15 seconds)
  unsigned long start = millis();
  while (!Firebase.ready() && millis() - start < 15000) {
    Serial.print(".");
    delay(200);
  }
  Serial.println();
  Serial.printf("Firebase %s (heap: %d)\n", Firebase.ready() ? "READY" : "FAILED", ESP.getFreeHeap());
}

const char* getValveStr() {
  return (alarmActive || gasValve.read() == VALVE_CLOSED) ? "CLOSED" : "OPEN";
}

const char* getWeightStateStr() {
  return weightState == WS_IDLE ? "IDLE" : (weightState == WS_SETTLING ? "SETTLING" : "STABLE");
}

void writeFirebaseStatus(bool force) {
  if (WiFi.status() != WL_CONNECTED || !Firebase.ready()) return;
  unsigned long now = millis();
  if (!force && now - lastFirebaseUpdate < FIREBASE_UPDATE_DELAY) return;

  FirebaseJson json;
  json.set("deviceId", FIREBASE_DEVICE_ID);
  json.set("gasLevel", gasLevel);
  json.set("gasThreshold", gasThreshold);
  json.set("gasWeightKg", gasWeight);
  json.set("cylinderWeightKg", cylinderWeight);
  json.set("gasPercentage", gasPercentage);
  json.set("temperatureC", temperature);
  json.set("humidityPercent", humidity);
  json.set("gasDetected", gasDetected);
  json.set("alarmActive", alarmActive);
  json.set("valveState", getValveStr());
  json.set("weightState", getWeightStateStr());
  json.set("uptimeSeconds", millis() / 1000);
  json.set("updatedAt/.sv", "timestamp");

  String path = "/devices/" + String(FIREBASE_DEVICE_ID) + "/latest";
  if (Firebase.RTDB.setJSON(&firebaseData, path.c_str(), &json))
    lastFirebaseUpdate = now;
}

void pushFirebaseEvent(const char *type, const char *msg) {
  if (WiFi.status() != WL_CONNECTED || !Firebase.ready()) return;
  FirebaseJson ev;
  ev.set("type", type);
  ev.set("message", msg);
  ev.set("gasLevel", gasLevel);
  ev.set("gasThreshold", gasThreshold);
  ev.set("temperatureC", temperature);
  ev.set("humidityPercent", humidity);
  ev.set("gasWeightKg", gasWeight);
  ev.set("cylinderWeightKg", cylinderWeight);
  ev.set("gasPercentage", gasPercentage);
  ev.set("valveState", getValveStr());
  ev.set("createdAt/.sv", "timestamp");
  String path = "/devices/" + String(FIREBASE_DEVICE_ID) + "/events";
  Firebase.RTDB.pushJSON(&firebaseData, path.c_str(), &ev);
}

void readFirebaseCommands() {
  if (WiFi.status() != WL_CONNECTED || !Firebase.ready()) return;
  unsigned long now = millis();
  if (now - lastCommandCheck < FIREBASE_COMMANDS_DELAY) return;
  lastCommandCheck = now;

  String path = "/devices/" + String(FIREBASE_DEVICE_ID) + "/commands";
  if (Firebase.RTDB.getJSON(&firebaseData, path.c_str()) && firebaseData.dataType() == "json") {
    FirebaseJson &json = firebaseData.jsonObject();

    FirebaseJsonData valveData;
    json.get(valveData, "valveState/valveState");
    if (valveData.success && valveData.type == "string" && !alarmActive) {
      if (valveData.stringValue == "CLOSED") gasValve.write(VALVE_CLOSED);
      else if (valveData.stringValue == "OPEN") gasValve.write(VALVE_OPEN);
    }

    FirebaseJsonData threshData;
    json.get(threshData, "gasThreshold/gasThreshold");
    if (threshData.success && (threshData.type == "int" || threshData.type == "double")) {
      int t = threshData.intValue;
      if (t >= 200 && t <= 3500 && t != gasThreshold) {
        gasThreshold = t;
        Serial.printf("Remote threshold: %d\n", gasThreshold);
      }
    }
  }
}

// ========== MQ-5 WARM-UP ==========
void warmUpMQ5() {
  Serial.println("MQ-5 warm-up (30s)...");
  lcdWriteLine(0, "Warming MQ-5");
  unsigned long start = millis();
  int baselineSum = 0, baselineCount = 0;

  while (millis() - start < 30000) {
    baselineSum += analogRead(MQ5_PIN);
    baselineCount++;
    char buf[17];
    snprintf(buf, sizeof(buf), "Progress: %d%%", (int)((millis() - start) / 300));
    lcdWriteLine(1, buf);
    digitalWrite(GREEN_LED_PIN, (((millis() - start) / 300) % 20 < 10) ? HIGH : LOW);
    delay(100);
  }
  Serial.printf("Baseline: %d\n", baselineSum / baselineCount);
  digitalWrite(GREEN_LED_PIN, HIGH);
}

// ========== SENSOR READINGS ==========
void readAllSensors() {
  gasLevel = analogRead(MQ5_PIN);

  unsigned long now = millis();
  if (now - lastLoadCellRead >= LOAD_CELL_READ_DELAY && scale.wait_ready_timeout(20)) {
    float raw = scale.get_units(LOAD_CELL_SAMPLES) - PLATFORM_WEIGHT_KG;
    if (raw < 0) raw = 0;
    lastRawWeight = raw;

    switch (weightState) {
      case WS_IDLE:
        cylinderWeight = 0;
        if (lastRawWeight >= WEIGHT_DETECT_THRESHOLD) {
          weightState = WS_SETTLING;
          weightBufferReset();
          weightBufferAdd(lastRawWeight);
        }
        break;
      case WS_SETTLING:
        if (lastRawWeight < WEIGHT_DETECT_THRESHOLD) {
          weightState = WS_IDLE;
          cylinderWeight = 0;
          weightBufferReset();
        } else {
          weightBufferAdd(lastRawWeight);
          float avg;
          if (weightBufferIsStable(avg)) {
            stableWeight = avg;
            cylinderWeight = stableWeight;
            weightState = WS_STABLE;
            Serial.printf("Weight STABLE: %.3f kg\n", stableWeight);
          } else {
            cylinderWeight = weightBufferAverage();
          }
        }
        break;
      case WS_STABLE:
        if (lastRawWeight < WEIGHT_DETECT_THRESHOLD) {
          weightState = WS_IDLE;
          cylinderWeight = 0;
          stableWeight = 0;
          weightBufferReset();
        } else if (abs(lastRawWeight - stableWeight) > WEIGHT_CHANGE_THRESHOLD) {
          weightState = WS_SETTLING;
          weightBufferReset();
          weightBufferAdd(lastRawWeight);
        }
        cylinderWeight = stableWeight;
        break;
    }
    calculateGasRemaining();
    lastLoadCellRead = now;

    if (now - lastLoadCellPrint >= LOAD_CELL_PRINT_DELAY) {
      Serial.printf("Raw:%.3f Disp:%.3f %s\n", lastRawWeight, cylinderWeight, getWeightStateStr());
      lastLoadCellPrint = now;
    }
  }

  if (lastDHTRead == 0 || now - lastDHTRead >= DHT_READ_DELAY) {
    float h = dht.readHumidity(), t = dht.readTemperature();
    if (!isnan(h) && !isnan(t)) { humidity = h; temperature = t; }
    lastDHTRead = now;
  }
}

// ========== ALARM ==========
void activateAlarm() {
  if (alarmActive) return;
  alarmActive = true;
  digitalWrite(GREEN_LED_PIN, LOW);
  gasValve.write(VALVE_CLOSED);
  Serial.printf("\nALERT: GAS LEAK! Level:%d Thresh:%d\n", gasLevel, gasThreshold);
  lcdWriteLine(0, "LEAK DETECTED!");
  lcdWriteLine(1, "Valve: CLOSED");
  writeFirebaseStatus(true);
  pushFirebaseEvent("gas_leak_detected", "Gas leak. Valve closed.");
}

void deactivateAlarm() {
  if (!alarmActive) return;
  alarmActive = false;
  digitalWrite(GREEN_LED_PIN, HIGH);
  digitalWrite(RED_LED_PIN, LOW);
  buzzerOff();
  gasValve.write(VALVE_OPEN);
  Serial.println("ALARM CLEARED");
  lcdWriteLine(0, "System Normal");
  lcdWriteLine(1, "Valve: OPEN");
  writeFirebaseStatus(true);
  pushFirebaseEvent("alarm_cleared", "Normal. Valve opened.");
}

void updateAlarmOutputs() {
  if (!alarmActive) return;
  if (millis() - lastBlinkTime > ALARM_BLINK) {
    blinkState = !blinkState;
    digitalWrite(RED_LED_PIN, blinkState);
    blinkState ? buzzerOn(BUZZER_FREQ) : buzzerOff();
    lastBlinkTime = millis();
  }
}

// ========== LCD ==========
void updateLCD() {
  if (millis() - lastLCDUpdate < LCD_UPDATE_DELAY) return;
  lastLCDUpdate = millis();
  if (alarmActive) {
    char buf[17];
    snprintf(buf, sizeof(buf), "Gas: %d", gasLevel);
    lcdWriteLine(0, "LEAK DETECTED!");
    lcdWriteLine(1, buf);
  } else {
    char l0[17], l1[17];
    const char* si = (weightState == WS_SETTLING) ? "~" : (weightState == WS_STABLE) ? "*" : " ";
    snprintf(l0, sizeof(l0), "G:%d W:%.2fkg%s", gasLevel, lastRawWeight, si);
    snprintf(l1, sizeof(l1), "C:%.1f R:%.1fkg", cylinderWeight, gasWeight);
    lcdWriteLine(0, l0);
    lcdWriteLine(1, l1);
  }
}

// ========== STATUS ==========
void printStatus() {
  Serial.printf("\nSTATUS: Gas=%d Cyl=%.3f Gas=%.3f(%.1f%%) T=%.1f H=%.1f %s %s %s\n",
    gasLevel, cylinderWeight, gasWeight, gasPercentage, temperature, humidity,
    getWeightStateStr(), alarmActive ? "ALARM" : "OK", getValveStr());
}

// ========== SERIAL COMMANDS ==========
void testAlarm() {
  bool saved = alarmActive;
  alarmActive = true;
  digitalWrite(GREEN_LED_PIN, LOW);
  lcdWriteLine(0, "TEST MODE");
  lcdWriteLine(1, "Alarm Testing");
  for (int i = 0; i < 10; i++) {
    blinkState = !blinkState;
    digitalWrite(RED_LED_PIN, blinkState);
    blinkState ? buzzerOn(BUZZER_FREQ) : buzzerOff();
    delay(200);
  }
  alarmActive = saved;
  if (!alarmActive) {
    digitalWrite(GREEN_LED_PIN, HIGH);
    digitalWrite(RED_LED_PIN, LOW);
    buzzerOff();
    lcdWriteLine(0, "System Normal");
    lcdWriteLine(1, "Test Complete");
    delay(1500);
  }
  Serial.println("Test done");
}

void resetLoadCellCalibration() {
  prefs.begin("gasguard", false);
  prefs.remove("hx_scale");
  prefs.remove("hx_tared");
  prefs.remove("hx_offset");
  prefs.end();
  loadCellScaleFactor = CALIBRATION_FACTOR;
  loadCellTareSaved = false;
  scale.set_scale(loadCellScaleFactor);
  weightState = WS_IDLE;
  weightBufferReset();
  stableWeight = 0;
  cylinderWeight = 0;
  Serial.printf("Cal reset to %.2f\n", CALIBRATION_FACTOR);
}

void calibrateLoadCell() {
  Serial.println("Place stand only, press 't':");
  while (Serial.available()) Serial.read();
  char c = 0;
  while (c != 't' && c != 'T') {
    while (!Serial.available()) delay(100);
    c = Serial.read();
  }
  scale.tare();
  prefs.begin("gasguard", false);
  prefs.putInt("hx_offset", scale.get_offset());
  prefs.putBool("hx_tared", true);
  prefs.end();
  loadCellTareSaved = true;
  Serial.println("Tare saved. Enter known weight (kg), 0 to skip:");
  while (Serial.available()) Serial.read();
  while (!Serial.available()) delay(100);
  float kw = Serial.parseFloat();
  if (kw > 0) {
    float f = scale.get_value(10) / kw;
    scale.set_scale(f);
    loadCellScaleFactor = f;
    prefs.begin("gasguard", false);
    prefs.putFloat("hx_scale", f);
    prefs.end();
    Serial.printf("Factor: %.2f saved\n", f);
  }
  weightState = WS_IDLE;
  weightBufferReset();
  stableWeight = 0;
  while (Serial.available()) Serial.read();
}

// ========== SETUP ==========
void setup() {
  Serial.begin(115200);
  delay(1000);
  Serial.println("\n=== SMARTGASML v3.1 ===");

  pinMode(GREEN_LED_PIN, OUTPUT);
  pinMode(RED_LED_PIN, OUTPUT);
  digitalWrite(GREEN_LED_PIN, HIGH);
  digitalWrite(RED_LED_PIN, LOW);

  ledcAttach(BUZZER_PIN, BUZZER_FREQ, BUZZER_RESOLUTION);
  buzzerOff();

  Wire.begin(I2C_SDA, I2C_SCL);
  lcd.init();
  lcd.backlight();
  lcdWriteLine(0, "SmartGasML v3.1");
  lcdWriteLine(1, "Initializing...");

  gasValve.attach(SERVO_PIN, 500, 2400);
  gasValve.write(VALVE_OPEN);

  initializeLoadCell();
  dht.begin();
  connectWiFi();
  initFirebase();
  warmUpMQ5();

  gasValve.write(VALVE_OPEN);
  digitalWrite(GREEN_LED_PIN, HIGH);
  lcdWriteLine(0, "System Ready!");
  lcdWriteLine(1, "Monitoring...");
  Serial.println("READY\n");
}

// ========== MAIN LOOP ==========
void loop() {
  unsigned long now = millis();
  if (now - lastLoopTime < LOOP_MIN_INTERVAL) return;
  lastLoopTime = now;

  readAllSensors();

  bool curGas = (gasLevel > gasThreshold);
  if (curGas && !gasDetected) {
    gasDetected = true;
    activateAlarm();
    clearTime = 0;
  }
  if (gasDetected && !curGas) {
    if (clearTime == 0) clearTime = millis();
    if (millis() - clearTime > 5000) {
      gasDetected = false;
      deactivateAlarm();
      clearTime = 0;
    }
  } else if (!gasDetected) {
    clearTime = 0;
  }

  if (alarmActive) updateAlarmOutputs();
  updateLCD();
  writeFirebaseStatus(false);
  readFirebaseCommands();

  if (!alarmActive && millis() - lastStatusPrint > STATUS_PRINT_DELAY) {
    printStatus();
    lastStatusPrint = millis();
  }

  if (Serial.available()) {
    char cmd = Serial.read();
    if (cmd == '\n' || cmd == '\r') return;
    switch(cmd) {
      case 's': case 'S': printStatus(); break;
      case 't': case 'T': testAlarm(); break;
      case '+': gasThreshold = max(200, gasThreshold - 50);
                Serial.printf("Threshold: %d\n", gasThreshold); break;
      case '-': gasThreshold = min(3500, gasThreshold + 50);
                Serial.printf("Threshold: %d\n", gasThreshold); break;
      case 'o': case 'O':
        if (!alarmActive) { gasValve.write(VALVE_OPEN); Serial.println("Valve OPEN"); }
        break;
      case 'c': case 'C':
        gasValve.write(VALVE_CLOSED); Serial.println("Valve CLOSED"); break;
      case 'l': case 'L': calibrateLoadCell(); break;
      case 'r': case 'R': resetLoadCellCalibration(); break;
      case 'b': case 'B':
        backlightOn = !backlightOn;
        backlightOn ? lcd.backlight() : lcd.noBacklight(); break;
      case 'h': case 'H':
        Serial.println("s=status t=test o=open c=close +=sens -=sens l=cal r=reset b=light"); break;
    }
  }
}
