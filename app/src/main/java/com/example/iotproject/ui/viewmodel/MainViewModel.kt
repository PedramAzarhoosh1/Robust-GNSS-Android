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
    val selectedTab: Int = 0
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val sensorDataManager = SensorDataManager(context)
    private val locationDataManager = LocationDataManager(context)
    private val positionEstimator = PositionEstimator()
    private val dataLogger = DataLogger(context)

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

    init {
        // Collect sensor updates in memory (high rate, zero UI overhead)
        viewModelScope.launch {
            sensorDataManager.sensorSnapshot.collect { sensors ->
                latestSensors = sensors
                positionEstimator.pdrEngine.processSensorSnapshot(sensors)
            }
        }

        // Collect ground truth location updates
        viewModelScope.launch {
            locationDataManager.currentLocation.collect { loc ->
                latestGroundTruth = loc
                if (loc != null) {
                    val pt = TrajectoryPoint(latitude = loc.latitude, longitude = loc.longitude, timestampMs = loc.timestamp, isPdr = false)
                    val updatedGtHistory = (_uiState.value.groundTruthHistory + pt).takeLast(200)
                    _uiState.update { it.copy(groundTruthLocation = loc, groundTruthHistory = updatedGtHistory) }
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

    fun resetPdr() {
        val currentLoc = latestGroundTruth
        positionEstimator.pdrEngine.reset(
            initialLat = currentLoc?.latitude ?: 0.0,
            initialLon = currentLoc?.longitude ?: 0.0
        )
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
                delay(200) // 5 Hz throttled cycle for rock-solid UI 60 FPS performance
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
        val pdrPt = if (pdrState.estimatedLatitude != 0.0) {
            TrajectoryPoint(
                latitude = pdrState.estimatedLatitude,
                longitude = pdrState.estimatedLongitude,
                timestampMs = System.currentTimeMillis(),
                isPdr = true
            )
        } else null

        val currentPdrHistory = _uiState.value.pdrHistory
        val updatedPdrHistory = if (pdrPt != null) {
            (currentPdrHistory + pdrPt).takeLast(200)
        } else currentPdrHistory

        val updatedRecent = (listOf(fusionOutput.assessment) + _uiState.value.recentAssessments).take(20)

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
        sensorDataManager.stopListening()
        locationDataManager.stopLocationUpdates()
        dataLogger.stopSession()
        TrackingService.stopService(context)
    }
}
