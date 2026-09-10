package com.example.iotproject

import com.example.iotproject.data.model.LocationData
import com.example.iotproject.data.model.RotationVectorData
import com.example.iotproject.data.model.SensorSnapshot
import com.example.iotproject.data.model.Vector3D
import com.example.iotproject.domain.pdr.PdrEngine
import com.example.iotproject.domain.pdr.StepDetector
import com.example.iotproject.domain.pdr.StepLengthEstimator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdrEngineTest {

    @Test
    fun testStepLengthEstimatorWeinbergBounds() {
        val estimator = StepLengthEstimator()

        val slSmall = estimator.estimateStepLength(10.5f, 9.2f)
        assertTrue(slSmall >= StepLengthEstimator.MIN_STEP_LENGTH)

        val slLarge = estimator.estimateStepLength(16.0f, 6.0f)
        assertTrue(slLarge <= StepLengthEstimator.MAX_STEP_LENGTH)
        assertTrue(slLarge > slSmall)
    }

    @Test
    fun testStepDetectorTriggersOnGaitWaveform() {
        var stepCount = 0
        val detector = StepDetector {
            stepCount++
        }

        // Baseline
        detector.processSensorSample(
            accel = Vector3D(0f, 9.81f, 0f),
            dynamicAccelMag = 0.5f,
            isDeviceStationary = false,
            timestampMs = 1000L
        )

        for (i in 0 until 5) {
            val t = 1000L + (i + 1) * 600L

            // Peak
            detector.processSensorSample(
                accel = Vector3D(0f, 13.0f, 1f),
                dynamicAccelMag = 3.5f,
                isDeviceStationary = false,
                timestampMs = t
            )
            // Valley
            detector.processSensorSample(
                accel = Vector3D(0f, 7.5f, 1f),
                dynamicAccelMag = 2.5f,
                isDeviceStationary = false,
                timestampMs = t + 200L
            )
            // Rise across baseline
            detector.processSensorSample(
                accel = Vector3D(0f, 11.5f, 1f),
                dynamicAccelMag = 2.0f,
                isDeviceStationary = false,
                timestampMs = t + 300L
            )
        }

        assertTrue("Expected at least 1 step detected from walking waveform, got $stepCount", stepCount >= 1)
    }

    @Test
    fun testStationaryZeroVelocityUpdateDoesNotTriggerSteps() {
        var stepCount = 0
        val detector = StepDetector {
            stepCount++
        }

        // Simulate stationary phone with micro-tremors (noise)
        for (i in 0 until 10) {
            val t = 1000L + i * 200L
            detector.processSensorSample(
                accel = Vector3D(0.1f, 9.85f, 0.1f),
                dynamicAccelMag = 0.1f,
                isDeviceStationary = true,
                timestampMs = t
            )
        }

        assertEquals("Stationary device must register exactly 0 steps", 0, stepCount)
    }

    @Test
    fun testPdrCoordinatePropagationNorth() {
        val pdrEngine = PdrEngine()
        val initialLat = 35.6892
        val initialLon = 51.3890

        val initialGps = LocationData(
            latitude = initialLat,
            longitude = initialLon,
            altitude = 1000.0,
            accuracy = 4.0f,
            speed = 1.0f,
            bearing = 0f,
            timestamp = 1000L,
            elapsedRealtimeNanos = 1000_000_000L,
            provider = "gps"
        )

        pdrEngine.syncWithValidGnss(initialGps)

        val sensorNorth = SensorSnapshot(
            rotation = RotationVectorData(azimuthDegrees = 0f, timestampNanos = 1000L),
            isDeviceStationary = false,
            dynamicAccelMagnitude = 2.5f,
            linearAcceleration = Vector3D(0f, 2.5f, 0f)
        )

        // Baseline
        pdrEngine.processSensorSnapshot(sensorNorth.copy(
            timestampMs = 1000L,
            accelerometer = Vector3D(0f, 9.81f, 0f)
        ))

        for (i in 1..5) {
            val t = 1000L + i * 600L
            // Crest
            pdrEngine.processSensorSnapshot(sensorNorth.copy(
                timestampMs = t,
                accelerometer = Vector3D(0f, 13.0f, 1f)
            ))
            // Trough
            pdrEngine.processSensorSnapshot(sensorNorth.copy(
                timestampMs = t + 200L,
                accelerometer = Vector3D(0f, 7.5f, 1f)
            ))
            // Rise
            pdrEngine.processSensorSnapshot(sensorNorth.copy(
                timestampMs = t + 300L,
                accelerometer = Vector3D(0f, 11.5f, 1f)
            ))
        }

        val state = pdrEngine.getPdrState(groundTruth = initialGps, isPdrActive = true)

        assertTrue("Expected steps > 0, got ${state.totalSteps}", state.totalSteps > 0)
        assertTrue("Latitude should have increased when walking North", state.estimatedLatitude > initialLat)
        assertEquals(initialLon, state.estimatedLongitude, 0.0001)
    }
}
