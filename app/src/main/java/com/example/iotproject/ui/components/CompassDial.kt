package com.example.iotproject.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.iotproject.ui.theme.*

@Composable
fun CompassDial(
    azimuthDegrees: Float,
    modifier: Modifier = Modifier
) {
    val animatedAzimuth by animateFloatAsState(
        targetValue = azimuthDegrees,
        animationSpec = spring(stiffness = 150f),
        label = "compassRotation"
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Slate800)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier.size(115.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val radius = size.minDimension / 2f
                    val center = Offset(size.width / 2f, size.height / 2f)

                    drawCircle(
                        color = Slate700,
                        radius = radius,
                        center = center,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f)
                    )

                    for (deg in 0 until 360 step 30) {
                        val isCardinal = deg % 90 == 0
                        val tickLength = if (isCardinal) 10f else 5f
                        val tickColor = if (isCardinal) CyanAccent else Slate500

                        rotate(deg.toFloat(), center) {
                            drawLine(
                                color = tickColor,
                                start = Offset(center.x, center.y - radius),
                                end = Offset(center.x, center.y - radius + tickLength),
                                strokeWidth = if (isCardinal) 2.5f else 1.5f
                            )
                        }
                    }

                    rotate(-animatedAzimuth, center) {
                        val northPath = Path().apply {
                            moveTo(center.x, center.y - radius + 12f)
                            lineTo(center.x - 6f, center.y)
                            lineTo(center.x + 6f, center.y)
                            close()
                        }
                        drawPath(northPath, color = RoseError)

                        val southPath = Path().apply {
                            moveTo(center.x, center.y + radius - 12f)
                            lineTo(center.x - 6f, center.y)
                            lineTo(center.x + 6f, center.y)
                            close()
                        }
                        drawPath(southPath, color = Slate400)

                        drawCircle(color = Slate900, radius = 4.5f, center = center)
                        drawCircle(color = CyanAccent, radius = 2f, center = center)
                    }
                }

                Text(
                    text = "N",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = RoseError,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 2.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Heading & Azimuth",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Slate100
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = String.format("%.1f°", azimuthDegrees),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = CyanAccent
                )

                Surface(
                    color = CoralOrange.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Text(
                        text = getCardinalDirection(azimuthDegrees),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = CoralOrange,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "6-DOF IMU Fused Orientation",
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate400,
                    fontSize = 11.sp
                )
            }
        }
    }
}

private fun getCardinalDirection(deg: Float): String {
    val d = (deg % 360 + 360) % 360
    return when {
        d >= 337.5 || d < 22.5 -> "North (N)"
        d >= 22.5 && d < 67.5 -> "North-East (NE)"
        d >= 67.5 && d < 112.5 -> "East (E)"
        d >= 112.5 && d < 157.5 -> "South-East (SE)"
        d >= 157.5 && d < 202.5 -> "South (S)"
        d >= 202.5 && d < 247.5 -> "South-West (SW)"
        d >= 247.5 && d < 292.5 -> "West (W)"
        else -> "North-West (NW)"
    }
}
