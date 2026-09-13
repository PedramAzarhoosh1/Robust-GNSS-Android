package com.example.iotproject.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.iotproject.data.model.SatelliteInfo
import com.example.iotproject.ui.theme.*

@Composable
fun SatelliteSignalList(
    satellites: List<SatelliteInfo>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Slate800)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Live Satellite Signals (C/N0 dB-Hz)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Slate100
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (satellites.isEmpty()) {
                Text(
                    text = "Awaiting satellite signal data...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Slate400,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    satellites.take(12).forEach { sat ->
                        SatelliteRow(sat = sat)
                    }
                }
            }
        }
    }
}

@Composable
fun SatelliteRow(sat: SatelliteInfo) {
    val barColor = when {
        sat.cn0DbHz >= 35f -> EmeraldGreen
        sat.cn0DbHz >= 26f -> AmberWarning
        else -> RoseError
    }

    val constellationColor = when (sat.constellationType) {
        "GPS" -> CyanAccent
        "GLONASS" -> PurpleAccent
        "Galileo" -> BlueAccent
        "BeiDou" -> CoralOrange
        else -> Slate400
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = constellationColor.copy(alpha = 0.15f),
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.width(62.dp)
        ) {
            Text(
                text = sat.constellationType,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = constellationColor,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = "#${sat.svid}",
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            color = Slate200,
            modifier = Modifier.width(36.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .height(10.dp)
                .background(Slate700, RoundedCornerShape(5.dp))
        ) {
            val fillFraction = (sat.cn0DbHz / 50f).coerceIn(0.05f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fillFraction)
                    .background(barColor, RoundedCornerShape(5.dp))
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = String.format("%.1f", sat.cn0DbHz),
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = Slate200,
            modifier = Modifier.width(36.dp)
        )

        if (sat.usedInFix) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Used in fix",
                tint = EmeraldGreen,
                modifier = Modifier.size(16.dp)
            )
        } else {
            Spacer(modifier = Modifier.size(16.dp))
        }
    }
}
