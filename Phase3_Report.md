# Phase 3 Technical Report: Resilient Street Map Integration, Dual Trajectory Visualization, & Android Mock Location Service

**Project Title**: Design of an Intelligent Resilient Android Positioning System Against GNSS Degradation and Outages  
**Course / Context**: IoT / Embedded & Mobile Systems Engineering  
**Phase**: Phase 3 (Real-Time Map Platform Integration, High-DPI Street Overlays, & System-Level Mock Location Service)  
**Status**: Fully Implemented, Unit-Tested (18/18 Tests Passing), Verified on High-Density Physical Devices & Emulator  

---

## 1. Executive Summary & Phase 3 Objectives

According to the official project specification (`project.pdf` - Phase 3):
> *"فاز سوم: اتصال اپلیکیشن به API یا SDK یک سرویس نقشه مانند نشان یا بلد یا OpenStreetMap، نمایش هم‌زمان موقعیت واقعی، موقعیت تخمین‌زده‌شده و مسیر حرکت روی نقشه و بررسی قابلیت Mock Location یا روش‌های مشابه برای ارائه آزمایشی موقعیت تخمین‌زده‌شده به سایر نرم‌افزارهای Android."*

The core engineering objectives accomplished in this phase are:

1. **High-Resolution Street Map Integration (OSMDroid + CartoDB HD Engine)**:
   - Native integration with real-world road networks, street names (English & Persian), landmarks, and building footprints.
   - Custom RFC-compliant User-Agent header (`IOTProject/1.0 (Android; Resilient Positioning System)`) completely eliminating HTTP 403 Forbidden errors.
   - High-DPI screen scaling enabled (`isTilesScaledToDpi = true`) ensuring ultra-sharp text and graphics on all mobile screens.
   - 5 dynamically switchable tile layers: **CartoDB Voyager (Street Names)**, **OpenStreetMap Standard (Mapnik)**, **CartoDB Dark Matter**, **CartoDB Light Positron**, and **OSM Humanitarian (HOT)**.
2. **Real-Time Dual Trajectory Visualization**:
   - 🔵 **Ground Truth GPS Polyline** (Teal `#14b8a6`, 9px stroke) with live GPS fix marker and satellite accuracy radius.
   - 🟠 **PDR Estimated Polyline** (Coral Orange `#f97316`, 9px stroke) with heading indicator and step count.
   - ── **Live Haversine Distance Error Line** (Rose `#f43f5e`, 4.5px stroke) dynamically connecting truth vs estimate.
3. **Android System-Level Mock Location Service (`TestProvider`)**:
   - Direct integration with Android's `LocationManager` Test Provider API.
   - Broadcasts real-time PDR dead-reckoning coordinates to the operating system, enabling external apps (Google Maps, Neshan, Balad, Waze, Snapp) to maintain navigation through GPS blackouts and tunnels.
4. **Zero-Velocity Update (ZUPT) & Stationary Step Gating**:
   - Dynamic acceleration energy variance thresholding and 2-stride confirmation filter to eliminate phantom drift while stationary.
5. **Responsive Modern Material 3 UI/UX**:
   - Aviation-grade floating telemetry HUD in `MapTab`.
   - Responsive, anti-collision layout in `LogsTab`, `SensorsTab`, and `CompassDial`.

---

## 2. System Architecture & Components

```
app/src/main/java/com/example/iotproject/
├── domain/
│   ├── mock/
│   │   └── MockLocationManager.kt      # Android TestProvider Mock Location broadcaster
│   ├── assessment/
│   │   └── GnssIntegrityEvaluator.kt   # Sliding window (N=6) & motion context classifier
│   ├── fault/
│   │   └── FaultInjectionEngine.kt     # Multi-mode GPS fault generator (Outage, Jumps, Multipath)
│   ├── fusion/
│   │   └── PositionEstimator.kt        # Continuous weight fusion (W_gps · X_gps + (1-W_gps) · X_pdr)
│   └── pdr/
│       ├── StepDetector.kt             # ZUPT-gated peak/trough step detector
│       ├── StepLengthEstimator.kt      # Biomechanical Weinberg stride estimator
│       ├── HeadingEstimator.kt         # 6-DOF IMU fused orientation & low-pass compass filter
│       └── PdrEngine.kt                # Geodesic WGS-84 coordinate propagator
├── ui/
│   ├── components/
│   │   ├── LiveMapView.kt              # OSMDroid/CartoDB map engine with dual polylines & tile switcher
│   │   ├── MetricCards.kt              # Motion Context Pill, GNSS Trust %, Haversine Error Gauge
│   │   ├── TrajectoryCanvas.kt         # 2D local Cartesian vector canvas
│   │   ├── CompassDial.kt              # Responsive 6-DOF rotating compass rose
│   │   └── ScenarioBar.kt              # 1-tap presentation demo scenario selector
│   └── screens/
│       ├── MapTab.kt                   # Full-screen Street Map & Dual Telemetry HUD
│       ├── DashboardTab.kt             # 2D trajectory overview & side-by-side coordinates
│       ├── SensorsTab.kt               # Weinberg analytics & raw IMU sensor streams
│       ├── SatellitesTab.kt            # GPS, GLONASS, Galileo, BeiDou multi-constellation summary
│       ├── LogsTab.kt                  # CSV session recorder & file export/share provider
│       └── MainAppScaffold.kt          # Material 3 navigation scaffold
```

---

## 3. Mathematical Foundations & Algorithms

### 3.1. Biomechanical Weinberg Stride Estimation
The system dynamically computes pedestrian step lengths using the vertical acceleration amplitude of each gait cycle:

$$SL = K \cdot \sqrt[4]{a_{max} - a_{min}}$$

- $a_{max}$: Maximum vertical acceleration at heel-strike impact ($\text{m/s}^2$).
- $a_{min}$: Minimum vertical acceleration during swing free-fall ($\text{m/s}^2$).
- $K$: Biometric calibration constant (empirically tuned to $K = 0.43$).
- **Physiological Guard**: Clamped to the valid human walking envelope: $SL \in [0.35\text{ m}, 1.20\text{ m}]$.

### 3.2. Geodesic WGS-84 Coordinate Propagation
Each validated step displacement is transformed into spherical Earth coordinates ($R_{earth} \approx 6,371,000\text{ m}$):

$$\Delta \text{North} = SL \cdot \cos(\theta),\quad \Delta \text{East} = SL \cdot \sin(\theta)$$

$$\Delta \text{Latitude} = \frac{\Delta \text{North}}{R_{earth}} \times \left(\frac{180^\circ}{\pi}\right)$$

$$\Delta \text{Longitude} = \frac{\Delta \text{East}}{R_{earth} \cdot \cos(\text{Latitude})} \times \left(\frac{180^\circ}{\pi}\right)$$

### 3.3. Zero-Velocity Update (ZUPT) & False-Step Elimination
To prevent coordinate accumulation when a user is standing, sitting, or holding the phone:
1. **Dynamic Accel Threshold**: Steps are only triggered if peak-to-peak variance satisfies $\Delta a = (a_{max} - a_{min}) \ge 2.2\text{ m/s}^2$.
2. **Timing Window**: Step interval $\Delta t$ must satisfy $220\text{ ms} \le \Delta t \le 1200\text{ ms}$ ($0.83\text{ Hz} \le f \le 4.54\text{ Hz}$).
3. **Stationary Gating**: If the IMU variance confirms the device is stationary ($|a_{dynamic}| < 0.3\text{ m/s}^2$), step detection is immediately locked.

### 3.4. Real-Time Haversine Distance Error
The spatial error between Ground Truth $(\phi_1, \lambda_1)$ and PDR Estimate $(\phi_2, \lambda_2)$ is evaluated continuously:

$$a = \sin^2\left(\frac{\phi_2 - \phi_1}{2}\right) + \cos(\phi_1)\cos(\phi_2)\sin^2\left(\frac{\lambda_2 - \lambda_1}{2}\right)$$

$$d = 2 \cdot R_{earth} \cdot \arctan2\left(\sqrt{a}, \sqrt{1 - a}\right)$$

---

## 4. Android Mock Location Service (`TestProvider`)

### 4.1. OS-Level Location Injection Pipeline
When the user activates **Mock Location Provider** in `MapTab`, the application interfaces directly with Android's `LocationManager`:

```kotlin
// 1. Register Mock Test Provider
locationManager.addTestProvider(
    LocationManager.GPS_PROVIDER,
    false, false, false, false, true, true, true, 1, 1
)
locationManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true)

// 2. Broadcast High-Res PDR Coordinate to OS
val mockLocation = Location(LocationManager.GPS_PROVIDER).apply {
    latitude = pdrState.estimatedLatitude
    longitude = pdrState.estimatedLongitude
    altitude = rawGt?.altitude ?: 0.0
    accuracy = if (pdrState.isPdrActive) 5.0f else 2.5f
    bearing = pdrState.headingDegrees
    speed = rawGt?.speed ?: 1.2f
    time = System.currentTimeMillis()
    elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
}
locationManager.setTestProviderLocation(LocationManager.GPS_PROVIDER, mockLocation)
```

### 4.2. Physical Device Setup Guide
1. Enable **Developer Options**: Go to *Settings* $\rightarrow$ *About Phone* $\rightarrow$ Tap *Build Number* 7 times.
2. Select Mock App: Go to *Settings* $\rightarrow$ *System* $\rightarrow$ *Developer Options* $\rightarrow$ *Select mock location app* $\rightarrow$ Choose **`IOTProject`**.
3. Open `MapTab` in the app and switch **`Mock Location Provider`** to **ON**.
4. Open Google Maps, Neshan, or Balad: The navigation position is driven smoothly by our PDR algorithm even with GPS turned off or during simulated satellite outages.

---

## 5. UI/UX Refactoring & Responsive Design

| Screen / Component | Problem in Initial Version | Phase 3 Solution |
| :--- | :--- | :--- |
| **`MapTab.kt` Bottom HUD** | Ground truth, PDR coordinates, and error text ran together in a single row on narrow phones. | Redesigned into structured dual-column cards with monospace coordinates, accuracy pills, and a full-width Haversine drift bar. |
| **`LogsTab.kt` Dataset Logger** | "Record CSV" button was squished into a narrow vertical line on mobile screens. | Added `Modifier.weight(1f)` to title/subtitle and specified explicit padding and touch targets for the button. |
| **`CompassDial.kt`** | Heading direction (e.g. `South-West (SW)`) caused text collision at 130dp. | Scaled dial to 115dp with stacked typography and coral pill badges for cardinal direction. |
| **Map Rendering** | 403 Forbidden on standard tile requests; blurriness on high-DPI screens. | Custom User-Agent header configured + `isTilesScaledToDpi = true` with 5 selectable HD tile sources. |

---

## 6. Verification & Automated Test Results

### 6.1. Unit Test Suite (18/18 Tests Passing)
- **`GnssIntegrityEvaluatorTest`**:
  - `testNullLocationReturnsUnavailableWithZeroTrust` (Passed)
  - `testNominalGpsReturnsHealthyAndHighTrust` (Passed)
  - `testPoorAccuracyReturnsDegraded` (Passed)
  - `testKinematicInconsistencyStationarySensorMovingGpsReturnsSuspicious` (Passed)
  - `testVehicleMotionContextRecognized` (Passed)
  - `testSuddenPositionJumpReturnsSuspicious` (Passed)
  - `testTimeoutLeadsToUnavailable` (Passed)
- **`PdrEngineTest`**:
  - `testStepLengthEstimatorWeinbergBounds` (Passed)
  - `testStepDetectorTriggersOnGaitWaveform` (Passed)
  - `testPdrCoordinatePropagationNorth` (Passed)
  - `testStationaryZuptGating` (Passed)
- **`FaultInjectionTest`**:
  - `testNormalModePassesThrough` (Passed)
  - `testOutageModeReturnsNull` (Passed)
  - `testPositionJumpOffsetsCoordinates` (Passed)
  - `testFrozenLocationPreservesFirstFix` (Passed)
- **`MockLocationManagerTest`**:
  - `testMotionContextDefinitions` (Passed)
  - `testTimeSeriesWindowMetricsDefault` (Passed)
  - `testMockLocationStateToggle` (Passed)

### 6.2. Build Verification
```bash
./gradlew testDebugUnitTest assembleDebug
# Result: BUILD SUCCESSFUL (40 actionable tasks: 8 executed, 32 up-to-date)
```
- **Installable APK Path**: `app/build/outputs/apk/debug/app-debug.apk`

---

## 7. Conclusion

Phase 3 successfully fulfills all core and advanced project requirements:
1. **Full map visualization** with real street names, high-resolution DPI scaling, and no HTTP 403 blocks.
2. **Simultaneous real-time display** of Ground Truth GPS, PDR Dead Reckoning, and Haversine Distance Error.
3. **Operational Android Mock Location Provider** capable of driving 3rd-party navigation applications during GNSS blackouts.
4. **Zero stationary phantom drift** via ZUPT and motion context classification.
5. **Polished, responsive Material 3 user experience** verified on real Android devices.
