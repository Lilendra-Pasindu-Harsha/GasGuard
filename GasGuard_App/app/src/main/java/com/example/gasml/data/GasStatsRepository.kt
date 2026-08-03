package com.example.gasml.data

import android.util.Log
import com.example.gasml.model.GasStats
import com.example.gasml.model.DeviceStatus
import com.example.gasml.model.MlMetrics
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ServerValue
import com.example.gasml.util.Constants
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import java.text.SimpleDateFormat
import java.util.*

class GasStatsRepository {
    private val database = FirebaseDatabase.getInstance(Constants.DATABASE_URL).getReference("gas_stats")

    fun getUnitStats(unitId: String): Flow<GasStats?> = callbackFlow {
        if (unitId.isBlank()) {
            trySend(null)
            close()
            return@callbackFlow
        }
        
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val stats = try {
                    if (snapshot.exists()) {
                        snapshot.getValue(GasStats::class.java)
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    Log.e("GasStatsRepository", "Failed to parse GasStats for $unitId", e)
                    null
                }
                trySend(stats)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("GasStatsRepository", "Error listening to unit stats for $unitId", error.toException())
                close(error.toException())
            }
        }

        val ref = database.child(unitId)
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    suspend fun updateUnitStats(unitId: String, updates: Map<String, Any>): Result<Unit> {
        return try {
            database.child(unitId).updateChildren(updates).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("GasStatsRepository", "Failed to update unit stats for $unitId", e)
            Result.failure(e)
        }
    }

    suspend fun simulateLeak(unitId: String, isLeak: Boolean): Result<Unit> {
        return try {
            val snapshot = withTimeout(5000) {
                database.child(unitId).get().await()
            }
            val deviceId = snapshot.child("deviceId").getValue(String::class.java) ?: "gasguard-esp32-01"
            
            val updates = mutableMapOf<String, Any>(
                "leakDetected" to isLeak,
                "leakPercentage" to if (isLeak) 85.0 else 0.0,
                "systemStatus" to if (isLeak) "CRITICAL" else "NORMAL",
                "valveClosed" to isLeak
            )
            
            if (!snapshot.exists()) {
                updates.putAll(mapOf(
                    "unitId" to unitId,
                    "deviceId" to deviceId,
                    "currentWeight" to 5.0,
                    "gasLevel" to 100L,
                    "gasPercentage" to 100.0,
                    "temperature" to 27.5,
                    "timestamp" to SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault()).format(Date()),
                    "dailyUsage" to 0.12,
                    "daysRemaining" to 41L,
                    "capacity" to "5kg"
                ))
            }
            
            updateUnitStats(unitId, updates)

            // Update live device node so simulation updates hardware status and triggers alarms/services
            val deviceRef = FirebaseDatabase.getInstance(Constants.DATABASE_URL).getReference("devices").child(deviceId).child("latest")
            val deviceUpdates = mapOf<String, Any>(
                "gasDetected" to isLeak,
                "alarmActive" to isLeak,
                "gasLevel" to if (isLeak) 950 else 120,
                "valveState" to if (isLeak) "CLOSED" else "OPEN"
            )
            deviceRef.updateChildren(deviceUpdates).await()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("GasStatsRepository", "Simulation failed", e)
            Result.failure(e)
        }
    }

    suspend fun acknowledgeLeak(unitId: String): Result<Unit> {
        return try {
            val snapshot = withTimeout(5000) {
                database.child(unitId).get().await()
            }
            val deviceId = snapshot.child("deviceId").getValue(String::class.java) ?: "gasguard-esp32-01"
            
            updateUnitStats(unitId, mapOf(
                "leakDetected" to false,
                "leakPercentage" to 0.0,
                "systemStatus" to "NORMAL",
                "valveClosed" to false
            ))

            val deviceRef = FirebaseDatabase.getInstance(Constants.DATABASE_URL).getReference("devices").child(deviceId).child("latest")
            val deviceUpdates = mapOf<String, Any>(
                "gasDetected" to false,
                "alarmActive" to false,
                "gasLevel" to 120,
                "valveState" to "OPEN"
            )
            deviceRef.updateChildren(deviceUpdates).await()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("GasStatsRepository", "Acknowledge failed", e)
            Result.failure(e)
        }
    }

    fun getDeviceStatus(deviceId: String): Flow<DeviceStatus?> = callbackFlow {
        if (deviceId.isBlank()) {
            trySend(null)
            close()
            return@callbackFlow
        }
        val ref = FirebaseDatabase.getInstance(Constants.DATABASE_URL).getReference("devices").child(deviceId).child("latest")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val status = try {
                    if (snapshot.exists()) {
                        snapshot.getValue(DeviceStatus::class.java)
                    } else null
                } catch (e: Exception) {
                    Log.e("GasStatsRepository", "Failed to parse DeviceStatus for $deviceId", e)
                    null
                }
                trySend(status)
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e("GasStatsRepository", "Error reading device status for $deviceId", error.toException())
                close(error.toException())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    fun getMlMetrics(): Flow<MlMetrics?> = callbackFlow {
        val ref = FirebaseDatabase.getInstance(Constants.DATABASE_URL).getReference("gasguard").child("metrics")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val metrics = try {
                    if (snapshot.exists()) {
                        snapshot.getValue(MlMetrics::class.java)
                    } else null
                } catch (e: Exception) {
                    Log.e("GasStatsRepository", "Failed to parse MlMetrics", e)
                    null
                }
                trySend(metrics)
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e("GasStatsRepository", "Error reading ML metrics", error.toException())
                close(error.toException())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    suspend fun setValveStateCommand(deviceId: String, valveState: String): Result<Unit> {
        return try {
            val ref = FirebaseDatabase.getInstance(Constants.DATABASE_URL).getReference("devices")
                .child(deviceId).child("commands").child("valveState")
            val command = mapOf(
                "valveState" to valveState,
                "requestedAt" to ServerValue.TIMESTAMP
            )
            ref.setValue(command).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("GasStatsRepository", "Failed to set valve state command for $deviceId", e)
            Result.failure(e)
        }
    }

    suspend fun setGasThresholdCommand(deviceId: String, threshold: Long): Result<Unit> {
        return try {
            val ref = FirebaseDatabase.getInstance(Constants.DATABASE_URL).getReference("devices")
                .child(deviceId).child("commands").child("gasThreshold")
            val command = mapOf(
                "gasThreshold" to threshold,
                "requestedAt" to ServerValue.TIMESTAMP
            )
            ref.setValue(command).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("GasStatsRepository", "Failed to set threshold command for $deviceId", e)
            Result.failure(e)
        }
    }
}
