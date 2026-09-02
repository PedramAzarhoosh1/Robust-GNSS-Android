package com.example.iotproject.domain.pdr

import kotlin.math.pow

class StepLengthEstimator(
    private var kCoefficient: Float = 0.43f // Standard Weinberg constant
) {
    companion object {
        const val MIN_STEP_LENGTH = 0.35f
        const val MAX_STEP_LENGTH = 1.20f
        const val DEFAULT_STEP_LENGTH = 0.70f
    }

    /**
     * Estimates step length using the Weinberg model:
     * SL = K * (aMax - aMin)^(1/4)
     */
    fun estimateStepLength(aMax: Float, aMin: Float): Float {
        val deltaA = (aMax - aMin).coerceAtLeast(0.1f)
        val rawStepLength = kCoefficient * deltaA.toDouble().pow(0.25).toFloat()
        return rawStepLength.coerceIn(MIN_STEP_LENGTH, MAX_STEP_LENGTH)
    }

    fun calibrate(distanceMeters: Double, stepsCount: Int, avgDeltaA: Float) {
        if (stepsCount <= 0 || avgDeltaA <= 0f) return
        val observedAvgStep = (distanceMeters / stepsCount).toFloat()
        val fourthRoot = avgDeltaA.toDouble().pow(0.25).toFloat()
        if (fourthRoot > 0.1f) {
            val estimatedK = (observedAvgStep / fourthRoot).coerceIn(0.25f, 0.65f)
            kCoefficient = 0.8f * kCoefficient + 0.2f * estimatedK
        }
    }

    fun setKCoefficient(k: Float) {
        kCoefficient = k.coerceIn(0.2f, 0.7f)
    }

    fun getKCoefficient(): Float = kCoefficient
}
