package com.example.iotproject.data.model

import kotlin.math.sqrt

data class Vector3D(
    val x: Float = 0f,
    val y: Float = 0f,
    val z: Float = 0f,
    val timestampNanos: Long = 0L
) {
    val magnitude: Float
        get() = sqrt(x * x + y * y + z * z)
}

data class RotationVectorData(
    val x: Float = 0f,
    val y: Float = 0f,
    val z: Float = 0f,
    val scalar: Float = 0f,
    val azimuthDegrees: Float = 0f,
    val pitchDegrees: Float = 0f,
    val rollDegrees: Float = 0f,
    val timestampNanos: Long = 0L
)

data class StepSensorData(
    val totalSteps: Long = 0L,
    val recentStepsDetected: Int = 0,
    val lastStepTimestampNanos: Long = 0L
)

data class SensorSnapshot(
    val timestampMs: Long = System.currentTimeMillis(),
    val accelerometer: Vector3D = Vector3D(),
    val linearAcceleration: Vector3D = Vector3D(),
    val gyroscope: Vector3D = Vector3D(),
    val magnetometer: Vector3D = Vector3D(),
    val rotation: RotationVectorData = RotationVectorData(),
    val steps: StepSensorData = StepSensorData(),
    val dynamicAccelMagnitude: Float = 0f,
    val isDeviceStationary: Boolean = true
)
