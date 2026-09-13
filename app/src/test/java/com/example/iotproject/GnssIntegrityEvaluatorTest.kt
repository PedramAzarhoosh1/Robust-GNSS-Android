package com.example.iotproject

import com.example.iotproject.data.model.GnssConstellationSummary
import com.example.iotproject.data.model.GnssStatusState
import com.example.iotproject.data.model.LocationData
import com.example.iotproject.data.model.MotionContext
import com.example.iotproject.data.model.SensorSnapshot
import com.example.iotproject.data.model.Vector3D
import com.example.iotproject.domain.assessment.GnssIntegrityEvaluator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GnssIntegrityEvaluatorTest {

    private lateinit var evaluator: GnssIntegrityEvaluator

    @Before
    fun setup() {
        evaluator = GnssIntegrityEvaluator()
    }

    @Test
    fun testNullLocationReturnsUnavailableWithZeroTrust() {
        val result = evaluator.evaluate(
            currentLocation = null,
            sensorSnapshot = SensorSnapshot(),
            gnssSummary = GnssConstellationSummary(),
            currentTimeMs = 10000L
        )

        assertEquals(GnssStatusState.UNAVAILABLE, result.state)
        assertEquals(0.0f, result.gpsTrustScore, 0.001f)
    }

    @Test
    fun testNominalGpsReturnsHealthyAndHighTrust() {
        val now = 10000L
        val loc = LocationData(
            latitude = 35.6892,
            longitude = 51.3890,
            altitude = 1200.0,
            accuracy = 4.5f,
            speed = 1.2f,
            bearing = 45f,
            timestamp = now,
            elapsedRealtimeNanos = 10_000_000_000L,
            provider = "gps",
            satellitesUsedInFix = 12
        )
        val sensorSnapshot = SensorSnapshot(
            isDeviceStationary = false,
            dynamicAccelMagnitude = 0.8f
        )
        val gnssSummary = GnssConstellationSummary(
            totalSatellites = 16,
            usedInFixCount = 12,
            gpsCount = 6,
            glonassCount = 4,
            galileoCount = 2,
            avgCn0 = 36.0f
        )

        val result = evaluator.evaluate(loc, sensorSnapshot, gnssSummary, currentTimeMs = now)

        assertEquals(GnssStatusState.HEALTHY, result.state)
        assertTrue("Expected trust score >= 0.85, got ${result.gpsTrustScore}", result.gpsTrustScore >= 0.85f)
        assertEquals(MotionContext.PEDESTRIAN_WALK, result.motionContext)
    }

    @Test
    fun testPoorAccuracyReturnsDegraded() {
        val now = 10000L
        val loc = LocationData(
            latitude = 35.6892,
            longitude = 51.3890,
            altitude = 1200.0,
            accuracy = 28.0f, // Degraded threshold (>18m)
            speed = 1.0f,
            bearing = 0f,
            timestamp = now,
            elapsedRealtimeNanos = 10_000_000_000L,
            provider = "gps",
            satellitesUsedInFix = 6
        )

        val result = evaluator.evaluate(
            loc,
            SensorSnapshot(isDeviceStationary = false, dynamicAccelMagnitude = 1.0f),
            GnssConstellationSummary(usedInFixCount = 6),
            currentTimeMs = now
        )

        assertEquals(GnssStatusState.DEGRADED, result.state)
        assertTrue(result.isAccuracyDegraded)
    }

    @Test
    fun testKinematicInconsistencyStationarySensorMovingGpsReturnsSuspicious() {
        val now = 10000L
        val loc = LocationData(
            latitude = 35.6892,
            longitude = 51.3890,
            altitude = 1200.0,
            accuracy = 5.0f,
            speed = 8.5f, // High speed reported by GPS
            bearing = 90f,
            timestamp = now,
            elapsedRealtimeNanos = 10_000_000_000L,
            provider = "gps",
            satellitesUsedInFix = 8
        )
        // Sensor says phone is stationary on a desk
        val sensorSnapshot = SensorSnapshot(
            isDeviceStationary = true,
            dynamicAccelMagnitude = 0.02f,
            gyroscope = Vector3D(0f, 0f, 0f)
        )

        val result = evaluator.evaluate(
            loc,
            sensorSnapshot,
            GnssConstellationSummary(usedInFixCount = 8),
            currentTimeMs = now
        )

        assertEquals(GnssStatusState.SUSPICIOUS, result.state)
        assertTrue(result.isKinematicInconsistent)
        assertTrue(result.gpsTrustScore < 0.20f)
    }

    @Test
    fun testVehicleMotionContextRecognized() {
        val now = 10000L
        val carLoc = LocationData(
            latitude = 35.6892,
            longitude = 51.3890,
            altitude = 1200.0,
            accuracy = 4.0f,
            speed = 15.0f, // ~54 km/h driving
            bearing = 90f,
            timestamp = now,
            elapsedRealtimeNanos = 10_000_000_000L,
            provider = "gps",
            satellitesUsedInFix = 10
        )
        val carSensor = SensorSnapshot(
            isDeviceStationary = false,
            dynamicAccelMagnitude = 0.4f // low vertical bounce inside car
        )

        val result = evaluator.evaluate(carLoc, carSensor, GnssConstellationSummary(usedInFixCount = 10), now)

        assertEquals(MotionContext.VEHICLE_TRANSIT, result.motionContext)
        assertEquals(GnssStatusState.HEALTHY, result.state)
    }

    @Test
    fun testSuddenPositionJumpReturnsSuspicious() {
        val t1 = 10000L
        val loc1 = LocationData(
            latitude = 35.6892,
            longitude = 51.3890,
            altitude = 1200.0,
            accuracy = 5.0f,
            speed = 1.0f,
            bearing = 0f,
            timestamp = t1,
            elapsedRealtimeNanos = 10_000_000_000L,
            provider = "gps"
        )
        evaluator.evaluate(loc1, SensorSnapshot(dynamicAccelMagnitude = 0.8f), GnssConstellationSummary(usedInFixCount = 8), t1)

        // 1 second later, position teleports ~300 meters away
        val t2 = 11000L
        val loc2 = LocationData(
            latitude = 35.6920, // ~310 meters north
            longitude = 51.3890,
            altitude = 1200.0,
            accuracy = 5.0f,
            speed = 1.0f,
            bearing = 0f,
            timestamp = t2,
            elapsedRealtimeNanos = 11_000_000_000L,
            provider = "gps"
        )

        val result2 = evaluator.evaluate(loc2, SensorSnapshot(dynamicAccelMagnitude = 0.8f), GnssConstellationSummary(usedInFixCount = 8), t2)

        assertEquals(GnssStatusState.SUSPICIOUS, result2.state)
        assertTrue(result2.isJumpDetected)
    }

    @Test
    fun testTimeoutLeadsToUnavailable() {
        val fixTime = 10000L
        val loc = LocationData(
            latitude = 35.6892,
            longitude = 51.3890,
            altitude = 1200.0,
            accuracy = 5.0f,
            speed = 0f,
            bearing = 0f,
            timestamp = fixTime,
            elapsedRealtimeNanos = 10_000_000_000L,
            provider = "gps"
        )

        // 8 seconds later with no new fix
        val checkTime = fixTime + 8000L
        val result = evaluator.evaluate(loc, SensorSnapshot(), GnssConstellationSummary(usedInFixCount = 8), checkTime)

        assertEquals(GnssStatusState.UNAVAILABLE, result.state)
        assertTrue(result.isTimeout)
        assertEquals(0.0f, result.gpsTrustScore, 0.001f)
    }
}
