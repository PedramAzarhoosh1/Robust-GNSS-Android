package com.example.iotproject.domain.fault

import com.example.iotproject.data.model.FaultConfig
import com.example.iotproject.data.model.FaultInjectionMode
import com.example.iotproject.data.model.LocationData
import java.util.LinkedList
import kotlin.math.cos

class FaultInjectionEngine {

    var faultConfig: FaultConfig = FaultConfig()
        private set

    private var faultStartTimeMs: Long = 0L
    private var frozenLocation: LocationData? = null
    private val delayedQueue = LinkedList<Pair<Long, LocationData>>()

    fun setMode(mode: FaultInjectionMode) {
        faultConfig = faultConfig.copy(mode = mode)
        faultStartTimeMs = System.currentTimeMillis()
        frozenLocation = null
        delayedQueue.clear()
    }

    fun setConfig(config: FaultConfig) {
        this.faultConfig = config
        faultStartTimeMs = System.currentTimeMillis()
        frozenLocation = null
        delayedQueue.clear()
    }

    /**
     * Transforms ground-truth location based on the active fault simulation mode.
     * Returns null if GNSS is in OUTAGE mode.
     */
    fun processLocation(groundTruth: LocationData?): LocationData? {
        if (groundTruth == null) return null

        val now = System.currentTimeMillis()

        return when (faultConfig.mode) {
            FaultInjectionMode.NORMAL -> groundTruth

            FaultInjectionMode.OUTAGE -> null

            FaultInjectionMode.POSITION_JUMP -> {
                val metersNorth = faultConfig.jumpOffsetMeters * 0.7071
                val metersEast = faultConfig.jumpOffsetMeters * 0.7071
                val earthRadius = 6371000.0

                val deltaLat = Math.toDegrees(metersNorth / earthRadius)
                val deltaLon = Math.toDegrees(metersEast / (earthRadius * cos(Math.toRadians(groundTruth.latitude))))

                groundTruth.copy(
                    latitude = groundTruth.latitude + deltaLat,
                    longitude = groundTruth.longitude + deltaLon,
                    accuracy = 5.0f
                )
            }

            FaultInjectionMode.FROZEN_LOCATION -> {
                if (frozenLocation == null) {
                    frozenLocation = groundTruth
                }
                frozenLocation!!.copy(
                    timestamp = groundTruth.timestamp,
                    elapsedRealtimeNanos = groundTruth.elapsedRealtimeNanos,
                    speed = 0.0f
                )
            }

            FaultInjectionMode.GRADUAL_DRIFT -> {
                val elapsedSec = (now - faultStartTimeMs).coerceAtLeast(0L) / 1000.0
                val totalDriftMeters = faultConfig.driftRateMps * elapsedSec

                val earthRadius = 6371000.0
                val deltaLat = Math.toDegrees(totalDriftMeters / earthRadius)
                val deltaLon = Math.toDegrees(totalDriftMeters / (earthRadius * cos(Math.toRadians(groundTruth.latitude))))

                groundTruth.copy(
                    latitude = groundTruth.latitude + deltaLat,
                    longitude = groundTruth.longitude + deltaLon
                )
            }

            FaultInjectionMode.DELAYED_UPDATES -> {
                delayedQueue.add(Pair(now, groundTruth))
                val targetDelayMs = faultConfig.delaySeconds * 1000L

                while (delayedQueue.isNotEmpty() && (now - (delayedQueue.peek()?.first ?: 0L) >= targetDelayMs)) {
                    val candidate = delayedQueue.poll()?.second
                    if (delayedQueue.isEmpty() || (now - (delayedQueue.peek()?.first ?: 0L) < targetDelayMs)) {
                        return candidate
                    }
                }
                delayedQueue.peek()?.second
            }

            FaultInjectionMode.RECOVERY -> {
                groundTruth
            }
        }
    }
}
