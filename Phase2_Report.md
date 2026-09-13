# Phase 2 & Phase 3 Technical Report: Resilient GNSS Positioning, Multi-Modal Motion Context Classification, and Pedestrian Dead Reckoning (PDR)

**Project Title**: Design of an Intelligent Resilient Android Positioning System Against GNSS Degradation and Outages  
**Course / Context**: IoT / Embedded & Mobile Systems Engineering  
**Branch**: `Phase2` (Real-Time Architecture & Multi-Sensor Fusion Framework)  
**Status**: Fully Implemented, Unit-Tested (15/15 Tests Passed), and Verified on Device  

---

## 1. Executive Summary & Project Objectives

According to the official project specification (`project.pdf`):
- **Phase 1**: Ingestion and validation of multi-constellation GNSS (GPS, GLONASS, Galileo, BeiDou) and inertial IMU sensors.
- **Phase 2**: Software-based Fault Injection simulation engine and Pedestrian Dead Reckoning (PDR) with Weinberg stride length estimation.
- **Phase 3 Readiness**: Multi-sensor fusion with time-series sliding window trust analysis, multi-modal motion context recognition (Pedestrian / Vehicle / Stationary), continuous GPS trust scoring ($W_{gps} \in [0.0, 1.0]$), and clean zero-GPS indoor navigation.

---

## 2. Core Architecture & Mathematical Foundations

### 2.1. Biomechanical Weinberg PDR Stride Engine
Human step length varies dynamically with movement vigor. The **Weinberg Model** estimates step length from vertical acceleration amplitude:

$$SL = K \cdot \sqrt[4]{a_{max} - a_{min}}$$

- **$a_{max}$**: Peak upward thrust acceleration during heel strike impact.
- **$a_{min}$**: Trough acceleration during the forward leg swing free-fall phase.
- **$K$**: Individual calibration constant (default $\approx 0.43$).
- **Bounding**: Bounded to the physiological human range $[0.35\text{ m}, 1.20\text{ m}]$.

### 2.2. Geodesic WGS-84 Coordinate Propagation
Physical step displacements are projected onto the spherical Earth ($R_{earth} \approx 6,371,000\text{ m}$):

$$\Delta \text{North} = SL \cdot \cos(\theta),\quad \Delta \text{East} = SL \cdot \sin(\theta)$$

$$\Delta \text{Latitude (deg)} = \frac{\Delta \text{North}}{R_{earth}} \times \frac{180^\circ}{\pi}$$

$$\Delta \text{Longitude (deg)} = \frac{\Delta \text{East}}{R_{earth} \cdot \cos(\text{Latitude})} \times \frac{180^\circ}{\pi}$$

### 2.3. Haversine Error Distance Metric
Real-time discrepancy between true Ground Truth GPS $(\phi_1, \lambda_1)$ and estimated PDR $(\phi_2, \lambda_2)$ is computed via the Haversine formula:

$$a = \sin^2\left(\frac{\Delta\phi}{2}\right) + \cos(\phi_1)\cos(\phi_2)\sin^2\left(\frac{\Delta\lambda}{2}\right)$$

$$\text{Estimation Error (meters)} = 2 \cdot R_{earth} \cdot \arctan2\left(\sqrt{a}, \sqrt{1-a}\right)$$

---

## 3. Real-Time Multi-Situation & Phase 3 Features

```
                                  [Sensor & GPS Ingestion]
                                             │
                                             ▼
                      ┌─────────────────────────────────────────────┐
                      │    GnssIntegrityEvaluator & Classifier      │
                      │  • Sliding Window Buffer (N = 6 samples)    │
                      │  • Spatial Variance Metric (σ²)             │
                      │  • Motion Context: Walk / Vehicle / Desk    │
                      │  • Continuous Trust Score (W_gps: 0% - 100%)│
                      └─────────────────────────────────────────────┘
                                             │
                                             ▼
                      ┌─────────────────────────────────────────────┐
                      │              PositionEstimator              │
                      │  • Adaptive Weight Blending:                │
                      │    X_est = W_gps · X_gps + (1-W_gps) · X_pdr│
                      │  • Recovery Holdoff (4 consecutive fixes)   │
                      │  • ZUPT for Stationary / Zero Drift         │
                      └─────────────────────────────────────────────┘
```

### 3.1. Time-Series Sliding Window Trust Analysis ($N=6$)
Instead of evaluating instantaneous single-sample fixes (which are prone to single multipath spikes), the engine maintains a historical sliding window of the last 6 GPS points. It computes:
- **Spatial Variance ($\sigma^2$)**: Quantifies scatter and jitter in meters squared.
- **Speed Derivative ($\frac{dv}{dt}$)**: Compares GPS velocity slope against IMU acceleration.

### 3.2. Multi-Modal Motion Context Classifier
- 🛑 **`STATIONARY`**: Device is motionless on a desk or in hand ($|a_{dynamic}| < 0.3\text{ m/s}^2$, $\Delta\theta \approx 0$). Applies Zero-Velocity Update (ZUPT) to prevent coordinate drift.
- 🚶 **`PEDESTRIAN_WALK`**: Step oscillation frequency ($1-3\text{ Hz}$) and dynamic vertical bounce detected. Active Weinberg PDR.
- 🚗 **`VEHICLE_TRANSIT`**: High sustained forward velocity ($> 4.5\text{ m/s}$ / $> 16\text{ km/h}$) with vehicle turn dynamics and low vertical stride oscillation. High speed is validated as legitimate vehicle movement rather than false sensor mismatch.

### 3.3. Continuous GPS Trust Scoring ($W_{gps} \in [0.0, 1.0]$)
A continuous trust score ($0\% - 100\%$) is computed in real-time based on:
- Horizontal Dilution of Precision (HDOP) / Reported Accuracy penalty.
- Number of satellites used in 3D fix.
- Carrier-to-Noise Density ($C/N_0\text{ dB-Hz}$).
- Time-series spatial variance.

### 3.4. Graceful Zero-GPS Indoor Handling
When entering a basement, subway, or elevator where GPS is `null`:
- Status cleanly transitions to 🔴 **`UNAVAILABLE`** with `gpsTrustScore = 0.0f`.
- The app operates autonomously in pure PDR mode without false kinematic alarms.

---

## 4. Software Architecture Overview

```
app/src/main/java/com/example/iotproject/
├── data/
│   ├── model/
│   │   ├── MotionContext.kt            # Stationary, Pedestrian, Vehicle context enums
│   │   ├── AssessmentResult.kt         # Integrity verdict, trust score (0-1), variance
│   │   ├── FaultInjectionMode.kt       # Outage, Spoofing Jump, Frozen, Drift modes
│   │   ├── PdrState.kt                 # Lat, Lon, Step Count, Step Length, Error
│   │   └── TrajectoryPoint.kt          # 2D Canvas coordinate history
│   ├── logging/
│   │   └── DataLogger.kt               # Synchronized CSV logger with Phase 3 fields
│   └── location/
│       └── LocationDataManager.kt      # Multi-constellation GNSS provider
├── domain/
│   ├── assessment/
│   │   └── GnssIntegrityEvaluator.kt   # Sliding-window trust analyzer & context classifier
│   ├── fault/
│   │   └── FaultInjectionEngine.kt     # Software GPS failure generator
│   ├── pdr/
│   │   ├── StepDetector.kt             # Peak-to-peak zero-crossing step detector
│   │   ├── StepLengthEstimator.kt      # Weinberg step length estimator
│   │   ├── HeadingEstimator.kt         # Fused azimuth & orientation estimator
│   │   └── PdrEngine.kt                # Geodesic coordinate propagator
│   └── fusion/
│       └── PositionEstimator.kt        # Adaptive weighted fusion & smooth recovery
└── ui/
    ├── components/
    │   ├── MetricCards.kt              # Motion Context Pill, Trust % Badge, Error Gauge
    │   ├── TrajectoryCanvas.kt         # 2D Real-time vector map (Teal vs Orange)
    │   ├── CompassDial.kt              # Smooth rotating compass rose
    │   ├── SatelliteBars.kt            # C/N0 dB-Hz multi-constellation bars
    │   └── ScenarioBar.kt              # 1-tap demo scenario presets
    ├── screens/
    │   ├── DashboardTab.kt             # Main 2D map, comparison table, and fault bar
    │   ├── SensorsTab.kt               # Weinberg analytics & raw IMU streams
    │   ├── SatellitesTab.kt            # GPS, GLONASS, Galileo, BeiDou breakdown
    │   ├── LogsTab.kt                  # CSV session recorder & direct exporter
    │   └── MainAppScaffold.kt          # Material 3 Dark Slate 4-tab container
    └── viewmodel/
        └── MainViewModel.kt            # 5 Hz throttled state orchestrator
```

---

## 5. Automated Unit Tests & Verification

All **15 automated unit tests** passed with 100% success rate:
- **`GnssIntegrityEvaluatorTest`**:
  - `testNullLocationReturnsUnavailableWithZeroTrust`
  - `testNominalGpsReturnsHealthyAndHighTrust`
  - `testPoorAccuracyReturnsDegraded`
  - `testKinematicInconsistencyStationarySensorMovingGpsReturnsSuspicious`
  - `testVehicleMotionContextRecognized`
  - `testSuddenPositionJumpReturnsSuspicious`
  - `testTimeoutLeadsToUnavailable`
- **`PdrEngineTest`**:
  - `testStepLengthEstimatorWeinbergBounds`
  - `testStepDetectorTriggersOnGaitWaveform`
  - `testPdrCoordinatePropagationNorth`
- **`FaultInjectionTest`**:
  - `testNormalModePassesThrough`
  - `testOutageModeReturnsNull`
  - `testPositionJumpOffsetsCoordinates`
  - `testFrozenLocationPreservesFirstFix`

**Build Command:**
```bash
./gradlew testDebugUnitTest assembleDebug
```
**Installable APK Path:**
```
app/build/outputs/apk/debug/app-debug.apk
```
