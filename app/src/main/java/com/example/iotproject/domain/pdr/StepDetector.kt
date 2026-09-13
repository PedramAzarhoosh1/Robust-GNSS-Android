package com.example.iotproject.domain.pdr

import com.example.iotproject.data.model.Vector3D

data class StepEvent(
    val timestampMs: Long,
    val aMax: Float,
    val aMin: Float,
    val stepDurationMs: Long
)

/**
 * Robust Zero-Velocity Update (ZUPT) aware Step Detector.
 * Filters out stationary micro-vibrations, phone handling tremors, and non-walking noise.
 */
class StepDetector(
    private val onStepDetected: (StepEvent) -> Unit
) {
    private var prevMagnitude = BASELINE_GRAVITY
    private var currentPeak = BASELINE_GRAVITY
    private var currentValley = BASELINE_GRAVITY
    private var lastStepTimeMs = 0L
    private var isInitialized = false
    private var consecutiveSteps = 0

    companion object {
        const val BASELINE_GRAVITY = 9.81f
        // Minimum interval between human footsteps (max ~3.3 steps/sec)
        const val MIN_STEP_INTERVAL_MS = 300L
        // Maximum interval before gait cadence resets (cadence timeout)
        const val MAX_STEP_INTERVAL_MS = 2500L
        // Realistic dynamic peak and valley thresholds for walking gait
        const val PEAK_THRESHOLD = 11.2f
        const val VALLEY_THRESHOLD = 8.5f
        const val MIN_PEAK_TO_PEAK = 2.0f
        // Minimum dynamic linear acceleration energy required to process steps
        const val MIN_DYNAMIC_ENERGY = 0.40f
    }

    /**
     * Process sensor sample with ZUPT stationary gating.
     * @param accel Raw accelerometer reading (with gravity)
     * @param dynamicAccelMag Magnitude of linear acceleration (without gravity)
     * @param isDeviceStationary Flag from SensorDataManager motion energy classifier
     * @param timestampMs Timestamp in milliseconds
     */
    fun processSensorSample(
        accel: Vector3D,
        dynamicAccelMag: Float = 0f,
        isDeviceStationary: Boolean = false,
        timestampMs: Long = System.currentTimeMillis()
    ) {
        // Zero-Velocity Update (ZUPT): If device is stationary or dynamic energy is negligible, suppress all steps
        if (isDeviceStationary || dynamicAccelMag < MIN_DYNAMIC_ENERGY) {
            currentPeak = BASELINE_GRAVITY
            currentValley = BASELINE_GRAVITY
            val timeSinceLastStep = timestampMs - lastStepTimeMs
            if (timeSinceLastStep > MAX_STEP_INTERVAL_MS) {
                consecutiveSteps = 0
            }
            return
        }

        val magnitude = accel.magnitude
        if (!isInitialized) {
            prevMagnitude = magnitude
            currentPeak = magnitude
            currentValley = magnitude
            isInitialized = true
            return
        }

        // Track cycle extremes
        if (magnitude > currentPeak) {
            currentPeak = magnitude
        }
        if (magnitude < currentValley) {
            currentValley = magnitude
        }

        // Detect zero-crossing: signal crosses baseline from valley toward peak
        if (prevMagnitude <= BASELINE_GRAVITY && magnitude > BASELINE_GRAVITY) {
            val peakToPeak = currentPeak - currentValley
            val dt = timestampMs - lastStepTimeMs

            // Cadence timeout check
            if (lastStepTimeMs > 0 && dt > MAX_STEP_INTERVAL_MS) {
                consecutiveSteps = 0
            }

            if (currentPeak >= PEAK_THRESHOLD &&
                currentValley <= VALLEY_THRESHOLD &&
                peakToPeak >= MIN_PEAK_TO_PEAK &&
                (lastStepTimeMs == 0L || dt >= MIN_STEP_INTERVAL_MS)
            ) {
                consecutiveSteps++

                // Require at least 2 consecutive strides to filter out isolated bumps/drops
                if (consecutiveSteps >= 2 || lastStepTimeMs == 0L) {
                    val stepEvent = StepEvent(
                        timestampMs = timestampMs,
                        aMax = currentPeak,
                        aMin = currentValley,
                        stepDurationMs = if (lastStepTimeMs > 0) dt else 550L
                    )
                    lastStepTimeMs = timestampMs
                    onStepDetected(stepEvent)
                } else {
                    lastStepTimeMs = timestampMs
                }
            }

            // Reset cycle peak and valley for next stride
            currentPeak = magnitude
            currentValley = magnitude
        }

        prevMagnitude = magnitude
    }

    fun reset() {
        prevMagnitude = BASELINE_GRAVITY
        currentPeak = BASELINE_GRAVITY
        currentValley = BASELINE_GRAVITY
        lastStepTimeMs = 0L
        consecutiveSteps = 0
        isInitialized = false
    }
}
