package com.example.iotproject.data.model

data class SatelliteInfo(
    val svid: Int,
    val constellationType: String,
    val cn0DbHz: Float,
    val elevationDegrees: Float,
    val azimuthDegrees: Float,
    val usedInFix: Boolean,
    val hasEphemeris: Boolean,
    val hasAlmanac: Boolean
)

data class GnssConstellationSummary(
    val totalSatellites: Int = 0,
    val usedInFixCount: Int = 0,
    val gpsCount: Int = 0,
    val glonassCount: Int = 0,
    val galileoCount: Int = 0,
    val beidouCount: Int = 0,
    val otherCount: Int = 0,
    val avgCn0: Float = 0f,
    val satellites: List<SatelliteInfo> = emptyList()
)
