package com.example.iotproject.data.model

enum class MotionContext(
    val displayName: String,
    val description: String
) {
    STATIONARY(
        displayName = "Stationary",
        description = "Device is motionless. Coordinates held with Zero-Velocity Update (ZUPT)."
    ),
    PEDESTRIAN_WALK(
        displayName = "Pedestrian Walking",
        description = "Gait cycle detected (1-3 Hz). Weinberg PDR Dead Reckoning active."
    ),
    VEHICLE_TRANSIT(
        displayName = "In-Vehicle Transit",
        description = "Sustained high-speed forward travel detected without human stepping."
    )
}

data class TimeSeriesWindowMetrics(
    val windowSize: Int = 0,
    val spatialVarianceMeters: Float = 0f,
    val maxDisplacementMeters: Float = 0f,
    val speedDerivativeMps2: Float = 0f,
    val isConsistentOverTime: Boolean = true
)
