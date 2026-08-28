package com.example.iotproject.data.model

data class LocationData(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val accuracy: Float,
    val speed: Float, // in m/s
    val bearing: Float, // in degrees
    val timestamp: Long,
    val elapsedRealtimeNanos: Long,
    val provider: String,
    val satellitesTotal: Int = 0,
    val satellitesUsedInFix: Int = 0,
    val hasSpeed: Boolean = false,
    val hasBearing: Boolean = false,
    val hasAltitude: Boolean = false
)
