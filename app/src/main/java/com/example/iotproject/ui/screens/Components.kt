package com.example.iotproject.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.iotproject.data.model.AssessmentResult
import com.example.iotproject.data.model.GnssConstellationSummary
import com.example.iotproject.data.model.GnssStatusState
import com.example.iotproject.data.model.LocationData
import com.example.iotproject.data.model.SensorSnapshot
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
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
fun IntegrityStatusCard(assessment: AssessmentResult, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = assessment.state.color.copy(alpha = 0.08f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "GNSS Integrity Status",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                StatusBadge(state = assessment.state)
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = assessment.state.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Assessment Factors:",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(6.dp))

            assessment.reasons.forEach { reason ->
                Row(
                    modifier = Modifier.padding(vertical = 2.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text("• ", color = assessment.state.color, fontWeight = FontWeight.Bold)
                    Text(
                        text = reason,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricItem(label = "Satellites in Fix", value = "${assessment.satellitesInFix}")
                MetricItem(
                    label = "Staleness",
                    value = if (assessment.timeSinceLastFixSec > 100) "--" else "${String.format("%.1f", assessment.timeSinceLastFixSec)}s"
                )
                MetricItem(
                    label = "Jump Shift",
                    value = if (assessment.jumpDistanceMeters > 0) "${String.format("%.1f", assessment.jumpDistanceMeters)}m" else "None"
                )
            }
        }
    }
}

@Composable
fun LocationCard(location: LocationData?, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Location & GNSS Position",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (location == null) {
                Text(
                    text = "Awaiting Location Fix (GPS)...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            } else {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        MetricItem(label = "Latitude", value = String.format("%.6f°", location.latitude))
                        Spacer(modifier = Modifier.height(8.dp))
                        MetricItem(label = "Altitude", value = String.format("%.1f m", location.altitude))
                        Spacer(modifier = Modifier.height(8.dp))
                        MetricItem(label = "Speed", value = String.format("%.1f m/s (%.1f km/h)", location.speed, location.speed * 3.6f))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        MetricItem(label = "Longitude", value = String.format("%.6f°", location.longitude))
                        Spacer(modifier = Modifier.height(8.dp))
                        MetricItem(label = "Accuracy", value = String.format("±%.1f m", location.accuracy))
                        Spacer(modifier = Modifier.height(8.dp))
                        MetricItem(label = "Bearing", value = String.format("%.1f°", location.bearing))
                    }
                }
            }
        }
    }
}

@Composable
fun SensorCard(sensors: SensorSnapshot, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "IMU & Motion Sensors",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    color = if (sensors.isDeviceStationary) Color(0xFF1976D2).copy(alpha = 0.15f) else Color(0xFF388E3C).copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = if (sensors.isDeviceStationary) "STATIONARY" else "MOVING",
                        color = if (sensors.isDeviceStationary) Color(0xFF1976D2) else Color(0xFF388E3C),
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    MetricItem(
                        label = "Accelerometer (m/s²)",
                        value = "x:${String.format("%.1f", sensors.accelerometer.x)} y:${String.format("%.1f", sensors.accelerometer.y)} z:${String.format("%.1f", sensors.accelerometer.z)}"
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    MetricItem(
                        label = "Gyroscope (rad/s)",
                        value = "x:${String.format("%.2f", sensors.gyroscope.x)} y:${String.format("%.2f", sensors.gyroscope.y)} z:${String.format("%.2f", sensors.gyroscope.z)}"
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    MetricItem(
                        label = "Total Steps",
                        value = "${sensors.steps.totalSteps}"
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    MetricItem(
                        label = "Linear Accel Dynamic",
                        value = "${String.format("%.2f", sensors.dynamicAccelMagnitude)} m/s²"
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    MetricItem(
                        label = "Heading (Azimuth)",
                        value = "${String.format("%.1f°", sensors.rotation.azimuthDegrees)}"
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    MetricItem(
                        label = "Magnetometer (µT)",
                        value = "${String.format("%.1f", sensors.magnetometer.magnitude)} µT"
                    )
                }
            }
        }
    }
}

@Composable
fun ConstellationCard(summary: GnssConstellationSummary, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "GNSS Constellations & Satellites",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricItem(label = "Total Satellites", value = "${summary.totalSatellites}")
                MetricItem(label = "Used in Fix", value = "${summary.usedInFixCount}")
                MetricItem(label = "Avg C/N0", value = "${String.format("%.1f", summary.avgCn0)} dB-Hz")
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ConstellationPill(name = "GPS", count = summary.gpsCount)
                ConstellationPill(name = "GLONASS", count = summary.glonassCount)
                ConstellationPill(name = "Galileo", count = summary.galileoCount)
                ConstellationPill(name = "BeiDou", count = summary.beidouCount)
            }
        }
    }
}

@Composable
fun ConstellationPill(name: String, count: Int) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(name, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "$count",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun MetricItem(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace
        )
    }
}
