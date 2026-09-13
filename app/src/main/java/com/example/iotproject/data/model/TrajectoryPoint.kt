package com.example.iotproject.data.model

data class TrajectoryPoint(
    val latitude: Double,
    val longitude: Double,
    val timestampMs: Long = System.currentTimeMillis(),
    val isPdr: Boolean = false
)

data class RelativePoint(
    val xMeters: Float,
    val yMeters: Float
)
