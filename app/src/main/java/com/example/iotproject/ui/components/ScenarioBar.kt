package com.example.iotproject.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.iotproject.data.model.FaultInjectionMode
import com.example.iotproject.ui.theme.*

@Composable
fun ScenarioPresetBar(
    currentMode: FaultInjectionMode,
    onSelectMode: (FaultInjectionMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Slate800)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Live Presentation Demo Scenarios",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Slate100
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ScenarioButton(
                    title = "Normal",
                    icon = Icons.Default.GpsFixed,
                    isActive = currentMode == FaultInjectionMode.NORMAL,
                    color = CyanAccent,
                    onClick = { onSelectMode(FaultInjectionMode.NORMAL) },
                    modifier = Modifier.weight(1f)
                )

                ScenarioButton(
                    title = "Tunnel",
                    icon = Icons.Default.GpsOff,
                    isActive = currentMode == FaultInjectionMode.OUTAGE,
                    color = RoseError,
                    onClick = { onSelectMode(FaultInjectionMode.OUTAGE) },
                    modifier = Modifier.weight(1f)
                )

                ScenarioButton(
                    title = "Spoofing",
                    icon = Icons.Default.Warning,
                    isActive = currentMode == FaultInjectionMode.POSITION_JUMP,
                    color = CoralOrange,
                    onClick = { onSelectMode(FaultInjectionMode.POSITION_JUMP) },
                    modifier = Modifier.weight(1f)
                )

                ScenarioButton(
                    title = "Recovery",
                    icon = Icons.Default.Autorenew,
                    isActive = currentMode == FaultInjectionMode.RECOVERY,
                    color = EmeraldGreen,
                    onClick = { onSelectMode(FaultInjectionMode.RECOVERY) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun ScenarioButton(
    title: String,
    icon: ImageVector,
    isActive: Boolean,
    color: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isActive) color else Slate700,
            contentColor = if (isActive) Slate900 else Slate200
        ),
        contentPadding = PaddingValues(vertical = 8.dp, horizontal = 4.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(imageVector = icon, contentDescription = title, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = title, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
    }
}
