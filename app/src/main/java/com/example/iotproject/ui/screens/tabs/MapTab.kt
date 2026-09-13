package com.example.iotproject.ui.screens.tabs

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.iotproject.data.model.FaultInjectionMode
import com.example.iotproject.ui.components.*
import com.example.iotproject.ui.theme.*
import com.example.iotproject.ui.viewmodel.MainUiState
import com.example.iotproject.ui.viewmodel.MainViewModel

@Composable
fun MapTab(
    uiState: MainUiState,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isExpanded by remember { mutableStateOf(false) }
    var scenarioDropdownOpen by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        // 1. Full-Screen Native Real Street Map (OSMDroid & HD Street Tiles)
        LiveMapView(
            groundTruthHistory = uiState.groundTruthHistory,
            pdrHistory = uiState.pdrHistory,
            currentGroundTruth = uiState.groundTruthLocation,
            currentPdr = uiState.pdrState,
            modifier = Modifier.fillMaxSize()
        )

        // 2. Centered Floating Scenario Dropdown Pill (Ultra-clean, compact, no conflict with right-side controls)
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 10.dp)
        ) {
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { scenarioDropdownOpen = true },
                color = Slate900.copy(alpha = 0.94f),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, Slate700.copy(alpha = 0.8f)),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val activeColor = when (uiState.faultMode) {
                        FaultInjectionMode.NORMAL -> EmeraldGreen
                        FaultInjectionMode.OUTAGE -> RoseError
                        FaultInjectionMode.POSITION_JUMP -> CoralOrange
                        FaultInjectionMode.RECOVERY -> EmeraldGreen
                        FaultInjectionMode.FROZEN_LOCATION -> RoseError
                        FaultInjectionMode.GRADUAL_DRIFT, FaultInjectionMode.DELAYED_UPDATES -> AmberWarning
                    }
                    val labelText = when (uiState.faultMode) {
                        FaultInjectionMode.NORMAL -> "Scenario: Normal (No Fault)"
                        FaultInjectionMode.OUTAGE -> "Scenario: Tunnel / Outage"
                        FaultInjectionMode.POSITION_JUMP -> "Scenario: Position Jump (+80m)"
                        FaultInjectionMode.RECOVERY -> "Scenario: Smooth Recovery"
                        FaultInjectionMode.FROZEN_LOCATION -> "Scenario: Frozen Position"
                        FaultInjectionMode.GRADUAL_DRIFT -> "Scenario: Gradual Drift (+1.5m/s)"
                        FaultInjectionMode.DELAYED_UPDATES -> "Scenario: Delayed Updates (5s)"
                    }

                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(activeColor)
                    )
                    Text(
                        text = labelText,
                        color = Color.White,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Select Scenario",
                        tint = Slate400,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            DropdownMenu(
                expanded = scenarioDropdownOpen,
                onDismissRequest = { scenarioDropdownOpen = false },
                modifier = Modifier
                    .background(Slate900)
                    .border(BorderStroke(1.dp, Slate700), RoundedCornerShape(12.dp))
            ) {
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(EmeraldGreen))
                            Text("Normal (No Fault)", color = Slate100, fontSize = 13.sp)
                        }
                    },
                    onClick = {
                        viewModel.setFaultMode(FaultInjectionMode.NORMAL)
                        scenarioDropdownOpen = false
                    }
                )
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(RoseError))
                            Text("Tunnel / GNSS Outage", color = Slate100, fontSize = 13.sp)
                        }
                    },
                    onClick = {
                        viewModel.setFaultMode(FaultInjectionMode.OUTAGE)
                        scenarioDropdownOpen = false
                    }
                )
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(CoralOrange))
                            Text("Position Jump / Spoofing (+80m)", color = Slate100, fontSize = 13.sp)
                        }
                    },
                    onClick = {
                        viewModel.setFaultMode(FaultInjectionMode.POSITION_JUMP)
                        scenarioDropdownOpen = false
                    }
                )
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(EmeraldGreen))
                            Text("Smooth Recovery (4-Fix Holdoff)", color = Slate100, fontSize = 13.sp)
                        }
                    },
                    onClick = {
                        viewModel.setFaultMode(FaultInjectionMode.RECOVERY)
                        scenarioDropdownOpen = false
                    }
                )
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(RoseError))
                            Text("Frozen Location (Stall)", color = Slate100, fontSize = 13.sp)
                        }
                    },
                    onClick = {
                        viewModel.setFaultMode(FaultInjectionMode.FROZEN_LOCATION)
                        scenarioDropdownOpen = false
                    }
                )
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(AmberWarning))
                            Text("Gradual Drift (+1.5 m/s ramp)", color = Slate100, fontSize = 13.sp)
                        }
                    },
                    onClick = {
                        viewModel.setFaultMode(FaultInjectionMode.GRADUAL_DRIFT)
                        scenarioDropdownOpen = false
                    }
                )
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(AmberWarning))
                            Text("Delayed Updates (5s Stale)", color = Slate100, fontSize = 13.sp)
                        }
                    },
                    onClick = {
                        viewModel.setFaultMode(FaultInjectionMode.DELAYED_UPDATES)
                        scenarioDropdownOpen = false
                    }
                )
            }
        }

        // 3. Sleek Collapsible Bottom Telemetry & Mock Location Dock
        Card(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .animateContentSize(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Slate900.copy(alpha = 0.94f)),
            border = BorderStroke(1.dp, Slate700.copy(alpha = 0.6f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                // Main Header Row (Always Visible)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Compact Telemetry Summary (When Collapsed)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { isExpanded = !isExpanded }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Mock Location",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = Slate100
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = if (uiState.isMockLocationEnabled) EmeraldGreen.copy(alpha = 0.2f) else Slate700,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = if (uiState.isMockLocationEnabled) "ON" else "OFF",
                                    color = if (uiState.isMockLocationEnabled) EmeraldGreen else Slate400,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.sp,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Drift: ${String.format("%.2fm", uiState.pdrState.estimationErrorMeters)}",
                                color = if (uiState.pdrState.estimationErrorMeters > 20f) RoseError else EmeraldGreen,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            )
                        }

                        if (!isExpanded) {
                            Spacer(modifier = Modifier.height(2.dp))
                            val gtStr = if (uiState.groundTruthLocation != null)
                                "${String.format("%.4f", uiState.groundTruthLocation.latitude)}, ${String.format("%.4f", uiState.groundTruthLocation.longitude)}"
                            else "No Fix"
                            val pdrStr = if (uiState.pdrState.estimatedLatitude > 1.0)
                                "${String.format("%.4f", uiState.pdrState.estimatedLatitude)}, ${String.format("%.4f", uiState.pdrState.estimatedLongitude)}"
                            else "Syncing"
                            Text(
                                text = "GPS: $gtStr • PDR: $pdrStr",
                                color = Slate400,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp
                            )
                        }
                    }

                    // Quick Switch + Expand Chevron
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = uiState.isMockLocationEnabled,
                            onCheckedChange = { viewModel.toggleMockLocation(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Slate900,
                                checkedTrackColor = CyanAccent,
                                uncheckedThumbColor = Slate400,
                                uncheckedTrackColor = Slate700
                            ),
                            modifier = Modifier.scale(0.85f)
                        )

                        IconButton(
                            onClick = { isExpanded = !isExpanded },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                                contentDescription = if (isExpanded) "Collapse" else "Expand",
                                tint = CyanAccent,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                // Expanded Detailed Section
                if (isExpanded) {
                    if (uiState.mockLocationError != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(
                            color = RoseError.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = uiState.mockLocationError ?: "",
                                    color = RoseError,
                                    fontSize = 10.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(
                                    onClick = {
                                        context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
                                    },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("Dev Settings", fontSize = 10.sp, color = CyanAccent)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Side-by-Side Dual Telemetry Cards
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // GPS Fix
                        Surface(
                            modifier = Modifier.weight(1f),
                            color = Slate800,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, TealAccent.copy(alpha = 0.3f))
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(6.dp).background(TealAccent, CircleShape))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("GPS SATELLITE", color = TealAccent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                if (uiState.groundTruthLocation != null) {
                                    Text("Lat: ${String.format("%.5f", uiState.groundTruthLocation.latitude)}°", color = Slate200, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                                    Text("Lon: ${String.format("%.5f", uiState.groundTruthLocation.longitude)}°", color = Slate200, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                                    Text("Acc: ±${String.format("%.1f", uiState.groundTruthLocation.accuracy)}m", color = Slate400, fontSize = 9.sp)
                                } else {
                                    Text("Acquiring...", color = Slate400, fontSize = 10.sp)
                                }
                            }
                        }

                        // PDR Dead Reckoning
                        Surface(
                            modifier = Modifier.weight(1f),
                            color = Slate800,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, CoralOrange.copy(alpha = 0.3f))
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(6.dp).background(CoralOrange, CircleShape))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("PDR DEAD RECK.", color = CoralOrange, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                if (uiState.pdrState.estimatedLatitude > 1.0) {
                                    Text("Lat: ${String.format("%.5f", uiState.pdrState.estimatedLatitude)}°", color = Slate200, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                                    Text("Lon: ${String.format("%.5f", uiState.pdrState.estimatedLongitude)}°", color = Slate200, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                                    Text("Steps: ${uiState.pdrState.totalSteps} | ${String.format("%.1fm", uiState.pdrState.totalDistanceMeters)}", color = Slate400, fontSize = 9.sp)
                                } else {
                                    Text("Calibrating...", color = Slate400, fontSize = 10.sp)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Haversine Drift Banner
                    Surface(
                        color = (if (uiState.pdrState.estimationErrorMeters > 20f) RoseError else EmeraldGreen).copy(alpha = 0.12f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Distance Error:", color = Slate300, fontSize = 10.sp)
                            Text(
                                text = String.format("%.2f meters", uiState.pdrState.estimationErrorMeters),
                                color = if (uiState.pdrState.estimationErrorMeters > 20f) RoseError else EmeraldGreen,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
