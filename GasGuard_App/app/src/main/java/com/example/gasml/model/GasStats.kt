package com.example.gasml.model

import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
data class GasStats(
    var unitId: String = "",
    var deviceId: String = "gasguard-esp32-01",
    var currentWeight: Double = 0.0,
    var gasLevel: Long = 0,
    var gasPercentage: Double = 0.0,
    var leakDetected: Boolean = false,
    var leakPercentage: Double = 0.0,
    var systemStatus: String = "NORMAL",
    var temperature: Double = 0.0,
    var timestamp: String = "",
    var valveClosed: Boolean = false,
    var dailyUsage: Double = 0.0,
    var daysRemaining: Long = 0,
    var estimatedCost: Double = 0.0,
    var capacity: String = "5kg",
    var monthlyCost: List<Any> = emptyList(),
    var history: Map<String, Any> = emptyMap()
)
