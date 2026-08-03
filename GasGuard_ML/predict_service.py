"""
================================================================================
GASGUARD - REAL-TIME MACHINE LEARNING INFERENCE SERVICE
================================================================================
Project: IoT-Based Gas Cylinder Monitoring & Prediction System
Model: Linear Regression for Gas Depletion Prediction
Author: WALP Harsha
Date: June 2026
================================================================================
"""

import os
import time
import pickle
import json
from datetime import datetime, timezone
import urllib.request
import urllib.error

# ========== CONFIGURATION ==========
FIREBASE_API_KEY = "AIzaSyAnUdXb9dJzbqOF90RwgAxMdtHJzfchaSg"
FIREBASE_DB_URL = "https://gasguardkdu-default-rtdb.asia-southeast1.firebasedatabase.app"
FIREBASE_EMAIL = "gasgurd@gmail.com"
FIREBASE_PASSWORD = "gasgurd"

DEVICE_ID = "gasguard-esp32-01"
UNIT_ID = "UNIT_001"
POLL_INTERVAL_SECONDS = 5.0
DEFAULT_DAILY_USAGE = 0.12  # kg/day default fallback
CYLINDER_CAPACITY_KG = 5.0

# Paths to models
MODEL_PATH = "gasguard_model.pkl"
SCALER_PATH = "gasguard_scaler.pkl"

class GasGuardPredictor:
    def __init__(self):
        self.id_token = None
        self.token_expiry_time = 0
        self.model = None
        self.scaler = None
        self.last_update_time = None
        self.load_models()
        self.authenticate()

    def load_models(self):
        print("[INFO] Loading Machine Learning models...")
        try:
            # Try to load from root, then gasguard_models/ folder
            m_path = MODEL_PATH if os.path.exists(MODEL_PATH) else os.path.join("gasguard_models", MODEL_PATH)
            s_path = SCALER_PATH if os.path.exists(SCALER_PATH) else os.path.join("gasguard_models", SCALER_PATH)
            
            with open(m_path, "rb") as f:
                self.model = pickle.load(f)
            with open(s_path, "rb") as f:
                self.scaler = pickle.load(f)
            print("[SUCCESS] Models loaded successfully!")
        except Exception as e:
            print(f"[ERROR] Error loading pickle models: {e}")
            print("Ensure train.py has been run and models exist.")
            raise e

    def _http_request(self, url, method='GET', data=None, headers=None, timeout=10):
        if headers is None:
            headers = {}
        req_data = None
        if data is not None:
            req_data = json.dumps(data).encode('utf-8')
            if 'Content-Type' not in headers:
                headers['Content-Type'] = 'application/json'
        req = urllib.request.Request(url, data=req_data, headers=headers, method=method)
        
        context = None
        try:
            import ssl
            context = ssl._create_unverified_context()
        except Exception:
            pass
            
        try:
            with urllib.request.urlopen(req, timeout=timeout, context=context) as response:
                content = response.read().decode('utf-8')
                if content:
                    return json.loads(content)
                return {}
        except Exception as e:
            raise e

    def authenticate(self):
        """Authenticates with Firebase Auth to retrieve an ID token for DB operations."""
        print("[AUTH] Authenticating with Firebase Auth...")
        auth_url = f"https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key={FIREBASE_API_KEY}"
        payload = {
            "email": FIREBASE_EMAIL,
            "password": FIREBASE_PASSWORD,
            "returnSecureToken": True
        }
        try:
            data = self._http_request(auth_url, method='POST', data=payload)
            self.id_token = data["idToken"]
            expires_in = int(data["expiresIn"])
            self.token_expiry_time = time.time() + expires_in - 60  # Buffer of 1 minute
            print("[SUCCESS] Authentication successful!")
        except Exception as e:
            print(f"[ERROR] Authentication failed: {e}")
            raise e

    def get_auth_token(self):
        """Returns the ID token, refreshing it if expired."""
        if not self.id_token or time.time() >= self.token_expiry_time:
            self.authenticate()
        return self.id_token

    def fetch_latest_device_status(self):
        """Fetches the raw device status from Firebase RTDB."""
        token = self.get_auth_token()
        url = f"{FIREBASE_DB_URL}/devices/{DEVICE_ID}/latest.json?auth={token}"
        try:
            return self._http_request(url, method='GET')
        except Exception as e:
            print(f"[WARNING] Error fetching device status: {e}")
            return None

    def fetch_history(self):
        """Fetches weight consumption history from Firebase RTDB."""
        token = self.get_auth_token()
        url = f"{FIREBASE_DB_URL}/gas_stats/{UNIT_ID}/history.json?auth={token}"
        try:
            val = self._http_request(url, method='GET')
            return val if val else {}
        except Exception as e:
            print(f"[WARNING] Error fetching history: {e}")
            return {}

    def update_history(self, history, current_weight):
        """Updates weight history, detecting refills and daily consumption."""
        today_str = datetime.now().strftime("%Y-%m-%d")
        updated = False
        
        # Parse existing history (keys might not be sorted)
        sorted_dates = sorted(history.keys())
        
        # Detect refill if weight increased significantly (>0.5kg) compared to last entry
        if sorted_dates:
            last_date = sorted_dates[-1]
            last_weight = float(history[last_date])
            if current_weight - last_weight > 0.5:
                print(f"[NOTIFICATION] Refill detected! Previous weight: {last_weight}kg -> New weight: {current_weight}kg. Resetting history.")
                # Reset history to only contain the new weight
                history = {today_str: current_weight}
                updated = True
                sorted_dates = [today_str]
        
        # Add or update today's weight
        if today_str not in history or abs(float(history[today_str]) - current_weight) > 0.01:
            history[today_str] = current_weight
            updated = True
            
        # Write back to Firebase if modified
        if updated:
            token = self.get_auth_token()
            url = f"{FIREBASE_DB_URL}/gas_stats/{UNIT_ID}/history.json?auth={token}"
            try:
                self._http_request(url, method='PUT', data=history)
            except Exception as e:
                print(f"[WARNING] Failed to update history in Firebase: {e}")
                
        return history

    def calculate_daily_usage(self, history):
        """Calculates daily usage in kg and 7-day rolling average based on weight history."""
        sorted_dates = sorted(history.keys())
        if len(sorted_dates) < 2:
            return DEFAULT_DAILY_USAGE, DEFAULT_DAILY_USAGE
            
        usages = []
        for i in range(1, len(sorted_dates)):
            d1 = datetime.strptime(sorted_dates[i-1], "%Y-%m-%d")
            d2 = datetime.strptime(sorted_dates[i], "%Y-%m-%d")
            days_diff = (d2 - d1).days
            if days_diff <= 0:
                continue
                
            w1 = float(history[sorted_dates[i-1]])
            w2 = float(history[sorted_dates[i]])
            weight_diff = w1 - w2
            
            # Only count depletion (ignore refills or positive fluctuations)
            if weight_diff > 0:
                daily_usage = weight_diff / days_diff
                # Cap usage at reasonable levels to filter out outliers
                if daily_usage < 1.0: 
                    usages.append(daily_usage)
                    
        if not usages:
            return DEFAULT_DAILY_USAGE, DEFAULT_DAILY_USAGE
            
        current_usage = usages[-1]
        rolling_avg = sum(usages[-7:]) / len(usages[-7:]) if len(usages) >= 7 else sum(usages) / len(usages)
        
        # Ensure values are within normal limits
        current_usage = max(0.05, min(0.3, current_usage))
        rolling_avg = max(0.05, min(0.3, rolling_avg))
        
        return float(current_usage), float(rolling_avg)

    def write_predictions(self, device_status, daily_usage, rolling_avg, days_remaining):
        """Updates the /gas_stats/UNIT_001 node with prediction and raw data."""
        token = self.get_auth_token()
        url = f"{FIREBASE_DB_URL}/gas_stats/{UNIT_ID}.json?auth={token}"
        
        raw_cylinder_weight = float(device_status.get("cylinderWeightKg", 0.0))
        if raw_cylinder_weight > 8.6:
            current_weight = raw_cylinder_weight - 8.6
        else:
            current_weight = raw_cylinder_weight if raw_cylinder_weight > 0.0 else float(device_status.get("gasWeightKg", 0.0))
        
        gas_pct = float(current_weight / CYLINDER_CAPACITY_KG * 100.0)
        gas_pct = max(0.0, min(100.0, gas_pct))
        
        alarm_active = bool(device_status.get("alarmActive", False))
        gas_detected = bool(device_status.get("gasDetected", False))
        valve_state = device_status.get("valveState", "OPEN")
        
        # Assemble payload matching Android app's expectations
        payload = {
            "unitId": UNIT_ID,
            "deviceId": DEVICE_ID,
            "currentWeight": current_weight,
            "gasLevel": int(device_status.get("gasLevel", 0)),
            "gasPercentage": gas_pct,
            "leakDetected": gas_detected,
            "leakPercentage": 85.0 if gas_detected else 0.0,
            "systemStatus": "CRITICAL" if alarm_active else "NORMAL",
            "temperature": float(device_status.get("temperatureC", 27.5)),
            "timestamp": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
            "valveClosed": valve_state == "CLOSED" or alarm_active,
            "dailyUsage": daily_usage,
            "daysRemaining": int(days_remaining),
            "estimatedCost": daily_usage * 30 * 382.0,  # 382 LKR per kg
            "capacity": "5kg"
        }
        
        try:
            self._http_request(url, method='PATCH', data=payload)
            print(f"[DATA] Live Prediction Sent: {current_weight:.2f}kg | Daily Usage: {daily_usage:.3f}kg/day | Days Left: {days_remaining}")
        except Exception as e:
            print(f"[ERROR] Failed to write predictions to Firebase: {e}")

    def run_prediction_cycle(self):
        """Runs a single prediction step."""
        # 1. Fetch latest raw readings
        status = self.fetch_latest_device_status()
        if not status:
            return
            
        updated_at_ms = status.get("updatedAt", 0)
        if updated_at_ms == self.last_update_time:
            # No new data since last check
            return
            
        self.last_update_time = updated_at_ms
        raw_weight = float(status.get("gasWeightKg", 0.0))
        # Clamp to valid 5kg cylinder range (raw load cell values can be uncalibrated)
        current_weight = max(0.0, min(CYLINDER_CAPACITY_KG, raw_weight))
        
        # 2. Fetch history and update it
        history = self.fetch_history()
        history = self.update_history(history, current_weight)
        
        # 3. Calculate daily usage rates
        daily_usage, rolling_avg = self.calculate_daily_usage(history)
        
        # 4. Prepare feature matrix for the ML model
        gas_pct = (current_weight / CYLINDER_CAPACITY_KG) * 100.0
        gas_pct = max(0.0, min(100.0, gas_pct))
        
        day_of_week = datetime.now().weekday()
        weekend = 1 if day_of_week >= 5 else 0
        consumption_rate = daily_usage / gas_pct if gas_pct > 0 else 0
        
        # Scale features using the trained scaler (supports list of lists)
        features = [[
            current_weight,
            gas_pct,
            daily_usage,
            day_of_week,
            weekend,
            consumption_rate,
            rolling_avg
        ]]
        
        features_scaled = self.scaler.transform(features)
        
        # 5. Predict days remaining
        days_pred = self.model.predict(features_scaled)[0]
        
        # Apply sanity limits to prediction
        days_remaining = max(0, min(90, int(round(days_pred))))
        if current_weight <= 0.1:
            days_remaining = 0
            
        # 6. Write final predictions back to Firebase
        self.write_predictions(status, daily_usage, rolling_avg, days_remaining)

    def start(self):
        print("\n[STARTING] Starting GasGuard Real-Time Inference Daemon...")
        print(f"[STATUS] Device ID: {DEVICE_ID}  -->  Customer Unit: {UNIT_ID}")
        print("Press Ctrl+C to stop.\n")
        
        while True:
            try:
                self.run_prediction_cycle()
            except KeyboardInterrupt:
                print("\n[SHUTDOWN] Shutting down prediction service...")
                break
            except Exception as e:
                print(f"[WARNING] Error in prediction loop: {e}")
                
            time.sleep(POLL_INTERVAL_SECONDS)

if __name__ == "__main__":
    predictor = GasGuardPredictor()
    predictor.start()
