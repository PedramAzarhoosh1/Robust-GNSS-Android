package com.example.iotproject.data.model

data class AssessmentResult(
    val state: GnssStatusState = GnssStatusState.UNAVAILABLE,
    val reasons: List<String> = listOf("Awaiting initial GNSS fix..."),
    val accuracyMeters: Float = 0f,
    val speedMps: Float = 0f,
    val timeSinceLastFixSec: Double = 0.0,
    val jumpDistanceMeters: Float = 0f,
    val isSpeedAnomalous: Boolean = false,
    val isJumpDetected: Boolean = false,
    val isKinematicInconsistent: Boolean = false,
    val isTimeout: Boolean = false,
    val isAccuracyDegraded: Boolean = false,
    val satellitesInFix: Int = 0,
    val timestampMs: Long = System.currentTimeMillis()
)
