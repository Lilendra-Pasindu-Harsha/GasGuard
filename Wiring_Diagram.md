# SmartGasML Wiring & Connection Diagram

This document contains the complete hardware connection list, operating voltages, logic levels, and wiring recommendations for the **SmartGasML Gas Monitoring & Alarm System** using the **ESP32 DevKit V1**.

---

## 1. Pin Mapping & Connection Table

All connections listed below correspond to the updated pins in your code.

| Component | Component Pin | ESP32 Pin | Voltage | Power Source | Logic Level | GND Connection | Notes |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **ESP32 DevKit V1** | VIN / GND | — | 5V | USB / Ext. 5V | 3.3V Internal | **Common GND** | Power supply input to the system. |
| **MQ-5 Gas Sensor** | VCC <br> GND <br> AO (Analog Out) | — <br> — <br> **GPIO 34** | 5V | **VIN** (5V) | 0–5V *(Requires Divider)* | **Common GND** | The MQ-5 internal heater **requires 5V**. Use a voltage divider (1kΩ and 2kΩ) on AO to step it down to 3.3V before GPIO 34. |
| **HX711 Load Cell** | VCC <br> GND <br> DT (Data) <br> SCK (Clock) | — <br> — <br> **GPIO 25** <br> **GPIO 26** | 3.3V | **3V3** | 3.3V *(Direct)* | **Common GND** | Running the HX711 on 3.3V guarantees safe logic signals for the ESP32. |
| **DHT22 Sensor** | VCC <br> GND <br> DAT (Data) | — <br> — <br> **GPIO 27** | 3.3V | **3V3** | 3.3V *(Direct)* | **Common GND** | Connect a 4.7kΩ–10kΩ pull-up resistor between DAT and 3V3 (often built-in on modules). |
| **LCD 1602 (I2C)** | VCC <br> GND <br> SDA <br> SCL | — <br> — <br> **GPIO 21** <br> **GPIO 22** | 5V | **VIN** (5V) | 5V *(Requires Shifter)* | **Common GND** | Liquid crystals require 5V to display contrast. Use an I2C level shifter on SDA/SCL lines to protect ESP32 I2C pins. |
| **Servo Motor** | VCC (Red) <br> GND (Brown) <br> PWM (Orange) | — <br> — <br> **GPIO 13** | 5V | **VIN** (5V) | 3.3V *(Direct)* | **Common GND** | Servos run on 5V, but can accept 3.3V PWM control signals directly. |
| **Active Buzzer** | Positive (+) <br> Negative (-) | **GPIO 19** <br> — | 3.3V | GPIO (Direct) | 3.3V *(Direct)* | **Common GND** | Connect directly to GPIO 19. If it draws > 20mA, run it through a 2N2222 transistor buffer. |
| **Green LED** | Anode (+) <br> Cathode (-) | **GPIO 23** <br> — | 3.3V | GPIO (Direct) | 3.3V *(Direct)* | **Common GND** | Connect through a 220Ω–330Ω current-limiting resistor in series. |
| **Red LED** | Anode (+) <br> Cathode (-) | **GPIO 32** <br> — | 3.3V | GPIO (Direct) | 3.3V *(Direct)* | **Common GND** | Connect through a 220Ω–330Ω current-limiting resistor in series. |

---

## 2. Crucial Wiring Protections

### A. MQ-5 Sensor Voltage Divider (5V to 3.3V Conversion)
Because the MQ-5 analog output (AO) can output up to 5V during gas detection, connecting it directly to ESP32 inputs will damage the microchip. Use this voltage divider circuit:

```text
MQ-5 AO Pin (0-5V) ----[ 1kΩ Resistor ]----+---- ESP32 GPIO 34 (0-3.3V)
                                           |
                                    [ 2kΩ Resistor ]
                                           |
                                       Common GND
```

### B. LCD I2C Level Shifting
The I2C backpack pulls SDA and SCL to 5V. To protect the ESP32's hardware I2C bus:
* Connect SDA (GPIO 21) and SCL (GPIO 22) through a **bi-directional logic level shifter** (e.g. BSS138 module) between the 3.3V ESP32 and 5V LCD side.
* Alternatively, if your ESP32 board is 5V-tolerant on I2C or if your LCD backpack has removable pull-ups, ensure no more than 3.3V is present on those pins.

### C. Common Ground (GND)
All grounds from the power supply, ESP32, MQ-5, LCD, HX711, DHT22, LEDs, and Servo **must** be tied together at a single common ground point. A floating ground will lead to unstable sensor readouts and erratic servo movements.
