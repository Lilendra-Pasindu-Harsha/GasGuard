import time
import json
import sys
import requests
import urllib3

# Suppress insecure request warnings from using verify=False
urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

# ========== CONFIGURATION ==========
FIREBASE_API_KEY = "AIzaSyAnUdXb9dJzbqOF90RwgAxMdtHJzfchaSg"
FIREBASE_DB_URL = "https://gasguardkdu-default-rtdb.asia-southeast1.firebasedatabase.app"
FIREBASE_EMAIL = "gasgurd@gmail.com"
FIREBASE_PASSWORD = "gasgurd"
FIREBASE_DEVICE_ID = "gasguard-esp32-01"

EMPTY_CYLINDER_KG = 8.6
GAS_CAPACITY_KG = 5.0
PLATFORM_WEIGHT_KG = 1.394

# Import Windows console library to read keypresses without hitting Enter
try:
    import msvcrt
    has_msvcrt = True
except ImportError:
    has_msvcrt = False

def http_request(url, method='GET', data=None, headers=None, timeout=10):
    if headers is None:
        headers = {}
    try:
        if method == 'POST':
            res = requests.post(url, json=data, headers=headers, timeout=timeout, verify=False)
        elif method == 'PUT':
            res = requests.put(url, json=data, headers=headers, timeout=timeout, verify=False)
        elif method == 'PATCH':
            res = requests.patch(url, json=data, headers=headers, timeout=timeout, verify=False)
        else:
            res = requests.get(url, headers=headers, timeout=timeout, verify=False)
            
        return res.json() if res.content else {}
    except Exception as e:
        return {"error": str(e)}

class InteractiveFirebaseSender:
    def __init__(self):
        self.id_token = None
        self.token_expiry = 0
        self.current_weight = 5.0
        self.authenticate()

    def authenticate(self):
        print("[AUTH] Connecting to Firebase Authentication...")
        print("       (Please wait up to 1-2 minutes, as DNS resolution in this environment can be slow...)")
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
            self.token_expiry = time.time() + expires_in - 60
            print("[SUCCESS] Firebase connection established!")
            return True
        else:
            print(f"[ERROR] Authentication failed: {res}")
            return False

    def get_token(self):
        if not self.id_token or time.time() >= self.token_expiry:
            self.authenticate()
        return self.id_token

    def send_data(self):
        token = self.get_token()
        url = f"{FIREBASE_DB_URL}/devices/{FIREBASE_DEVICE_ID}/latest.json?auth={token}"
        
        gas_pct = (self.current_weight / GAS_CAPACITY_KG) * 100.0
        gas_pct = max(0.0, min(100.0, gas_pct))
        
        # Format payload exactly like ESP32 output (SensorTest.ino)
        payload = {
            "gasWeightKg": round(self.current_weight, 3),
            "cylinderWeightKg": round(self.current_weight + EMPTY_CYLINDER_KG + PLATFORM_WEIGHT_KG, 3),
            "gasLevel": int(150 + (self.current_weight * 10)),  # Simulated MQ sensor level
            "gasPercentage": round(gas_pct, 2),
            "temperatureC": 28.5,
            "humidityPercent": 65.0,
            "gasThreshold": 800,
            "gasDetected": False,
            "alarmActive": False,
            "valveState": "OPEN",
            "rawAdc": int(397352 + (self.current_weight * 92946)),
            "updatedAt": int(time.time() * 1000)
        }
        
        res = http_request(url, method='PUT', data=payload)
        if res and "error" not in res:
            print(f"\r[PUSHED] Gas Weight: {self.current_weight:.3f} kg | Cylinder: {payload['cylinderWeightKg']:.3f} kg | Percent: {payload['gasPercentage']:.1f}%  ", end="", flush=True)
        else:
            print(f"\n[ERROR] Failed to push: {res}")

    def start(self):
        print("\n" + "="*60)
        print("          GASGUARD INTERACTIVE TEST SENDER          ")
        print("="*60)
        
        try:
            start_wt = input("Enter initial gas weight in kg (Default: 5.0): ").strip()
            if start_wt:
                self.current_weight = float(start_wt)
        except ValueError:
            print("Invalid number. Starting with 5.0 kg.")
            
        print("\nInstructions:")
        if has_msvcrt:
            print("  • Press UP ARROW  or [+] key to increase weight by 0.01 kg")
            print("  • Press DOWN ARROW or [-] key to decrease weight by 0.01 kg")
        else:
            print("  • Type '+' and press Enter to increase weight by 0.01 kg")
            print("  • Type '-' and press Enter to decrease weight by 0.01 kg")
        print("  • Press Ctrl+C or Esc to exit.\n")
        
        # Send initial state
        self.send_data()
        
        # Interactive loop
        while True:
            try:
                if has_msvcrt:
                    if msvcrt.kbhit():
                        ch = msvcrt.getch()
                        
                        # Detect Arrow Keys on Windows
                        if ch == b'\xe0':
                            arrow = msvcrt.getch()
                            if arrow == b'H':  # Up Arrow
                                self.current_weight = min(5.5, self.current_weight + 0.01)
                                self.send_data()
                            elif arrow == b'P':  # Down Arrow
                                self.current_weight = max(0.0, self.current_weight - 0.01)
                                self.send_data()
                                
                        # Detect [+] and [-] keys
                        elif ch == b'+' or ch == b'=':
                            self.current_weight = min(5.5, self.current_weight + 0.01)
                            self.send_data()
                        elif ch == b'-':
                            self.current_weight = max(0.0, self.current_weight - 0.01)
                            self.send_data()
                            
                        # Exit on ESC
                        elif ch == b'\x1b':
                            print("\nExiting interactive sender.")
                            break
                            
                    time.sleep(0.05)
                else:
                    # Fallback for non-Windows terminal environment
                    cmd = sys.stdin.readline().strip()
                    if cmd == '+':
                        self.current_weight = min(5.5, self.current_weight + 0.01)
                        self.send_data()
                    elif cmd == '-':
                        self.current_weight = max(0.0, self.current_weight - 0.01)
                        self.send_data()
                    elif cmd.lower() in ['exit', 'q']:
                        break
            except KeyboardInterrupt:
                print("\nExiting interactive sender.")
                break

if __name__ == "__main__":
    sender = InteractiveFirebaseSender()
    sender.start()
