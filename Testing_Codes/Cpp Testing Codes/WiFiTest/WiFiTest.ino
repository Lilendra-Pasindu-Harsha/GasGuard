#include <WiFi.h>

// ========== WIFI CREDENTIALS ==========
#define WIFI_SSID     "test"
#define WIFI_PASSWORD "12345678"

void setup() {
  Serial.begin(115200);
  delay(1500);
  Serial.println("\n====================================");
  Serial.println("ESP32 WIFI DIAGNOSTICS TEST STARTING");
  Serial.println("====================================");

  // Set WiFi mode to Station
  WiFi.mode(WIFI_STA);
  
  // Disconnect from any previous AP just in case
  WiFi.disconnect();
  delay(500);

  Serial.printf("Connecting to SSID: %s\n", WIFI_SSID);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);

  // Try connecting for up to 30 seconds
  unsigned long startAttempt = millis();
  int dotCount = 0;
  
  while (WiFi.status() != WL_CONNECTED && millis() - startAttempt < 30000) {
    delay(500);
    Serial.print(".");
    dotCount++;
    if (dotCount % 20 == 0) {
      Serial.println(); // Wrap lines for readability
    }
  }
  Serial.println();

  if (WiFi.status() == WL_CONNECTED) {
    Serial.println("\n====================================");
    Serial.println("[SUCCESS] ESP32 Connected to WiFi!");
    Serial.print("IP Address:   ");
    Serial.println(WiFi.localIP());
    Serial.print("Signal (RSSI): ");
    Serial.print(WiFi.RSSI());
    Serial.println(" dBm");
    Serial.println("====================================");
  } else {
    Serial.println("\n====================================");
    Serial.println("[FAILED] WiFi Connection Timeout!");
    Serial.print("Reason Code: ");
    Serial.println(WiFi.status());
    Serial.println("Please check: ");
    Serial.println("1. Is your hotspot/router active?");
    Serial.println("2. Are the SSID and Password correct?");
    Serial.println("====================================");
  }
}

void loop() {
  // Blink the built-in LED (usually GPIO 2) if connected
  if (WiFi.status() == WL_CONNECTED) {
    pinMode(2, OUTPUT);
    digitalWrite(2, HIGH);
    delay(500);
    digitalWrite(2, LOW);
    delay(500);
  } else {
    // Fast flash if disconnected
    pinMode(2, OUTPUT);
    digitalWrite(2, HIGH);
    delay(100);
    digitalWrite(2, LOW);
    delay(100);
  }
}
