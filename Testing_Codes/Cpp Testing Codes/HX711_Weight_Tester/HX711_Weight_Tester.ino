/*
 * HX711 Calibrated Weight Tester
 * 
 * PURPOSE:
 * A test utility to verify the calibration factor and platform weight.
 * It shows the bare raw ADC, the calibrated total weight, and the net weight 
 * (after subtracting the platform weight).
 * 
 * WIRING (ESP32 DevKit V1):
 *   HX711 DT  -> GPIO16
 *   HX711 SCK -> GPIO17
 *   HX711 VCC -> 5V or 3.3V
 *   HX711 GND -> GND
 */

#include <HX711.h>

// PIN DEFINITIONS
#define LOADCELL_DT   25
#define LOADCELL_SCK  26

// ====== CALIBRATION CONFIGURATION ======
// Choose the calibration factor and platform weight based on your raw values.
#define CALIBRATION_FACTOR 99651.00  // Calibrated to fit 1.010kg precisely
#define PLATFORM_WEIGHT_KG 1.394     // Platform weight in kg (from 424566 - 285690 raw counts)

HX711 scale;

void setup() {
  Serial.begin(115200);
  delay(1000);
  
  Serial.println("\n=============================================");
  Serial.println("       HX711 Calibrated Weight Tester");
  Serial.println("=============================================");
  Serial.printf("Config: Factor = %.2f, Platform Weight = %.3f kg\n", CALIBRATION_FACTOR, PLATFORM_WEIGHT_KG);
  Serial.println("=============================================");
  
  scale.begin(LOADCELL_DT, LOADCELL_SCK);
  
  if (scale.wait_ready_timeout(2000)) {
    Serial.println("OK: HX711 detected.");
  } else {
    Serial.println("ERROR: HX711 not found! Check DT=GPIO25, SCK=GPIO26");
    while (1) delay(1000);
  }
  
  // Set the scaling factor
  scale.set_scale(CALIBRATION_FACTOR);
  
  Serial.println("\n=== INSTRUCTIONS ===");
  Serial.println("1. REMOVE the platform and all weights (empty load cell).");
  Serial.println("2. Open Serial Monitor, type any key and press ENTER to TARE.");
  
  // Clear Serial buffer
  while (Serial.available()) Serial.read();
  
  // Wait for user key press to tare
  while (!Serial.available()) {
    delay(100);
  }
  while (Serial.available()) Serial.read(); // consume input
  
  Serial.println("\nTaring... hold steady...");
  scale.tare(30); // Take 30 readings to establish a stable zero offset
  Serial.println("OK: Bare scale tared to 0.000 kg.");
  
  Serial.println("\n3. Place your PLATFORM and WEIGHTS on the scale.");
  Serial.println("------------------------------------------------------------------");
  Serial.println("Raw ADC\t\tTotal Calibrated Weight\tNet Weight (Subtract Platform)");
  Serial.println("------------------------------------------------------------------");
}

void loop() {
  if (scale.is_ready()) {
    long rawVal = scale.read();
    
    // get_units() returns the calibrated weight (including platform if platform is on it)
    float totalWeight = scale.get_units(5); // Average of 5 readings
    
    // Net weight subtracts the platform weight
    float netWeight = totalWeight - PLATFORM_WEIGHT_KG;
    if (netWeight < 0.0) netWeight = 0.0; // Avoid showing tiny negative values from noise
    
    Serial.print(rawVal);
    Serial.print("\t\t");
    Serial.print(totalWeight, 3);
    Serial.print(" kg\t\t\t");
    Serial.print(netWeight, 3);
    Serial.println(" kg");
  } else {
    Serial.println("Error: HX711 not ready.");
  }
  
  delay(500); // Read 2 times per second
}
