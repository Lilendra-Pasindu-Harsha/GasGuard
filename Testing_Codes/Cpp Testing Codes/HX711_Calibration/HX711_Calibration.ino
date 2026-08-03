/*
 * HX711 Stable Weight Reader
 * ESP32 DevKit V1
 * 
 * PURPOSE:
 * Standalone weight measurement tool with stability detection.
 * Use this to find the PLATFORM WEIGHT, then put that value
 * into Fullcode.ino's EMPTY_CYLINDER_KG or use it as reference.
 * 
 * HOW IT WORKS:
 * - Platform sits on load cell permanently
 * - When you place something on the platform (>200g increase), 
 *   the system detects it and starts collecting readings
 * - Once 10 consecutive readings are within 30g of each other,
 *   the value LOCKS and displays a stable reading
 * - When you remove the weight (<200g), it resets to zero
 * - If weight changes significantly (>50g), it re-settles
 * 
 * SETUP:
 *   [Load Cell] -> [Platform always on top] -> [Place items here]
 * 
 * WIRING:
 *   HX711 DT  -> GPIO16
 *   HX711 SCK -> GPIO17
 *   HX711 VCC -> 5V
 *   HX711 GND -> GND
 * 
 * SERIAL COMMANDS (115200 baud):
 *   1 = Tare (bare load cell, nothing on it)
 *   2 = Calibrate with known weight
 *   3 = Measure platform weight (place only platform, press 3)
 *   4 = Start stable weighing mode (platform weight subtracted)
 *   5 = Show values to copy into Fullcode.ino
 *   r = Toggle raw live reading (no stability, just raw values)
 *   s = Stop weighing mode
 */

#include <HX711.h>

// ========== PIN DEFINITIONS ==========
#define LOADCELL_DT   25
#define LOADCELL_SCK  26

// ========== STABILITY CONFIG ==========
#define WEIGHT_DETECT_THRESHOLD 0.2   // 200g - min weight to detect something placed
#define WEIGHT_STABLE_RANGE     0.03  // 30g - readings within this = stable
#define WEIGHT_CHANGE_THRESHOLD 0.05  // 50g - change from stable = re-settle
#define BUFFER_SIZE             10    // readings needed for stability check
#define READ_INTERVAL           100   // ms between readings
#define PRINT_INTERVAL          500   // ms between serial prints

// ========== OBJECTS ==========
HX711 scale;

// ========== CALIBRATION STATE ==========
float calibrationFactor = 1.0;
float platformWeight = 0.0;
bool isTared = false;
bool isCalibrated = false;
bool platformMeasured = false;

// ========== STABILITY STATE MACHINE ==========
enum WeightState { WS_IDLE, WS_SETTLING, WS_STABLE };
WeightState weightState = WS_IDLE;
float weightBuffer[BUFFER_SIZE];
int bufferIndex = 0;
int bufferCount = 0;
float stableWeight = 0;
float displayWeight = 0;

// ========== MODE ==========
bool weighingMode = false;
bool rawLiveMode = false;

// ========== TIMERS ==========
unsigned long lastRead = 0;
unsigned long lastPrint = 0;

// ========== BUFFER HELPERS ==========
void bufferAdd(float value) {
  weightBuffer[bufferIndex] = value;
  bufferIndex = (bufferIndex + 1) % BUFFER_SIZE;
  if (bufferCount < BUFFER_SIZE) bufferCount++;
}

void bufferReset() {
  bufferIndex = 0;
  bufferCount = 0;
}

bool bufferIsStable(float &avgOut) {
  if (bufferCount < BUFFER_SIZE) return false;
  
  float minVal = weightBuffer[0];
  float maxVal = weightBuffer[0];
  float sum = 0;
  
  for (int i = 0; i < BUFFER_SIZE; i++) {
    if (weightBuffer[i] < minVal) minVal = weightBuffer[i];
    if (weightBuffer[i] > maxVal) maxVal = weightBuffer[i];
    sum += weightBuffer[i];
  }
  
  avgOut = sum / BUFFER_SIZE;
  return (maxVal - minVal) <= WEIGHT_STABLE_RANGE;
}

float bufferAverage() {
  if (bufferCount == 0) return 0;
  float sum = 0;
  for (int i = 0; i < bufferCount; i++) sum += weightBuffer[i];
  return sum / bufferCount;
}

// ========== SETUP ==========
void setup() {
  Serial.begin(115200);
  delay(1000);
  
  Serial.println();
  Serial.println("========================================");
  Serial.println("  HX711 STABLE WEIGHT READER");
  Serial.println("  Platform Weight Finder + Stable Scale");
  Serial.println("========================================");
  Serial.println();
  
  scale.begin(LOADCELL_DT, LOADCELL_SCK);
  
  if (scale.wait_ready_timeout(2000)) {
    Serial.println("OK: HX711 detected.");
  } else {
    Serial.println("ERROR: HX711 not found! Check DT=GPIO16, SCK=GPIO17");
    while (1) delay(1000);
  }
  
  scale.set_scale(1.0);
  printMenu();
}

void printMenu() {
  Serial.println();
  Serial.println("-------------------------------------------");
  Serial.println("COMMANDS:");
  Serial.println();
  Serial.println("  1 = TARE (bare load cell, nothing on it)");
  Serial.println("  2 = CALIBRATE with known weight");
  Serial.println("  3 = MEASURE PLATFORM weight");
  Serial.println("  4 = START stable weighing (auto subtracts platform)");
  Serial.println("  5 = SHOW VALUES for Fullcode.ino");
  Serial.println("  r = Toggle raw live reading");
  Serial.println("  s = Stop weighing/reading mode");
  Serial.println("-------------------------------------------");
  Serial.println();
}

// ========== STEP 1: TARE ==========
void doTare() {
  weighingMode = false;
  rawLiveMode = false;
  
  Serial.println();
  Serial.println("[STEP 1] TARE");
  Serial.println("Load cell must be completely empty. Nothing on it.");
  Serial.println("Taring... hold steady...");
  
  scale.set_scale(1.0);
  scale.tare(20);
  
  isTared = true;
  platformMeasured = false;
  
  Serial.println("OK: Tared to zero!");
  Serial.println();
  Serial.println(">> Next: Place known weight on load cell, press 2");
}

// ========== STEP 2: CALIBRATE ==========
void doCalibrate() {
  if (!isTared) {
    Serial.println("ERROR: Tare first (press 1).");
    return;
  }
  
  weighingMode = false;
  rawLiveMode = false;
  
  Serial.println();
  Serial.println("[STEP 2] CALIBRATE");
  Serial.print("Enter known weight in kg (e.g. 1.0): ");
  
  while (Serial.available()) Serial.read();
  while (!Serial.available()) delay(50);
  
  float knownWeight = Serial.parseFloat();
  while (Serial.available()) Serial.read();
  
  if (knownWeight <= 0) {
    Serial.println("\nERROR: Must be > 0.");
    return;
  }
  
  Serial.println(knownWeight, 3);
  Serial.println("Reading... hold steady...");
  
  float rawValue = scale.get_value(20);
  calibrationFactor = rawValue / knownWeight;
  scale.set_scale(calibrationFactor);
  
  isCalibrated = true;
  
  float verify = scale.get_units(10);
  Serial.printf("  Calibration Factor: %.2f\n", calibrationFactor);
  Serial.printf("  Verify: %.3f kg (expected ~%.3f)\n", verify, knownWeight);
  Serial.println("OK: Calibrated!");
  Serial.println();
  Serial.println(">> Next: Remove weight. Place ONLY platform, press 3");
}

// ========== STEP 3: MEASURE PLATFORM ==========
void doMeasurePlatform() {
  if (!isCalibrated) {
    Serial.println("ERROR: Calibrate first (steps 1+2).");
    return;
  }
  
  weighingMode = false;
  rawLiveMode = false;
  
  Serial.println();
  Serial.println("[STEP 3] MEASURE PLATFORM");
  Serial.println("ONLY the platform should be on the load cell.");
  Serial.println("Measuring... hold steady...");
  
  platformWeight = scale.get_units(20);
  if (platformWeight < 0) platformWeight = 0;
  
  platformMeasured = true;
  
  Serial.println();
  Serial.println("========================================");
  Serial.printf("  PLATFORM WEIGHT = %.3f kg (%.0f g)\n", platformWeight, platformWeight * 1000);
  Serial.println("========================================");
  Serial.println();
  Serial.println(">> Press 4 to start stable weighing mode");
  Serial.println("   (platform weight will be auto-subtracted)");
  Serial.println(">> Press 5 to see values for Fullcode.ino");
}

// ========== STEP 4: START STABLE WEIGHING ==========
void startWeighingMode() {
  if (!platformMeasured) {
    Serial.println("ERROR: Measure platform first (steps 1+2+3).");
    return;
  }
  
  rawLiveMode = false;
  weighingMode = true;
  weightState = WS_IDLE;
  bufferReset();
  stableWeight = 0;
  displayWeight = 0;
  
  Serial.println();
  Serial.println("========================================");
  Serial.println("  STABLE WEIGHING MODE ACTIVE");
  Serial.printf("  Platform (%.3f kg) auto-subtracted\n", platformWeight);
  Serial.println("========================================");
  Serial.println();
  Serial.println("Place items on the platform. Weight will");
  Serial.println("stabilize automatically. Press 's' to stop.");
  Serial.println();
}

// ========== STEP 5: SHOW VALUES ==========
void showValues() {
  Serial.println();
  Serial.println("========================================");
  Serial.println("  VALUES FOR Fullcode.ino");
  Serial.println("========================================");
  Serial.println();
  
  if (!isCalibrated) {
    Serial.println("WARNING: Not calibrated. Run steps 1+2 first.");
  }
  
  Serial.println("Replace these lines in Fullcode.ino:");
  Serial.println();
  Serial.println("-------- COPY START --------");
  Serial.printf("#define CALIBRATION_FACTOR %.2f\n", calibrationFactor);
  
  if (platformMeasured) {
    Serial.printf("#define EMPTY_CYLINDER_KG  %.1f    // This is your platform weight\n", platformWeight);
  } else {
    Serial.println("#define EMPTY_CYLINDER_KG  8.6    // NOT MEASURED - default");
  }
  
  Serial.println("-------- COPY END ----------");
  Serial.println();
  
  if (platformMeasured) {
    Serial.println("HOW IT WORKS IN MAIN CODE:");
    Serial.printf("  Scale reads total weight on platform\n");
    Serial.printf("  Subtract %.1f kg (platform) = weight of item only\n", platformWeight);
    Serial.println();
    Serial.printf("  Example: Platform + 5kg item = %.1f kg total\n", platformWeight + 5.0);
    Serial.printf("  Display shows: %.1f - %.1f = 5.0 kg\n", platformWeight + 5.0, platformWeight);
  }
  
  Serial.println();
}

// ========== WEIGHING STATE MACHINE ==========
void processWeighing() {
  unsigned long now = millis();
  if (now - lastRead < READ_INTERVAL) return;
  lastRead = now;
  
  if (!scale.wait_ready_timeout(20)) return;
  
  // Get weight with platform subtracted
  float raw = scale.get_units(1);
  float netWeight = raw - platformWeight;
  if (netWeight < 0) netWeight = 0;
  
  // State machine
  switch (weightState) {
    case WS_IDLE:
      displayWeight = 0;
      if (netWeight >= WEIGHT_DETECT_THRESHOLD) {
        weightState = WS_SETTLING;
        bufferReset();
        bufferAdd(netWeight);
        Serial.println("\n>> Weight detected, settling...");
      }
      break;
      
    case WS_SETTLING:
      if (netWeight < WEIGHT_DETECT_THRESHOLD) {
        weightState = WS_IDLE;
        displayWeight = 0;
        bufferReset();
        Serial.println(">> Weight removed.");
      } else {
        bufferAdd(netWeight);
        float avg;
        if (bufferIsStable(avg)) {
          stableWeight = avg;
          displayWeight = stableWeight;
          weightState = WS_STABLE;
        } else {
          displayWeight = bufferAverage();
        }
      }
      break;
      
    case WS_STABLE:
      if (netWeight < WEIGHT_DETECT_THRESHOLD) {
        weightState = WS_IDLE;
        displayWeight = 0;
        stableWeight = 0;
        bufferReset();
        Serial.println("\n>> Weight removed, reset.");
      } else if (abs(netWeight - stableWeight) > WEIGHT_CHANGE_THRESHOLD) {
        weightState = WS_SETTLING;
        bufferReset();
        bufferAdd(netWeight);
        Serial.println("\n>> Weight changed, re-settling...");
      }
      displayWeight = stableWeight;
      break;
  }
  
  // Print status
  if (now - lastPrint >= PRINT_INTERVAL) {
    const char* stateStr = (weightState == WS_IDLE) ? "IDLE" : 
                           (weightState == WS_SETTLING) ? "SETTLING..." : 
                           ">> STABLE <<";
    
    Serial.printf("  Raw: %.3f kg | Display: %.3f kg | %s", netWeight, displayWeight, stateStr);
    
    if (weightState == WS_STABLE) {
      Serial.print(" [LOCKED]");
    } else if (weightState == WS_SETTLING) {
      Serial.printf(" [%d/%d readings]", bufferCount, BUFFER_SIZE);
    }
    
    Serial.println();
    lastPrint = now;
  }
}

// ========== RAW LIVE READING ==========
void processRawLive() {
  unsigned long now = millis();
  if (now - lastRead < READ_INTERVAL) return;
  if (now - lastPrint < PRINT_INTERVAL) return;
  lastRead = now;
  lastPrint = now;
  
  if (!scale.wait_ready_timeout(20)) return;
  
  float raw = scale.get_units(3);
  if (raw < 0) raw = 0;
  
  if (platformMeasured) {
    float net = raw - platformWeight;
    if (net < 0) net = 0;
    Serial.printf("  Raw: %.3f kg | Net (platform subtracted): %.3f kg\n", raw, net);
  } else {
    Serial.printf("  Weight: %.3f kg\n", raw);
  }
}

// ========== MAIN LOOP ==========
void loop() {
  // Process active mode
  if (weighingMode) {
    processWeighing();
  } else if (rawLiveMode) {
    processRawLive();
  }
  
  // Handle serial commands
  if (Serial.available()) {
    char cmd = Serial.read();
    if (cmd == '\n' || cmd == '\r') return;
    
    switch (cmd) {
      case '1':
        doTare();
        break;
      case '2':
        doCalibrate();
        break;
      case '3':
        doMeasurePlatform();
        break;
      case '4':
        startWeighingMode();
        break;
      case '5':
        showValues();
        break;
      case 'r': case 'R':
        weighingMode = false;
        rawLiveMode = !rawLiveMode;
        Serial.println(rawLiveMode ? "\nRaw live reading ON (press s to stop)\n" : "\nRaw live reading OFF\n");
        break;
      case 's': case 'S':
        weighingMode = false;
        rawLiveMode = false;
        Serial.println("\nStopped. Press any number for menu.");
        break;
      default:
        weighingMode = false;
        rawLiveMode = false;
        printMenu();
        break;
    }
  }
}
