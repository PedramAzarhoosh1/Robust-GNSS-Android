package com.example.iotproject.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.iotproject.ui.components.*
import com.example.iotproject.ui.viewmodel.MainUiState
import com.example.iotproject.ui.viewmodel.MainViewModel

@Composable
fun DashboardTab(
    uiState: MainUiState,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp)
    ) {
        // 1. Live Presentation Demo Scenarios
        item {
            ScenarioPresetBar(
                currentMode = uiState.faultMode,
                onSelectMode = { mode -> viewModel.setFaultMode(mode) }
            )
        }

        // 2. Real-Time 2D Vector Trajectory Canvas
        item {
            TrajectoryCanvas(
                groundTruthHistory = uiState.groundTruthHistory,
                pdrHistory = uiState.pdrHistory,
                currentGroundTruth = uiState.groundTruthLocation,
                currentPdr = uiState.pdrState
            )
        }

        // 3. Side-by-Side Numerical Comparison: GNSS vs PDR Coordinates & Distance
        item {
            PdrPositionComparisonCard(
                groundTruth = uiState.groundTruthLocation,
                pdrState = uiState.pdrState,
                onResetPdr = { viewModel.resetPdr() }
            )
        }

        // 4. Live Estimation Error Gauge
        item {
            ErrorGaugeCard(
                errorMeters = uiState.pdrState.estimationErrorMeters,
                isPdrActive = uiState.pdrState.isPdrActive,
                onResetPdr = { viewModel.resetPdr() }
            )
        }

        // 5. GNSS Integrity Verdict
        item {
            IntegrityBannerCard(assessment = uiState.assessment)
        }

        // 6. Fault Injection Detailed Controls
        item {
            FaultInjectionCard(
                currentMode = uiState.faultMode,
                onSelectMode = { mode -> viewModel.setFaultMode(mode) }
            )
        }
    }
}
