package com.example.iotproject.domain.assessment

import com.example.iotproject.data.model.AssessmentResult
import com.example.iotproject.data.model.GnssConstellationSummary
import com.example.iotproject.data.model.GnssStatusState
import com.example.iotproject.data.model.LocationData
import com.example.iotproject.data.model.SensorSnapshot
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class GnssIntegrityEvaluator {

    private var previousLocation: LocationData? = null
    private var previousTimestampNanos: Long = 0L

    // Configurable thresholds
    companion object {
        const val MAX_HEALTHY_ACCURACY_METERS = 18f
        const val MAX_DEGRADED_ACCURACY_METERS = 45f
        const val MAX_PLAUSIBLE_SPEED_MPS = 55f // ~200 km/h for road/general transport
        const val MIN_SATELLITES_FOR_HEALTHY = 5
        const val TIMEOUT_DEGRADED_SECONDS = 3.5
        const val TIMEOUT_UNAVAILABLE_SECONDS = 7.0
        const val STATIONARY_SPEED_THRESHOLD_MPS = 1.8f
    }

    /**
     * Evaluates the current GNSS status against physical kinematics and sensor data.
     */
    fun evaluate(
        currentLocation: LocationData?,
        sensorSnapshot: SensorSnapshot,
        gnssSummary: GnssConstellationSummary,
        currentTimeMs: Long = System.currentTimeMillis()
    ): AssessmentResult {
        val reasons = mutableListOf<String>()

        // 1. Check if location data has never been received or is unavailable
        if (currentLocation == null) {
            return AssessmentResult(
                state = GnssStatusState.UNAVAILABLE,
                reasons = listOf("No GNSS fix received yet."),
                timeSinceLastFixSec = 999.0,
                satellitesInFix = gnssSummary.usedInFixCount,
                timestampMs = currentTimeMs
            )
        }

        // 2. Check time staleness
        val timeSinceFixSec = (currentTimeMs - currentLocation.timestamp) / 1000.0
        var isTimeout = false
        if (timeSinceFixSec > TIMEOUT_UNAVAILABLE_SECONDS) {
            isTimeout = true
            reasons.add("GNSS update timeout (> ${TIMEOUT_UNAVAILABLE_SECONDS}s). Signal lost.")
            return AssessmentResult(
                state = GnssStatusState.UNAVAILABLE,
                reasons = reasons,
                accuracyMeters = currentLocation.accuracy,
                speedMps = currentLocation.speed,
                timeSinceLastFixSec = timeSinceFixSec,
                isTimeout = true,
                satellitesInFix = gnssSummary.usedInFixCount,
                timestampMs = currentTimeMs
            )
        } else if (timeSinceFixSec > TIMEOUT_DEGRADED_SECONDS) {
            isTimeout = true
            reasons.add("GNSS updates delayed (${String.format("%.1f", timeSinceFixSec)}s since last fix).")
        }

        // 3. Accuracy Evaluation
        var isAccuracyDegraded = false
        if (currentLocation.accuracy > MAX_DEGRADED_ACCURACY_METERS) {
            isAccuracyDegraded = true
            reasons.add("Reported accuracy is very poor: ±${String.format("%.1f", currentLocation.accuracy)}m")
        } else if (currentLocation.accuracy > MAX_HEALTHY_ACCURACY_METERS) {
            isAccuracyDegraded = true
            reasons.add("Reported accuracy degraded: ±${String.format("%.1f", currentLocation.accuracy)}m")
        }

        // 4. Sudden Jump & Speed Anomaly Detection
        var jumpDistanceMeters = 0f
        var isJumpDetected = false
        var isSpeedAnomalous = false

        if (previousLocation != null && currentLocation.elapsedRealtimeNanos > previousTimestampNanos) {
            val dtSeconds = (currentLocation.elapsedRealtimeNanos - previousTimestampNanos) / 1_000_000_000.0
            if (dtSeconds > 0.05) {
                jumpDistanceMeters = computeDistanceMeters(
                    previousLocation!!.latitude, previousLocation!!.longitude,
                    currentLocation.latitude, currentLocation.longitude
                )

                val derivedSpeed = (jumpDistanceMeters / dtSeconds).toFloat()

                // Check for coordinate teleportation / sudden jumps
                // E.g. jump > 35m in 1s without high reported speed
                if (jumpDistanceMeters > 35f && derivedSpeed > MAX_PLAUSIBLE_SPEED_MPS) {
                    isJumpDetected = true
                    reasons.add("Sudden position jump detected: ${String.format("%.1f", jumpDistanceMeters)}m in ${String.format("%.1f", dtSeconds)}s (${String.format("%.1f", derivedSpeed)} m/s).")
                }
            }
        }

        if (currentLocation.speed > MAX_PLAUSIBLE_SPEED_MPS) {
            isSpeedAnomalous = true
            reasons.add("Unrealistic GPS speed reported: ${String.format("%.1f", currentLocation.speed * 3.6f)} km/h.")
        }

        // 5. Kinematic Consistency Check (GPS vs IMU)
        var isKinematicInconsistent = false

        // Case A: Device is physically motionless (IMU variance very low), but GPS reports motion
        if (sensorSnapshot.isDeviceStationary && (currentLocation.speed > STATIONARY_SPEED_THRESHOLD_MPS || (jumpDistanceMeters > 15f && isJumpDetected))) {
            isKinematicInconsistent = true
            reasons.add("Kinematic Inconsistency: GPS reports motion (${String.format("%.1f", currentLocation.speed)} m/s), but IMU shows stationary device.")
        }

        // Case B: Step sensors / dynamic IMU show high movement, but GPS speed is 0 and position is completely frozen
        if (sensorSnapshot.dynamicAccelMagnitude > 2.0f && !sensorSnapshot.isDeviceStationary && currentLocation.speed < 0.2f && timeSinceFixSec > 4.0) {
            isKinematicInconsistent = true
            reasons.add("Kinematic Inconsistency: IMU active movement detected while GNSS position is frozen.")
        }

        // 6. Satellite Constellation Checks
        val usedSats = if (gnssSummary.usedInFixCount > 0) gnssSummary.usedInFixCount else currentLocation.satellitesUsedInFix
        if (usedSats in 1 until MIN_SATELLITES_FOR_HEALTHY) {
            reasons.add("Low satellite count in fix ($usedSats satellites).")
        }

        // Determine Final State
        val finalState = when {
            isJumpDetected || isSpeedAnomalous || isKinematicInconsistent -> {
                GnssStatusState.SUSPICIOUS
            }
            isAccuracyDegraded || isTimeout || (usedSats in 1 until MIN_SATELLITES_FOR_HEALTHY) -> {
                GnssStatusState.DEGRADED
            }
            else -> {
                if (reasons.isEmpty()) {
                    reasons.add("GNSS nominal. High accuracy and consistent with IMU sensors.")
                }
                GnssStatusState.HEALTHY
            }
        }

        // Update previous reference
        previousLocation = currentLocation
        previousTimestampNanos = currentLocation.elapsedRealtimeNanos

        return AssessmentResult(
            state = finalState,
            reasons = reasons,
            accuracyMeters = currentLocation.accuracy,
            speedMps = currentLocation.speed,
            timeSinceLastFixSec = timeSinceFixSec,
            jumpDistanceMeters = jumpDistanceMeters,
            isSpeedAnomalous = isSpeedAnomalous,
            isJumpDetected = isJumpDetected,
            isKinematicInconsistent = isKinematicInconsistent,
            isTimeout = isTimeout,
            isAccuracyDegraded = isAccuracyDegraded,
            satellitesInFix = usedSats,
            timestampMs = currentTimeMs
        )
    }

    fun reset() {
        previousLocation = null
        previousTimestampNanos = 0L
    }

    private fun computeDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val earthRadius = 6371000.0 // Earth radius in meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return (earthRadius * c).toFloat()
    }
}
