# Phase 1 Report: Design of an Intelligent GNSS Integrity Evaluation and Kinematic Data Logging System

---

## 1. Introduction and Objective of Phase 1
The objective of Phase 1 is to design and implement the core infrastructure of an Android application for the simultaneous reception, synchronization, validation, and logging of spatial (GNSS) and kinematic sensor (IMU) data. In this phase, the system is implemented to run on all standard Android devices without hardware dependencies or root access, providing a stable foundation for position estimation algorithms in subsequent phases.

---

## 2. Software Architecture and Data Flow
The software architecture is based on the **MVVM (Model-View-ViewModel)** pattern and **Clean Architecture** principles, alongside the modern UI toolkit **Jetpack Compose**.

The overall data flow structure in the software is as follows:

```text
                  ┌──────────────────────────────────────────────┐
                  │               Android Hardware               │
                  │ (GNSS Chipset, IMU Sensors, Pedometer, Clock)│
                  └──────────────────────┬───────────────────────┘
                                         │
                    ┌────────────────────┴────────────────────┐
                    ▼                                         ▼
     ┌─────────────────────────────┐           ┌─────────────────────────────┐
     │     LocationDataManager     │           │      SensorDataManager      │
     │  - LocationListener         │           │  - Accelerometer, Gyroscope │
     │  - GnssStatus.Callback      │           │  - Magnetometer, LinearAcc  │
     │  (GPS, GLONASS, BeiDou...)  │           │  - RotationVector, Steps    │
     └──────────────┬──────────────┘           └──────────────┬──────────────┘
                    │                                         │
                    └────────────────────┬────────────────────┘
                                         │
                                         ▼
                         ┌───────────────────────────────┐
                         │    GnssIntegrityEvaluator     │
                         │   (Cross-validation with IMU) │
                         └───────────────┬───────────────┘
                                         │
                    ┌────────────────────┴────────────────────┐
                    ▼                                         ▼
     ┌─────────────────────────────┐           ┌─────────────────────────────┐
     │         DataLogger          │           │       UI (Dashboard)        │
     │ - Sync logging to CSV file  │           │ - Live status & cards view  │
     │ - Export via FileProvider   │           │ - Background Service        │
     └─────────────────────────────┘           └─────────────────────────────┘

```

---

## 3. Description of Implemented Components and Modules

### 3.1. Spatial Data Management and Reception (`LocationDataManager`)

This module establishes communication with the operating system's location service (`LocationManager`):

* **1 Hz Frequency Position Reception:** Retrieves Latitude, Longitude, Altitude, Accuracy, Speed, and Bearing.
* **Hardware Synchronization:** Logs the `elapsedRealtimeNanos` (nanosecond timestamp since device boot) to ensure precise time alignment with high-frequency sensors.
* **Satellite Constellation Separation (`GnssStatus.Callback`):** Separates and counts active satellites based on different constellations:
* GPS (USA)
* GLONASS (Russia)
* Galileo (EU)
* BeiDou (China)


* Calculates the average satellite signal strength in decibel-hertz ($C/N_0$ in $\text{dB-Hz}$).

---

### 3.2. Kinematic Sensor Management and Processing (`SensorDataManager`)

This module logs and analyzes data from the following high-frequency sensors (50 Hz - `SENSOR_DELAY_GAME`):

1. **Accelerometer:** Measures the acceleration vector across three axes $X, Y, Z$, including Earth's gravity.
2. **Dynamic Linear Acceleration:** Removes the constant gravity component ($9.8\text{ m/s}^2$) using a Low-Pass Alpha Filter in environments lacking hardware support:

$$g_k = \alpha \cdot g_{k-1} + (1 - \alpha) \cdot a_k$$


$$a_{\text{linear}} = a_k - g_k$$


3. **Gyroscope:** Measures the device's angular rotation speed across three axes in radians per second ($\text{rad/s}$).
4. **Magnetometer:** Measures the ambient magnetic field vector in microteslas ($\mu\text{T}$).
5. **Rotation Vector & Heading:** Extracts Euler angles (Azimuth, Pitch, Roll) from the rotation quaternion and calculates the compass heading angle (0 to 360 degrees).
6. **Step Detector & Step Counter:** Counts the number of steps for use in the Pedestrian Dead Reckoning (PDR) position estimation phase.

#### Smart Stationary Detector:

To validate spatial data, the system maintains a 50-sample sliding window of the linear acceleration magnitude, calculates its standard deviation ($\sigma$), and checks the gyroscope's angular velocity to determine whether the device is stationary or moving:

* If $\sigma < 0.25\text{ m/s}^2$ and $\|a_{\text{linear}}\| < 0.35\text{ m/s}^2$ and $\|\omega_{\text{gyro}}\| < 0.25\text{ rad/s}$, the device is in a **`STATIONARY`** state; otherwise, it is reported as **`MOVING`**.

---

### 3.3. Signal Health and Integrity Evaluation Module (`GnssIntegrityEvaluator`)

This module is the logical heart of Phase 1. By combining spatial and physical metrics, it categorizes the signal status into 4 levels:

| State | Indicator Color | Status Description and Trigger Conditions |
| --- | --- | --- |
| **`HEALTHY`** | Green | Signal is valid; accuracy is less than 18 meters, satellite count is sufficient, and GPS data aligns with kinematic sensor movement. |
| **`DEGRADED`** | Orange | Signal quality is reduced; accuracy is between 18 and 45 meters, visible satellites are low (< 5), or fix reception delay exceeds 3.5 seconds. |
| **`SUSPICIOUS`** | Crimson | Physical anomaly detected; sudden position jump (via Haversine formula), unrealistic speed ($>200\text{ km/h}$), or contradiction with sensors (e.g., GPS claims movement while the accelerometer confirms the device is stationary). |
| **`UNAVAILABLE`** | Red | Signal is completely lost, GPS is turned off, or no data received for over 7 seconds (Timeout). |

---

### 3.4. Data Storage, Synchronization, and Export System (`DataLogger`)

* **CSV Data Structure:** Data is stored in the app's dedicated external storage folder with the standard format `gnss_log_YYYYMMDD_HHmmss.csv`.
* **28-Column Header:** Includes all spatial parameters, 3-axis sensors, heading angles, step count, satellite status, stationary label, and integrity evaluation status.
* **Secure Sharing (`FileProvider`):** Enables direct sending of the recorded CSV file via the in-app menu to email, messengers, or Google Drive for analysis and plotting in MATLAB or Python.
* **Foreground Service (`TrackingService`):** Runs the logging process as a Foreground Service with a persistent notification, ensuring data recording does not stop when the screen is locked or the app is exited.

---

### 3.5. User Interface (`DashboardScreen` in Jetpack Compose)

The application dashboard consists of the following sections:

1. **Control Card:** Start/stop CSV recording button, pause sensors, and view saved sessions.
2. **Integrity Status Card:** Displays the colored status label, evaluation reasons, signal staleness, satellites used in fix, and jump shift distance.
3. **Location Card:** Real-time display of latitude, longitude, accuracy, altitude, speed, and bearing.
4. **Sensor Card:** Displays stationary/moving status (`STATIONARY`/`MOVING`), accelerometer, gyroscope, linear acceleration, compass angle, step count, and magnetometer.
5. **Constellation Card:** Displays total satellites, satellites used in fix, average signal strength ($C/N_0$), and a breakdown of GPS, GLONASS, Galileo, and BeiDou satellites.

---

## 4. Phase 1 Requirements Compliance Review

| Row | Requirement in Project Document (`project.pdf`) | Implementation Status | Code Location |
| --- | --- | --- | --- |
| 1 | Design initial Android application based on Kotlin | ✅ Completed | Modern Compose + MVVM Architecture |
| 2 | Obtain necessary permissions (Location, Sensors, Notification) | ✅ Completed | [`DashboardScreen.kt`](https://www.google.com/search?q=app/src/main/java/com/example/iotproject/ui/screens/DashboardScreen.kt) |
| 3 | Read Location data (longitude, latitude, accuracy, speed, bearing) | ✅ Completed | [`LocationDataManager.kt`](https://www.google.com/search?q=app/src/main/java/com/example/iotproject/data/location/LocationDataManager.kt) |
| 4 | Read accelerometer, gyroscope, magnetometer, rotation vector, and step counter sensors | ✅ Completed | [`SensorDataManager.kt`](https://www.google.com/search?q=app/src/main/java/com/example/iotproject/data/sensor/SensorDataManager.kt) |
| 5 | Record appropriate Timestamps for time synchronization | ✅ Completed | `timestamp_ms` and `elapsedRealtimeNanos` fields |
| 6 | Generate and save data in CSV file format | ✅ Completed | [`DataLogger.kt`](https://www.google.com/search?q=app/src/main/java/com/example/iotproject/data/logging/DataLogger.kt) |
| 7 | Define 4 operational states: Healthy, Degraded, Suspicious, Unavailable | ✅ Completed | [`GnssStatusState.kt`](https://www.google.com/search?q=app/src/main/java/com/example/iotproject/data/model/GnssStatusState.kt) |
| 8 | Design and implement GNSS integrity evaluation module by comparing sensor kinematics | ✅ Completed | [`GnssIntegrityEvaluator.kt`](https://www.google.com/search?q=app/src/main/java/com/example/iotproject/domain/assessment/GnssIntegrityEvaluator.kt) |

---

## 5. Conclusion and Readiness for Phase 2

All objectives and requirements specified for Phase 1 have been successfully implemented, tested, and documented. This stable infrastructure allows direct entry into **Phase 2** (designing the Fault Injection simulation module and implementing motion sensor-based position estimation algorithms such as PDR and Kalman Filters).
