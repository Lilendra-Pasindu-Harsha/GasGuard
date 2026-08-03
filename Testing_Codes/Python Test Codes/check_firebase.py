import time
import json
import urllib.request
import urllib.error

# ========== CONFIGURATION ==========
FIREBASE_API_KEY = "AIzaSyAnUdXb9dJzbqOF90RwgAxMdtHJzfchaSg"
FIREBASE_DB_URL = "https://gasguardkdu-default-rtdb.asia-southeast1.firebasedatabase.app"
FIREBASE_EMAIL = "gasgurd@gmail.com"
FIREBASE_PASSWORD = "gasgurd"

def http_request(url, method='GET', data=None, headers=None, timeout=15):
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
    print("[INFO] Authenticating...")
    auth_url = f"https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key={FIREBASE_API_KEY}"
    payload = {
        "email": FIREBASE_EMAIL,
        "password": FIREBASE_PASSWORD,
        "returnSecureToken": True
    }
    auth_data = http_request(auth_url, method='POST', data=payload)
    if not auth_data or "idToken" not in auth_data:
        print("[ERROR] Authentication failed!")
        return
    id_token = auth_data["idToken"]

    print("[INFO] Fetching entire database dump...")
    db_url = f"{FIREBASE_DB_URL}/.json?auth={id_token}"
    db_dump = http_request(db_url, method='GET')
    
    if db_dump:
        print("[SUCCESS] Database fetched!")
        with open("db_dump.json", "w") as f:
            json.dump(db_dump, f, indent=4)
        print("[INFO] Dump saved to db_dump.json")
    else:
        print("[ERROR] Failed to fetch database.")

if __name__ == "__main__":
    main()
