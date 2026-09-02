package com.example.iotproject.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.iotproject.data.model.*
import com.example.iotproject.ui.theme.*
import java.util.Locale

@Composable
fun StatusBadge(state: GnssStatusState, modifier: Modifier = Modifier) {
    Surface(
        color = state.color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, state.color.copy(alpha = 0.5f)),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(state.color)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = state.displayName.uppercase(Locale.ROOT),
                color = state.color,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
fun FaultInjectionCard(
    currentMode: FaultInjectionMode,
    onSelectMode: (FaultInjectionMode) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (currentMode == FaultInjectionMode.NORMAL)
                Slate800
            else
                RoseError.copy(alpha = 0.15f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (currentMode != FaultInjectionMode.NORMAL) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Fault Active",
                            tint = RoseError,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = "Fault Simulation Engine",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Slate100
                    )
                }

                Box {
                    Button(
                        onClick = { expanded = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (currentMode == FaultInjectionMode.NORMAL)
                                CyanAccent
                            else
                                RoseError,
                            contentColor = Slate900
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(currentMode.displayName.take(16) + if (currentMode.displayName.length > 16) "..." else "")
                    }

                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        FaultInjectionMode.values().forEach { mode ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(mode.displayName, fontWeight = FontWeight.Bold)
                                        Text(
                                            mode.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Slate400
                                        )
                                    }
                                },
                                onClick = {
                                    onSelectMode(mode)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = currentMode.description,
                style = MaterialTheme.typography.bodySmall,
                color = Slate400
            )
        }
    }
}

@Composable
fun PdrPositionComparisonCard(
    groundTruth: LocationData?,
    pdrState: PdrState,
    onResetPdr: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Slate800)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "PDR & Ground Truth Trajectory",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Slate100
                )

                IconButton(onClick = onResetPdr, modifier = Modifier.size(32.dp)) {
                    Icon(imageVector = Icons.Default.RestartAlt, contentDescription = "Reset PDR", tint = CyanAccent)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Surface(
                color = if (pdrState.estimationErrorMeters > 30f) RoseError.copy(alpha = 0.15f)
                else EmeraldGreen.copy(alpha = 0.15f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Estimation Error (PDR vs Truth):",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Slate200
                    )
                    Text(
                        text = String.format("%.2f meters", pdrState.estimationErrorMeters),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = if (pdrState.estimationErrorMeters > 30f) RoseError else EmeraldGreen
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Ground Truth (GPS)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = TealAccent
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    if (groundTruth == null) {
                        Text("Awaiting GPS fix...", style = MaterialTheme.typography.bodySmall, color = Slate400)
                    } else {
                        MetricItem(label = "Lat", value = String.format("%.6f°", groundTruth.latitude))
                        MetricItem(label = "Lon", value = String.format("%.6f°", groundTruth.longitude))
                        MetricItem(label = "Speed", value = String.format("%.1f m/s", groundTruth.speed))
                        MetricItem(label = "Accuracy", value = String.format("±%.1f m", groundTruth.accuracy))
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Estimated (PDR)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = CoralOrange
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    if (pdrState.estimatedLatitude == 0.0) {
                        Text("Syncing initial coordinate...", style = MaterialTheme.typography.bodySmall, color = Slate400)
                    } else {
                        MetricItem(label = "Est Lat", value = String.format("%.6f°", pdrState.estimatedLatitude))
                        MetricItem(label = "Est Lon", value = String.format("%.6f°", pdrState.estimatedLongitude))
                        MetricItem(label = "Steps", value = "${pdrState.totalSteps} steps")
                        MetricItem(label = "Distance", value = String.format("%.1f m", pdrState.totalDistanceMeters))
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = Slate700)
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricItem(label = "Step Length", value = String.format("%.2f m", pdrState.stepLengthMeters))
                MetricItem(label = "PDR Heading", value = String.format("%.1f°", pdrState.headingDegrees))
                MetricItem(label = "Step Cadence", value = String.format("%.1f Hz", pdrState.stepFrequencyHz))
            }
        }
    }
}

@Composable
fun MetricItem(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Slate400
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            color = Slate100
        )
    }
}
