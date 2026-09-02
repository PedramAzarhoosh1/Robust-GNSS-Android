package com.example.iotproject.domain.pdr

import com.example.iotproject.data.model.SensorSnapshot

class HeadingEstimator {

    private var fusedHeadingDegrees = 0f
    private var lastTimestampNanos = 0L

    /**
     * Updates and extracts the most accurate heading from sensor fusion / rotation vector
     */
    fun updateHeading(sensorSnapshot: SensorSnapshot): Float {
        val rotation = sensorSnapshot.rotation

        // 1. Primary: Use Android's fused 6-DOF / 9-DOF Rotation Vector
        if (rotation.azimuthDegrees in 0f..360f && rotation.timestampNanos > 0L) {
            fusedHeadingDegrees = rotation.azimuthDegrees
            lastTimestampNanos = rotation.timestampNanos
            return fusedHeadingDegrees
        }

        // 2. Secondary: Integrate Gyroscope Z angular velocity
        val gyro = sensorSnapshot.gyroscope
        if (lastTimestampNanos > 0L && gyro.timestampNanos > lastTimestampNanos) {
            val dt = (gyro.timestampNanos - lastTimestampNanos) / 1_000_000_000.0f
            val deltaDeg = -Math.toDegrees(gyro.z.toDouble() * dt).toFloat()
            fusedHeadingDegrees = normalizeDegrees(fusedHeadingDegrees + deltaDeg)
        }
        lastTimestampNanos = gyro.timestampNanos

        return fusedHeadingDegrees
    }

    fun getHeadingRadians(): Double = Math.toRadians(fusedHeadingDegrees.toDouble())

    fun getHeadingDegrees(): Float = fusedHeadingDegrees

    private fun normalizeDegrees(deg: Float): Float {
        var normalized = deg % 360f
        if (normalized < 0f) normalized += 360f
        return normalized
    }

    fun reset(initialHeadingDegrees: Float = 0f) {
        fusedHeadingDegrees = initialHeadingDegrees
        lastTimestampNanos = 0L
    }
}
