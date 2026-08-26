package com.example.iotproject.data.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.example.iotproject.data.model.GnssConstellationSummary
import com.example.iotproject.data.model.LocationData
import com.example.iotproject.data.model.SatelliteInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class LocationDataManager(private val context: Context) : LocationListener {

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val _currentLocation = MutableStateFlow<LocationData?>(null)
    val currentLocation: StateFlow<LocationData?> = _currentLocation.asStateFlow()

    private val _gnssSummary = MutableStateFlow(GnssConstellationSummary())
    val gnssSummary: StateFlow<GnssConstellationSummary> = _gnssSummary.asStateFlow()

    var isTracking: Boolean = false
        private set

    private var gnssStatusCallback: GnssStatus.Callback? = null

    @SuppressLint("MissingPermission")
    fun startLocationUpdates(minTimeMs: Long = 1000L, minDistanceM: Float = 0f) {
        if (isTracking) return
        isTracking = true

        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    minTimeMs,
                    minDistanceM,
                    this,
                    Looper.getMainLooper()
                )
            }

            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    minTimeMs,
                    minDistanceM,
                    this,
                    Looper.getMainLooper()
                )
            }

            registerGnssStatusCallback()
        } catch (e: SecurityException) {
            e.printStackTrace()
            isTracking = false
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stopLocationUpdates() {
        if (!isTracking) return
        try {
            locationManager.removeUpdates(this)
            unregisterGnssStatusCallback()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        isTracking = false
    }

    @SuppressLint("MissingPermission")
    private fun registerGnssStatusCallback() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            gnssStatusCallback = object : GnssStatus.Callback() {
                override fun onSatelliteStatusChanged(status: GnssStatus) {
                    val count = status.satelliteCount
                    var usedCount = 0
                    var gps = 0
                    var glonass = 0
                    var galileo = 0
                    var beidou = 0
                    var other = 0
                    var totalCn0 = 0f

                    val satList = mutableListOf<SatelliteInfo>()

                    for (i in 0 until count) {
                        val cType = status.getConstellationType(i)
                        val constellationName = when (cType) {
                            GnssStatus.CONSTELLATION_GPS -> { gps++; "GPS" }
                            GnssStatus.CONSTELLATION_GLONASS -> { glonass++; "GLONASS" }
                            GnssStatus.CONSTELLATION_GALILEO -> { galileo++; "Galileo" }
                            GnssStatus.CONSTELLATION_BEIDOU -> { beidou++; "BeiDou" }
                            GnssStatus.CONSTELLATION_QZSS -> { other++; "QZSS" }
                            GnssStatus.CONSTELLATION_SBAS -> { other++; "SBAS" }
                            else -> { other++; "Unknown" }
                        }

                        val used = status.usedInFix(i)
                        if (used) usedCount++

                        val cn0 = status.getCn0DbHz(i)
                        totalCn0 += cn0

                        satList.add(
                            SatelliteInfo(
                                svid = status.getSvid(i),
                                constellationType = constellationName,
                                cn0DbHz = cn0,
                                elevationDegrees = status.getElevationDegrees(i),
                                azimuthDegrees = status.getAzimuthDegrees(i),
                                usedInFix = used,
                                hasEphemeris = status.hasEphemerisData(i),
                                hasAlmanac = status.hasAlmanacData(i)
                            )
                        )
                    }

                    val avgCn0 = if (count > 0) totalCn0 / count else 0f

                    _gnssSummary.value = GnssConstellationSummary(
                        totalSatellites = count,
                        usedInFixCount = usedCount,
                        gpsCount = gps,
                        glonassCount = glonass,
                        galileoCount = galileo,
                        beidouCount = beidou,
                        otherCount = other,
                        avgCn0 = avgCn0,
                        satellites = satList
                    )
                }
            }

            gnssStatusCallback?.let {
                locationManager.registerGnssStatusCallback(it, Handler(Looper.getMainLooper()))
            }
        }
    }

    private fun unregisterGnssStatusCallback() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && gnssStatusCallback != null) {
            locationManager.unregisterGnssStatusCallback(gnssStatusCallback!!)
            gnssStatusCallback = null
        }
    }

    override fun onLocationChanged(location: Location) {
        val summary = _gnssSummary.value
        val locData = LocationData(
            latitude = location.latitude,
            longitude = location.longitude,
            altitude = location.altitude,
            accuracy = location.accuracy,
            speed = if (location.hasSpeed()) location.speed else 0f,
            bearing = if (location.hasBearing()) location.bearing else 0f,
            timestamp = location.time,
            elapsedRealtimeNanos = location.elapsedRealtimeNanos,
            provider = location.provider ?: "gps",
            satellitesTotal = summary.totalSatellites,
            satellitesUsedInFix = summary.usedInFixCount,
            hasSpeed = location.hasSpeed(),
            hasBearing = location.hasBearing(),
            hasAltitude = location.hasAltitude()
        )
        _currentLocation.value = locData
    }

    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}
    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
}
