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

    private var evaluationLoopJob: Job? = null
    private var recordingTimerJob: Job? = null
    private var recordingStartTimeMs: Long = 0L

    init {
        // Collect sensor updates
        viewModelScope.launch {
            sensorDataManager.sensorSnapshot.collect { sensors ->
                _uiState.update { it.copy(sensors = sensors) }
                // Feed high-rate sensors directly to PDR
                positionEstimator.pdrEngine.processSensorSnapshot(sensors)
                updatePdrSnapshot()
            }
        }

        // Collect ground truth location updates
        viewModelScope.launch {
            locationDataManager.currentLocation.collect { loc ->
                if (loc != null) {
                    val pt = TrajectoryPoint(latitude = loc.latitude, longitude = loc.longitude, timestampMs = loc.timestamp, isPdr = false)
                    val updatedGtHistory = (_uiState.value.groundTruthHistory + pt).takeLast(200)
                    _uiState.update { it.copy(groundTruthLocation = loc, groundTruthHistory = updatedGtHistory) }
                } else {
                    _uiState.update { it.copy(groundTruthLocation = null) }
                }
                performFusionCycle()
            }
        }

        // Collect GNSS satellite metadata
        viewModelScope.launch {
            locationDataManager.gnssSummary.collect { summary ->
                _uiState.update { it.copy(gnssSummary = summary) }
                performFusionCycle()
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
        performFusionCycle()
    }

    fun resetPdr() {
        val currentLoc = _uiState.value.groundTruthLocation
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
        updatePdrSnapshot()
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
                performFusionCycle()
                delay(400) // Continuous fusion & timeout assessment cycle
            }
        }
    }

    private fun performFusionCycle() {
        val currentState = _uiState.value
        val fusionOutput = positionEstimator.processFrame(
            rawGroundTruthLocation = currentState.groundTruthLocation,
            sensors = currentState.sensors,
            gnssSummary = currentState.gnssSummary,
            currentTimeMs = System.currentTimeMillis()
        )

        val updatedRecent = (listOf(fusionOutput.assessment) + currentState.recentAssessments).take(20)

        _uiState.update {
            it.copy(
                simulatedGnssLocation = fusionOutput.simulatedGnssLocation,
                assessment = fusionOutput.assessment,
                pdrState = fusionOutput.pdrState,
                faultMode = fusionOutput.faultMode,
                recentAssessments = updatedRecent,
                recordedSamplesCount = dataLogger.recordedSamplesCount
            )
        }

        if (dataLogger.isRecording) {
            dataLogger.logSample(
                groundTruth = currentState.groundTruthLocation,
                simulatedGnss = fusionOutput.simulatedGnssLocation,
                sensors = currentState.sensors,
                gnssSummary = currentState.gnssSummary,
                assessment = fusionOutput.assessment,
                pdrState = fusionOutput.pdrState,
                faultMode = fusionOutput.faultMode
            )
        }
    }

    private fun updatePdrSnapshot() {
        val state = _uiState.value
        val pdrState = positionEstimator.pdrEngine.getPdrState(
            groundTruth = state.groundTruthLocation,
            isPdrActive = positionEstimator.pdrEngine.isReady()
        )

        val pdrPt = if (pdrState.estimatedLatitude != 0.0) {
            TrajectoryPoint(
                latitude = pdrState.estimatedLatitude,
                longitude = pdrState.estimatedLongitude,
                timestampMs = System.currentTimeMillis(),
                isPdr = true
            )
        } else null

        val updatedPdrHistory = if (pdrPt != null) {
            (state.pdrHistory + pdrPt).takeLast(200)
        } else state.pdrHistory

        _uiState.update {
            it.copy(
                pdrState = pdrState,
                pdrHistory = updatedPdrHistory
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
