package com.example.iotproject.ui.screens

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.iotproject.ui.viewmodel.MainViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    var showLogsDialog by remember { mutableStateOf(false) }

    // Required permissions
    val permissionsToRequest = remember {
        val list = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            list.add(Manifest.permission.ACTIVITY_RECOGNITION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        list.toTypedArray()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val fineLocation = results[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocation = results[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        val locationGranted = fineLocation || coarseLocation
        val sensorGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            results[Manifest.permission.ACTIVITY_RECOGNITION] ?: false
        } else true

        viewModel.onPermissionsGranted(locationGranted, sensorGranted)
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(permissionsToRequest)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "GNSS Resilient Tracker",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Phase 1: Ingestion, Integrity & Logging",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        viewModel.refreshLogFiles()
                        showLogsDialog = true
                    }) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "View CSV Logs"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)
        ) {
            // 1. Recording & Tracking Controls Card
            item {
                ControlCard(
                    isTracking = uiState.isTracking,
                    isRecording = uiState.isRecording,
                    recordingDurationSec = uiState.recordingDurationSec,
                    recordedSamplesCount = uiState.recordedSamplesCount,
                    onToggleTracking = {
                        if (uiState.isTracking) viewModel.stopTracking() else viewModel.startTracking()
                    },
                    onToggleRecording = {
                        if (uiState.isRecording) viewModel.stopRecording() else viewModel.startRecording()
                    },
                    onOpenLogs = {
                        viewModel.refreshLogFiles()
                        showLogsDialog = true
                    }
                )
            }

            // 2. GNSS Integrity Assessment Card
            item {
                IntegrityStatusCard(assessment = uiState.assessment)
            }

            // 3. Location / GNSS Card
            item {
                LocationCard(location = uiState.location)
            }

            // 4. IMU Sensors Card
            item {
                SensorCard(sensors = uiState.sensors)
            }

            // 5. Constellation & Satellite Summary Card
            item {
                ConstellationCard(summary = uiState.gnssSummary)
            }
        }
    }

    if (showLogsDialog) {
        LogsListDialog(
            files = uiState.logFiles,
            onDismiss = { showLogsDialog = false },
            onShareFile = { file ->
                val shareIntent = viewModel.getShareIntentForFile(file)
                context.startActivity(android.content.Intent.createChooser(shareIntent, "Share CSV Log"))
            },
            onRefresh = { viewModel.refreshLogFiles() }
        )
    }
}

@Composable
fun ControlCard(
    isTracking: Boolean,
    isRecording: Boolean,
    recordingDurationSec: Long,
    recordedSamplesCount: Long,
    onToggleTracking: () -> Unit,
    onToggleRecording: () -> Unit,
    onOpenLogs: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (isRecording) "Recording Session Active" else if (isTracking) "Live Tracking Active" else "Tracking Paused",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isRecording) Color(0xFFC2185B) else MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    if (isRecording) {
                        val minutes = recordingDurationSec / 60
                        val seconds = recordingDurationSec % 60
                        Text(
                            text = "Duration: %02d:%02d | Samples: $recordedSamplesCount".format(minutes, seconds),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onToggleRecording,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = if (isRecording) "Stop CSV Log" else "Record CSV"
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isRecording) "Stop" else "Record CSV")
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                OutlinedButton(
                    onClick = onToggleTracking,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (isTracking) "Pause Sensors" else "Resume Sensors")
                }
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(
                    onClick = onOpenLogs,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Saved CSVs")
                }
            }
        }
    }
}

@Composable
fun LogsListDialog(
    files: List<File>,
    onDismiss: () -> Unit,
    onShareFile: (File) -> Unit,
    onRefresh: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Saved CSV Sessions")
                IconButton(onClick = onRefresh) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh")
                }
            }
        },
        text = {
            if (files.isEmpty()) {
                Text("No recorded CSV log sessions found yet. Tap 'Record CSV' on the main screen.")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(files) { file ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = file.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${dateFormat.format(Date(file.lastModified()))} • ${(file.length() / 1024)} KB",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { onShareFile(file) }) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = "Share File",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
