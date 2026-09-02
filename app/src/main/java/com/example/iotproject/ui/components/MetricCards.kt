package com.example.iotproject.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.iotproject.data.model.AssessmentResult
import com.example.iotproject.data.model.GnssStatusState
import com.example.iotproject.ui.theme.*
import java.util.Locale

@Composable
fun PulsingStatusBadge(state: GnssStatusState, modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

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
                    .scale(scale)
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
fun ErrorGaugeCard(
    errorMeters: Float,
    isPdrActive: Boolean,
    onResetPdr: () -> Unit,
    modifier: Modifier = Modifier
) {
    val gaugeColor = when {
        errorMeters <= 5f -> EmeraldGreen
        errorMeters <= 15f -> AmberWarning
        else -> RoseError
    }

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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Estimation Error",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Slate100
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = if (isPdrActive) CoralOrange.copy(alpha = 0.2f) else Slate700,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (isPdrActive) "PDR AUTONOMOUS" else "GNSS LOCKED",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isPdrActive) CoralOrange else Slate300,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                IconButton(onClick = onResetPdr, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.RestartAlt,
                        contentDescription = "Reset PDR",
                        tint = CyanAccent
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = String.format("%.2f m", errorMeters),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = gaugeColor
                )
                Text(
                    text = "Haversine (Truth vs PDR)",
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate400
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .background(Slate700, RoundedCornerShape(5.dp))
            ) {
                val fraction = (errorMeters / 40f).coerceIn(0.02f, 1f)
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction)
                        .background(gaugeColor, RoundedCornerShape(5.dp))
                )
            }
        }
    }
}

@Composable
fun IntegrityBannerCard(assessment: AssessmentResult, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = assessment.state.color.copy(alpha = 0.12f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "GNSS Integrity Verdict",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Slate100
                )
                PulsingStatusBadge(state = assessment.state)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = assessment.state.description,
                style = MaterialTheme.typography.bodyMedium,
                color = Slate300
            )

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = Slate700)
            Spacer(modifier = Modifier.height(10.dp))

            assessment.reasons.forEach { reason ->
                Row(
                    modifier = Modifier.padding(vertical = 2.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text("• ", color = assessment.state.color, fontWeight = FontWeight.Bold)
                    Text(
                        text = reason,
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate200
                    )
                }
            }
        }
    }
}

@Composable
fun InfoMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Slate400
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = Slate100
        )
    }
}
