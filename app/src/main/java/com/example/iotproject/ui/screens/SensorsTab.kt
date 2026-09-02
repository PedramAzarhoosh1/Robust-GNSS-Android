package com.example.iotproject.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.iotproject.data.model.PdrState
import com.example.iotproject.data.model.SensorSnapshot
import com.example.iotproject.ui.components.CompassDial
import com.example.iotproject.ui.components.InfoMetric
import com.example.iotproject.ui.theme.*

@Composable
fun SensorsTab(
    sensors: SensorSnapshot,
    pdrState: PdrState,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp)
    ) {
        // 1. Live Compass Dial
        item {
            CompassDial(azimuthDegrees = pdrState.headingDegrees)
        }

        // 2. Weinberg PDR & Pedestrian Stride Analytics
        item {
            WeinbergPdrCard(pdrState = pdrState)
        }

        // 3. IMU Hardware Sensors Breakdown
        item {
            HardwareSensorsCard(sensors = sensors)
        }
    }
}

@Composable
fun WeinbergPdrCard(pdrState: PdrState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
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
                    text = "Weinberg PDR Stride Engine",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Slate100
                )
                Surface(
                    color = CyanAccent.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "SL = K·∜(Δa)",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = CyanAccent,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    InfoMetric(label = "Total Steps Detected", value = "${pdrState.totalSteps} steps")
                    Spacer(modifier = Modifier.height(10.dp))
                    InfoMetric(label = "Estimated Step Length", value = String.format("%.2f m", pdrState.stepLengthMeters))
                }
                Column(modifier = Modifier.weight(1f)) {
                    InfoMetric(label = "Total Distance Walked", value = String.format("%.1f m", pdrState.totalDistanceMeters))
                    Spacer(modifier = Modifier.height(10.dp))
                    InfoMetric(label = "Step Cadence", value = String.format("%.1f Hz", pdrState.stepFrequencyHz))
                }
            }
        }
    }
}

@Composable
fun HardwareSensorsCard(sensors: SensorSnapshot) {
    Card(
        modifier = Modifier.fillMaxWidth(),
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
                    text = "IMU Motion Ingestion",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Slate100
                )
                Surface(
                    color = if (sensors.isDeviceStationary) BlueAccent.copy(alpha = 0.15f) else EmeraldGreen.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (sensors.isDeviceStationary) "STATIONARY" else "MOVING",
                        color = if (sensors.isDeviceStationary) BlueAccent else EmeraldGreen,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    InfoMetric(
                        label = "Accelerometer (m/s²)",
                        value = "x:${String.format("%.1f", sensors.accelerometer.x)}\ny:${String.format("%.1f", sensors.accelerometer.y)}\nz:${String.format("%.1f", sensors.accelerometer.z)}"
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    InfoMetric(
                        label = "Gyroscope (rad/s)",
                        value = "x:${String.format("%.2f", sensors.gyroscope.x)}\ny:${String.format("%.2f", sensors.gyroscope.y)}\nz:${String.format("%.2f", sensors.gyroscope.z)}"
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    InfoMetric(
                        label = "Linear Dynamic Accel",
                        value = String.format("%.2f m/s²", sensors.dynamicAccelMagnitude)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    InfoMetric(
                        label = "Magnetometer (µT)",
                        value = String.format("%.1f µT", sensors.magnetometer.magnitude)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    InfoMetric(
                        label = "Pitch / Roll",
                        value = String.format("%.0f° / %.0f°", sensors.rotation.pitchDegrees, sensors.rotation.rollDegrees)
                    )
                }
            }
        }
    }
}
