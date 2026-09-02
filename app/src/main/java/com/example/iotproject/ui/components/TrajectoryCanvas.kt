package com.example.iotproject.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.iotproject.data.model.LocationData
import com.example.iotproject.data.model.PdrState
import com.example.iotproject.data.model.TrajectoryPoint
import com.example.iotproject.ui.theme.*
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max

@Composable
fun TrajectoryCanvas(
    groundTruthHistory: List<TrajectoryPoint>,
    pdrHistory: List<TrajectoryPoint>,
    currentGroundTruth: LocationData?,
    currentPdr: PdrState,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(280.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Slate800)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val origin = remember(groundTruthHistory, pdrHistory) {
                groundTruthHistory.firstOrNull() ?: pdrHistory.firstOrNull()
            }

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                val canvasWidth = size.width
                val canvasHeight = size.height
                val centerOffset = Offset(canvasWidth / 2f, canvasHeight / 2f)

                val gridColor = Slate700.copy(alpha = 0.5f)
                val numGridLines = 6
                for (i in 0..numGridLines) {
                    val x = i * (canvasWidth / numGridLines)
                    val y = i * (canvasHeight / numGridLines)
                    drawLine(
                        color = gridColor,
                        start = Offset(x, 0f),
                        end = Offset(x, canvasHeight),
                        strokeWidth = 1f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f))
                    )
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(canvasWidth, y),
                        strokeWidth = 1f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f))
                    )
                }

                drawLine(
                    color = Slate600,
                    start = Offset(centerOffset.x, centerOffset.y - 12f),
                    end = Offset(centerOffset.x, centerOffset.y + 12f),
                    strokeWidth = 2f
                )
                drawLine(
                    color = Slate600,
                    start = Offset(centerOffset.x - 12f, centerOffset.y),
                    end = Offset(centerOffset.x + 12f, centerOffset.y),
                    strokeWidth = 2f
                )

                if (origin != null) {
                    val earthRadius = 6371000.0

                    fun latLonToMeters(lat: Double, lon: Double): Offset {
                        val dLat = Math.toRadians(lat - origin.latitude)
                        val dLon = Math.toRadians(lon - origin.longitude)
                        val northMeters = (dLat * earthRadius).toFloat()
                        val eastMeters = (dLon * earthRadius * cos(Math.toRadians(origin.latitude))).toFloat()
                        return Offset(eastMeters, northMeters)
                    }

                    val allPoints = groundTruthHistory + pdrHistory
                    var maxDist = 25f
                    allPoints.forEach { pt ->
                        val m = latLonToMeters(pt.latitude, pt.longitude)
                        maxDist = max(maxDist, max(abs(m.x), abs(m.y)))
                    }

                    val pixelsPerMeter = (canvasWidth.coerceAtMost(canvasHeight) * 0.42f) / maxDist

                    fun metersToScreen(meters: Offset): Offset {
                        return Offset(
                            centerOffset.x + meters.x * pixelsPerMeter,
                            centerOffset.y - meters.y * pixelsPerMeter
                        )
                    }

                    // 1. Draw Ground Truth Path (Teal)
                    if (groundTruthHistory.size > 1) {
                        val gtPath = Path()
                        val firstScreen = metersToScreen(latLonToMeters(groundTruthHistory.first().latitude, groundTruthHistory.first().longitude))
                        gtPath.moveTo(firstScreen.x, firstScreen.y)

                        for (i in 1 until groundTruthHistory.size) {
                            val pt = groundTruthHistory[i]
                            val s = metersToScreen(latLonToMeters(pt.latitude, pt.longitude))
                            gtPath.lineTo(s.x, s.y)
                        }

                        drawPath(
                            path = gtPath,
                            color = TealAccent,
                            style = Stroke(width = 4f)
                        )
                    }

                    // 2. Draw PDR Estimated Path (Coral / Orange)
                    if (pdrHistory.size > 1) {
                        val pdrPath = Path()
                        val firstScreen = metersToScreen(latLonToMeters(pdrHistory.first().latitude, pdrHistory.first().longitude))
                        pdrPath.moveTo(firstScreen.x, firstScreen.y)

                        for (i in 1 until pdrHistory.size) {
                            val pt = pdrHistory[i]
                            val s = metersToScreen(latLonToMeters(pt.latitude, pt.longitude))
                            pdrPath.lineTo(s.x, s.y)
                        }

                        drawPath(
                            path = pdrPath,
                            color = CoralOrange,
                            style = Stroke(
                                width = 4f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 6f))
                            )
                        )
                    }

                    // 3. Draw Live Error Vector Line
                    if (currentGroundTruth != null && currentPdr.estimatedLatitude != 0.0) {
                        val gtScreen = metersToScreen(latLonToMeters(currentGroundTruth.latitude, currentGroundTruth.longitude))
                        val pdrScreen = metersToScreen(latLonToMeters(currentPdr.estimatedLatitude, currentPdr.estimatedLongitude))

                        drawLine(
                            color = RoseError.copy(alpha = 0.8f),
                            start = gtScreen,
                            end = pdrScreen,
                            strokeWidth = 2f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                        )

                        drawCircle(color = CoralOrange, radius = 7f, center = pdrScreen)
                        drawCircle(color = Color.White, radius = 3f, center = pdrScreen)

                        drawCircle(color = TealAccent, radius = 7f, center = gtScreen)
                        drawCircle(color = Color.White, radius = 3f, center = gtScreen)
                    }

                    drawCircle(color = EmeraldGreen, radius = 5f, center = centerOffset)
                }
            }

            // Legend Overlay
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LegendChip(color = TealAccent, label = "Ground Truth (GPS)")
                LegendChip(color = CoralOrange, label = "Estimated (PDR)")
            }

            Surface(
                color = Slate900.copy(alpha = 0.8f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp)
            ) {
                Text(
                    text = "2D Trajectory View (N ▲)",
                    style = MaterialTheme.typography.labelSmall,
                    color = Slate400,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
fun LegendChip(color: Color, label: String) {
    Surface(
        color = Slate900.copy(alpha = 0.75f),
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(color, CircleShape)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Slate200
            )
        }
    }
}
