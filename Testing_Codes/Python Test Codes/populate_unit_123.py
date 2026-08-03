import json
import urllib.request

# ========== CONFIGURATION ==========
FIREBASE_API_KEY = "AIzaSyAnUdXb9dJzbqOF90RwgAxMdtHJzfchaSg"
FIREBASE_DB_URL = "https://gasguardkdu-default-rtdb.asia-southeast1.firebasedatabase.app"
FIREBASE_EMAIL = "gasgurd@gmail.com"
FIREBASE_PASSWORD = "gasgurd"

def http_request(url, method='GET', data=None):
    headers = {'Content-Type': 'application/json'}
    req_data = None
    if data is not None:
        req_data = json.dumps(data).encode('utf-8')
    req = urllib.request.Request(url, data=req_data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=10) as response:
            content = response.read().decode('utf-8')
            return json.loads(content) if content else {}
    except Exception as e:
        print(f"Error: {e}")
        return None

def main():
    # 1. Authenticate
    auth_url = f"https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key={FIREBASE_API_KEY}"
    payload = {
        "email": FIREBASE_EMAIL,
        "password": FIREBASE_PASSWORD,
        "returnSecureToken": True
    }
    auth_data = http_request(auth_url, method='POST', data=payload)
    id_token = auth_data["idToken"]

    # 2. Read UNIT_001 template
    print("Reading UNIT_001 data...")
    get_url = f"{FIREBASE_DB_URL}/gas_stats/UNIT_001.json?auth={id_token}"
    unit_001_data = http_request(get_url)
    
    if not unit_001_data:
        print("Failed to read UNIT_001 template.")
        return

    # 3. Create UNIT_123 data based on template
    unit_123_data = unit_001_data.copy()
    unit_123_data["unitId"] = "UNIT_123"
    unit_123_data["currentWeight"] = 0.0  # Initial weight 0 until update
    unit_123_data["gasPercentage"] = 0.0
    unit_123_data["gasLevel"] = 0
    unit_123_data["temperature"] = 0.0
    
    print("Creating UNIT_123 in database...")
    put_url = f"{FIREBASE_DB_URL}/gas_stats/UNIT_123.json?auth={id_token}"
    result = http_request(put_url, method='PUT', data=unit_123_data)
    
    if result:
        print("SUCCESS: UNIT_123 created in Firebase gas_stats node!")
        print(json.dumps(result, indent=4))
    else:
        print("Failed to write UNIT_123 data.")

if __name__ == "__main__":
    main()
