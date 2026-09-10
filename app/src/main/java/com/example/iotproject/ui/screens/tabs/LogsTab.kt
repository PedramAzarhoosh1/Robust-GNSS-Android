package com.example.iotproject.ui.screens.tabs

import android.content.Intent
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.iotproject.ui.theme.*
import com.example.iotproject.ui.viewmodel.MainUiState
import com.example.iotproject.ui.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LogsTab(
    uiState: MainUiState,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp)
    ) {
        // 1. Recording Session Control Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (uiState.isRecording) RoseError.copy(alpha = 0.15f) else Slate800
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = if (uiState.isRecording) "Recording Session..." else "Dataset Logger",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (uiState.isRecording) RoseError else Slate100
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            if (uiState.isRecording) {
                                val minutes = uiState.recordingDurationSec / 60
                                val seconds = uiState.recordingDurationSec % 60
                                Text(
                                    text = "Duration: %02d:%02d | Samples: ${uiState.recordedSamplesCount}".format(minutes, seconds),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Slate200
                                )
                            } else {
                                Text(
                                    text = "Record Ground Truth vs PDR logs for reporting",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Slate400
                                )
                            }
                        }

                        Button(
                            onClick = {
                                if (uiState.isRecording) viewModel.stopRecording() else viewModel.startRecording()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (uiState.isRecording) RoseError else CyanAccent,
                                contentColor = Slate900
                            ),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Icon(
                                imageVector = if (uiState.isRecording) Icons.Default.Stop else Icons.Default.PlayArrow,
                                contentDescription = "Toggle Record",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (uiState.isRecording) "Stop" else "Record CSV",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }

        // 2. Saved CSV Log Files List
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Saved CSV Datasets (${uiState.logFiles.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Slate100
                )
                IconButton(onClick = { viewModel.refreshLogFiles() }) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh", tint = CyanAccent)
                }
            }
        }

        if (uiState.logFiles.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Slate800)
                ) {
                    Text(
                        text = "No recorded CSV logs yet. Tap 'Record CSV' above to capture a walk session.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Slate400,
                        modifier = Modifier.padding(20.dp)
                    )
                }
            }
        } else {
            items(uiState.logFiles) { file ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Slate800)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = file.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = Slate100
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${dateFormat.format(Date(file.lastModified()))} • ${(file.length() / 1024)} KB",
                                style = MaterialTheme.typography.bodySmall,
                                color = Slate400
                            )
                        }

                        IconButton(
                            onClick = {
                                val shareIntent = viewModel.getShareIntentForFile(file)
                                context.startActivity(Intent.createChooser(shareIntent, "Share CSV Dataset"))
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share File",
                                tint = CyanAccent
                            )
                        }
                    }
                }
            }
        }
    }
}
