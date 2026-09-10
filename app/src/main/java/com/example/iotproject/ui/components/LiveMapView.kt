package com.example.iotproject.ui.components

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.iotproject.data.model.LocationData
import com.example.iotproject.data.model.PdrState
import com.example.iotproject.data.model.TrajectoryPoint
import com.example.iotproject.ui.theme.*
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

// 1. OpenStreetMap Humanitarian (HOT) - Full Persian & English Typography, NO Watermark, NO 403
val OsmHotTileSource = XYTileSource(
    "OpenStreetMapHOT",
    0, 19, 256, ".png",
    arrayOf(
        "https://a.tile.openstreetmap.fr/hot/",
        "https://b.tile.openstreetmap.fr/hot/",
        "https://c.tile.openstreetmap.fr/hot/"
    )
)

// 2. OpenStreetMap High-Performance Community Engine (DE) - Persian Labels, NO Watermark
val OsmDeTileSource = XYTileSource(
    "OpenStreetMapDE",
    0, 19, 256, ".png",
    arrayOf(
        "https://tile.openstreetmap.de/"
    )
)

// 3. CartoDB Positron (Clean Light)
val CartoPositronTileSource = XYTileSource(
    "CartoPositron",
    0, 20, 256, ".png",
    arrayOf(
        "https://a.basemaps.cartocdn.com/light_all/",
        "https://b.basemaps.cartocdn.com/light_all/",
        "https://c.basemaps.cartocdn.com/light_all/",
        "https://d.basemaps.cartocdn.com/light_all/"
    )
)

// 4. CartoDB Dark Matter
val CartoDarkTileSource = XYTileSource(
    "CartoDark",
    0, 20, 256, ".png",
    arrayOf(
        "https://a.basemaps.cartocdn.com/dark_all/",
        "https://b.basemaps.cartocdn.com/dark_all/",
        "https://c.basemaps.cartocdn.com/dark_all/",
        "https://d.basemaps.cartocdn.com/dark_all/"
    )
)

@Composable
fun LiveMapView(
    groundTruthHistory: List<TrajectoryPoint>,
    pdrHistory: List<TrajectoryPoint>,
    currentGroundTruth: LocationData?,
    currentPdr: PdrState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Initialize OSMDroid with custom User-Agent to completely bypass any 403 blocks
    LaunchedEffect(Unit) {
        Configuration.getInstance().load(context, context.getSharedPreferences("osmdroid_pref", Context.MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = "IOTProject/1.0 (Android; Resilient Positioning System)"
    }

    var mapViewInstance by remember { mutableStateOf<MapView?>(null) }
    var followMode by remember { mutableStateOf(true) }
    var currentTileIndex by remember { mutableIntStateOf(0) }

    val tileSources = remember {
        listOf(
            OsmHotTileSource to "Persian Streets (HOT)",
            OsmDeTileSource to "OSM HD (DE)",
            CartoDarkTileSource to "Dark Theme",
            CartoPositronTileSource to "Light Theme"
        )
    }

    val isInsideIran = remember(currentGroundTruth) {
        val lat = currentGroundTruth?.latitude ?: 35.6892
        val lon = currentGroundTruth?.longitude ?: 51.3890
        lat in 25.0..40.0 && lon in 44.0..64.0
    }

    // Overlays
    val gtPolyline = remember {
        Polyline().apply {
            outlinePaint.color = Color.parseColor("#14b8a6")
            outlinePaint.strokeWidth = 9f
            outlinePaint.strokeCap = Paint.Cap.ROUND
            outlinePaint.strokeJoin = Paint.Join.ROUND
            title = "Ground Truth (GPS)"
        }
    }

    val pdrPolyline = remember {
        Polyline().apply {
            outlinePaint.color = Color.parseColor("#f97316")
            outlinePaint.strokeWidth = 9f
            outlinePaint.strokeCap = Paint.Cap.ROUND
            outlinePaint.strokeJoin = Paint.Join.ROUND
            title = "PDR Estimated Track"
        }
    }

    val errorPolyline = remember {
        Polyline().apply {
            outlinePaint.color = Color.parseColor("#f43f5e")
            outlinePaint.strokeWidth = 4.5f
            outlinePaint.strokeCap = Paint.Cap.ROUND
            title = "Distance Error"
        }
    }

    var gtMarker by remember { mutableStateOf<Marker?>(null) }
    var pdrMarker by remember { mutableStateOf<Marker?>(null) }

    // Sanitize valid coordinates
    val validGtPoints = remember(groundTruthHistory) {
        groundTruthHistory.filter { it.latitude > 1.0 && it.longitude > 1.0 }
    }
    val validPdrPoints = remember(pdrHistory) {
        pdrHistory.filter { it.latitude > 1.0 && it.longitude > 1.0 }
    }

    // Manage MapView Lifecycle
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapViewInstance?.onResume()
                Lifecycle.Event.ON_PAUSE -> mapViewInstance?.onPause()
                Lifecycle.Event.ON_DESTROY -> mapViewInstance?.onDetach()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapViewInstance?.onDetach()
        }
    }

    // Update Ground Truth Polyline
    LaunchedEffect(validGtPoints, mapViewInstance) {
        val map = mapViewInstance ?: return@LaunchedEffect
        val pts = validGtPoints.map { GeoPoint(it.latitude, it.longitude) }
        gtPolyline.setPoints(pts)
        if (!map.overlays.contains(gtPolyline)) {
            map.overlays.add(gtPolyline)
        }
        map.invalidate()
    }

    // Update PDR Polyline
    LaunchedEffect(validPdrPoints, mapViewInstance) {
        val map = mapViewInstance ?: return@LaunchedEffect
        val pts = validPdrPoints.map { GeoPoint(it.latitude, it.longitude) }
        pdrPolyline.setPoints(pts)
        if (!map.overlays.contains(pdrPolyline)) {
            map.overlays.add(pdrPolyline)
        }
        map.invalidate()
    }

    // Update Markers, Error Line & Camera Follow
    LaunchedEffect(currentGroundTruth, currentPdr.estimatedLatitude, currentPdr.estimatedLongitude, followMode, mapViewInstance) {
        val map = mapViewInstance ?: return@LaunchedEffect

        // 1. Error Line
        if (currentGroundTruth != null && currentPdr.estimatedLatitude > 1.0) {
            val errPts = listOf(
                GeoPoint(currentGroundTruth.latitude, currentGroundTruth.longitude),
                GeoPoint(currentPdr.estimatedLatitude, currentPdr.estimatedLongitude)
            )
            errorPolyline.setPoints(errPts)
            if (!map.overlays.contains(errorPolyline)) {
                map.overlays.add(errorPolyline)
            }
        } else {
            errorPolyline.setPoints(emptyList())
        }

        // 2. Ground Truth Marker
        if (currentGroundTruth != null && currentGroundTruth.latitude > 1.0) {
            val gtGeo = GeoPoint(currentGroundTruth.latitude, currentGroundTruth.longitude)
            if (gtMarker == null) {
                gtMarker = Marker(map).apply {
                    position = gtGeo
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    title = "GPS Satellite Fix (±${String.format("%.1f", currentGroundTruth.accuracy)}m)"
                }
                map.overlays.add(gtMarker)
            } else {
                gtMarker?.position = gtGeo
                gtMarker?.title = "GPS Satellite Fix (±${String.format("%.1f", currentGroundTruth.accuracy)}m)"
            }
        }

        // 3. PDR Marker
        if (currentPdr.estimatedLatitude > 1.0) {
            val pdrGeo = GeoPoint(currentPdr.estimatedLatitude, currentPdr.estimatedLongitude)
            if (pdrMarker == null) {
                pdrMarker = Marker(map).apply {
                    position = pdrGeo
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    title = "PDR Dead Reckoning (${currentPdr.totalSteps} steps)"
                }
                map.overlays.add(pdrMarker)
            } else {
                pdrMarker?.position = pdrGeo
                pdrMarker?.title = "PDR Dead Reckoning (${currentPdr.totalSteps} steps)"
            }
        }

        // 4. Camera Follow
        if (followMode) {
            val targetLat = if (currentPdr.isPdrActive && currentPdr.estimatedLatitude > 1.0)
                currentPdr.estimatedLatitude
            else
                currentGroundTruth?.latitude?.takeIf { it > 1.0 } ?: currentPdr.estimatedLatitude

            val targetLon = if (currentPdr.isPdrActive && currentPdr.estimatedLongitude > 1.0)
                currentPdr.estimatedLongitude
            else
                currentGroundTruth?.longitude?.takeIf { it > 1.0 } ?: currentPdr.estimatedLongitude

            if (targetLat > 1.0 && targetLon > 1.0) {
                map.controller.animateTo(GeoPoint(targetLat, targetLon))
            }
        }

        map.invalidate()
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                Configuration.getInstance().load(ctx, ctx.getSharedPreferences("osmdroid_pref", Context.MODE_PRIVATE))
                Configuration.getInstance().userAgentValue = "IOTProject/1.0 (Android; Resilient Positioning System)"

                MapView(ctx).apply {
                    setTileSource(tileSources[currentTileIndex].first)
                    setMultiTouchControls(true)
                    isTilesScaledToDpi = true

                    // Disable built-in zoom controls to use our custom modern Jetpack Compose buttons
                    zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)

                    val initialLat = currentGroundTruth?.latitude?.takeIf { it > 1.0 } ?: 35.6997
                    val initialLon = currentGroundTruth?.longitude?.takeIf { it > 1.0 } ?: 51.3380

                    controller.setZoom(17.5)
                    controller.setCenter(GeoPoint(initialLat, initialLon))

                    mapViewInstance = this
                }
            }
        )

        // Floating Compact Map Controls (Top-Right Toolbar - Never covers bottom HUD)
        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 12.dp, top = 12.dp),
            color = Slate900.copy(alpha = 0.90f),
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Slate700.copy(alpha = 0.6f)),
            shadowElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(3.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Recenter / Follow Mode
                IconButton(
                    onClick = {
                        followMode = true
                        val targetLat = if (currentPdr.isPdrActive && currentPdr.estimatedLatitude > 1.0)
                            currentPdr.estimatedLatitude
                        else
                            currentGroundTruth?.latitude?.takeIf { it > 1.0 } ?: currentPdr.estimatedLatitude

                        val targetLon = if (currentPdr.isPdrActive && currentPdr.estimatedLongitude > 1.0)
                            currentPdr.estimatedLongitude
                        else
                            currentGroundTruth?.longitude?.takeIf { it > 1.0 } ?: currentPdr.estimatedLongitude

                        if (targetLat > 1.0 && targetLon > 1.0) {
                            mapViewInstance?.controller?.animateTo(GeoPoint(targetLat, targetLon), 17.5, 400L)
                        }
                    },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = "Recenter Map",
                        tint = if (followMode) CyanAccent else Slate400,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Jump to Tehran (when testing on emulator or outside Iran)
                if (!isInsideIran) {
                    IconButton(
                        onClick = {
                            followMode = false
                            mapViewInstance?.controller?.animateTo(GeoPoint(35.6997, 51.3380), 16.5, 500L)
                        },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationCity,
                            contentDescription = "Tehran",
                            tint = CoralOrange,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Switch Map Style
                IconButton(
                    onClick = {
                        currentTileIndex = (currentTileIndex + 1) % tileSources.size
                        mapViewInstance?.setTileSource(tileSources[currentTileIndex].first)
                    },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Layers,
                        contentDescription = "Switch Map Style",
                        tint = Slate300,
                        modifier = Modifier.size(18.dp)
                    )
                }

                HorizontalDivider(modifier = Modifier.width(20.dp), color = Slate700)

                // Zoom In
                IconButton(
                    onClick = { mapViewInstance?.controller?.zoomIn() },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Zoom In",
                        tint = Slate200,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Zoom Out
                IconButton(
                    onClick = { mapViewInstance?.controller?.zoomOut() },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = "Zoom Out",
                        tint = Slate200,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
