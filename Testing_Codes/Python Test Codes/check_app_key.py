import time
import json
import urllib.request
import urllib.error

# ========== CONFIGURATION (using App's API Key from google-services.json) ==========
FIREBASE_API_KEY = "AIzaSyADHrlsOBQ8Tf-QcPycb0JkcvMKFm3Dzrk"
FIREBASE_DB_URL = "https://gasguardkdu-default-rtdb.asia-southeast1.firebasedatabase.app"
FIREBASE_EMAIL = "gasgurd@gmail.com"
FIREBASE_PASSWORD = "gasgurd"

def http_request(url, method='GET', data=None, headers=None, timeout=10):
    if headers is None:
        headers = {}
    req_data = None
    if data is not None:
        req_data = json.dumps(data).encode('utf-8')
        if 'Content-Type' not in headers:
            headers['Content-Type'] = 'application/json'
    req = urllib.request.Request(url, data=req_data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as response:
            content = response.read().decode('utf-8')
            if content:
                return json.loads(content)
            return {}
    except Exception as e:
        print(f"HTTP request error: {e}")
        return None

def main():
    print("[INFO] Authenticating using App's API Key...")
    auth_url = f"https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key={FIREBASE_API_KEY}"
    payload = {
        "email": FIREBASE_EMAIL,
        "password": FIREBASE_PASSWORD,
        "returnSecureToken": True
    }
    
    auth_data = http_request(auth_url, method='POST', data=payload)
    if not auth_data or "idToken" not in auth_data:
        print("[ERROR] Firebase Authentication failed! The App's API key might be invalid or restricted.")
        if auth_data:
            print(json.dumps(auth_data, indent=4))
        return

    id_token = auth_data["idToken"]
    print("[SUCCESS] Authenticated successfully with App's API Key!")

    print("\n[INFO] Fetching latest device status...")
    db_url = f"{FIREBASE_DB_URL}/devices/gasguard-esp32-01/latest.json?auth={id_token}"
    latest_status = http_request(db_url, method='GET')
    
    if latest_status:
        print("\n=== LATEST DEVICE STATUS ===")
        print(json.dumps(latest_status, indent=4))
    else:
        print("[WARNING] Could not read database.")

if __name__ == "__main__":
    main()
