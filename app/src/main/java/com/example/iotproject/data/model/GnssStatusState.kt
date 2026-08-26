package com.example.iotproject.data.model

import androidx.compose.ui.graphics.Color

/**
 * The 4 operational/integrity states defined in the project specification:
 * - HEALTHY: GNSS is reliable, high accuracy, kinematics match IMU sensors.
 * - DEGRADED: Reduced accuracy, weak satellite geometry, or partial signal issues.
 * - SUSPICIOUS: Anomaly detected (sudden position jump, unrealistic speed, or GPS movement when IMU is stationary).
 * - UNAVAILABLE: No GNSS signal, timeout, or location service disabled.
 */
enum class GnssStatusState(
    val displayName: String,
    val description: String,
    val color: Color
) {
    HEALTHY(
        displayName = "Healthy",
        description = "GNSS signal is valid, reliable and consistent with motion sensors.",
        color = Color(0xFF2E7D32) // Forest Green
    ),
    DEGRADED(
        displayName = "Degraded",
        description = "GNSS quality reduced (high DOP, low accuracy, or low satellite count).",
        color = Color(0xFFF57F17) // Amber / Orange
    ),
    SUSPICIOUS(
        displayName = "Suspicious",
        description = "Anomaly detected! Coordinate jump, unrealistic speed, or GPS-IMU mismatch.",
        color = Color(0xFFC2185B) // Crimson / Magenta
    ),
    UNAVAILABLE(
        displayName = "Unavailable",
        description = "No GNSS updates received or signal completely lost.",
        color = Color(0xFFD32F2F) // Red
    )
}
