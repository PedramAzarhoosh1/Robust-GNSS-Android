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
    val gpsTrustScore: Float = 0.0f, // Continuous trust score (0.0 to 1.0)
    val motionContext: MotionContext = MotionContext.STATIONARY,
    val spatialVarianceMeters: Float = 0f,
    val timestampMs: Long = System.currentTimeMillis()
)
