package com.example.iotproject.ui.viewmodel

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.iotproject.data.location.LocationDataManager
import com.example.iotproject.data.logging.DataLogger
import com.example.iotproject.data.model.*
import com.example.iotproject.data.sensor.SensorDataManager
import com.example.iotproject.domain.fusion.PositionEstimator
import com.example.iotproject.domain.mock.MockLocationManager
import com.example.iotproject.service.TrackingService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

data class MainUiState(
    val groundTruthLocation: LocationData? = null,
    val simulatedGnssLocation: LocationData? = null,
    val pdrState: PdrState = PdrState(),
    val groundTruthHistory: List<TrajectoryPoint> = emptyList(),
    val pdrHistory: List<TrajectoryPoint> = emptyList(),
    val faultMode: FaultInjectionMode = FaultInjectionMode.NORMAL,
    val sensors: SensorSnapshot = SensorSnapshot(),
    val gnssSummary: GnssConstellationSummary = GnssConstellationSummary(),
    val assessment: AssessmentResult = AssessmentResult(),
    val isTracking: Boolean = false,
    val isRecording: Boolean = false,
    val recordedSamplesCount: Long = 0L,
    val recordingDurationSec: Long = 0L,
    val logFiles: List<File> = emptyList(),
    val hasLocationPermission: Boolean = false,
    val hasSensorPermission: Boolean = false,
    val recentAssessments: List<AssessmentResult> = emptyList(),
    val isMockLocationEnabled: Boolean = false,
    val mockLocationError: String? = null,
    val selectedTab: Int = 0
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val sensorDataManager = SensorDataManager(context)
    private val locationDataManager = LocationDataManager(context)
    private val positionEstimator = PositionEstimator()
    private val dataLogger = DataLogger(context)
    private val mockLocationManager = MockLocationManager(context)

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    @Volatile
    private var latestSensors = SensorSnapshot()
    @Volatile
    private var latestGroundTruth: LocationData? = null
    @Volatile
    private var latestGnssSummary = GnssConstellationSummary()

    private var evaluationLoopJob: Job? = null
    private var recordingTimerJob: Job? = null
    private var recordingStartTimeMs: Long = 0L

    @Volatile
    private var lastRecordedSteps = 0
    @Volatile
    private var lastRecordedPdrLat = 0.0
    @Volatile
    private var lastRecordedPdrLon = 0.0
    @Volatile
    private var lastRecordedGtLat = 0.0
    @Volatile
    private var lastRecordedGtLon = 0.0

    init {
        // Collect sensor updates in memory (high-speed, zero UI overhead)
        viewModelScope.launch {
            sensorDataManager.sensorSnapshot.collect { sensors ->
                latestSensors = sensors
            }
        }

        // Collect ground truth location updates
        viewModelScope.launch {
            locationDataManager.currentLocation.collect { loc ->
                latestGroundTruth = loc
                if (loc != null) {
                    val dLat = loc.latitude - lastRecordedGtLat
                    val dLon = loc.longitude - lastRecordedGtLon
                    val distApproxM = Math.hypot(dLat * 111000.0, dLon * 111000.0 * Math.cos(Math.toRadians(loc.latitude)))
                    if (distApproxM >= 0.5 || lastRecordedGtLat == 0.0) {
                        lastRecordedGtLat = loc.latitude
                        lastRecordedGtLon = loc.longitude
                        val pt = TrajectoryPoint(latitude = loc.latitude, longitude = loc.longitude, timestampMs = loc.timestamp, isPdr = false)
                        val updatedGtHistory = (_uiState.value.groundTruthHistory + pt).takeLast(300)
                        _uiState.update { it.copy(groundTruthLocation = loc, groundTruthHistory = updatedGtHistory) }
                    } else {
                        _uiState.update { it.copy(groundTruthLocation = loc) }
                    }
                }
            }
        }

        // Collect GNSS satellite metadata
        viewModelScope.launch {
            locationDataManager.gnssSummary.collect { summary ->
                latestGnssSummary = summary
            }
        }

        refreshLogFiles()
    }

    fun setSelectedTab(index: Int) {
        _uiState.update { it.copy(selectedTab = index) }
    }

    fun onPermissionsGranted(locationGranted: Boolean, sensorGranted: Boolean) {
        _uiState.update {
            it.copy(
                hasLocationPermission = locationGranted,
                hasSensorPermission = sensorGranted
            )
        }
        if (locationGranted) {
            startTracking()
        }
    }

    fun setFaultMode(mode: FaultInjectionMode) {
        positionEstimator.setFaultMode(mode)
        _uiState.update { it.copy(faultMode = mode) }
    }

    fun toggleMockLocation(enable: Boolean) {
        if (enable) {
            val success = mockLocationManager.enableMockLocation()
            _uiState.update {
                it.copy(
                    isMockLocationEnabled = success,
                    mockLocationError = mockLocationManager.lastErrorMessage
                )
            }
        } else {
            mockLocationManager.disableMockLocation()
            _uiState.update {
                it.copy(
                    isMockLocationEnabled = false,
                    mockLocationError = null
                )
            }
        }
    }

    fun resetPdr() {
        val currentLoc = latestGroundTruth
        positionEstimator.pdrEngine.reset(
            initialLat = currentLoc?.latitude ?: 0.0,
            initialLon = currentLoc?.longitude ?: 0.0
        )
        lastRecordedSteps = 0
        lastRecordedPdrLat = currentLoc?.latitude ?: 0.0
        lastRecordedPdrLon = currentLoc?.longitude ?: 0.0
        lastRecordedGtLat = currentLoc?.latitude ?: 0.0
        lastRecordedGtLon = currentLoc?.longitude ?: 0.0

        val initialHistory = if (currentLoc != null) {
            listOf(TrajectoryPoint(currentLoc.latitude, currentLoc.longitude, isPdr = false))
        } else emptyList()

        _uiState.update {
            it.copy(
                groundTruthHistory = initialHistory,
                pdrHistory = initialHistory
            )
        }
    }

    fun setCustomStepLength(lengthMeters: Float) {
        positionEstimator.pdrEngine.stepLengthEstimator.setKCoefficient(lengthMeters / 1.6f)
    }

    fun startTracking() {
        sensorDataManager.startListening()
        locationDataManager.startLocationUpdates()
        startEvaluationLoop()
        _uiState.update { it.copy(isTracking = true) }
    }

    fun stopTracking() {
        if (_uiState.value.isRecording) {
            stopRecording()
        }
        sensorDataManager.stopListening()
        locationDataManager.stopLocationUpdates()
        evaluationLoopJob?.cancel()
        TrackingService.stopService(context)
        _uiState.update { it.copy(isTracking = false) }
    }

    private fun startEvaluationLoop() {
        evaluationLoopJob?.cancel()
        evaluationLoopJob = viewModelScope.launch {
            while (isActive) {
                performThrottledCycle()
                delay(200) // 5 Hz throttled cycle for smooth UI performance
            }
        }
    }

    private fun performThrottledCycle() {
        val sensors = latestSensors
        val rawGt = latestGroundTruth
        val gnssSum = latestGnssSummary

        val fusionOutput = positionEstimator.processFrame(
            rawGroundTruthLocation = rawGt,
            sensors = sensors,
            gnssSummary = gnssSum,
            currentTimeMs = System.currentTimeMillis()
        )

        val pdrState = fusionOutput.pdrState
        val currentPdrHistory = _uiState.value.pdrHistory
        val updatedPdrHistory: List<TrajectoryPoint>

        if (pdrState.estimatedLatitude != 0.0) {
            val dLat = pdrState.estimatedLatitude - lastRecordedPdrLat
            val dLon = pdrState.estimatedLongitude - lastRecordedPdrLon
            val distApproxM = Math.hypot(dLat * 111000.0, dLon * 111000.0 * Math.cos(Math.toRadians(pdrState.estimatedLatitude)))
            val hasNewStep = pdrState.totalSteps > lastRecordedSteps

            if (hasNewStep || distApproxM >= 0.5 || lastRecordedPdrLat == 0.0) {
                lastRecordedSteps = pdrState.totalSteps
                lastRecordedPdrLat = pdrState.estimatedLatitude
                lastRecordedPdrLon = pdrState.estimatedLongitude
                val pdrPt = TrajectoryPoint(
                    latitude = pdrState.estimatedLatitude,
                    longitude = pdrState.estimatedLongitude,
                    timestampMs = System.currentTimeMillis(),
                    isPdr = true
                )
                updatedPdrHistory = (currentPdrHistory + pdrPt).takeLast(300)
            } else {
                updatedPdrHistory = currentPdrHistory
            }
        } else {
            updatedPdrHistory = currentPdrHistory
        }

        val updatedRecent = (listOf(fusionOutput.assessment) + _uiState.value.recentAssessments).take(20)

        // If mock location is enabled, publish PDR position to Android OS
        if (_uiState.value.isMockLocationEnabled && pdrState.estimatedLatitude != 0.0) {
            mockLocationManager.publishMockLocation(
                latitude = pdrState.estimatedLatitude,
                longitude = pdrState.estimatedLongitude,
                altitude = rawGt?.altitude ?: 0.0,
                accuracy = if (pdrState.isPdrActive) 8.0f else 3.0f,
                speed = rawGt?.speed ?: 1.2f,
                bearing = pdrState.headingDegrees
            )
        }

        _uiState.update {
            it.copy(
                groundTruthLocation = rawGt,
                simulatedGnssLocation = fusionOutput.simulatedGnssLocation,
                sensors = sensors,
                gnssSummary = gnssSum,
                assessment = fusionOutput.assessment,
                pdrState = pdrState,
                pdrHistory = updatedPdrHistory,
                faultMode = fusionOutput.faultMode,
                recentAssessments = updatedRecent,
                recordedSamplesCount = dataLogger.recordedSamplesCount
            )
        }

        if (dataLogger.isRecording) {
            dataLogger.logSample(
                groundTruth = rawGt,
                simulatedGnss = fusionOutput.simulatedGnssLocation,
                sensors = sensors,
                gnssSummary = gnssSum,
                assessment = fusionOutput.assessment,
                pdrState = pdrState,
                faultMode = fusionOutput.faultMode
            )
        }
    }

    fun startRecording() {
        if (!_uiState.value.isTracking) {
            startTracking()
        }
        dataLogger.startNewSession()
        recordingStartTimeMs = System.currentTimeMillis()
        TrackingService.startService(context)

        recordingTimerJob?.cancel()
        recordingTimerJob = viewModelScope.launch {
            while (isActive) {
                val durationSec = (System.currentTimeMillis() - recordingStartTimeMs) / 1000
                _uiState.update {
                    it.copy(
                        isRecording = true,
                        recordingDurationSec = durationSec,
                        recordedSamplesCount = dataLogger.recordedSamplesCount
                    )
                }
                delay(1000)
            }
        }
    }

    fun stopRecording() {
        dataLogger.stopSession()
        recordingTimerJob?.cancel()
        _uiState.update {
            it.copy(
                isRecording = false,
                recordedSamplesCount = dataLogger.recordedSamplesCount
            )
        }
        refreshLogFiles()
    }

    fun refreshLogFiles() {
        val files = dataLogger.getLogFiles()
        _uiState.update { it.copy(logFiles = files) }
    }

    fun getShareIntentForFile(file: File): Intent {
        return dataLogger.shareLogFile(file)
    }

    override fun onCleared() {
        super.onCleared()
        mockLocationManager.disableMockLocation()
        sensorDataManager.stopListening()
        locationDataManager.stopLocationUpdates()
        dataLogger.stopSession()
        TrackingService.stopService(context)
    }
}
