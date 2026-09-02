package com.example.iotproject.domain.assessment

import com.example.iotproject.data.model.*
import java.util.LinkedList
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

class GnssIntegrityEvaluator {

    // Sliding Window of recent GPS fixes (N = 6)
    private val locationHistoryWindow = LinkedList<LocationData>()
    private var previousTimestampNanos: Long = 0L

    companion object {
        const val WINDOW_MAX_SIZE = 6
        const val MAX_HEALTHY_ACCURACY_METERS = 18f
        const val MAX_DEGRADED_ACCURACY_METERS = 45f
        const val MAX_PLAUSIBLE_SPEED_MPS = 55f // ~200 km/h for road transport
        const val MIN_SATELLITES_FOR_HEALTHY = 5
        const val TIMEOUT_DEGRADED_SECONDS = 3.5
        const val TIMEOUT_UNAVAILABLE_SECONDS = 7.0
        const val STATIONARY_SPEED_THRESHOLD_MPS = 1.8f
        const val VEHICLE_SPEED_THRESHOLD_MPS = 4.5f // ~16 km/h
    }

    /**
     * Evaluates GNSS status, calculates continuous trust score (0.0 to 1.0),
     * and performs time-series sliding window + multi-modal motion context classification.
     */
    fun evaluate(
        currentLocation: LocationData?,
        sensorSnapshot: SensorSnapshot,
        gnssSummary: GnssConstellationSummary,
        currentTimeMs: Long = System.currentTimeMillis()
    ): AssessmentResult {
        val reasons = mutableListOf<String>()

        // 1. Determine Motion Context (Stationary vs Pedestrian vs Vehicle)
        val motionContext = classifyMotionContext(sensorSnapshot, currentLocation)

        // 2. Handle Zero-Signal / Indoor Outage Gracefully
        if (currentLocation == null) {
            locationHistoryWindow.clear()
            return AssessmentResult(
                state = GnssStatusState.UNAVAILABLE,
                reasons = listOf("No GNSS signal (Indoor / Tunnel). PDR autonomous navigation active."),
                timeSinceLastFixSec = 999.0,
                satellitesInFix = gnssSummary.usedInFixCount,
                gpsTrustScore = 0.0f,
                motionContext = motionContext,
                spatialVarianceMeters = 0f,
                timestampMs = currentTimeMs
            )
        }

        // 3. Time Staleness Check
        val timeSinceFixSec = (currentTimeMs - currentLocation.timestamp) / 1000.0
        var isTimeout = false
        if (timeSinceFixSec > TIMEOUT_UNAVAILABLE_SECONDS) {
            isTimeout = true
            reasons.add("GNSS update timeout (> ${TIMEOUT_UNAVAILABLE_SECONDS}s). Signal lost.")
            locationHistoryWindow.clear()
            return AssessmentResult(
                state = GnssStatusState.UNAVAILABLE,
                reasons = reasons,
                accuracyMeters = currentLocation.accuracy,
                speedMps = currentLocation.speed,
                timeSinceLastFixSec = timeSinceFixSec,
                isTimeout = true,
                satellitesInFix = gnssSummary.usedInFixCount,
                gpsTrustScore = 0.0f,
                motionContext = motionContext,
                spatialVarianceMeters = 0f,
                timestampMs = currentTimeMs
            )
        } else if (timeSinceFixSec > TIMEOUT_DEGRADED_SECONDS) {
            isTimeout = true
            reasons.add("GNSS updates delayed (${String.format("%.1f", timeSinceFixSec)}s since last fix).")
        }

        // 4. Update Sliding Window & Compute Time-Series Variance
        locationHistoryWindow.add(currentLocation)
        if (locationHistoryWindow.size > WINDOW_MAX_SIZE) {
            locationHistoryWindow.removeFirst()
        }
        val windowMetrics = computeWindowMetrics(locationHistoryWindow)

        // 5. Accuracy Evaluation
        var isAccuracyDegraded = false
        if (currentLocation.accuracy > MAX_DEGRADED_ACCURACY_METERS) {
            isAccuracyDegraded = true
            reasons.add("Reported accuracy is very poor: ±${String.format("%.1f", currentLocation.accuracy)}m")
        } else if (currentLocation.accuracy > MAX_HEALTHY_ACCURACY_METERS) {
            isAccuracyDegraded = true
            reasons.add("Reported accuracy degraded: ±${String.format("%.1f", currentLocation.accuracy)}m")
        }

        // 6. Sudden Jump & Speed Anomaly Detection
        var jumpDistanceMeters = 0f
        var isJumpDetected = false
        var isSpeedAnomalous = false

        val prevLoc = if (locationHistoryWindow.size >= 2) locationHistoryWindow[locationHistoryWindow.size - 2] else null
        if (prevLoc != null && currentLocation.elapsedRealtimeNanos > previousTimestampNanos) {
            val dtSeconds = (currentLocation.elapsedRealtimeNanos - previousTimestampNanos) / 1_000_000_000.0
            if (dtSeconds > 0.05) {
                jumpDistanceMeters = computeDistanceMeters(
                    prevLoc.latitude, prevLoc.longitude,
                    currentLocation.latitude, currentLocation.longitude
                )
                val derivedSpeed = (jumpDistanceMeters / dtSeconds).toFloat()

                // Check coordinate teleportation / sudden jumps
                if (jumpDistanceMeters > 35f && derivedSpeed > MAX_PLAUSIBLE_SPEED_MPS) {
                    isJumpDetected = true
                    reasons.add("Sudden position jump detected: ${String.format("%.1f", jumpDistanceMeters)}m in ${String.format("%.1f", dtSeconds)}s.")
                }
            }
        }

        if (currentLocation.speed > MAX_PLAUSIBLE_SPEED_MPS) {
            isSpeedAnomalous = true
            reasons.add("Unrealistic GPS speed reported: ${String.format("%.1f", currentLocation.speed * 3.6f)} km/h.")
        }

        // 7. Context-Aware Kinematic Consistency Checks
        var isKinematicInconsistent = false

        when (motionContext) {
            MotionContext.STATIONARY -> {
                // Device is stationary on a table or in hand, but GPS reports motion
                if (currentLocation.speed > STATIONARY_SPEED_THRESHOLD_MPS || (jumpDistanceMeters > 15f && isJumpDetected)) {
                    isKinematicInconsistent = true
                    reasons.add("Kinematic Inconsistency: GPS reports motion (${String.format("%.1f", currentLocation.speed)} m/s), but device is stationary.")
                }
            }
            MotionContext.PEDESTRIAN_WALK -> {
                // User is walking with steps, but GPS is frozen
                if (currentLocation.speed < 0.2f && timeSinceFixSec > 4.0 && sensorSnapshot.dynamicAccelMagnitude > 1.8f) {
                    isKinematicInconsistent = true
                    reasons.add("Kinematic Inconsistency: Walking strides detected while GNSS position is frozen.")
                }
            }
            MotionContext.VEHICLE_TRANSIT -> {
                // In a vehicle, GPS speed is high and IMU confirms vehicle surge/turn dynamics
                // High speed is expected and valid!
            }
        }

        // 8. Satellite Constellation Checks
        val usedSats = if (gnssSummary.usedInFixCount > 0) gnssSummary.usedInFixCount else currentLocation.satellitesUsedInFix
        if (usedSats in 1 until MIN_SATELLITES_FOR_HEALTHY) {
            reasons.add("Low satellite count in fix ($usedSats satellites).")
        }

        // 9. Time-Series Stability Penalty
        if (windowMetrics.spatialVarianceMeters > 25f && !isJumpDetected && motionContext == MotionContext.STATIONARY) {
            reasons.add("High spatial jitter detected over sliding window (variance: ${String.format("%.1f", windowMetrics.spatialVarianceMeters)}m).")
        }

        // 10. Calculate Continuous Trust Score (W_gps in [0.0, 1.0])
        val trustScore = calculateTrustScore(
            accuracy = currentLocation.accuracy,
            satellitesUsed = usedSats,
            avgCn0 = gnssSummary.avgCn0,
            isJump = isJumpDetected,
            isKinematicInconsistent = isKinematicInconsistent,
            isTimeout = isTimeout,
            spatialVariance = windowMetrics.spatialVarianceMeters
        )

        // Determine Final State
        val finalState = when {
            isJumpDetected || isSpeedAnomalous || isKinematicInconsistent || trustScore < 0.25f -> {
                GnssStatusState.SUSPICIOUS
            }
            isAccuracyDegraded || isTimeout || (usedSats in 1 until MIN_SATELLITES_FOR_HEALTHY) || trustScore < 0.70f -> {
                GnssStatusState.DEGRADED
            }
            else -> {
                if (reasons.isEmpty()) {
                    reasons.add("GNSS nominal. High accuracy and consistent with $motionContext.")
                }
                GnssStatusState.HEALTHY
            }
        }

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
            gpsTrustScore = trustScore,
            motionContext = motionContext,
            spatialVarianceMeters = windowMetrics.spatialVarianceMeters,
            timestampMs = currentTimeMs
        )
    }

    /**
     * Multi-modal classifier: Distinguishes between Stationary, Pedestrian Walking, and Vehicle Transit.
     */
    private fun classifyMotionContext(
        sensors: SensorSnapshot,
        location: LocationData?
    ): MotionContext {
        // 1. If device is explicitly stationary (e.g. resting on a desk with near-zero acceleration and gyro)
        if (sensors.isDeviceStationary) {
            return MotionContext.STATIONARY
        }

        // 2. Check for sustained vehicle transit
        val gpsSpeed = location?.speed ?: 0f
        if (gpsSpeed > VEHICLE_SPEED_THRESHOLD_MPS && sensors.dynamicAccelMagnitude < 2.5f) {
            return MotionContext.VEHICLE_TRANSIT
        }

        // 3. Check for pedestrian walking cadence & dynamic step variance
        if (sensors.dynamicAccelMagnitude >= 0.7f || sensors.steps.totalSteps > 0) {
            return MotionContext.PEDESTRIAN_WALK
        }

        // 4. Otherwise stationary
        return MotionContext.STATIONARY
    }

    /**
     * Computes time-series spatial variance and maximum displacement across the sliding window.
     */
    private fun computeWindowMetrics(window: List<LocationData>): TimeSeriesWindowMetrics {
        if (window.size < 2) {
            return TimeSeriesWindowMetrics(windowSize = window.size)
        }

        val avgLat = window.map { it.latitude }.average()
        val avgLon = window.map { it.longitude }.average()

        var sumSqDist = 0.0
        var maxDisp = 0f

        for (loc in window) {
            val dist = computeDistanceMeters(avgLat, avgLon, loc.latitude, loc.longitude)
            sumSqDist += (dist * dist)
            maxDisp = max(maxDisp, dist)
        }

        val variance = (sumSqDist / window.size).toFloat()

        return TimeSeriesWindowMetrics(
            windowSize = window.size,
            spatialVarianceMeters = variance,
            maxDisplacementMeters = maxDisp,
            isConsistentOverTime = variance < 30f
        )
    }

    /**
     * Computes continuous trust score W_gps in [0.0, 1.0].
     */
    private fun calculateTrustScore(
        accuracy: Float,
        satellitesUsed: Int,
        avgCn0: Float,
        isJump: Boolean,
        isKinematicInconsistent: Boolean,
        isTimeout: Boolean,
        spatialVariance: Float
    ): Float {
        if (isJump || isKinematicInconsistent) return 0.05f
        if (isTimeout) return 0.20f

        var score = 1.0f

        // Accuracy penalty
        val accPenalty = ((accuracy - 5f) / 45f).coerceIn(0f, 0.45f)
        score -= accPenalty

        // Satellite count factor
        if (satellitesUsed < MIN_SATELLITES_FOR_HEALTHY) {
            score -= 0.25f
        }

        // Signal strength factor
        if (avgCn0 in 1f..25f) {
            score -= 0.20f
        }

        // Spatial jitter penalty
        if (spatialVariance > 20f) {
            score -= 0.15f
        }

        return score.coerceIn(0.0f, 1.0f)
    }

    fun reset() {
        locationHistoryWindow.clear()
        previousTimestampNanos = 0L
    }

    private fun computeDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val earthRadius = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return (earthRadius * c).toFloat()
    }
}
