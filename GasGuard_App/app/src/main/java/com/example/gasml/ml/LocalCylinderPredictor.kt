package com.example.gasml.ml

import java.util.Calendar

object LocalCylinderPredictor {
    // Model regression coefficients and intercept
    private const val INTERCEPT = 29.96527777777778
    private val COEFFICIENTS = doubleArrayOf(
        8.394085300553089,   // gas_weight_kg
        8.39408530055309,    // gas_percentage
        -0.7895159092784456, // daily_usage_kg
        0.08851858820478908, // day_of_week
        0.17551420026023434, // weekend
        0.15826249334818598, // consumption_rate
        -0.9659423255869282  // rolling_avg_7
    )

    // Standard scaler means
    private val MEANS = doubleArrayOf(
        2.596889330992135,
        51.93778661984269,
        0.08326608823971829,
        3.0208333333333335,
        0.2916666666666667,
        0.0031659200310085087,
        0.08311064751833207
    )

    // Standard scaler standard deviations (scales)
    private val SCALES = doubleArrayOf(
        1.4293800645613115,
        28.587601291226235,
        0.011996079985192252,
        2.0257671729221225,
        0.45452967144315476,
        0.00461263663278823,
        0.0014986029046067171
    )

    /**
     * Runs local linear regression inference to predict remaining days.
     * @param weight Remaining gas weight in kg (0.0 to 5.0)
     * @param dailyUsage Average daily usage in kg (fallback/current usage rate)
     * @param rollingAvg 7-day rolling average usage (defaults to dailyUsage if not tracked)
     */
    fun predictDaysRemaining(
        weight: Double,
        dailyUsage: Double,
        rollingAvg: Double = dailyUsage
    ): Int {
        // Clamp input weight to safe ranges
        val currentWeight = weight.coerceIn(0.0, 5.0)
        
        // If cylinder is empty, return 0 days remaining immediately
        if (currentWeight <= 0.1) return 0
        
        val gasPct = (currentWeight / 5.0) * 100.0
        
        // Date features
        val calendar = Calendar.getInstance()
        // Calendar.DAY_OF_WEEK: Sunday=1, Monday=2, ..., Saturday=7
        // Map to Python style: Mon=0, Tue=1, ..., Sun=6
        val dayOfWeek = (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7
        val weekend = if (dayOfWeek >= 5) 1.0 else 0.0
        
        val consumptionRate = if (gasPct > 0) dailyUsage / gasPct else 0.0

        // Feature vector matching the trained model features
        val features = doubleArrayOf(
            currentWeight,
            gasPct,
            dailyUsage,
            dayOfWeek.toDouble(),
            weekend,
            consumptionRate,
            rollingAvg
        )

        // Scale features and calculate prediction: y = intercept + sum(coef[i] * scaled_feature[i])
        // Clamp scaled values to ±3 std devs to prevent extrapolation beyond training distribution
        var daysPrediction = INTERCEPT
        for (i in features.indices) {
            val scaledVal = ((features[i] - MEANS[i]) / SCALES[i]).coerceIn(-3.0, 3.0)
            daysPrediction += COEFFICIENTS[i] * scaledVal
        }

        // Clamp prediction to a realistic range (0 to 90 days)
        return daysPrediction.roundToInt().coerceIn(0, 90)
    }

    private fun Double.roundToInt(): Int {
        return Math.round(this).toInt()
    }
}
