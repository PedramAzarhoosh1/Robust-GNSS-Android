package com.example.iotproject.domain.fusion

import com.example.iotproject.data.model.*
import com.example.iotproject.domain.assessment.GnssIntegrityEvaluator
import com.example.iotproject.domain.fault.FaultInjectionEngine
import com.example.iotproject.domain.pdr.PdrEngine

class PositionEstimator(
    val faultInjectionEngine: FaultInjectionEngine = FaultInjectionEngine(),
    val integrityEvaluator: GnssIntegrityEvaluator = GnssIntegrityEvaluator(),
    val pdrEngine: PdrEngine = PdrEngine()
) {
    private var lastValidGnssLocation: LocationData? = null
    private var consecutiveHealthyFixes = 0
    private var isPdrPrimary = false

    companion object {
        const val RECOVERY_CONSECUTIVE_REQUIRED = 4
    }

    /**
     * Main fusion cycle called on incoming data frames.
     * Takes raw ground truth location and sensor snapshot.
     */
    fun processFrame(
        rawGroundTruthLocation: LocationData?,
        sensors: SensorSnapshot,
        gnssSummary: GnssConstellationSummary,
        currentTimeMs: Long = System.currentTimeMillis()
    ): FusionOutput {
        // 1. Process PDR IMU motion & orientation
        pdrEngine.processSensorSnapshot(sensors)

        // 2. Apply Fault Injection to simulate scenarios if active
        val simulatedGnssLocation = faultInjectionEngine.processLocation(rawGroundTruthLocation)

        // 3. Evaluate GNSS Integrity of the simulated stream
        val assessment = integrityEvaluator.evaluate(
            currentLocation = simulatedGnssLocation,
            sensorSnapshot = sensors,
            gnssSummary = gnssSummary,
            currentTimeMs = currentTimeMs
        )

        // 4. State Transition & Sensor Fusion Logic
        when (assessment.state) {
            GnssStatusState.HEALTHY -> {
                if (simulatedGnssLocation != null) {
                    if (isPdrPrimary) {
                        // In recovery phase: count consecutive healthy fixes
                        consecutiveHealthyFixes++
                        if (consecutiveHealthyFixes >= RECOVERY_CONSECUTIVE_REQUIRED) {
                            // Smoothly blend PDR coordinates to GNSS
                            pdrEngine.smoothConvergeToGnss(simulatedGnssLocation, factor = 0.5)
                            isPdrPrimary = false
                            consecutiveHealthyFixes = 0
                        } else {
                            // Still relying on PDR while checking consistency
                            pdrEngine.smoothConvergeToGnss(simulatedGnssLocation, factor = 0.15)
                        }
                    } else {
                        // Normal tracking: lock PDR anchor to GNSS
                        pdrEngine.syncWithValidGnss(simulatedGnssLocation)
                        lastValidGnssLocation = simulatedGnssLocation
                        consecutiveHealthyFixes = 0
                    }
                }
            }

            GnssStatusState.DEGRADED -> {
                if (simulatedGnssLocation != null && !pdrEngine.isReady()) {
                    pdrEngine.syncWithValidGnss(simulatedGnssLocation)
                    lastValidGnssLocation = simulatedGnssLocation
                }
            }

            GnssStatusState.SUSPICIOUS, GnssStatusState.UNAVAILABLE -> {
                isPdrPrimary = true
                consecutiveHealthyFixes = 0
            }
        }

        val pdrState = pdrEngine.getPdrState(
            groundTruth = rawGroundTruthLocation,
            isPdrActive = isPdrPrimary
        ).copy(
            consecutiveRecoveryFixes = consecutiveHealthyFixes,
            isRecovering = (isPdrPrimary && consecutiveHealthyFixes > 0)
        )

        return FusionOutput(
            groundTruthLocation = rawGroundTruthLocation,
            simulatedGnssLocation = simulatedGnssLocation,
            assessment = assessment,
            pdrState = pdrState,
            isPdrPrimary = isPdrPrimary,
            faultMode = faultInjectionEngine.faultConfig.mode
        )
    }

    fun setFaultMode(mode: FaultInjectionMode) {
        faultInjectionEngine.setMode(mode)
    }

    fun reset() {
        lastValidGnssLocation = null
        consecutiveHealthyFixes = 0
        isPdrPrimary = false
        integrityEvaluator.reset()
        pdrEngine.reset()
    }
}

data class FusionOutput(
    val groundTruthLocation: LocationData?,
    val simulatedGnssLocation: LocationData?,
    val assessment: AssessmentResult,
    val pdrState: PdrState,
    val isPdrPrimary: Boolean,
    val faultMode: FaultInjectionMode
)
