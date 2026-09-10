package com.example.iotproject.ui.screens.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.iotproject.data.model.GnssConstellationSummary
import com.example.iotproject.ui.components.InfoMetric
import com.example.iotproject.ui.components.SatelliteSignalList
import com.example.iotproject.ui.theme.*

@Composable
fun SatellitesTab(
    summary: GnssConstellationSummary,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp)
    ) {
        // 1. Constellations Overview Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Slate800)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Multi-Constellation GNSS Receiver",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Slate100
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        InfoMetric(label = "Total Visible", value = "${summary.totalSatellites}")
                        InfoMetric(label = "Used in 3D Fix", value = "${summary.usedInFixCount}")
                        InfoMetric(label = "Avg Signal (C/N0)", value = String.format("%.1f dB-Hz", summary.avgCn0))
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = Slate700)
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Tracked Constellations:",
                        style = MaterialTheme.typography.labelSmall,
                        color = Slate400
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        ConstellationChip(name = "GPS", count = summary.gpsCount, color = CyanAccent)
                        ConstellationChip(name = "GLONASS", count = summary.glonassCount, color = PurpleAccent)
                        ConstellationChip(name = "Galileo", count = summary.galileoCount, color = BlueAccent)
                        ConstellationChip(name = "BeiDou", count = summary.beidouCount, color = CoralOrange)
                    }
                }
            }
        }

        // 2. Individual Satellite Signal Bars (C/N0)
        item {
            SatelliteSignalList(satellites = summary.satellites)
        }
    }
}

@Composable
fun ConstellationChip(name: String, count: Int, color: Color) {
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(name, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = color)
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "$count",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Slate100
            )
        }
    }
}
