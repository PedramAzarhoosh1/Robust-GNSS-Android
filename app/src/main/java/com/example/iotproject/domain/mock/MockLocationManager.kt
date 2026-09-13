package com.example.iotproject.domain.mock

import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock

class MockLocationManager(private val context: Context) {

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val providerName = LocationManager.GPS_PROVIDER

    var isMockEnabled: Boolean = false
        private set

    var lastErrorMessage: String? = null
        private set

    /**
     * Enables system-level Test Provider in Android OS.
     * Requires the app to be selected as "Mock Location App" in Android Developer Options.
     */
    fun enableMockLocation(): Boolean {
        try {
            locationManager.addTestProvider(
                providerName,
                false, // requiresNetwork
                false, // requiresSatellite
                false, // requiresCell
                false, // hasMonetaryCost
                true,  // supportsAltitude
                true,  // supportsSpeed
                true,  // supportsBearing
                1,     // powerRequirement (1 = low)
                1      // accuracy (1 = fine)
            )
            locationManager.setTestProviderEnabled(providerName, true)
            isMockEnabled = true
            lastErrorMessage = null
            return true
        } catch (e: SecurityException) {
            lastErrorMessage = "Permission denied. Enable Developer Options -> Select Mock Location App -> IOTProject."
            isMockEnabled = false
            return false
        } catch (e: Exception) {
            // Already registered or provider active
            try {
                locationManager.setTestProviderEnabled(providerName, true)
                isMockEnabled = true
                lastErrorMessage = null
                return true
            } catch (ex: Exception) {
                lastErrorMessage = ex.localizedMessage ?: "Failed to enable test provider"
                isMockEnabled = false
                return false
            }
        }
    }

    /**
     * Publishes PDR estimated coordinate to Android OS as the active system GPS location.
     */
    fun publishMockLocation(
        latitude: Double,
        longitude: Double,
        altitude: Double = 0.0,
        accuracy: Float = 3.0f,
        speed: Float = 1.0f,
        bearing: Float = 0.0f
    ) {
        if (!isMockEnabled || latitude == 0.0 || longitude == 0.0) return

        try {
            val mockLocation = Location(providerName).apply {
                this.latitude = latitude
                this.longitude = longitude
                this.altitude = altitude
                this.accuracy = accuracy
                this.speed = speed
                this.bearing = bearing
                this.time = System.currentTimeMillis()
                this.elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    this.bearingAccuracyDegrees = 5.0f
                    this.verticalAccuracyMeters = 3.0f
                    this.speedAccuracyMetersPerSecond = 0.5f
                }
            }
            locationManager.setTestProviderLocation(providerName, mockLocation)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Cleans up and disables test provider.
     */
    fun disableMockLocation() {
        try {
            locationManager.setTestProviderEnabled(providerName, false)
            locationManager.removeTestProvider(providerName)
        } catch (e: Exception) {
            // ignore cleanup errors
        } finally {
            isMockEnabled = false
        }
    }
}
