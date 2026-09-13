package com.example.iotproject

import com.example.iotproject.data.model.FaultInjectionMode
import com.example.iotproject.data.model.LocationData
import com.example.iotproject.domain.fault.FaultInjectionEngine
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class FaultInjectionTest {

    private lateinit var faultEngine: FaultInjectionEngine
    private lateinit var sampleLocation: LocationData

    @Before
    fun setup() {
        faultEngine = FaultInjectionEngine()
        sampleLocation = LocationData(
            latitude = 35.6892,
            longitude = 51.3890,
            altitude = 1200.0,
            accuracy = 4.0f,
            speed = 1.2f,
            bearing = 45f,
            timestamp = 10000L,
            elapsedRealtimeNanos = 10_000_000_000L,
            provider = "gps"
        )
    }

    @Test
    fun testNormalModePassesThrough() {
        faultEngine.setMode(FaultInjectionMode.NORMAL)
        val result = faultEngine.processLocation(sampleLocation)
        assertNotNull(result)
        assertEquals(sampleLocation.latitude, result!!.latitude, 0.00001)
        assertEquals(sampleLocation.longitude, result.longitude, 0.00001)
    }

    @Test
    fun testOutageModeReturnsNull() {
        faultEngine.setMode(FaultInjectionMode.OUTAGE)
        val result = faultEngine.processLocation(sampleLocation)
        assertNull(result)
    }

    @Test
    fun testPositionJumpOffsetsCoordinates() {
        faultEngine.setMode(FaultInjectionMode.POSITION_JUMP)
        val result = faultEngine.processLocation(sampleLocation)
        assertNotNull(result)
        assertNotEquals(sampleLocation.latitude, result!!.latitude, 0.0001)
        assertNotEquals(sampleLocation.longitude, result.longitude, 0.0001)
    }

    @Test
    fun testFrozenLocationPreservesFirstFix() {
        faultEngine.setMode(FaultInjectionMode.FROZEN_LOCATION)
        val fix1 = faultEngine.processLocation(sampleLocation)

        val movedLocation = sampleLocation.copy(
            latitude = 35.7000,
            longitude = 51.4000,
            timestamp = 12000L
        )
        val fix2 = faultEngine.processLocation(movedLocation)

        assertNotNull(fix1)
        assertNotNull(fix2)
        assertEquals(fix1!!.latitude, fix2!!.latitude, 0.000001)
        assertEquals(fix1.longitude, fix2.longitude, 0.000001)
    }
}
