package com.example.iotproject.domain.pdr

import com.example.iotproject.data.model.Vector3D

data class StepEvent(
    val timestampMs: Long,
    val aMax: Float,
    val aMin: Float,
    val stepDurationMs: Long
)

class StepDetector(
    private val onStepDetected: (StepEvent) -> Unit
) {
    private var prevMagnitude = 9.8f
    private var currentPeak = 9.8f
    private var currentValley = 9.8f
    private var lastStepTimeMs = 0L
    private var isInitialized = false

    companion object {
        const val BASELINE_GRAVITY = 9.81f
        const val MIN_STEP_INTERVAL_MS = 220L
        const val PEAK_THRESHOLD = 10.3f
        const val VALLEY_THRESHOLD = 9.3f
        const val MIN_PEAK_TO_PEAK = 1.0f
    }

    fun processSensorSample(accel: Vector3D, timestampMs: Long = System.currentTimeMillis()) {
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

        // Zero-crossing / baseline crossing detection: signal rises across baseline from valley
        if (prevMagnitude <= BASELINE_GRAVITY && magnitude > BASELINE_GRAVITY) {
            val peakToPeak = currentPeak - currentValley
            val dt = timestampMs - lastStepTimeMs

            if (currentPeak >= PEAK_THRESHOLD &&
                currentValley <= VALLEY_THRESHOLD &&
                peakToPeak >= MIN_PEAK_TO_PEAK &&
                dt >= MIN_STEP_INTERVAL_MS
            ) {
                val stepEvent = StepEvent(
                    timestampMs = timestampMs,
                    aMax = currentPeak,
                    aMin = currentValley,
                    stepDurationMs = if (lastStepTimeMs > 0) dt else 500L
                )
                lastStepTimeMs = timestampMs
                onStepDetected(stepEvent)
            }

            // Reset cycle peak and valley for next stride
            currentPeak = magnitude
            currentValley = magnitude
        }

        prevMagnitude = magnitude
    }

    fun reset() {
        prevMagnitude = 9.8f
        currentPeak = 9.8f
        currentValley = 9.8f
        lastStepTimeMs = 0L
        isInitialized = false
    }
}
