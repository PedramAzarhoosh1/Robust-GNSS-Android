package com.example.iotproject.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.iotproject.data.model.LocationData
import com.example.iotproject.data.model.PdrState
import com.example.iotproject.data.model.TrajectoryPoint

@Composable
fun NeshanMapView(
    groundTruthHistory: List<TrajectoryPoint>,
    pdrHistory: List<TrajectoryPoint>,
    currentGroundTruth: LocationData?,
    currentPdr: PdrState,
    modifier: Modifier = Modifier
) {
    LiveMapView(
        groundTruthHistory = groundTruthHistory,
        pdrHistory = pdrHistory,
        currentGroundTruth = currentGroundTruth,
        currentPdr = currentPdr,
        modifier = modifier
    )
}

