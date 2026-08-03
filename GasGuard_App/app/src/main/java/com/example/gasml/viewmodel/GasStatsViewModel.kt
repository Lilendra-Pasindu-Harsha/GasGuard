package com.example.gasml.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.gasml.data.GasStatsRepository
import com.example.gasml.model.GasStats
import com.example.gasml.model.DeviceStatus
import com.example.gasml.model.MlMetrics
import com.example.gasml.util.NetworkObserver
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class GasStatsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = GasStatsRepository()
    private val networkObserver = NetworkObserver(application)

    private val _stats = MutableStateFlow<GasStats?>(null)
    val stats: StateFlow<GasStats?> = _stats.asStateFlow()

    private val _networkStatus = MutableStateFlow(NetworkObserver.Status.Unavailable)
    val networkStatus: StateFlow<NetworkObserver.Status> = _networkStatus.asStateFlow()

    private val _daysRemaining = MutableStateFlow(0.0)
    val daysRemaining = _daysRemaining.asStateFlow()

    private val _refillDate = MutableStateFlow("Calculating...")
    val refillDate = _refillDate.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _deviceStatus = MutableStateFlow<DeviceStatus?>(null)
    val deviceStatus: StateFlow<DeviceStatus?> = _deviceStatus.asStateFlow()

    private val _mlMetrics = MutableStateFlow<MlMetrics?>(null)
    val mlMetrics: StateFlow<MlMetrics?> = _mlMetrics.asStateFlow()

    private var deviceJob: Job? = null
    private var networkJob: Job? = null
    private var mlMetricsJob: Job? = null

    init {
        observeNetwork()
        observeMlMetrics()
    }

    private fun observeNetwork() {
        networkJob?.cancel()
        networkJob = viewModelScope.launch {
            networkObserver.observe.collect {
                _networkStatus.value = it
            }
        }
    }

    private fun observeMlMetrics() {
        mlMetricsJob?.cancel()
        mlMetricsJob = viewModelScope.launch {
            repository.getMlMetrics().collect {
                _mlMetrics.value = it
            }
        }
    }
    private var activeUnitId: String? = null
    private var statsJob: Job? = null

    fun loadStats(unitId: String?) {
        if (unitId == null) {
            _stats.value = getMockStats("MOCK_UNIT")
            updateDerivedStats(_stats.value!!)
            return
        }
        
        val formattedId = if (unitId.startsWith("UNIT_")) unitId else "UNIT_$unitId"
        
        // Skip re-subscription if already observing the same unit (prevents reset on navigation)
        if (activeUnitId == formattedId && statsJob?.isActive == true) {
            return
        }
        activeUnitId = formattedId
        statsJob?.cancel()
        
        statsJob = viewModelScope.launch {
            _isLoading.value = true
            repository.getUnitStats(formattedId).collectLatest { updatedStats ->
                // Clean data before emitting to UI to prevent "255%" errors
                val cleanedStats = updatedStats?.copy(
                    gasPercentage = updatedStats.gasPercentage.coerceIn(0.0, 100.0),
                    leakPercentage = updatedStats.leakPercentage.coerceIn(0.0, 100.0),
                    gasLevel = updatedStats.gasLevel.coerceIn(0L, 4095L)
                )
                
                val finalStats = cleanedStats ?: getMockStats(formattedId)
                _stats.value = finalStats
                updateDerivedStats(finalStats)
                _isLoading.value = false

                // Start observing raw device status
                val devId = finalStats.deviceId.ifBlank { "gasguard-esp32-01" }
                observeDeviceStatus(devId)
            }
        }
    }

    private var activeDeviceId: String? = null

    private fun observeDeviceStatus(deviceId: String) {
        if (activeDeviceId == deviceId && deviceJob?.isActive == true) {
            return // Already observing this device
        }
        activeDeviceId = deviceId
        deviceJob?.cancel()
        deviceJob = viewModelScope.launch {
            repository.getDeviceStatus(deviceId).collectLatest { status ->
                _deviceStatus.value = status
                // Re-run local ML prediction using the live device weight
                if (status != null) {
                    // Use cylinderWeightKg directly — this is the net weight on the
                    // scale (platform already tared out by ESP32 firmware)
                    val gasWeight = status.cylinderWeightKg
                    val clampedWeight = gasWeight.coerceIn(0.0, 5.0)
                    val dailyUsage = _stats.value?.dailyUsage ?: 0.12
                    Log.d("GasGuardML", "[DEVICE] gasWeightKg=${status.gasWeightKg}, cylinderWeightKg=${status.cylinderWeightKg}, clampedWeight=$clampedWeight, dailyUsage=$dailyUsage")
                    val localDays = com.example.gasml.ml.LocalCylinderPredictor.predictDaysRemaining(
                        weight = clampedWeight,
                        dailyUsage = dailyUsage,
                        rollingAvg = dailyUsage
                    )
                    Log.d("GasGuardML", "[DEVICE] ML prediction: $localDays days")
                    _daysRemaining.value = localDays.toDouble()
                    if (localDays > 0) {
                        val calendar = Calendar.getInstance()
                        calendar.add(Calendar.DAY_OF_YEAR, localDays)
                        val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                        _refillDate.value = sdf.format(calendar.time)
                    } else {
                        _refillDate.value = "N/A"
                    }
                }
            }
        }
    }

    fun toggleValveState(deviceId: String, targetState: String) {
        viewModelScope.launch {
            repository.setValveStateCommand(deviceId, targetState)
        }
    }

    fun updateGasThreshold(deviceId: String, threshold: Long) {
        viewModelScope.launch {
            repository.setGasThresholdCommand(deviceId, threshold)
        }
    }

    fun toggleLeakSimulation(unitId: String?, isLeak: Boolean) {
        val id = unitId ?: return
        val formattedId = if (id.startsWith("UNIT_")) id else "UNIT_$id"
        
        // Update local state first to ensure mock data & offline mode work properly
        _stats.value = _stats.value?.copy(
            leakDetected = isLeak,
            systemStatus = if (isLeak) "CRITICAL" else "NORMAL",
            leakPercentage = if (isLeak) 85.0 else 0.0,
            valveClosed = isLeak
        ) ?: getMockStats(formattedId).copy(
            leakDetected = isLeak,
            systemStatus = if (isLeak) "CRITICAL" else "NORMAL",
            leakPercentage = if (isLeak) 85.0 else 0.0,
            valveClosed = isLeak
        )
        
        // Update local device status to keep UI in sync
        _deviceStatus.value = _deviceStatus.value?.copy(
            alarmActive = isLeak,
            gasDetected = isLeak,
            valveState = if (isLeak) "CLOSED" else "OPEN",
            gasLevel = if (isLeak) 950 else 120
        )
        
        viewModelScope.launch {
            repository.simulateLeak(formattedId, isLeak)
        }
    }

    fun acknowledgeLeak(unitId: String?) {
        val id = unitId ?: return
        val formattedId = if (id.startsWith("UNIT_")) id else "UNIT_$id"
        
        _stats.value = _stats.value?.copy(
            leakDetected = false,
            systemStatus = "NORMAL",
            leakPercentage = 0.0,
            valveClosed = false
        )
        
        _deviceStatus.value = _deviceStatus.value?.copy(
            alarmActive = false,
            gasDetected = false,
            valveState = "OPEN",
            gasLevel = 120
        )
        
        viewModelScope.launch {
            repository.acknowledgeLeak(formattedId)
        }
    }

    private fun getMockStats(unitId: String): GasStats {
        return GasStats(
            unitId = unitId,
            currentWeight = 5.0,
            gasLevel = 100L,
            gasPercentage = 100.0,
            leakDetected = false,
            leakPercentage = 0.0,
            systemStatus = "NORMAL",
            temperature = 27.5,
            timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault()).format(Date()),
            valveClosed = false,
            dailyUsage = 0.12,
            daysRemaining = 41L,
            estimatedCost = 1200.0,
            capacity = "5kg"
        )
    }

    private fun updateDerivedStats(stats: GasStats) {
        Log.d("GasGuardML", "[STATS] currentWeight=${stats.currentWeight}, dailyUsage=${stats.dailyUsage}, firebase_daysRemaining=${stats.daysRemaining}")
        // Calculate the predicted days remaining locally on the device using Multiple Linear Regression!
        val localDays = com.example.gasml.ml.LocalCylinderPredictor.predictDaysRemaining(
            weight = stats.currentWeight,
            dailyUsage = stats.dailyUsage,
            rollingAvg = stats.dailyUsage
        )
        Log.d("GasGuardML", "[STATS] ML prediction: $localDays days")
        
        // Use local ML prediction, but fall back to Firebase value if local returns 0 unexpectedly
        val finalDays = if (localDays > 0) {
            localDays
        } else if (stats.daysRemaining > 0) {
            stats.daysRemaining.toInt()
        } else {
            0
        }
        
        _daysRemaining.value = finalDays.toDouble()
        Log.d("GasGuardML", "[STATS] Final days set to: $finalDays")

        if (finalDays > 0) {
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.DAY_OF_YEAR, finalDays)
            val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
            _refillDate.value = sdf.format(calendar.time)
        } else {
            _refillDate.value = "N/A"
        }
    }

    fun formatESP32Timestamp(timestamp: Any?): String {
        return when (timestamp) {
            is String -> {
                try {
                    val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
                    inputFormat.timeZone = TimeZone.getTimeZone("UTC")
                    val date = inputFormat.parse(timestamp)
                    val outputFormat = SimpleDateFormat("h:mm:ss a", Locale.getDefault())
                    date?.let { outputFormat.format(it) } ?: "Just now"
                } catch (e: Exception) {
                    "Just now"
                }
            }
            else -> "Just now"
        }
    }
    
    fun getCurrentTime(): String {
        val sdf = SimpleDateFormat("EEEE, d MMM", Locale.getDefault())
        return sdf.format(Date())
    }
}
