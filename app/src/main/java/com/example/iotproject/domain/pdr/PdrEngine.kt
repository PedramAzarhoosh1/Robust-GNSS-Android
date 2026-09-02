package com.example.iotproject.domain.pdr

import com.example.iotproject.data.model.LocationData
import com.example.iotproject.data.model.PdrState
import com.example.iotproject.data.model.SensorSnapshot
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class PdrEngine {

    val stepLengthEstimator = StepLengthEstimator()
    val headingEstimator = HeadingEstimator()

    private var estimatedLat: Double = 0.0
    private var estimatedLon: Double = 0.0
    private var totalSteps: Int = 0
    private var totalDistanceMeters: Float = 0f
    private var lastStepTimestampMs: Long = 0L
    private var currentStepLength: Float = 0.70f
    private var stepFrequencyHz: Float = 0f
    private var isInitialized = false

    private val stepDetector = StepDetector { stepEvent ->
        onStep(stepEvent)
    }

    private fun onStep(stepEvent: StepEvent) {
        if (!isInitialized) return

        totalSteps++
        currentStepLength = stepLengthEstimator.estimateStepLength(stepEvent.aMax, stepEvent.aMin)
        totalDistanceMeters += currentStepLength

        val dt = (stepEvent.timestampMs - lastStepTimestampMs).coerceAtLeast(100L)
        lastStepTimestampMs = stepEvent.timestampMs
        stepFrequencyHz = if (dt in 200..2500) (1000f / dt) else 0f

        // Geodesic coordinate propagation
        val headingRad = headingEstimator.getHeadingRadians()
        val deltaNorth = currentStepLength * cos(headingRad)
        val deltaEast = currentStepLength * sin(headingRad)

        val earthRadius = 6371000.0 // meters
        val deltaLatDeg = Math.toDegrees(deltaNorth / earthRadius)
        val deltaLonDeg = Math.toDegrees(deltaEast / (earthRadius * cos(Math.toRadians(estimatedLat))))

        estimatedLat += deltaLatDeg
        estimatedLon += deltaLonDeg
    }

    fun processSensorSnapshot(sensors: SensorSnapshot) {
        headingEstimator.updateHeading(sensors)
        stepDetector.processSensorSample(sensors.accelerometer, sensors.timestampMs)
    }

    fun syncWithValidGnss(gnssLocation: LocationData) {
        estimatedLat = gnssLocation.latitude
        estimatedLon = gnssLocation.longitude
        isInitialized = true
    }

    fun smoothConvergeToGnss(gnssLocation: LocationData, factor: Double = 0.3) {
        if (!isInitialized) {
            syncWithValidGnss(gnssLocation)
            return
        }
        estimatedLat = (1.0 - factor) * estimatedLat + factor * gnssLocation.latitude
        estimatedLon = (1.0 - factor) * estimatedLon + factor * gnssLocation.longitude
    }

    fun getPdrState(groundTruth: LocationData?, isPdrActive: Boolean): PdrState {
        val errorMeters = if (groundTruth != null && isInitialized && estimatedLat != 0.0) {
            computeDistanceMeters(groundTruth.latitude, groundTruth.longitude, estimatedLat, estimatedLon)
        } else 0f

        return PdrState(
            estimatedLatitude = estimatedLat,
            estimatedLongitude = estimatedLon,
            totalSteps = totalSteps,
            stepLengthMeters = currentStepLength,
            headingDegrees = headingEstimator.getHeadingDegrees(),
            totalDistanceMeters = totalDistanceMeters,
            estimationErrorMeters = errorMeters,
            isPdrActive = isPdrActive,
            lastStepTimestampMs = lastStepTimestampMs,
            stepFrequencyHz = stepFrequencyHz
        )
    }

    fun reset(initialLat: Double = 0.0, initialLon: Double = 0.0) {
        estimatedLat = initialLat
        estimatedLon = initialLon
        totalSteps = 0
        totalDistanceMeters = 0f
        lastStepTimestampMs = 0L
        stepFrequencyHz = 0f
        isInitialized = (initialLat != 0.0 && initialLon != 0.0)
        stepDetector.reset()
        headingEstimator.reset()
    }

    fun isReady(): Boolean = isInitialized

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
