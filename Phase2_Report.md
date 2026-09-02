# Phase 2 Project Report: GNSS Fault Injection & Pedestrian Dead Reckoning (PDR)

**Project Title**: Design of an Intelligent Resilient Android Positioning System Against GNSS Degradation and Outages  
**Course / Context**: IoT / Embedded & Mobile Systems Engineering  
**Phase**: Phase 2 (Fault Injection Simulation & Dead Reckoning Navigation)  
**Status**: Fully Implemented, Unit-Tested, and Verified on Real Device  

---

## 1. Phase 2 Objectives & Project Scope

According to the official project specification (`project.pdf`, Phase 2):
> *"فاز دوم: پیاده‌سازی بخش Fault Injection برای شبیه‌سازی قطع، نامعتبر شدن و بازگشت GNSS بدون نیاز به ایجاد اختلال واقعی و طراحی و پیاده‌سازی الگوریتم تخمین موقعیت با استفاده از آخرین موقعیت معتبر، داده‌های حسگرهای حرکتی، Dead Reckoning و در صورت نیاز Sensor Fusion."*

The primary goals of this phase are:
1. **Software Fault Injection Simulation Engine**: Enable evaluating GPS failures (signal outages, spoofing jumps, frozen coordinates, multipath drift, delayed fixes) safely in software without requiring hardware satellite jammers.
2. **Pedestrian Dead Reckoning (PDR) Algorithm**: Continue tracking the user's real-time position using onboard IMU sensors (Accelerometer, Gyroscope, Magnetometer, Step Detector) when GPS is degraded or completely lost.
3. **Ground Truth vs. Estimated Trajectory Comparison**: Maintain the raw Android GPS coordinate as Ground Truth in the background while PDR computes the independent trajectory, measuring the real-time distance error in meters.
4. **Smooth Recovery Transition**: Verify consecutive healthy satellite fixes upon GPS restoration and softly blend PDR coordinates back to GPS without discontinuous jumps.

---

## 2. What is PDR (Pedestrian Dead Reckoning)?

**Dead Reckoning** is a navigation technique where a moving object calculates its current position by starting from a known initial point and continually adding its estimated displacement (distance and direction) over time.

For a pedestrian walking with a smartphone, **Pedestrian Dead Reckoning (PDR)** operates in 4 consecutive stages on every detected step:

```
[Initial GPS Fix (Lat0, Lon0)]
           │
           ▼
[1. Step Detection] ────► Real-time zero-crossing & peak-to-peak acceleration wave analysis
           │
           ▼
[2. Step Length (SL)] ──► Weinberg Model: SL = K · ∜(a_max - a_min)
           │
           ▼
[3. Heading Angle (θ)] ─► 6-DOF / 9-DOF Fused Rotation Vector (Gyroscope + Magnetometer)
           │
           ▼
[4. Geodesic Propagation]
     ΔNorth = SL · cos(θ)
     ΔEast  = SL · sin(θ)
     Lat_new = Lat_prev + (ΔNorth / R_earth) · (180 / π)
     Lon_new = Lon_prev + (ΔEast / (R_earth · cos(Lat))) · (180 / π)
```

---

## 3. Mathematical Models & Theoretical Foundations

### 3.1. Weinberg Step Length Estimation Model
Human step length varies dynamically based on walking speed, stride vigor, and terrain. The **Weinberg Model** accurately estimates stride length using the fourth root of dynamic vertical acceleration amplitude:

$$SL = K \cdot \sqrt[4]{a_{max} - a_{min}}$$

- **$K$**: Individual calibration coefficient (default $\approx 0.43$).
- **$a_{max}, a_{min}$**: Maximum peak and minimum trough acceleration recorded during the stride cycle.
- **Clamping**: Output is bounded to the physiological human range $[0.35\text{ m}, 1.20\text{ m}]$.

### 3.2. Geodesic Coordinate Propagation (WGS84 Earth Ellipsoid)
Displacement in local Cartesian coordinates ($\Delta \text{North}, \Delta \text{East}$) is projected onto spherical Earth coordinates ($R_{earth} \approx 6,371,000\text{ m}$):

$$\Delta \text{Latitude (deg)} = \frac{\Delta \text{North}}{R_{earth}} \times \frac{180^\circ}{\pi}$$

$$\Delta \text{Longitude (deg)} = \frac{\Delta \text{East}}{R_{earth} \cdot \cos(\text{Latitude})} \times \frac{180^\circ}{\pi}$$

### 3.3. Haversine Error Distance Metric
The real-time estimation error (distance between Ground Truth GPS $(\phi_1, \lambda_1)$ and Estimated PDR $(\phi_2, \lambda_2)$) is computed via the Haversine formula:

$$a = \sin^2\left(\frac{\Delta\phi}{2}\right) + \cos(\phi_1)\cos(\phi_2)\sin^2\left(\frac{\Delta\lambda}{2}\right)$$

$$\text{Error (meters)} = 2 \cdot R_{earth} \cdot \arctan2\left(\sqrt{a}, \sqrt{1-a}\right)$$

---

## 4. Software Architecture & Implementation Breakdown

```
app/src/main/java/com/example/iotproject/
├── data/
│   ├── model/
│   │   ├── FaultInjectionMode.kt       # Fault simulation modes (Outage, Jump, Drift, etc.)
│   │   ├── PdrState.kt                 # PDR metrics (Lat, Lon, Steps, Step Length, Error)
│   │   ├── TrajectoryPoint.kt          # 2D path coordinates for canvas rendering
│   │   ├── LocationData.kt             # GNSS fix data structure
│   │   └── SensorData.kt               # IMU & Motion snapshot data structures
│   ├── logging/
│   │   └── DataLogger.kt               # Synchronized CSV recorder with Phase 2 fields
│   └── location/
│       └── LocationDataManager.kt      # Real GPS & satellite status listener
├── domain/
│   ├── fault/
│   │   └── FaultInjectionEngine.kt     # Software-controlled GPS fault generator
│   ├── pdr/
│   │   ├── StepDetector.kt             # Peak-to-peak zero-crossing step detector
│   │   ├── StepLengthEstimator.kt      # Weinberg step length estimator
│   │   ├── HeadingEstimator.kt         # Fused azimuth & orientation estimator
│   │   └── PdrEngine.kt                # Dead reckoning coordinate propagator
│   └── fusion/
│       └── PositionEstimator.kt        # Coordinates GNSS validation, PDR takeover, & recovery
└── ui/
    ├── components/
    │   ├── TrajectoryCanvas.kt         # Real-time 2D path visualizer (GPS vs. PDR)
    │   ├── CompassDial.kt              # Smooth rotating compass rose widget
    │   ├── SatelliteBars.kt            # C/N0 dB-Hz signal bar charts
    │   ├── ScenarioBar.kt              # 1-tap presentation demo preset buttons
    │   └── MetricCards.kt              # Error gauge and pulsating status badges
    ├── screens/
    │   ├── DashboardTab.kt             # Main 2D map, error meter, and fault controls
    │   ├── SensorsTab.kt               # Compass rose, Weinberg model, & IMU metrics
    │   ├── SatellitesTab.kt            # GPS, GLONASS, Galileo, BeiDou SNR breakdown
    │   ├── LogsTab.kt                  # CSV session recorder and one-tap exporter
    │   └── MainAppScaffold.kt          # Material 3 dark slate 4-tab container
    └── viewmodel/
        └── MainViewModel.kt            # State orchestration and data pipeline
```

---

## 5. Fault Injection Simulation Scenarios

| Scenario Mode | Technical Behavior | Expected System Response |
| :--- | :--- | :--- |
| **`NORMAL`** | Passes real GPS coordinates without modification. | System operates in `HEALTHY` mode; PDR anchors to GPS. |
| **`OUTAGE`** | Drops GPS coordinates (`null` returned). | System switches to `UNAVAILABLE`; **PDR autonomously navigates**. |
| **`POSITION_JUMP`** | Shifts coordinates by $+80\text{m}$ offset instantly. | Anomaly detector catches kinematic teleportation $\rightarrow$ `SUSPICIOUS`. |
| **`FROZEN_LOCATION`** | Coordinates stay frozen while physical walking occurs. | Kinematic inconsistency detected between IMU motion and GPS. |
| **`GRADUAL_DRIFT`** | Injects an accumulating $+1.5\text{ m/s}$ drift vector. | Simulates multipath / urban canyon drift error. |
| **`DELAYED_UPDATES`** | Buffers GPS fixes by $5.0\text{s}$ in a FIFO queue. | Tests staleness timeout evaluation. |
| **`RECOVERY`** | Restores clean GPS stream. | Validates 4 consecutive fixes and softly converges back to GPS. |

---

## 6. CSV Dataset Structure for Project Reports

Recorded CSV files are saved in the app's external files directory (`gnss_sensor_logs/`) with the following headers:
```csv
timestamp_ms, iso_time, gt_latitude, gt_longitude, gt_altitude, gt_accuracy, gt_speed, gt_bearing, est_latitude, est_longitude, pdr_error_meters, pdr_steps, pdr_step_length_m, pdr_heading_deg, pdr_distance_m, is_pdr_active, fault_mode, gnss_state, accel_x, accel_y, accel_z, linear_accel_x, linear_accel_y, linear_accel_z, gyro_x, gyro_y, gyro_z, mag_x, mag_y, mag_z, heading_deg, step_count, satellites_total, satellites_used, avg_cn0, is_stationary, assessment_reasons
```

---

## 7. Verification & Automated Test Results

All unit tests pass cleanly with 100% success rate:
- **`PdrEngineTest`**: Weinberg model boundaries, waveform step triggering, and Northbound geodesic coordinate propagation.
- **`FaultInjectionTest`**: Outage blackout, coordinate offset shift, and frozen position caching.
- **`GnssIntegrityEvaluatorTest`**: Verification of `HEALTHY`, `DEGRADED`, `SUSPICIOUS`, and `UNAVAILABLE` state transitions.

**Command to execute tests & build APK:**
```bash
./gradlew testDebugUnitTest assembleDebug
```
**Output Installable APK Path:**
```
app/build/outputs/apk/debug/app-debug.apk
```
