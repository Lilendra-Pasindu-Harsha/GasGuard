import json
import urllib.request

# ========== CONFIGURATION ==========
FIREBASE_API_KEY = "AIzaSyAnUdXb9dJzbqOF90RwgAxMdtHJzfchaSg"
FIREBASE_DB_URL = "https://gasguardkdu-default-rtdb.asia-southeast1.firebasedatabase.app"
FIREBASE_EMAIL = "gasgurd@gmail.com"
FIREBASE_PASSWORD = "gasgurd"

def http_request(url, method='GET', data=None):
    req_data = None
    if data is not None:
        req_data = json.dumps(data).encode('utf-8')
    req = urllib.request.Request(url, data=req_data, method=method)
    try:
        with urllib.request.urlopen(req, timeout=10) as response:
            content = response.read().decode('utf-8')
            return json.loads(content) if content else {}
    except Exception as e:
        print(f"Error: {e}")
        return None

def main():
    # Authenticate
    auth_url = f"https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key={FIREBASE_API_KEY}"
    payload = {
        "email": FIREBASE_EMAIL,
        "password": FIREBASE_PASSWORD,
        "returnSecureToken": True
    }
    auth_data = http_request(auth_url, method='POST', data=payload)
    id_token = auth_data["idToken"]

    # Read gas_stats
    print("Reading gas_stats keys from DB...")
    db_url = f"{FIREBASE_DB_URL}/gas_stats.json?auth={id_token}"
    gas_stats = http_request(db_url)
    
    if gas_stats:
        for k, v in gas_stats.items():
            print(f"Unit ID: {k}")
            print(f"  Device ID: {v.get('deviceId')}")
            print(f"  Current Weight: {v.get('currentWeight')} kg")
            print(f"  Gas Level: {v.get('gasLevel')}")
            print(f"  Timestamp: {v.get('timestamp')}")
    else:
        print("No gas_stats found.")

if __name__ == "__main__":
    main()
