/*
 * HX711 Raw ADC Reader
 * 
 * PURPOSE:
 * A simple, lightweight sketch to read the raw, un-calibrated ADC values 
 * directly from the HX711 load cell. Useful for troubleshooting hardware, 
 * verifying wiring, and checking noise levels.
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

HX711 scale;

void setup() {
  Serial.begin(115200);
  delay(1000);
  
  Serial.println("\n=============================================");
  Serial.println("         HX711 Raw ADC Reader");
  Serial.println("=============================================");
  Serial.printf("Connecting HX711 on DT (GPIO %d) and SCK (GPIO %d)...\n", LOADCELL_DT, LOADCELL_SCK);
  
  scale.begin(LOADCELL_DT, LOADCELL_SCK);
  
  // Wait until scale is ready
  unsigned long startTime = millis();
  while (!scale.is_ready()) {
    if (millis() - startTime > 5000) {
      Serial.println("WARNING: HX711 not ready. Verify wiring (DT/SCK, VCC, GND).");
      startTime = millis(); // Reset warning timer
    }
    delay(200);
  }
  
  Serial.println("HX711 is ready. Printing raw ADC values...");
  Serial.println("---------------------------------------------");
}

void loop() {
  if (scale.is_ready()) {
    // scale.read() gets the direct raw value from the HX711 (without tare offset or scaling factor)
    long rawValue = scale.read();
    
    Serial.print("Raw ADC Value: ");
    Serial.println(rawValue);
  } else {
    Serial.println("Error: HX711 not ready.");
  }
  
  delay(250); // Sample 4 times per second
}
