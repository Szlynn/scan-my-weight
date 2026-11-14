package io.github.szlynn.scanmyweight.data

data class WeightMeasurement(
    val weightKg: Double,
    val bodyFatPercent: Double,
    val bodyWaterPercent: Double,
    val bodyMusclePercent: Double,
)
