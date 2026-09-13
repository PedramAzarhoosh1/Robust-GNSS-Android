package com.example.iotproject.data.model

data class PdrState(
    val estimatedLatitude: Double = 0.0,
    val estimatedLongitude: Double = 0.0,
    val totalSteps: Int = 0,
    val stepLengthMeters: Float = 0.70f,
    val headingDegrees: Float = 0f,
    val totalDistanceMeters: Float = 0f,
    val estimationErrorMeters: Float = 0f,
    val isPdrActive: Boolean = false,
    val lastStepTimestampMs: Long = 0L,
    val stepFrequencyHz: Float = 0f,
    val consecutiveRecoveryFixes: Int = 0,
    val isRecovering: Boolean = false
)
