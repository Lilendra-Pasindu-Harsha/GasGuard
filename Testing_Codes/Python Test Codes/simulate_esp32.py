import time
import json
import urllib.request
import urllib.error
import threading
import random
import sys
from datetime import datetime

# ========== CONFIGURATION ==========
FIREBASE_API_KEY = "AIzaSyAnUdXb9dJzbqOF90RwgAxMdtHJzfchaSg"
FIREBASE_DB_URL = "https://gasguardkdu-default-rtdb.asia-southeast1.firebasedatabase.app"
FIREBASE_EMAIL = "gasgurd@gmail.com"
FIREBASE_PASSWORD = "gasgurd"
FIREBASE_DEVICE_ID = "gasguard-esp32-01"

# Hardware parameters matching SensorTest.ino & NewFullcode.ino
CALIBRATION_FACTOR = 92946.11
TARE_OFFSET = 397352.0
EMPTY_CYLINDER_KG = 8.6
GAS_CAPACITY_KG = 5.0
PLATFORM_WEIGHT_KG = 1.394

def http_request(url, method='GET', data=None, headers=None, timeout=10):
    if headers is None:
        headers = {}
    req_data = None
    if data is not None:
        req_data = json.dumps(data).encode('utf-8')
        if 'Content-Type' not in headers:
            headers['Content-Type'] = 'application/json'
            
    req = urllib.request.Request(url, data=req_data, headers=headers, method=method)
    
    # Avoid SSL certificate errors on local Python setups
    context = None
    try:
        import ssl
        context = ssl._create_unverified_context()
    except Exception:
        pass

    try:
        with urllib.request.urlopen(req, timeout=timeout, context=context) as response:
            content = response.read().decode('utf-8')
            return json.loads(content) if content else {}
    except urllib.error.HTTPError as e:
        err_content = e.read().decode('utf-8')
        try:
            return json.loads(err_content)
        except Exception:
            return {"error": str(e), "content": err_content}
    except Exception as e:
        return {"error": str(e)}

class FirebaseSession:
    def __init__(self):
        self.id_token = None
        self.token_expiry = 0

    def authenticate(self):
        auth_url = f"https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key={FIREBASE_API_KEY}"
        payload = {
            "email": FIREBASE_EMAIL,
            "password": FIREBASE_PASSWORD,
            "returnSecureToken": True
        }
        res = http_request(auth_url, method='POST', data=payload)
        if res and "idToken" in res:
            self.id_token = res["idToken"]
            expires_in = int(res.get("expiresIn", 3600))
            self.token_expiry = time.time() + expires_in - 60  # Refresh 1 minute early
            return True
        else:
            print(f"\n[ERROR] Authentication failed: {res}")
            return False

    def get_token(self):
        if not self.id_token or time.time() >= self.token_expiry:
            if not self.authenticate():
                print("\n[ERROR] Unable to refresh Firebase token. Retrying on next request...")
        return self.id_token

class ESP32Simulator:
    def __init__(self):
        self.session = FirebaseSession()
        self.running = False
        self.update_event = threading.Event()
        
        # State variables
        self.gas_level = 150
        self.gas_threshold = 500
        self.cylinder_weight = 13.6  # Default full cylinder (8.6 kg empty + 5.0 kg gas)
        self.target_weight = 13.6
        self.temperature = 27.5
        self.humidity = 60.0
        self.valve_state = "OPEN"
        self.weight_state = "STABLE"
        
        # Settler tracking
        self.weight_settle_counter = 0
        self.last_target_weight = 13.6
        self.auto_random_weight = True
        self.uptime_start = time.time()
        self.last_alarm_active = False

    def calculate_derived(self):
        alarm_active = self.gas_level > self.gas_threshold
        gas_detected = alarm_active
        
        # In case of alarm, solenoid automatically shuts
        if alarm_active:
            valve_state = "CLOSED"
        else:
            valve_state = self.valve_state
            
        # Weight calculations
        gas_weight = self.cylinder_weight - EMPTY_CYLINDER_KG
        if gas_weight < 0.0:
            gas_weight = 0.0
            
        gas_percentage = (gas_weight / GAS_CAPACITY_KG) * 100.0
        if gas_percentage > 100.0:
            gas_percentage = 100.0
            
        # Simulate raw scale ADC value matching physical load cell formulas
        raw_adc = int(self.cylinder_weight * CALIBRATION_FACTOR + TARE_OFFSET)
        
        return alarm_active, gas_detected, valve_state, gas_weight, gas_percentage, raw_adc

    def update_sensors(self):
        # If auto random weight fluctuation is enabled, choose a random weight
        if self.auto_random_weight:
            self.target_weight = round(random.uniform(1.0, 14.0), 2)

        # Fluctuate temperature and humidity slightly for realism
        self.temperature += random.uniform(-0.1, 0.1)
        self.temperature = max(15.0, min(45.0, self.temperature))
        
        self.humidity += random.uniform(-0.3, 0.3)
        self.humidity = max(20.0, min(90.0, self.humidity))
        
        # Slight fluctuations in normal gas readings
        if abs(self.gas_level - self.gas_threshold) > 10:
            self.gas_level += random.choice([-1, 0, 1])
            self.gas_level = max(50, min(4000, self.gas_level))
            
        # Handle weight settling state flow
        if self.target_weight != self.last_target_weight:
            self.last_target_weight = self.target_weight
            self.cylinder_weight = self.target_weight
            if self.target_weight < 0.2:
                self.weight_state = "IDLE"
            else:
                self.weight_state = "SETTLING"
                self.weight_settle_counter = 2  # Settle over 2 cycles
        else:
            if self.weight_state == "STABLE":
                # Add slight fluctuations around target weight (e.g. +/- 0.005 kg)
                self.cylinder_weight = self.target_weight + random.uniform(-0.005, 0.005)
                self.cylinder_weight = max(0.2, self.cylinder_weight)
            elif self.weight_state == "IDLE":
                # Fluctuate empty scale slightly around 0.0
                self.cylinder_weight = max(0.0, random.uniform(0.0, 0.01))
            elif self.weight_state == "SETTLING":
                self.weight_settle_counter -= 1
                if self.weight_settle_counter <= 0:
                    self.weight_state = "STABLE"

    def check_commands(self, token):
        commands_url = f"{FIREBASE_DB_URL}/devices/{FIREBASE_DEVICE_ID}/commands.json?auth={token}"
        commands = http_request(commands_url, method='GET')
        if not commands or "error" in commands:
            return
            
        alarm_active = self.gas_level > self.gas_threshold
        
        # Parse valve command
        valve_data = commands.get("valveState", {})
        if isinstance(valve_data, dict):
            valve_val = valve_data.get("valveState")
            if valve_val in ["OPEN", "CLOSED"] and not alarm_active:
                if valve_val != self.valve_state:
                    print(f"\n[REMOTE] Valve state changed to {valve_val} via Firebase Command!")
                    self.valve_state = valve_val
                    self.update_event.set()
                    
        # Parse threshold command
        thresh_data = commands.get("gasThreshold", {})
        if isinstance(thresh_data, dict):
            thresh_val = thresh_data.get("gasThreshold")
            if thresh_val is not None:
                try:
                    thresh_val = int(thresh_val)
                    if 200 <= thresh_val <= 3500 and thresh_val != self.gas_threshold:
                        print(f"\n[REMOTE] Threshold changed to {thresh_val} MQ-5 via Firebase Command!")
                        self.gas_threshold = thresh_val
                        self.update_event.set()
                except (ValueError, TypeError):
                    pass

    def publish_status(self, token):
        alarm_active, gas_detected, valve_state, gas_weight, gas_percentage, raw_adc = self.calculate_derived()
        uptime = int(time.time() - self.uptime_start)
        
        # Prepare payload
        payload = {
            "deviceId": FIREBASE_DEVICE_ID,
            "gasLevel": int(self.gas_level),
            "gasThreshold": int(self.gas_threshold),
            "rawADC": int(raw_adc),
            "gasWeightKg": float(round(gas_weight, 3)),
            "cylinderWeightKg": float(round(self.cylinder_weight, 3)),
            "gasPercentage": float(round(gas_percentage, 1)),
            "temperatureC": float(round(self.temperature, 1)),
            "humidityPercent": float(round(self.humidity, 1)),
            "gasDetected": bool(gas_detected),
            "alarmActive": bool(alarm_active),
            "valveState": valve_state,
            "weightState": self.weight_state,
            "uptimeSeconds": uptime,
            "updatedAt": {".sv": "timestamp"}
        }
        
        # Publish
        status_url = f"{FIREBASE_DB_URL}/devices/{FIREBASE_DEVICE_ID}/latest.json?auth={token}"
        res = http_request(status_url, method='PUT', data=payload)
        
        if res and "error" not in res:
            now_str = datetime.now().strftime("%H:%M:%S")
            print(f"[{now_str}] [PUSHED] Gas={self.gas_level}ppm, Weight={self.cylinder_weight:.2f}kg ({self.weight_state}), Temp={self.temperature:.1f}C, Valve={valve_state}")
        else:
            print(f"\n[ERROR] Status upload failed: {res}")
            
        # Log events on alarm state transition
        if alarm_active and not self.last_alarm_active:
            self.push_event(token, "gas_leak_detected", "Gas leak detected. Shutting valve.", 
                            alarm_active, gas_detected, valve_state, gas_weight, gas_percentage, raw_adc)
        elif not alarm_active and self.last_alarm_active:
            self.push_event(token, "alarm_cleared", "Gas levels normal. Valve opened.", 
                            alarm_active, gas_detected, valve_state, gas_weight, gas_percentage, raw_adc)
            
        self.last_alarm_active = alarm_active

    def push_event(self, token, event_type, message, alarm_active, gas_detected, valve_state, gas_weight, gas_percentage, raw_adc):
        event_payload = {
            "type": event_type,
            "message": message,
            "gasLevel": int(self.gas_level),
            "gasThreshold": int(self.gas_threshold),
            "temperatureC": float(round(self.temperature, 1)),
            "humidityPercent": float(round(self.humidity, 1)),
            "rawADC": int(raw_adc),
            "gasWeightKg": float(round(gas_weight, 3)),
            "cylinderWeightKg": float(round(self.cylinder_weight, 3)),
            "gasPercentage": float(round(gas_percentage, 1)),
            "valveState": valve_state,
            "createdAt": {".sv": "timestamp"}
        }
        event_url = f"{FIREBASE_DB_URL}/devices/{FIREBASE_DEVICE_ID}/events.json?auth={token}"
        res = http_request(event_url, method='POST', data=event_payload)
        if res and "error" not in res:
            print(f"[EVENT] Logged '{event_type}' to Firebase database events node.")

    def background_loop(self):
        token = self.session.get_token()
        if not token:
            print("[SIMULATOR] Error: Authentication failed during startup.")
            return

        self.running = True
        last_update = 0
        
        while self.running:
            now = time.time()
            if (now - last_update >= 5.0) or self.update_event.is_set():
                self.update_event.clear()
                last_update = now
                
                token = self.session.get_token()
                if token:
                    self.update_sensors()
                    self.check_commands(token)
                    self.publish_status(token)
            
            time.sleep(0.1)

    def print_status_snapshot(self):
        alarm_active, gas_detected, valve_state, gas_weight, gas_percentage, raw_adc = self.calculate_derived()
        uptime = int(time.time() - self.uptime_start)
        h = uptime // 3600
        m = (uptime % 3600) // 60
        s = uptime % 60
        
        print("\n" + "="*50)
        print("           LIVE ESP32 SENSOR SNAPSHOT")
        print("="*50)
        print(f" Device ID:       {FIREBASE_DEVICE_ID}")
        print(f" System Status:   {'CRITICAL (GAS LEAK)' if alarm_active else 'NORMAL (SAFE)'}")
        print(f" Valve State:     {valve_state}")
        print(f" Uptime:          {h:02d}:{m:02d}:{s:02d}")
        print("-"*50)
        print(f" Gas Level:       {self.gas_level} MQ-5 (Threshold: {self.gas_threshold})")
        print(f" Temperature:     {self.temperature:.1f} °C")
        print(f" Humidity:        {self.humidity:.1f} %")
        print("-"*50)
        print(f" Cylinder Weight: {self.cylinder_weight:.3f} kg [{self.weight_state}]")
        print(f"   - Gas Weight:  {gas_weight:.3f} kg")
        print(f"   - Percentage:  {gas_percentage:.1f} %")
        print(f"   - Raw Scale:   {raw_adc} ADC")
        print("="*50 + "\n")

    def print_help(self):
        print("\n" + "-"*50)
        print(" Simulator Commands Reference")
        print("-"*50)
        print("   leak         - Simulate a gas leak (gas level -> 750 MQ-5)")
        print("   clear        - Clear the simulated gas leak (gas level -> 150 MQ-5)")
        print("   gas <val>    - Set gas level manually (e.g. gas 450)")
        print("   weight <val> - Set weight in kg (e.g. 13.6=Full, 8.6=Empty, 0=Removed)")
        print("   temp <val>   - Set temperature in Celsius")
        print("   humid <val>  - Set humidity percentage")
        print("   status       - Print a full live sensor status snapshot")
        print("   refresh      - Force an immediate state update push to Firebase")
        print("   help         - Show this help menu")
        print("   exit         - Terminate the simulator")
        print("-"*50 + "\n")

    def handle_command(self, cmd_line):
        parts = cmd_line.strip().split()
        if not parts:
            return
            
        cmd = parts[0].lower()
        
        if cmd == "exit":
            self.running = False
            print("[SIMULATOR] Shutting down background thread and exiting...")
            sys.exit(0)
            
        elif cmd in ["help", "h"]:
            self.print_help()
            
        elif cmd in ["status", "s", "show"]:
            self.print_status_snapshot()
            
        elif cmd == "leak":
            self.gas_level = 750
            print(f"[LOCAL] Gas level set to {self.gas_level} ppm (Alarm Active).")
            self.update_event.set()
            
        elif cmd == "clear":
            self.gas_level = 150
            print(f"[LOCAL] Gas leak cleared. Gas level set to {self.gas_level} ppm.")
            self.update_event.set()
            
        elif cmd == "gas":
            if len(parts) < 2:
                print("[ERROR] Usage: gas <val>")
                return
            try:
                self.gas_level = int(parts[1])
                print(f"[LOCAL] Gas level set to {self.gas_level} MQ-5.")
                self.update_event.set()
            except ValueError:
                print("[ERROR] Invalid integer value.")
                
        elif cmd == "weight":
            if len(parts) < 2:
                print("[ERROR] Usage: weight <val> (or 'weight auto')")
                return
            if parts[1].lower() == "auto":
                self.auto_random_weight = True
                print("[LOCAL] Auto random weight fluctuation ENABLED.")
                self.update_event.set()
                return
            try:
                val = float(parts[1])
                if val < 0:
                    print("[ERROR] Weight cannot be negative.")
                    return
                self.auto_random_weight = False  # Disable auto mode on manual entry
                self.target_weight = val
                print(f"[LOCAL] Target cylinder weight set to {self.target_weight:.3f} kg (State: SETTLING).")
                self.update_event.set()
            except ValueError:
                print("[ERROR] Invalid numeric value.")
                
        elif cmd == "autowt":
            self.auto_random_weight = not self.auto_random_weight
            print(f"[LOCAL] Auto random weight fluctuation is now {'ENABLED' if self.auto_random_weight else 'DISABLED'}.")
            self.update_event.set()
                
        elif cmd == "temp":
            if len(parts) < 2:
                print("[ERROR] Usage: temp <val>")
                return
            try:
                self.temperature = float(parts[1])
                print(f"[LOCAL] Temperature set to {self.temperature:.1f} °C.")
                self.update_event.set()
            except ValueError:
                print("[ERROR] Invalid numeric value.")
                
        elif cmd == "humid":
            if len(parts) < 2:
                print("[ERROR] Usage: humid <val>")
                return
            try:
                val = float(parts[1])
                if not (0.0 <= val <= 100.0):
                    print("[ERROR] Humidity must be between 0 and 100.")
                    return
                self.humidity = val
                print(f"[LOCAL] Humidity set to {self.humidity:.1f} %.")
                self.update_event.set()
            except ValueError:
                print("[ERROR] Invalid numeric value.")
                
        elif cmd == "refresh":
            self.update_event.set()
            
        else:
            print(f"[ERROR] Unknown command: '{cmd}'. Type 'help' to see reference.")

def main():
    print("="*60)
    print("      ESP32 SENSOR SIMULATOR DAEMON FOR GASGUARD SYSTEM")
    print("="*60)
    print(f"Device Endpoint:  {FIREBASE_DB_URL}/devices/{FIREBASE_DEVICE_ID}")
    print(f"Authentication:   {FIREBASE_EMAIL}")
    print("Type 'help' for available commands, 'status' for live snapshot.")
    print("="*60 + "\n")
    
    sim = ESP32Simulator()
    
    # Start loop in background thread
    bg_thread = threading.Thread(target=sim.background_loop, daemon=True)
    bg_thread.start()
    
    # Simple CLI loop in main thread
    time.sleep(1.0) # Wait for initial check
    while True:
        try:
            cmd_line = input()
            sim.handle_command(cmd_line)
        except KeyboardInterrupt:
            print("\n[SIMULATOR] Shutting down simulation...")
            sim.running = False
            break

if __name__ == "__main__":
    main()
