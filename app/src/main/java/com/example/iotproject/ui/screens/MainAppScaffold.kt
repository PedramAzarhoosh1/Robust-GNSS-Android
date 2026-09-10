package com.example.iotproject.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.iotproject.ui.components.PulsingStatusBadge
import com.example.iotproject.ui.screens.tabs.DashboardTab
import com.example.iotproject.ui.screens.tabs.LogsTab
import com.example.iotproject.ui.screens.tabs.MapTab
import com.example.iotproject.ui.screens.tabs.SatellitesTab
import com.example.iotproject.ui.screens.tabs.SensorsTab
import com.example.iotproject.ui.theme.*
import com.example.iotproject.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScaffold(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    val permissionsToRequest = remember {
        val list = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            list.add(Manifest.permission.ACTIVITY_RECOGNITION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        list.toTypedArray()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val fineLocation = results[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocation = results[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        val locationGranted = fineLocation || coarseLocation
        val sensorGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            results[Manifest.permission.ACTIVITY_RECOGNITION] ?: false
        } else true

        viewModel.onPermissionsGranted(locationGranted, sensorGranted)
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(permissionsToRequest)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Robust GNSS & PDR",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Slate100
                        )
                        Text(
                            text = "Resilient Positioning & Fault Injection",
                            style = MaterialTheme.typography.bodySmall,
                            color = CyanAccent
                        )
                    }
                },
                actions = {
                    if (uiState.isRecording) {
                        Surface(
                            color = RoseError.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(RoseError)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "REC",
                                    color = RoseError,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                    PulsingStatusBadge(
                        state = uiState.assessment.state,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Slate900)
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = Slate900,
                tonalElevation = 8.dp
            ) {
                val navItems = listOf(
                    NavItem("Dashboard", Icons.Default.Navigation, 0),
                    NavItem("Live Map", Icons.Default.Map, 1),
                    NavItem("PDR & IMU", Icons.Default.DirectionsWalk, 2),
                    NavItem("Satellites", Icons.Default.Language, 3),
                    NavItem("Datasets", Icons.Default.Folder, 4)
                )

                navItems.forEach { item ->
                    val selected = uiState.selectedTab == item.index
                    NavigationBarItem(
                        selected = selected,
                        onClick = { viewModel.setSelectedTab(item.index) },
                        icon = {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.title,
                                tint = if (selected) CyanAccent else Slate400
                            )
                        },
                        label = {
                            Text(
                                text = item.title,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) CyanAccent else Slate400,
                                fontSize = 10.sp
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = DarkPrimaryContainer
                        )
                    )
                }
            }
        },
        containerColor = Slate900
    ) { padding ->
        Crossfade(
            targetState = uiState.selectedTab,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            label = "tabTransition"
        ) { tabIndex ->
            when (tabIndex) {
                0 -> DashboardTab(uiState = uiState, viewModel = viewModel)
                1 -> MapTab(uiState = uiState, viewModel = viewModel)
                2 -> SensorsTab(sensors = uiState.sensors, pdrState = uiState.pdrState)
                3 -> SatellitesTab(summary = uiState.gnssSummary)
                4 -> LogsTab(uiState = uiState, viewModel = viewModel)
                else -> DashboardTab(uiState = uiState, viewModel = viewModel)
            }
        }
    }
}

data class NavItem(
    val title: String,
    val icon: ImageVector,
    val index: Int
)
