package com.example.gasml.model

import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
data class DeviceStatus(
    val deviceId: String = "",
    val gasLevel: Long = 0,
    val gasThreshold: Long = 700,
    val gasWeightKg: Double = 0.0,
    val cylinderWeightKg: Double = 0.0,
    val temperatureC: Double = 0.0,
    val humidityPercent: Double = 0.0,
    val gasDetected: Boolean = false,
    val alarmActive: Boolean = false,
    val valveState: String = "OPEN",
    val uptimeSeconds: Long = 0,
    val updatedAt: Long = 0
)
