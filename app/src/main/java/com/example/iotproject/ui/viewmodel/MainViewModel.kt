package com.example.iotproject.ui.viewmodel

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.iotproject.data.location.LocationDataManager
import com.example.iotproject.data.logging.DataLogger
import com.example.iotproject.data.model.AssessmentResult
import com.example.iotproject.data.model.GnssConstellationSummary
import com.example.iotproject.data.model.GnssStatusState
import com.example.iotproject.data.model.LocationData
import com.example.iotproject.data.model.SensorSnapshot
import com.example.iotproject.data.sensor.SensorDataManager
import com.example.iotproject.domain.assessment.GnssIntegrityEvaluator
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
    val location: LocationData? = null,
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
    val recentAssessments: List<AssessmentResult> = emptyList()
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val sensorDataManager = SensorDataManager(context)
    private val locationDataManager = LocationDataManager(context)
    private val integrityEvaluator = GnssIntegrityEvaluator()
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
            }
        }

        // Collect location updates
        viewModelScope.launch {
            locationDataManager.currentLocation.collect { loc ->
                _uiState.update { it.copy(location = loc) }
                performEvaluation()
            }
        }

        // Collect GNSS satellite metadata
        viewModelScope.launch {
            locationDataManager.gnssSummary.collect { summary ->
                _uiState.update { it.copy(gnssSummary = summary) }
                performEvaluation()
            }
        }

        refreshLogFiles()
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
                performEvaluation()
                delay(500) // Periodic assessment to catch timeout & staleness
            }
        }
    }

    private fun performEvaluation() {
        val currentState = _uiState.value
        val assessment = integrityEvaluator.evaluate(
            currentLocation = currentState.location,
            sensorSnapshot = currentState.sensors,
            gnssSummary = currentState.gnssSummary,
            currentTimeMs = System.currentTimeMillis()
        )

        val updatedRecent = (listOf(assessment) + currentState.recentAssessments).take(20)

        _uiState.update {
            it.copy(
                assessment = assessment,
                recentAssessments = updatedRecent,
                recordedSamplesCount = dataLogger.recordedSamplesCount
            )
        }

        if (dataLogger.isRecording) {
            dataLogger.logSample(
                location = currentState.location,
                sensors = currentState.sensors,
                gnssSummary = currentState.gnssSummary,
                assessment = assessment
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
