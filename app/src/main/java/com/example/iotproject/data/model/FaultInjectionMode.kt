package com.example.iotproject.data.model

enum class FaultInjectionMode(
    val displayName: String,
    val description: String
) {
    NORMAL(
        displayName = "Normal (No Fault)",
        description = "Real GNSS / GPS passes through without alteration."
    ),
    OUTAGE(
        displayName = "GNSS Outage / Loss",
        description = "Simulates total satellite signal loss; PDR algorithm takes over."
    ),
    POSITION_JUMP(
        displayName = "Sudden Position Jump",
        description = "Simulates spoofing / multipath teleportation jump (+80m offset)."
    ),
    FROZEN_LOCATION(
        displayName = "Frozen Location",
        description = "Coordinates stop updating while the user continues walking."
    ),
    GRADUAL_DRIFT(
        displayName = "Gradual Drift / Ramp Error",
        description = "Adds accumulating drift error (+1.5 m/s ramp) to GPS coordinates."
    ),
    DELAYED_UPDATES(
        displayName = "Delayed Updates (Stale)",
        description = "Artificially delays GPS updates by 5 seconds to test timeout handling."
    ),
    RECOVERY(
        displayName = "Recovery Mode",
        description = "GNSS returns; system validates consecutive healthy samples before smooth convergence."
    )
}

data class FaultConfig(
    val mode: FaultInjectionMode = FaultInjectionMode.NORMAL,
    val jumpOffsetMeters: Double = 80.0,
    val driftRateMps: Double = 1.5,
    val delaySeconds: Long = 5L,
    val recoverySamplesRequired: Int = 4
)
