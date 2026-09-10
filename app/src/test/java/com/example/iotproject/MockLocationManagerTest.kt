package com.example.iotproject

import com.example.iotproject.data.model.MotionContext
import com.example.iotproject.data.model.TimeSeriesWindowMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MockLocationManagerTest {

    @Test
    fun testMotionContextDefinitions() {
        assertEquals("Stationary", MotionContext.STATIONARY.displayName)
        assertEquals("Pedestrian Walking", MotionContext.PEDESTRIAN_WALK.displayName)
        assertEquals("In-Vehicle Transit", MotionContext.VEHICLE_TRANSIT.displayName)
    }

    @Test
    fun testTimeSeriesWindowMetricsDefault() {
        val metrics = TimeSeriesWindowMetrics(
            windowSize = 6,
            spatialVarianceMeters = 4.5f,
            maxDisplacementMeters = 12.0f,
            isConsistentOverTime = true
        )

        assertEquals(6, metrics.windowSize)
        assertEquals(4.5f, metrics.spatialVarianceMeters, 0.001f)
        assertTrue(metrics.isConsistentOverTime)
    }
}
