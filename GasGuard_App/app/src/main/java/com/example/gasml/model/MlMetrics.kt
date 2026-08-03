package com.example.gasml.model

import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
data class MlMetrics(
    val r2Score: Double = 0.0,
    val mae: Double = 0.0,
    val rmse: Double = 0.0,
    val mape: Double = 0.0,
    val datasetSize: Long = 0,
    val crossValidationMean: Double = 0.0
)
