import { initializeApp } from "https://www.gstatic.com/firebasejs/10.12.5/firebase-app.js";
import {
  getAuth,
  signInWithEmailAndPassword,
  onAuthStateChanged
} from "https://www.gstatic.com/firebasejs/10.12.5/firebase-auth.js";
import {
  getDatabase,
  onValue,
  push,
  ref,
  serverTimestamp,
  set,
  update
} from "https://www.gstatic.com/firebasejs/10.12.5/firebase-database.js";

const firebaseConfig = {
  apiKey: "AIzaSyAnUdXb9dJzbqOF90RwgAxMdtHJzfchaSg",
  authDomain: "gasguardkdu.firebaseapp.com",
  databaseURL: "https://gasguardkdu-default-rtdb.asia-southeast1.firebasedatabase.app",
  projectId: "gasguardkdu",
  storageBucket: "gasguardkdu.firebasestorage.app",
  messagingSenderId: "839530937364",
  appId: "1:839530937364:web:c6058adb75f4a1b8bc7b01"
};

const FIREBASE_USER_EMAIL = "gasgurd@gmail.com";
const FIREBASE_USER_PASSWORD = "gasgurd";
export const DEVICE_ID = "gasguard-esp32-01";

export const app = initializeApp(firebaseConfig);
export const auth = getAuth(app);
export const database = getDatabase(app);

export async function signInGasGuardUser() {
  return signInWithEmailAndPassword(
    auth,
    FIREBASE_USER_EMAIL,
    FIREBASE_USER_PASSWORD
  );
}

export function onGasGuardAuthState(callback) {
  return onAuthStateChanged(auth, callback);
}

export function listenLatestDeviceStatus(callback, errorCallback, deviceId = DEVICE_ID) {
  return onValue(
    ref(database, `devices/${deviceId}/latest`),
    (snapshot) => callback(snapshot.val()),
    errorCallback
  );
}

export async function writeLatestDeviceStatus(status, deviceId = DEVICE_ID) {
  const normalizedStatus = {
    deviceId,
    gasLevel: Number(status.gasLevel ?? 0),
    gasThreshold: Number(status.gasThreshold ?? 700),
    gasWeightKg: Number(status.gasWeightKg ?? 0),
    temperatureC: Number(status.temperatureC ?? 0),
    humidityPercent: Number(status.humidityPercent ?? 0),
    gasDetected: Boolean(status.gasDetected),
    alarmActive: Boolean(status.alarmActive),
    valveState: status.valveState ?? (status.alarmActive ? "CLOSED" : "OPEN"),
    uptimeSeconds: Number(status.uptimeSeconds ?? 0),
    updatedAt: serverTimestamp()
  };

  return set(ref(database, `devices/${deviceId}/latest`), normalizedStatus);
}

export async function updateDeviceStatus(partialStatus, deviceId = DEVICE_ID) {
  return update(ref(database, `devices/${deviceId}/latest`), {
    ...partialStatus,
    updatedAt: serverTimestamp()
  });
}

export async function pushDeviceEvent(type, payload = {}, deviceId = DEVICE_ID) {
  return push(ref(database, `devices/${deviceId}/events`), {
    type,
    ...payload,
    createdAt: serverTimestamp()
  });
}

export async function requestValveState(valveState, deviceId = DEVICE_ID) {
  return set(ref(database, `devices/${deviceId}/commands/valveState`), {
    valveState,
    requestedAt: serverTimestamp()
  });
}

export async function requestGasThreshold(gasThreshold, deviceId = DEVICE_ID) {
  return set(ref(database, `devices/${deviceId}/commands/gasThreshold`), {
    gasThreshold: Number(gasThreshold),
    requestedAt: serverTimestamp()
  });
}

window.GasGuardFirebase = {
  signInGasGuardUser,
  onGasGuardAuthState,
  listenLatestDeviceStatus,
  writeLatestDeviceStatus,
  updateDeviceStatus,
  pushDeviceEvent,
  requestValveState,
  requestGasThreshold
};
