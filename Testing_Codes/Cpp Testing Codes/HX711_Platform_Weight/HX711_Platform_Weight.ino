/*
 * HX711 Platform Weight Measurement Tool v2
 * ESP32 DevKit V1
 * 
 * PURPOSE:
 * Standalone tool to find the PLATFORM WEIGHT and correct calibration factor.
 * 
 * PROCEDURE:
 *   1. Remove everything from load cell, press 1 (TARE)
 *   2. Place platform on load cell, WAIT 30 seconds for it to settle
 *   3. Press w, enter the true weight (e.g. 2.010)
 *      - The sketch will wait for readings to stabilize before calculating
 *   4. Press 2 to measure and verify the platform weight
 *   5. Copy the output #define lines into NewFullcode.ino
 * 
 * WIRING:
 *   HX711 DT  -> GPIO16
 *   HX711 SCK -> GPIO17
 *   HX711 VCC -> 5V
 *   HX711 GND -> GND
 */

#include <HX711.h>

// ========== PIN DEFINITIONS ==========
#define LOADCELL_DT   25
#define LOADCELL_SCK  26

// ========== DEFAULT CALIBRATION FACTOR ==========
// If you don't know this yet, leave at 1.0 and use 'w' to find it.
#define DEFAULT_CALIBRATION_FACTOR 99.00

// ========== STABILITY CONFIG ==========
#define SETTLE_SAMPLES     50     // Samples to collect for stability check
#define SETTLE_TOLERANCE   0.5    // Max spread (in % of average) to consider stable
#define SETTLE_DELAY_MS    200    // Delay between settle samples
#define SETTLE_TIMEOUT_SEC 60     // Max seconds to wait for stability

// ========== OBJECTS ==========
HX711 scale;

// ========== STATE VARIABLES ==========
float calibrationFactor = DEFAULT_CALIBRATION_FACTOR;
float platformWeight = 0.0;
bool isTared = false;
bool liveMode = false;

unsigned long lastPrint = 0;
#define PRINT_INTERVAL 500  // ms between live prints

void setup() {
  Serial.begin(115200);
  delay(1000);
  
  Serial.println();
  Serial.println("=============================================");
  Serial.println("  HX711 PLATFORM WEIGHT MEASUREMENT TOOL v2");
  Serial.println("=============================================");
  Serial.printf("  Default Calibration Factor: %.2f\n", calibrationFactor);
  Serial.println("=============================================");
  
  scale.begin(LOADCELL_DT, LOADCELL_SCK);
  
  if (scale.wait_ready_timeout(2000)) {
    Serial.println("OK: HX711 detected.");
  } else {
    Serial.println("ERROR: HX711 not found! Check DT=GPIO16, SCK=GPIO17");
    while (1) delay(1000);
  }
  
  scale.set_scale(calibrationFactor);
  printMenu();
}

void printMenu() {
  Serial.println();
  Serial.println("-------------------------------------------");
  Serial.println("COMMANDS:");
  Serial.println("  1 = TARE (load cell must be empty)");
  Serial.println("  2 = MEASURE PLATFORM WEIGHT");
  Serial.println("  3 = TOGGLE LIVE READINGS (with raw ADC)");
  Serial.println("  w = CALIBRATE using known weight on scale");
  Serial.println("  c = Change calibration factor manually");
  Serial.println("  d = Show raw ADC drift test (30 seconds)");
  Serial.println("  h = Print this menu");
  Serial.println("-------------------------------------------");
  Serial.printf("  Current Factor: %.2f\n", calibrationFactor);
  Serial.println("-------------------------------------------");
  Serial.print("Enter command: ");
}

// ========== HELPER: Get stable raw ADC reading ==========
// Takes many readings, checks spread, returns stable average
// Returns true if stable, false if timed out
bool getStableRawReading(float &stableValue, int numSamples, float tolerancePct, int timeoutSec) {
  Serial.println("Waiting for readings to stabilize...");
  Serial.println("(Keep the weight steady and don't touch the scale)\n");
  
  float* readings = new float[numSamples];
  unsigned long startTime = millis();
  unsigned long deadline = startTime + (unsigned long)timeoutSec * 1000;
  int attempt = 0;
  
  while (millis() < deadline) {
    attempt++;
    
    // Fill buffer with readings
    float sum = 0;
    float minVal = 1e15;
    float maxVal = -1e15;
    
    for (int i = 0; i < numSamples; i++) {
      while (!scale.is_ready()) delay(10);
      readings[i] = scale.get_value(1);  // Single raw ADC reading
      sum += readings[i];
      if (readings[i] < minVal) minVal = readings[i];
      if (readings[i] > maxVal) maxVal = readings[i];
      delay(SETTLE_DELAY_MS);
    }
    
    float avg = sum / numSamples;
    float spread = maxVal - minVal;
    float spreadPct = (avg != 0) ? (spread / abs(avg)) * 100.0 : 100.0;
    
    Serial.printf("  Attempt %d: avg=%.0f, spread=%.0f (%.1f%%), need <%.1f%%\n", 
                   attempt, avg, spread, spreadPct, tolerancePct);
    
    if (spreadPct <= tolerancePct && abs(avg) > 10) {
      // Stable! Take one more averaged reading to be sure
      stableValue = scale.get_value(20);
      Serial.printf("  >> STABLE! Final raw ADC: %.2f\n", stableValue);
      delete[] readings;
      return true;
    }
    
    if (abs(avg) < 10) {
      Serial.println("  >> WARNING: Raw ADC near zero. Is the weight actually on the scale?");
    }
  }
  
  // Timed out - use best effort average
  Serial.println("\n  >> TIMEOUT: Readings did not fully stabilize.");
  Serial.println("  >> Using best-effort average (may be less accurate).");
  stableValue = scale.get_value(30);
  Serial.printf("  >> Best-effort raw ADC: %.2f\n", stableValue);
  delete[] readings;
  return false;
}

// ========== STEP 1: TARE ==========
void doTare() {
  liveMode = false;
  Serial.println();
  Serial.println("============================================");
  Serial.println("[STEP 1] TARE");
  Serial.println("============================================");
  Serial.println("Ensure load cell is COMPLETELY EMPTY.");
  Serial.println("Nothing on it at all.");
  Serial.println();
  Serial.println("Taring (30 samples)... hold steady...");
  
  scale.set_scale(calibrationFactor);
  scale.tare(30);
  
  isTared = true;
  Serial.println("OK: Tared to zero!");
  Serial.println();
  Serial.println(">> IMPORTANT: Place your platform on the scale now");
  Serial.println(">> and WAIT at least 30 seconds before pressing w");
  Serial.println(">> (the load cell needs time to settle)");
}

// ========== CALIBRATE WITH KNOWN WEIGHT ==========
void doCalibrateFromWeight() {
  if (!isTared) {
    Serial.println("\nERROR: You must Tare first (press 1) with an empty scale!");
    return;
  }
  
  liveMode = false;
  Serial.println();
  Serial.println("============================================");
  Serial.println("[CALIBRATE] Using known weight on scale");
  Serial.println("============================================");
  Serial.println("The weight should already be on the scale and settled.");
  Serial.println();
  Serial.print("Enter the TRUE weight in kg (e.g. 2.010): ");
  
  // Clear any leftover serial data
  while (Serial.available()) Serial.read();
  // Wait for user input
  while (!Serial.available()) delay(50);
  
  float trueWeight = Serial.parseFloat();
  while (Serial.available()) Serial.read();
  
  if (trueWeight <= 0) {
    Serial.println("\nERROR: Weight must be > 0 kg.");
    return;
  }
  
  Serial.println(trueWeight, 3);
  Serial.println();
  
  // Wait for stable reading
  float rawValue = 0;
  bool isStable = getStableRawReading(rawValue, 20, 5.0, SETTLE_TIMEOUT_SEC);
  
  if (abs(rawValue) < 10) {
    Serial.println("\nERROR: Raw ADC is near zero. The weight is not being detected.");
    Serial.println("Make sure the weight is on the scale and wired correctly.");
    return;
  }
  
  // Calculate the correct calibration factor
  float newFactor = rawValue / trueWeight;
  
  Serial.println();
  Serial.println("============================================");
  Serial.printf("  Raw ADC value:        %.2f\n", rawValue);
  Serial.printf("  True weight:          %.3f kg\n", trueWeight);
  Serial.printf("  OLD calibration:      %.2f\n", calibrationFactor);
  Serial.printf("  NEW calibration:      %.2f\n", newFactor);
  
  if (newFactor < 0) {
    Serial.println();
    Serial.println("  NOTE: Factor is negative. This means your load cell");
    Serial.println("  wires (A+ and A-) are swapped. This is OK - the code");
    Serial.println("  handles it. But if you want positive values, swap the");
    Serial.println("  red and white wires on the HX711 A+/A- terminals.");
  }
  
  Serial.println("============================================");
  
  // Apply the new factor
  calibrationFactor = newFactor;
  scale.set_scale(calibrationFactor);
  
  // Verify by reading back
  delay(500);
  float verify = scale.get_units(20);
  Serial.printf("  Verify reading:       %.3f kg (should be ~%.3f)\n", abs(verify), trueWeight);
  Serial.printf("  Error:                %.1f g\n", abs(abs(verify) - trueWeight) * 1000);
  Serial.println("============================================");
  Serial.println();
  Serial.println("OK: Calibration factor updated!");
  Serial.println();
  Serial.println(">> Press 2 to measure the platform weight");
}

// ========== STEP 2: MEASURE PLATFORM ==========
void doMeasurePlatform() {
  if (!isTared) {
    Serial.println("\nWARNING: Scale has not been tared yet.");
    Serial.println("Tare first (press 1) with empty scale for best results.");
  }
  
  liveMode = false;
  Serial.println();
  Serial.println("============================================");
  Serial.println("[MEASURE PLATFORM]");
  Serial.println("============================================");
  Serial.println("Ensure ONLY the platform is on the load cell.");
  Serial.println("Measuring (50 readings, discarding outliers)...");
  Serial.println("Hold steady...");
  
  scale.set_scale(calibrationFactor);
  
  // Take 50 readings and compute trimmed mean (discard top/bottom 10)
  const int totalReadings = 50;
  const int discard = 10;
  float readings[totalReadings];
  
  for (int i = 0; i < totalReadings; i++) {
    while (!scale.is_ready()) delay(10);
    readings[i] = scale.get_units(1);
    delay(100);
  }
  
  // Simple bubble sort for trimmed mean
  for (int i = 0; i < totalReadings - 1; i++) {
    for (int j = 0; j < totalReadings - i - 1; j++) {
      if (readings[j] > readings[j+1]) {
        float temp = readings[j];
        readings[j] = readings[j+1];
        readings[j+1] = temp;
      }
    }
  }
  
  // Average middle readings (discard outliers)
  float sum = 0;
  int count = 0;
  for (int i = discard; i < totalReadings - discard; i++) {
    sum += readings[i];
    count++;
  }
  
  float measured = sum / count;
  
  // Show range info
  Serial.printf("  Min reading:  %.3f kg\n", readings[0]);
  Serial.printf("  Max reading:  %.3f kg\n", readings[totalReadings-1]);
  Serial.printf("  Trimmed avg:  %.3f kg (middle %d of %d readings)\n", 
                 measured, count, totalReadings);
  
  platformWeight = abs(measured);
  
  Serial.println();
  Serial.println("============================================");
  Serial.printf("  PLATFORM WEIGHT = %.3f kg (%.0f g)\n", platformWeight, platformWeight * 1000);
  Serial.println("============================================");
  Serial.println();
  Serial.println("Copy these into your NewFullcode.ino:");
  Serial.println();
  Serial.println("-------- COPY START --------");
  Serial.printf("#define CALIBRATION_FACTOR %.2f\n", calibrationFactor);
  Serial.printf("#define PLATFORM_WEIGHT_KG %.3f    // Measured platform weight\n", platformWeight);
  Serial.printf("#define EMPTY_CYLINDER_KG  %.1f    // Cylinder tare + platform\n", 8.6 + platformWeight);
  Serial.println("-------- COPY END ----------");
  Serial.println();
  Serial.println("Note: EMPTY_CYLINDER_KG includes a standard 8.6kg cylinder tare.");
  Serial.println("Adjust if your cylinder empty weight is different.");
  Serial.println();
}

// ========== TOGGLE LIVE READINGS ==========
void toggleLiveReadings() {
  liveMode = !liveMode;
  if (liveMode) {
    Serial.println("\nLive readings ON (raw ADC + calibrated weight).");
    Serial.println("Press any key to stop.\n");
    scale.set_scale(calibrationFactor);
  } else {
    Serial.println("\nLive readings stopped.");
  }
}

// ========== DRIFT TEST ==========
void doDriftTest() {
  liveMode = false;
  Serial.println();
  Serial.println("============================================");
  Serial.println("[DRIFT TEST] 30-second raw ADC monitor");
  Serial.println("============================================");
  Serial.println("This shows raw ADC values once per second.");
  Serial.println("Watch if the values are stable or drifting.\n");
  
  for (int i = 0; i < 30; i++) {
    float raw = scale.get_value(5);
    float weight = raw / calibrationFactor;
    Serial.printf("  t=%2ds: Raw ADC = %8.0f  |  Weight = %.3f kg\n", i+1, raw, abs(weight));
    delay(1000);
    
    // Allow user to cancel
    if (Serial.available()) {
      while (Serial.available()) Serial.read();
      Serial.println("\n  Cancelled by user.");
      break;
    }
  }
  Serial.println("\nDrift test complete.");
}

// ========== CHANGE CALIBRATION FACTOR MANUALLY ==========
void handleSetCalibrationFactor(String input) {
  input.remove(0, 1);
  input.trim();
  float newFactor = input.toFloat();
  if (newFactor != 0.0) {
    calibrationFactor = newFactor;
    scale.set_scale(calibrationFactor);
    Serial.printf("\nCalibration factor updated to: %.2f\n", calibrationFactor);
  } else {
    Serial.println("\nInvalid! Format: c3200.5");
  }
}

// ========== MAIN LOOP ==========
void loop() {
  if (liveMode) {
    unsigned long now = millis();
    if (now - lastPrint >= PRINT_INTERVAL) {
      lastPrint = now;
      if (scale.is_ready()) {
        float rawADC = scale.get_value(3);  // 3 samples for smoother reading
        float weight = rawADC / calibrationFactor;
        Serial.printf("  Weight: %.3f kg  |  Raw ADC: %.0f  |  Factor: %.2f\n", 
                       abs(weight), rawADC, calibrationFactor);
      }
    }
  }
  
  if (Serial.available() > 0) {
    if (liveMode) {
      while (Serial.available()) Serial.read();
      liveMode = false;
      Serial.println("\nLive readings stopped.");
      printMenu();
      return;
    }
    
    String command = Serial.readStringUntil('\n');
    command.trim();
    if (command.length() == 0) return;
    
    char firstChar = command.charAt(0);
    
    if (firstChar == '1') {
      doTare();
      printMenu();
    } else if (firstChar == '2') {
      doMeasurePlatform();
      printMenu();
    } else if (firstChar == '3' || firstChar == 'l' || firstChar == 'L') {
      toggleLiveReadings();
    } else if (firstChar == 'w' || firstChar == 'W') {
      doCalibrateFromWeight();
      printMenu();
    } else if (firstChar == 'c' || firstChar == 'C') {
      handleSetCalibrationFactor(command);
      printMenu();
    } else if (firstChar == 'd' || firstChar == 'D') {
      doDriftTest();
      printMenu();
    } else if (firstChar == 'h' || firstChar == 'H') {
      printMenu();
    } else {
      Serial.println("\nUnknown command!");
      printMenu();
    }
  }
}
